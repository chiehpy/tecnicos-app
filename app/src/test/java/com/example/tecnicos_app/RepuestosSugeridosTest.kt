package com.example.tecnicos_app

import com.enofir.tecnicos_app.core.RepuestosSugeridos
import com.enofir.tecnicos_app.core.RepuestosSugeridos.Candidato
import com.enofir.tecnicos_app.core.RepuestosSugeridos.Sugerencias
import com.enofir.tecnicos_app.model.RecoveredPartItem
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class RepuestosSugeridosTest {

    private fun p(pn: String, name: String, usage: Int = 0) = RecoveredPartItem(pn, name, usageCount = usage)

    private val estetico = listOf("Carcasa", "Tapa", "Botones", "Boton", "Lente de camara", "Zebra", "Goma", "Almohadilla")

    private val sug = Sugerencias(
        estetico,
        mapOf(
            "N950" to mapOf(
                "Carcasa posterior rota" to listOf(Candidato("Carcasa posterior", true), Candidato("Tapa impresora", false)),
                "Botón de inicio defectuoso" to listOf(Candidato("Flex BEyF", true)),
            ),
            "N910" to mapOf("Display roto" to listOf(Candidato("Display", true))),
        )
    )

    // Catálogo N950 mezclado (orden original); usage_count más alto en lo que NO se sugiere
    private val n950 = listOf(
        p("A1", "Display", 50),
        p("C1", "Carcasa posterior (amarilla)", 3),
        p("C1U", "Carcasa posterior (amarilla)", 2),
        p("C2U", "Carcasa posterior (blanca)", 9),
        p("C2", "Carcasa posterior (blanca)", 1),
        p("T1", "Tapa impresora", 4),
        p("F1", "Flex BEyF", 5),
        p("F1U", "Flex BEyF", 6),
    )

    private fun calc(
        partes: List<RecoveredPartItem>, clave: String, fallas: List<String>, s: Sugerencias? = sug,
        saldo: (RecoveredPartItem) -> Int? = { null }, previa: Boolean = false,
    ) = RepuestosSugeridos.calcular(partes, clave, fallas, s, { it.usageCount ?: 0 }, saldo, previa)

    private fun RepuestosSugeridos.Resultado.pn(partes: List<RecoveredPartItem>) = orden.map { partes[it].pn }
    private fun RepuestosSugeridos.Resultado.tildadas(partes: List<RecoveredPartItem>) = pretildar.map { partes[it].pn }.toSet()

    @Test fun carcasaPosteriorRotaN950AmarillaEligeUsadaYLaTilda() {
        val r = calc(n950, "N950_amarilla", listOf("Carcasa posterior rota"))
        assertEquals(listOf("C1U", "T1"), r.pn(n950).take(2))
        assertEquals(setOf("C1U"), r.tildadas(n950))
    }

    @Test fun carcasaPosteriorRotaN950CelesteEligeBlancaUsada() {
        val r = calc(n950, "N950_celeste", listOf("Carcasa posterior rota"))
        assertEquals("C2U", r.pn(n950).first())
        assertEquals(setOf("C2U"), r.tildadas(n950))
    }

    @Test fun fallaFuncionalEligeLaNueva() {
        val r = calc(n950, "N950_amarilla", listOf("Botón de inicio defectuoso"))
        assertEquals("F1", r.pn(n950).first())
        assertEquals(setOf("F1"), r.tildadas(n950))
    }

    @Test fun conMochilaActivaYUsadaEnCeroCaeALaNueva() {
        val saldo: (RecoveredPartItem) -> Int? = { if (it.pn == "C1U") 0 else 2 }
        val r = calc(n950, "N950_amarilla", listOf("Carcasa posterior rota"), saldo = saldo)
        assertEquals("C1", r.pn(n950).first())
        assertEquals(setOf("C1"), r.tildadas(n950))
    }

    @Test fun mochilaActivaYUnicaVarianteSinSaldoSeSugierePeroNoSeTilda() {
        val partes = listOf(p("X", "Display"), p("Y", "Otro"))
        val s = Sugerencias(estetico, mapOf("N910" to mapOf("Display roto" to listOf(Candidato("Display", true)))))
        val r = calc(partes, "N910", listOf("Display roto"), s, saldo = { if (it.pn == "X") 0 else 1 })
        assertEquals(listOf("Y", "X"), r.pn(partes))   // sin saldo: abajo de todo
        assertTrue(r.pretildar.isEmpty())
    }

    @Test fun seleccionPreviaNoSePisaPeroElOrdenSeAplica() {
        val r = calc(n950, "N950_amarilla", listOf("Carcasa posterior rota"), previa = true)
        assertEquals("C1U", r.pn(n950).first())
        assertTrue(r.pretildar.isEmpty())
    }

    @Test fun sinSugerenciasOSinFallasEsElOrdenDeHoy() {
        val hoy = n950.sortedByDescending { it.usageCount ?: 0 }.map { it.pn }
        assertEquals(hoy, calc(n950, "N950_amarilla", listOf("Carcasa posterior rota"), s = null).pn(n950))
        val sinFallas = calc(n950, "N950_amarilla", emptyList())
        assertEquals(hoy, sinFallas.pn(n950))
        assertTrue(sinFallas.pretildar.isEmpty())
        assertEquals(hoy, calc(n950, "N950_amarilla", listOf("Falla rara")).pn(n950))
    }

    @Test fun elRestoConservaElOrdenDeHoyYN910UsaSuFamilia() {
        val partes = listOf(p("D", "Display", 1), p("Z", "Zeta", 10), p("Y", "Ye", 5))
        val r = calc(partes, "N910", listOf("Display roto"))
        assertEquals(listOf("D", "Z", "Y"), r.pn(partes))
    }

    @Test fun loQueNoTieneEnLaMochilaVaAbajoAunqueSeaSugerido() {
        val partes = listOf(p("D", "Display", 1), p("Z", "Zeta", 10), p("Y", "Ye", 5))
        val r = calc(partes, "N910", listOf("Display roto"), saldo = { if (it.pn == "D") 0 else 1 })
        assertEquals(listOf("Z", "Y", "D"), r.pn(partes))
    }

    @Test fun fallasDelCasoCombinaSinRepetir() {
        assertEquals(
            listOf("A", "B", "C"),
            RepuestosSugeridos.fallasDelCaso(listOf("A; B"), listOf("B, C", " "))
        )
    }

    @Test fun canonYColorOk() {
        assertEquals("Carcasa posterior", RepuestosSugeridos.canon("Carcasa posterior (Amarilla)"))
        assertTrue(RepuestosSugeridos.colorOk("Tapa", "N950_celeste"))
        assertFalse(RepuestosSugeridos.colorOk("Tapa (amarilla)", "N950_celeste"))
        assertTrue(RepuestosSugeridos.colorOk("Tapa (amarilla)", "N910"))
    }

    @Test fun parseTolerante() {
        val json = "{\"version\":1,\"estetico\":[\"Carcasa\"],\"por_falla\":{\"N950\":{\"F\":[{\"name\":\"X\",\"tildar\":true,\"score\":1.2}]}}}"
        val ok = RepuestosSugeridos.parse(JsonParser.parseString(json))
        assertEquals(listOf(Candidato("X", true)), ok!!.porFalla["N950"]!!["F"])
        assertNull(RepuestosSugeridos.parse(null))
        assertNull(RepuestosSugeridos.parse(JsonParser.parseString("\"basura\"")))
        assertNull(RepuestosSugeridos.parse(JsonParser.parseString("{\"por_falla\":[1,2]}")))
    }
}
