package com.example.tecnicos_app

import com.enofir.tecnicos_app.core.RepuestosDeclarados
import com.enofir.tecnicos_app.core.RepuestosDeclarados.Declarado
import com.enofir.tecnicos_app.core.RepuestosDeclarados.Efecto
import com.enofir.tecnicos_app.core.RepuestosDeclarados.Renglon
import com.enofir.tecnicos_app.model.RecoveredPartItem
import com.enofir.tecnicos_app.model.TerminalLookupCs
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class RepuestosDeclaradosTest {

    // Ids de 18 en el lookup, de 15 o 18 en el catálogo: se cruzan por los primeros 15.
    private val DISPLAY = "a03Vr00000lGEHkIAO"
    private val BATERIA = "a03Vr00000lGEHlIAO"
    private val FLEX = "a03Vr00000lGEHmIAO"
    private val DESCONOCIDO = "a03Vr00000ZZZZZIAO"

    private val catalogo = listOf(
        RecoveredPartItem("P-DISP", "Display", sfId = DISPLAY.take(15), stock = 7),
        RecoveredPartItem("P-BAT", "Bateria", sfId = BATERIA, stock = 3),
        RecoveredPartItem("P-FLEXU", "Flex BEyF", sfId = FLEX, stock = 0),
    )

    private fun filas(vararg d: Declarado) = RepuestosDeclarados.agrupar(d.toList(), catalogo)

    // ---------- parseo ----------

    @Test fun parse_ausente_null_o_mal_formado_es_vacio() {
        assertTrue(RepuestosDeclarados.parse(null).isEmpty())
        assertTrue(RepuestosDeclarados.parse(JsonParser.parseString("null")).isEmpty())
        assertTrue(RepuestosDeclarados.parse(JsonParser.parseString("{}")).isEmpty())
        assertTrue(RepuestosDeclarados.parse(JsonParser.parseString("\"x\"")).isEmpty())
        assertTrue(RepuestosDeclarados.parse(JsonParser.parseString("[]")).isEmpty())
    }

    @Test fun fuera_del_catalogo_muestra_el_nombre_de_salesforce_no_el_id() {
        // 26-11-2026: el film (fuera del catálogo de Reparación) salía como «Repuesto a03...».
        val json = """[{"sfId":"$DESCONOCIDO","qty":1,"tecnico":"Bruno Hoquigaray","name":"Film pantalla"},
                       {"sfId":"$DISPLAY","qty":1,"tecnico":"Pablo Scarone","name":"Nombre largo SF"}]"""
        val f = RepuestosDeclarados.agrupar(RepuestosDeclarados.parse(JsonParser.parseString(json)), catalogo)
        assertEquals("Film pantalla", f[0].nombre)
        assertEquals("Display", f[1].nombre)   // si está en el catálogo, manda el nombre de la app
        // MDW viejo, sin name: sigue el Id como antes
        assertEquals("Repuesto $DESCONOCIDO", filas(Declarado(DESCONOCIDO, 1, null))[0].nombre)
    }

    @Test fun parse_tolerante_por_elemento() {
        val json = """[
            {"sfId":"$DISPLAY","qty":1,"tecnico":"Pablo Scarone"},
            {"sfId":"$BATERIA","qty":2,"tecnico":null},
            {"sfId":"$FLEX"},
            {"qty":1},
            {"sfId":"  "},
            "basura",
            {"sfId":"$DISPLAY","qty":0,"tecnico":"  "}
        ]"""
        val r = RepuestosDeclarados.parse(JsonParser.parseString(json))
        assertEquals(
            listOf(
                Declarado(DISPLAY, 1, "Pablo Scarone"),
                Declarado(BATERIA, 2, null),
                Declarado(FLEX, 1, null),
                Declarado(DISPLAY, 1, null),
            ),
            r
        )
    }

    @Test fun gson_lookup_sin_declaredParts_no_rompe() {
        val cs = Gson().fromJson("""{"id":"500x","status":"Testeo"}""", TerminalLookupCs::class.java)
        assertEquals("500x", cs.id)
        assertNull(cs.declaredParts)
        assertTrue(RepuestosDeclarados.parse(cs.declaredParts).isEmpty())
    }

    @Test fun gson_lookup_con_declaredParts() {
        val cs = Gson().fromJson(
            """{"id":"500x","declaredParts":[{"sfId":"$DISPLAY","qty":1,"tecnico":"Pablo Scarone"}]}""",
            TerminalLookupCs::class.java
        )
        assertEquals(listOf(Declarado(DISPLAY, 1, "Pablo Scarone")), RepuestosDeclarados.parse(cs.declaredParts))
    }

    @Test fun gson_lookup_declaredParts_con_formato_inesperado_no_rompe() {
        val cs = Gson().fromJson("""{"id":"500x","declaredParts":"no es lista"}""", TerminalLookupCs::class.java)
        assertEquals("500x", cs.id)
        assertTrue(RepuestosDeclarados.parse(cs.declaredParts).isEmpty())
    }

    // ---------- agrupado ----------

    @Test fun agrupa_mismo_repuesto_sumando_cantidades_y_cruza_con_catalogo() {
        val f = filas(
            Declarado(DISPLAY, 1, "Pablo Scarone"),
            Declarado(BATERIA, 1, null),
            Declarado(DISPLAY, 1, "Pablo Scarone"),
        )
        assertEquals(2, f.size)
        assertEquals(DISPLAY, f[0].sfId)
        assertEquals(2, f[0].qty)
        assertEquals("Display", f[0].nombre)
        assertEquals("Pablo Scarone", f[0].tecnico)
        assertSame(catalogo[0], f[0].parte)
        assertEquals("Bateria", f[1].nombre)
    }

    @Test fun tecnico_null_o_vacio_dice_ya_declarado() {
        val f = filas(Declarado(BATERIA, 1, null), Declarado(DISPLAY, 1, "Pablo Scarone"))
        assertEquals("Ya declarado", RepuestosDeclarados.lineaTecnico(f[0]))
        assertEquals("Consumido por Pablo Scarone", RepuestosDeclarados.lineaTecnico(f[1]))
        val vacio = RepuestosDeclarados.Fila(BATERIA, 1, "  ", null)
        assertEquals("Ya declarado", RepuestosDeclarados.lineaTecnico(vacio))
    }

    @Test fun repuesto_fuera_del_catalogo_se_muestra_y_se_manda() {
        val f = filas(Declarado(DESCONOCIDO, 1, "Ana"))
        assertNull(f[0].parte)
        assertEquals("Repuesto $DESCONOCIDO", f[0].nombre)
        assertEquals(listOf(DESCONOCIDO), RepuestosDeclarados.armarEnvio(f, emptyList()))
    }

    // ---------- renglones y tildes ----------

    @Test fun sin_declarados_renglones_iguales_que_hoy() {
        val r = RepuestosDeclarados.renglones(emptyList(), listOf(2, 0, 1))
        assertEquals(listOf(Renglon.Ninguno, Renglon.Parte(2), Renglon.Parte(0), Renglon.Parte(1)), r)
        val sel = RepuestosDeclarados.seleccionInicial(r, false) { it == 0 }
        assertArrayEquals(booleanArrayOf(false, false, true, false), sel)
        assertEquals(emptyList<String>(), RepuestosDeclarados.armarEnvio(emptyList(), emptyList()))
        assertEquals(listOf("X"), RepuestosDeclarados.armarEnvio(emptyList(), listOf("X")))
        assertEquals(setOf(1, 3), RepuestosDeclarados.pretildarSinDeclarados(setOf(1, 3), catalogo, emptyList()))
    }

    @Test fun declarados_arriba_de_todo_y_tildados() {
        val f = filas(Declarado(DISPLAY, 1, "Pablo"), Declarado(BATERIA, 1, null))
        val r = RepuestosDeclarados.renglones(f, listOf(2, 0, 1))
        assertEquals(Renglon.Encabezado(RepuestosDeclarados.ENCABEZADO_DECLARADOS), r[0])
        assertEquals(Renglon.YaDeclarado(f[0]), r[1])
        assertEquals(Renglon.YaDeclarado(f[1]), r[2])
        assertTrue(r[3] is Renglon.Encabezado)
        assertEquals(Renglon.Ninguno, r[4])
        assertEquals(listOf(Renglon.Parte(2), Renglon.Parte(0), Renglon.Parte(1)), r.drop(5))

        val sel = RepuestosDeclarados.seleccionInicial(r, false) { false }
        assertTrue(sel[1]); assertTrue(sel[2])
        assertFalse(sel[0]); assertFalse(sel[3]); assertFalse(sel[4])
        // Aun con "Ninguno" elegido antes, los declarados arrancan tildados.
        val selNinguno = RepuestosDeclarados.seleccionInicial(r, true) { true }
        assertTrue(selNinguno[1]); assertTrue(selNinguno[2]); assertTrue(selNinguno[4])
        assertFalse(selNinguno[5])
    }

    @Test fun declarado_no_se_destilda_al_tocarlo() {
        val f = filas(Declarado(DISPLAY, 1, "Pablo"))
        val r = RepuestosDeclarados.renglones(f, listOf(0, 1, 2))
        val sel = RepuestosDeclarados.seleccionInicial(r, false) { false }
        val efecto = RepuestosDeclarados.tocar(r, sel, 1, checked = false) { 5 }
        assertEquals(Efecto.BLOQUEADO_DECLARADO, efecto)
        assertTrue(sel[1])
    }

    @Test fun ninguno_no_quita_los_declarados() {
        val f = filas(Declarado(DISPLAY, 1, "Pablo"))
        val r = RepuestosDeclarados.renglones(f, listOf(0, 1, 2))
        val sel = RepuestosDeclarados.seleccionInicial(r, false) { it == 1 }
        val posNinguno = r.indexOf(Renglon.Ninguno)
        val posBateria = r.indexOf(Renglon.Parte(1))
        assertTrue(sel[posBateria])
        RepuestosDeclarados.tocar(r, sel, posNinguno, checked = true) { 5 }
        assertTrue(sel[posNinguno])
        assertTrue(sel[1])                // el declarado sigue
        assertFalse(sel[posBateria])      // lo nuevo se limpia
        assertTrue(RepuestosDeclarados.ningunoElegido(r, sel))
        assertTrue(RepuestosDeclarados.partesTildadas(r, sel).isEmpty())
        // Tildar algo nuevo destilda Ninguno.
        RepuestosDeclarados.tocar(r, sel, posBateria, checked = true) { 5 }
        assertFalse(sel[posNinguno])
        assertTrue(sel[posBateria])
    }

    @Test fun declarado_no_se_bloquea_por_saldo_cero_pero_lo_nuevo_si() {
        // FLEX: saldo 0 en la mochila del técnico actual.
        val f = filas(Declarado(FLEX, 1, "Pablo"))
        val r = RepuestosDeclarados.renglones(f, listOf(0, 1, 2))
        val saldo: (Int) -> Int? = { origIdx -> if (origIdx == 2) 0 else 5 }
        val sel = RepuestosDeclarados.seleccionInicial(r, false) { false }
        assertTrue(sel[1])
        RepuestosDeclarados.tocar(r, sel, 1, checked = false, saldoDe = saldo)
        assertTrue(sel[1])
        // Otra unidad del mismo repuesto sin saldo: bloqueada como hoy.
        val posFlexNuevo = r.indexOf(Renglon.Parte(2))
        assertEquals(Efecto.BLOQUEADO_SIN_SALDO, RepuestosDeclarados.tocar(r, sel, posFlexNuevo, true, saldo))
        assertFalse(sel[posFlexNuevo])
        // Se puede confirmar sólo con lo declarado y se manda.
        assertTrue(RepuestosDeclarados.hayAlgoElegido(r, sel))
        assertEquals(listOf(FLEX), RepuestosDeclarados.armarEnvio(f, emptyList()))
    }

    @Test fun encabezado_no_se_tilda() {
        val f = filas(Declarado(DISPLAY, 1, null))
        val r = RepuestosDeclarados.renglones(f, listOf(0))
        val sel = RepuestosDeclarados.seleccionInicial(r, false) { false }
        RepuestosDeclarados.tocar(r, sel, 0, checked = true) { 5 }
        assertFalse(sel[0])
    }

    @Test fun sin_declarados_y_sin_nada_tildado_no_se_puede_confirmar() {
        val r = RepuestosDeclarados.renglones(emptyList(), listOf(0, 1))
        val sel = RepuestosDeclarados.seleccionInicial(r, false) { false }
        assertFalse(RepuestosDeclarados.hayAlgoElegido(r, sel))
    }

    // ---------- envío ----------

    @Test fun envio_declarados_por_unidad_mas_nuevos() {
        val f = filas(
            Declarado(DISPLAY, 1, "Pablo"),
            Declarado(DISPLAY, 1, "Pablo"),
            Declarado(BATERIA, 2, null),
        )
        // Display: id del catálogo (el mismo que la app manda hoy); Bateria x2; + nuevo Flex
        // + otra unidad de Display tildada abajo.
        val envio = RepuestosDeclarados.armarEnvio(f, listOf(FLEX, DISPLAY.take(15)))
        assertEquals(
            listOf(DISPLAY.take(15), DISPLAY.take(15), BATERIA, BATERIA, FLEX, DISPLAY.take(15)),
            envio
        )
    }

    @Test fun pretildado_de_sugeridos_no_duplica_un_declarado() {
        val f = filas(Declarado(DISPLAY, 1, "Pablo"))
        assertEquals(setOf(1), RepuestosDeclarados.pretildarSinDeclarados(setOf(0, 1), catalogo, f))
    }
}
