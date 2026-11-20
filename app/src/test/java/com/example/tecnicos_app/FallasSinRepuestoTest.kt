package com.example.tecnicos_app

import com.enofir.tecnicos_app.core.DemoInterceptor
import com.enofir.tecnicos_app.core.FallasSinRepuesto
import com.enofir.tecnicos_app.core.FallasSinRepuesto.Origen
import com.enofir.tecnicos_app.core.FallasSinRepuesto.Pendiente
import com.enofir.tecnicos_app.model.CatalogsResponse
import com.enofir.tecnicos_app.model.TerminalEventRequest
import com.enofir.tecnicos_app.model.TerminalLookupResponse
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Reparación: falla declarada sin el repuesto que la justifica.
 * El mapa sale del catálogo DEMO, que se generó con el módulo del MDW (`fallas_piezas.calcular`, misma regla
 * que `reglas_entrega`); así el test cubre también el formato real de `failure_parts`.
 */
class FallasSinRepuestoTest {

    private val gson = Gson()
    private val demoJson = File("src/demo/assets/demo_catalogs.json").readText(Charsets.UTF_8)
    private val catalogo = gson.fromJson(demoJson, CatalogsResponse::class.java)
    private val mapa = FallasSinRepuesto.parse(catalogo.failureParts)!!

    /** Id que manda la app en spareParts para una parte del catálogo (sfId, o PN si no tiene). */
    private fun id(clave: String, nombre: String): String =
        catalogo.repairedParts!![clave]!!.first { it.name == nombre }.let { it.sfId ?: it.pn }

    private val display = id("N910", "Carcasa frontal + Display")
    private val tapaBateria = id("N910", "Tapa bateria")
    private val impresora = id("N910", "Impresora")
    private val flex = id("N950_amarilla", "Flex BEyF")

    private fun detectar(preTest: List<String>, propias: List<String> = emptyList(), repuestos: List<String> = emptyList()) =
        FallasSinRepuesto.detectar(mapa, preTest, propias, repuestos)

    @Test fun fallaDePretestSinPiezaPregunta() {
        val r = detectar(listOf("Display roto", "Tamper"))
        assertEquals(1, r.size)
        assertEquals("Display roto", r[0].falla)
        assertEquals(Origen.PRETEST, r[0].origen)
        assertEquals(listOf("Carcasa frontal + Display"), r[0].piezas)
    }

    @Test fun fallaPropiaSinPiezaPregunta() {
        val r = detectar(emptyList(), listOf("Tapa de bateria rota"))
        assertEquals(listOf(Pendiente("Tapa de bateria rota", Origen.PROPIA, listOf("Tapa de bateria"))), r)
    }

    @Test fun fallaConSuPiezaNoPregunta() {
        assertTrue(detectar(listOf("Display roto"), listOf("Tapa de bateria rota"), listOf(display, tapaBateria)).isEmpty())
    }

    @Test fun bordesInternosSuciosExigeDisplay() {
        val r = detectar(listOf("Bordes internos sucios"))
        assertEquals(listOf("Carcasa frontal + Display"), r.single().piezas)
        assertTrue(detectar(listOf("Bordes internos sucios"), repuestos = listOf(display)).isEmpty())
    }

    @Test fun fallasSinPiezaNoPreguntan() {
        assertTrue(detectar(listOf("Tamper", "No comunica", "Sin falla", "No enciende")).isEmpty())
    }

    @Test fun filmDanadoNoSePreguntaEnReparacion() {
        assertTrue(detectar(listOf("Film dañado"), listOf("Film dañado")).isEmpty())
    }

    @Test fun esteticaTambienPregunta() {
        // Dueño 20-11: no hay fallas de repuesto opcional; las estéticas también llevan repuesto.
        assertEquals(1, detectar(listOf("Carcasa posterior rayada")).size)
    }

    @Test fun cualquieraDeLasPiezasAlcanza() {
        // «Impresora rota» la justifica la impresora o el soporte.
        assertTrue(detectar(listOf("Impresora rota"), repuestos = listOf(impresora)).isEmpty())
        assertEquals(1, detectar(listOf("Impresora rota")).size)
    }

    @Test fun repuestoYaDeclaradoCuenta() {
        // Un ya declarado llega en spareParts con su Id de SF (18 caracteres): se compara por los primeros 15.
        val n950 = detectar(listOf("Botón de inicio defectuoso"), repuestos = listOf(flex))
        assertTrue(n950.isEmpty())
        val de18 = display.take(15) + "XYZ"
        assertTrue(detectar(listOf("Display roto"), repuestos = listOf(de18)).isEmpty())
    }

    @Test fun normalizaComoElMdw() {
        val r = detectar(listOf("Rechazado por: Display roto."))
        assertEquals("Rechazado por: Display roto.", r.single().falla)
        assertEquals(
            "La falla «Display roto» requiere el reemplazo de «Carcasa frontal + Display», que no figura en los repuestos utilizados.",
            FallasSinRepuesto.mensaje(r.single())
        )
    }

    @Test fun nombreViejoDelLente() {
        assertEquals(listOf("Lente de camara"), detectar(listOf("Lente trasero de camara defectuoso")).single().piezas)
        assertEquals(listOf("Lente de camara"), detectar(listOf("Lente de camara roto")).single().piezas)
    }

    @Test fun unaPorFallaSinRepetirYPretestPrimero() {
        val r = detectar(listOf("Display roto", "Tapa de bateria rota"), listOf("Display roto", "Carcasa posterior rota"))
        assertEquals(listOf("Display roto", "Tapa de bateria rota", "Carcasa posterior rota"), r.map { it.falla })
        assertEquals(listOf(Origen.PRETEST, Origen.PRETEST, Origen.PROPIA), r.map { it.origen })
    }

    @Test fun mensajeConVariasPiezas() {
        val p = Pendiente("Impresora rota", Origen.PRETEST, listOf("Impresora", "Soporte de impresora"))
        assertEquals(
            "La falla «Impresora rota» requiere el reemplazo de «Impresora» o «Soporte de impresora», que no figura en los repuestos utilizados.",
            FallasSinRepuesto.mensaje(p)
        )
        assertEquals("Falla sin repuesto registrado", FallasSinRepuesto.TITULO)
        assertEquals("Descartar falla", FallasSinRepuesto.BOTON_DESCARTAR)
        assertEquals("Registrar repuesto", FallasSinRepuesto.BOTON_REGISTRAR)
    }

    @Test fun aplicarDescartes() {
        val propias = listOf("Carcasa posterior rota", "Display roto", "Tamper")
        val desc = listOf(
            Pendiente("Display roto", Origen.PRETEST, listOf("x")),           // de Pre-test y también tildada
            Pendiente("Carcasa posterior rota", Origen.PROPIA, listOf("x")),
        )
        val e = FallasSinRepuesto.aplicar(propias, desc)
        assertEquals(listOf("Tamper"), e.initialDiagnosis)
        assertEquals(listOf("Display roto"), e.discardedFailures)
        val nada = FallasSinRepuesto.aplicar(propias, emptyList())
        assertEquals(propias, nada.initialDiagnosis)
        assertTrue(nada.discardedFailures.isEmpty())
    }

    @Test fun sinMapaNoPregunta() {
        assertNull(FallasSinRepuesto.parse(null))
        assertNull(FallasSinRepuesto.parse(JsonParser().parse("[]")))
        assertNull(FallasSinRepuesto.parse(JsonParser().parse("""{"por_falla":{"Display roto":["cf"]},"por_repuesto":{}}""")))
        assertTrue(FallasSinRepuesto.detectar(null, listOf("Display roto"), emptyList(), emptyList()).isEmpty())
        // MDW viejo: el catálogo sin la clave se parsea igual y el campo queda null.
        val viejo = gson.fromJson("""{"failure_observations":[],"qa_options":[],"recovered_parts":{},"statuses":[],"substatus_reparacion":[]}""",
            CatalogsResponse::class.java)
        assertNull(viejo.failureParts)
    }

    @Test fun requestSoloLlevaElCampoSiHayDescartes() {
        val sin = gson.toJson(TerminalEventRequest(action = "COMPLETE", serial = "X", role = "Reparación"))
        assertFalse(sin.contains("discardedFailures"))
        val con = gson.toJson(TerminalEventRequest(action = "COMPLETE", serial = "X", role = "Reparación",
            discardedFailures = listOf("Display roto")))
        assertTrue(con.contains("\"discardedFailures\":[\"Display roto\"]"))
    }

    @Test fun demoQuitaLaDescartadaDelCaso() {
        val client = OkHttpClient.Builder().addInterceptor(DemoInterceptor { demoJson }).build()
        fun get(path: String) = client.newCall(Request.Builder().url("http://demo.invalid/$path").build()).execute().body()!!.string()
        val serial = "N7DEMO0000123"
        assertEquals("Display roto, Tamper", gson.fromJson(get("terminal/lookup?serial=$serial"), TerminalLookupResponse::class.java).data!!.cs!!.failureObservations)
        val req = TerminalEventRequest(action = "COMPLETE", serial = serial, role = "Reparación", discardedFailures = listOf("Display roto"))
        val body = RequestBody.create(MediaType.parse("application/json"), gson.toJson(req))
        val resp = client.newCall(Request.Builder().url("http://demo.invalid/terminal/event").post(body).build()).execute().body()!!.string()
        assertTrue(resp.contains("Descartadas: Display roto"))
        assertEquals("Tamper", gson.fromJson(get("terminal/lookup?serial=$serial"), TerminalLookupResponse::class.java).data!!.cs!!.failureObservations)
    }
}
