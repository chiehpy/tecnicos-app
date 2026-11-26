package com.enofir.tecnicos_app.core

import com.enofir.tecnicos_app.model.RecoveredPartItem
import com.google.gson.JsonElement

/**
 * Repuestos ya declarados en el caso (`cs.declaredParts` de `GET terminal/lookup`).
 *
 * Cada COMPLETE de Reparación con repuestos borra los del caso y reinserta lo enviado
 * (Salesforce conserva el técnico original de lo que se reenvía). Por eso, en un rebote,
 * lo ya declarado se muestra arriba, tildado y fijo, y se reenvía siempre junto con lo nuevo.
 * Sin `declaredParts` (MDW viejo, caso sin repuestos, Chile) todo queda igual que antes.
 *
 * Lógica pura, sin Android.
 */
object RepuestosDeclarados {

    /** Un elemento crudo de `declaredParts`. */
    /** [nombre]: Name de la ficha en Salesforce (lo manda el MDW desde el 26-11-2026; puede faltar). */
    data class Declarado(val sfId: String, val qty: Int, val tecnico: String?, val nombre: String? = null)

    /** Un renglón de la sección «Ya en la terminal»: un repuesto (Id de Repuesto__c) con su cantidad total. */
    data class Fila(
        val sfId: String,
        val qty: Int,
        val tecnico: String?,
        /** Parte del catálogo de la app con el mismo Id, o null si la app no lo conoce. */
        val parte: RecoveredPartItem?,
        /** Name de Salesforce, para lo que no está en el catálogo de la app (ej. el film). */
        val nombreSf: String? = null,
    ) {
        val nombre: String get() = parte?.name ?: nombreSf ?: "Repuesto $sfId"

        /** Id que se manda en `spareParts`: el mismo que la app manda hoy para esa parte. */
        val idEnvio: String get() = parte?.let { it.sfId ?: it.pn } ?: sfId
    }

    /** Renglones del diálogo de repuestos de Reparación. */
    sealed class Renglon {
        data class Encabezado(val texto: String) : Renglon()
        data class YaDeclarado(val fila: Fila) : Renglon()
        object Ninguno : Renglon()
        /** @param origIdx índice en el catálogo de la app (repairedPartsOptions). */
        data class Parte(val origIdx: Int) : Renglon()
    }

    const val ENCABEZADO_DECLARADOS = "Ya en la terminal"
    const val ENCABEZADO_NUEVOS = "Repuestos nuevos"

    enum class Efecto { OK, BLOQUEADO_DECLARADO, BLOQUEADO_SIN_SALDO }

    /** Ids de Salesforce: se comparan por los primeros 15 caracteres (15 y 18 son el mismo registro). */
    fun clave(id: String?): String? =
        id?.trim()?.takeIf { it.isNotEmpty() }?.let { if (it.length >= 15) it.take(15) else it }

    /** Parseo tolerante: lista vacía si falta, viene null o mal formado; ignora elementos sin sfId. */
    fun parse(el: JsonElement?): List<Declarado> = runCatching {
        val arr = el?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        arr.mapNotNull { e ->
            if (!e.isJsonObject) return@mapNotNull null
            val o = e.asJsonObject
            val sfId = o.get("sfId")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
                ?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val qty = o.get("qty")?.takeIf { it.isJsonPrimitive }
                ?.let { runCatching { it.asDouble.toInt() }.getOrNull() } ?: 1
            val tecnico = o.get("tecnico")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
                ?.takeIf { it.isNotEmpty() }
            val nombre = o.get("name")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
                ?.takeIf { it.isNotEmpty() }
            Declarado(sfId, qty.coerceAtLeast(1), tecnico, nombre)
        }
    }.getOrDefault(emptyList())

    /**
     * Agrupa por repuesto (mismo Id en varios registros = se suman las cantidades), en el orden
     * en que llegan, y lo cruza con el catálogo de la app.
     */
    fun agrupar(declarados: List<Declarado>, catalogo: List<RecoveredPartItem>): List<Fila> {
        val porClave = LinkedHashMap<String, MutableList<Declarado>>()
        for (d in declarados) {
            val k = clave(d.sfId) ?: continue
            porClave.getOrPut(k) { mutableListOf() }.add(d)
        }
        return porClave.map { (k, ds) ->
            val tecnicos = ds.mapNotNull { it.tecnico?.trim()?.takeIf { t -> t.isNotEmpty() } }.distinct()
            Fila(
                sfId = ds.first().sfId,
                qty = ds.sumOf { it.qty.coerceAtLeast(1) },
                tecnico = tecnicos.takeIf { it.isNotEmpty() }?.joinToString(", "),
                parte = catalogo.firstOrNull { clave(it.sfId) == k },
                nombreSf = ds.firstNotNullOfOrNull { it.nombre },
            )
        }
    }

    /** Segunda línea del renglón ya declarado. */
    fun lineaTecnico(fila: Fila): String =
        fila.tecnico?.trim()?.takeIf { it.isNotEmpty() }?.let { "Consumido por $it" } ?: "Ya declarado"

    /**
     * Arma los renglones: «Ya en la terminal» + declarados arriba de todo, después la opción
     * Ninguno y el catálogo en [orden] (el de RepuestosSugeridos). Sin declarados = igual que hoy.
     */
    fun renglones(filas: List<Fila>, orden: List<Int>): List<Renglon> {
        val out = mutableListOf<Renglon>()
        if (filas.isNotEmpty()) {
            out += Renglon.Encabezado(ENCABEZADO_DECLARADOS)
            filas.forEach { out += Renglon.YaDeclarado(it) }
            out += Renglon.Encabezado(ENCABEZADO_NUEVOS)
        }
        out += Renglon.Ninguno
        orden.forEach { out += Renglon.Parte(it) }
        return out
    }

    /**
     * Quita del pretildado de sugeridos las partes que ya están declaradas: pretildarlas
     * sumaría una unidad que el técnico no eligió.
     */
    fun pretildarSinDeclarados(pretildar: Set<Int>, catalogo: List<RecoveredPartItem>, filas: List<Fila>): Set<Int> {
        if (filas.isEmpty()) return pretildar
        val declaradas = filas.mapNotNull { clave(it.sfId) }.toSet()
        return pretildar.filter { clave(catalogo.getOrNull(it)?.sfId) !in declaradas }.toSet()
    }

    /**
     * Estado inicial de los tildes. Los ya declarados siempre tildados.
     * @param ninguno el técnico había elegido la opción Ninguno.
     * @param parteTildada si la parte del catálogo (origIdx) arranca tildada.
     */
    fun seleccionInicial(renglones: List<Renglon>, ninguno: Boolean, parteTildada: (Int) -> Boolean): BooleanArray =
        BooleanArray(renglones.size) { i ->
            when (val r = renglones[i]) {
                is Renglon.Encabezado -> false
                is Renglon.YaDeclarado -> true
                Renglon.Ninguno -> ninguno
                is Renglon.Parte -> !ninguno && parteTildada(r.origIdx)
            }
        }

    /**
     * Aplica un toque sobre el renglón [pos] ([checked] es el valor que el ListView le dio al
     * tilde). Actualiza [sel]; la UI después copia [sel] al ListView.
     *
     * - Ya declarado: no cambia nunca.
     * - Ninguno: limpia las partes nuevas; los ya declarados quedan.
     * - Parte sin saldo en la mochila ([saldoDe] <= 0): no se puede tildar.
     */
    fun tocar(
        renglones: List<Renglon>,
        sel: BooleanArray,
        pos: Int,
        checked: Boolean,
        saldoDe: (Int) -> Int?,
    ): Efecto {
        when (val r = renglones.getOrNull(pos)) {
            null, is Renglon.Encabezado -> {
                if (r != null) sel[pos] = false
                return Efecto.OK
            }
            is Renglon.YaDeclarado -> {
                sel[pos] = true
                return Efecto.BLOQUEADO_DECLARADO
            }
            Renglon.Ninguno -> {
                renglones.forEachIndexed { i, x -> if (x is Renglon.Parte) sel[i] = false }
                sel[pos] = checked
                return Efecto.OK
            }
            is Renglon.Parte -> {
                if (checked && (saldoDe(r.origIdx) ?: 1) <= 0) {
                    sel[pos] = false
                    return Efecto.BLOQUEADO_SIN_SALDO
                }
                renglones.forEachIndexed { i, x -> if (x == Renglon.Ninguno) sel[i] = false }
                sel[pos] = checked
                return Efecto.OK
            }
        }
    }

    /** Hay algo elegido para poder confirmar (un ya declarado cuenta). */
    fun hayAlgoElegido(renglones: List<Renglon>, sel: BooleanArray): Boolean =
        renglones.indices.any { sel[it] && renglones[it] !is Renglon.Encabezado }

    /** Partes del catálogo tildadas como nuevas (origIdx), en el orden del diálogo. */
    fun partesTildadas(renglones: List<Renglon>, sel: BooleanArray): Set<Int> =
        renglones.indices.mapNotNull { i -> (renglones[i] as? Renglon.Parte)?.takeIf { sel[i] }?.origIdx }.toSet()

    /** La opción Ninguno quedó elegida. */
    fun ningunoElegido(renglones: List<Renglon>, sel: BooleanArray): Boolean =
        renglones.indices.any { renglones[it] == Renglon.Ninguno && sel[it] }

    /**
     * Lista `spareParts` del COMPLETE: los ya declarados (un elemento por unidad, como manda la
     * app hoy) + lo nuevo. Un ya declarado tildado otra vez abajo es una unidad más.
     */
    fun armarEnvio(filas: List<Fila>, nuevos: List<String>): List<String> =
        filas.flatMap { f -> List(f.qty.coerceAtLeast(1)) { f.idEnvio } } + nuevos
}
