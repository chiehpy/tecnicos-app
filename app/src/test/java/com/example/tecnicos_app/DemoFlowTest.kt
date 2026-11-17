package com.example.tecnicos_app

import com.enofir.tecnicos_app.core.DemoInterceptor
import com.enofir.tecnicos_app.core.RepuestosSugeridos
import com.enofir.tecnicos_app.model.CatalogsResponse
import com.enofir.tecnicos_app.model.LoginResponse
import com.enofir.tecnicos_app.model.MochilaResponse
import com.enofir.tecnicos_app.model.RecoveredPartItem
import com.enofir.tecnicos_app.model.TerminalEventRequest
import com.enofir.tecnicos_app.model.TerminalEventResponse
import com.enofir.tecnicos_app.model.TerminalLookupResponse
import com.google.gson.Gson
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Recorre el flujo de la demo contra el DemoInterceptor real (sin red, sin Android). */
class DemoFlowTest {

    private val gson = Gson()
    private val jsonType = MediaType.parse("application/json")
    private val client = OkHttpClient.Builder()
        .addInterceptor(DemoInterceptor { File("src/demo/assets/demo_catalogs.json").readText(Charsets.UTF_8) })
        .build()

    private fun get(path: String): String =
        client.newCall(Request.Builder().url("http://demo.invalid/$path").build()).execute().body()!!.string()

    private fun post(path: String, body: Any): String =
        client.newCall(
            Request.Builder().url("http://demo.invalid/$path")
                .post(RequestBody.create(jsonType, gson.toJson(body))).build()
        ).execute().body()!!.string()

    private fun lookup(serial: String) = gson.fromJson(get("terminal/lookup?serial=$serial"), TerminalLookupResponse::class.java)
    private fun event(req: TerminalEventRequest) = gson.fromJson(post("terminal/event", req), TerminalEventResponse::class.java)

    @Test fun flujoReparacionYFilm() {
        // Login
        val login = gson.fromJson(post("auth/login", mapOf("username" to "cualquiera", "pin" to "1234")), LoginResponse::class.java)
        assertTrue(login.ok)
        assertEquals("Demo Técnico", login.user!!.technicianName)
        assertTrue("Reparación" in login.user!!.allowedRoles && "Film" in login.user!!.allowedRoles)
        val payload = login.token!!.split(".")[1]
        assertTrue(String(java.util.Base64.getUrlDecoder().decode(payload)).contains("Demo Técnico"))

        // Lookup
        val l = lookup("NCC000000001").data!!
        assertEquals("N950", l.asset!!.productName)
        assertEquals("Mercado Libre SA", l.cs!!.accountName)
        assertEquals("Carcasa posterior rota, Botón de inicio defectuoso, Tapa de bateria rayada", l.cs!!.failureObservations)
        assertEquals("Reparación Técnica", l.cs!!.status)
        assertEquals("N910", lookup("ABC123").data!!.asset!!.productName)
        assertEquals("Display roto, Tamper", lookup("ABC123").data!!.cs!!.failureObservations)

        // ASSIGN Reparación
        val a = event(TerminalEventRequest(action = "ASSIGN", serial = "NCC000000001", role = "Reparación", technicianName = "Demo Técnico"))
        assertTrue(a.ok && a.verified == true)
        assertEquals(a.status, lookup("NCC000000001").data!!.cs!!.status)   // 2da barrera del EventVerifier

        // Catálogos + mochila (lo que usa el diálogo de Repuestos utilizados)
        val cat = gson.fromJson(get("app/catalogs"), CatalogsResponse::class.java)
        val moch = gson.fromJson(get("app/mochila"), MochilaResponse::class.java)
        assertTrue(moch.tieneMochila && moch.activa)
        val saldo = moch.items.associate { it.repuestoId!!.take(15) to it.cantidad }
        val partes = cat.repairedParts!!["N950_amarilla"]!!
        val sug = RepuestosSugeridos.parse(cat.partsSuggestions)
        assertNotNull(sug)
        val fallas = RepuestosSugeridos.fallasDelCaso(l.cs!!.failureObservations!!.split(","), emptyList())
        val r = RepuestosSugeridos.calcular(
            partes, "N950_amarilla", fallas, sug, { it.usageCount ?: 0 },
            { p: RecoveredPartItem -> saldo[p.sfId!!.take(15)] ?: 0 },
        )
        println("DEMO orden dialogo (primeros 6): " + r.orden.take(6).joinToString { partes[it].name + "[" + partes[it].pn + ", mochila=" + (saldo[partes[it].sfId!!.take(15)] ?: 0) + "]" })
        println("DEMO pretildadas: " + r.pretildar.joinToString { partes[it].pn })
        assertEquals("111018119U", partes[r.orden.first()].pn)          // carcasa posterior amarilla USADA (saldo 3)
        assertTrue(r.pretildar.any { partes[it].pn == "111018119U" })
        assertTrue(r.pretildar.all { (saldo[partes[it].sfId!!.take(15)] ?: 0) > 0 })

        // COMPLETE Reparación con repuestos
        val c = event(TerminalEventRequest(action = "COMPLETE", serial = "NCC000000001", role = "Reparación",
            spareParts = listOf("a03Vr00000lGEHkIAO"), caseId = "500x", initialDiagnosis = listOf("Display roto")))
        assertTrue(c.ok && c.verified == true)
        assertEquals("Limpieza", lookup("NCC000000001").data!!.cs!!.status)

        // Film: ASSIGN + COMPLETE
        assertTrue(event(TerminalEventRequest(action = "ASSIGN", serial = "NCB000000002", role = "Film", technicianName = "Demo Técnico")).ok)
        val f = event(TerminalEventRequest(action = "COMPLETE", serial = "NCB000000002", role = "Film"))
        assertTrue(f.ok && f.verified == true)
        assertEquals(f.status, lookup("NCB000000002").data!!.cs!!.status)

        // Otros GET: vacíos válidos
        assertTrue(get("app/mis-pendientes").contains("\"pendientes\":[]"))
        assertTrue(get("terminal/history").contains("\"data\":[]"))
        assertTrue(get("app/version").contains("\"ok\":true"))
    }
}
