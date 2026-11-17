package com.enofir.tecnicos_app.core

import com.enofir.tecnicos_app.model.RecoveredPartItem
import com.google.gson.JsonElement

/**
 * Repuestos sugeridos por falla (clave aditiva `parts_suggestions` de `GET /app/catalogs`).
 * Lógica pura, sin Android. Mismo algoritmo que el desktop.
 */
object RepuestosSugeridos {

    data class Candidato(val name: String, val tildar: Boolean)

    data class Sugerencias(
        val estetico: List<String>,
        /** familia ("N910" | "N950") -> falla -> candidatos en orden. */
        val porFalla: Map<String, Map<String, List<Candidato>>>,
    )

    /**
     * @param orden índices sobre la lista de partes recibida: sugeridas primero, luego el resto en el orden de hoy.
     * @param pretildar índices (misma lista) a pretildar.
     */
    data class Resultado(val orden: List<Int>, val pretildar: Set<Int>)

    private val SUFIJO_COLOR = Regex("""\s*\((amarilla|blanca|celeste)\)\s*$""", RegexOption.IGNORE_CASE)

    /** Nombre sin el sufijo final ` (amarilla)`, ` (blanca)` o ` (celeste)`. */
    fun canon(nombre: String): String = SUFIJO_COLOR.replace(nombre, "").trim()

    fun familia(claveCatalogo: String): String =
        if (claveCatalogo.contains("N950", ignoreCase = true)) "N950" else "N910"

    /** Un nombre sin sufijo de color siempre coincide; en N910 siempre coincide. */
    fun colorOk(nombre: String, claveCatalogo: String): Boolean {
        val n = nombre.trim().lowercase()
        val amarilla = n.endsWith("(amarilla)")
        val blancaCeleste = n.endsWith("(blanca)") || n.endsWith("(celeste)")
        return when (claveCatalogo) {
            "N950_amarilla" -> amarilla || !blancaCeleste
            "N950_celeste" -> blancaCeleste || !amarilla
            else -> true
        }
    }

    fun esEstetico(canon: String, estetico: List<String>): Boolean =
        estetico.any { canon.startsWith(it, ignoreCase = true) }

    fun usado(part: RecoveredPartItem): Boolean = part.pn.endsWith("U")

    /** Fallas de Revisión Inicial + encontradas en Reparación (en ese orden), separadas por `;` o `,`, sin repetir. */
    fun fallasDelCaso(vararg listas: List<String>): List<String> =
        listas.flatMap { it }
            .flatMap { it.split(';', ',') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    /** Parseo tolerante: null si falta o viene mal formado. */
    fun parse(el: JsonElement?): Sugerencias? = runCatching {
        val obj = el?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val estetico = obj.get("estetico")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { runCatching { it.asString }.getOrNull() } ?: emptyList()
        val porFalla = obj.get("por_falla")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val mapa = porFalla.entrySet().mapNotNull { (fam, fallasEl) ->
            if (!fallasEl.isJsonObject) return@mapNotNull null
            val fallas = fallasEl.asJsonObject.entrySet().mapNotNull { (falla, candsEl) ->
                if (!candsEl.isJsonArray) return@mapNotNull null
                val cands = candsEl.asJsonArray.mapNotNull { c ->
                    if (!c.isJsonObject) return@mapNotNull null
                    val name = c.asJsonObject.get("name")?.takeIf { it.isJsonPrimitive }?.asString
                        ?: return@mapNotNull null
                    val tildar = c.asJsonObject.get("tildar")?.takeIf { it.isJsonPrimitive }
                        ?.let { runCatching { it.asBoolean }.getOrNull() } ?: false
                    Candidato(name, tildar)
                }
                falla to cands
            }.toMap()
            fam to fallas
        }.toMap()
        Sugerencias(estetico, mapa)
    }.getOrNull()

    /**
     * Ordena el diálogo de repuestos.
     *
     * @param partes catálogo de la clave actual, en el orden original.
     * @param usageCount usage_count de la parte (con el fallback local que ya usa la app).
     * @param saldoDe saldo de mochila de la parte, o null si no hay mochila activa / no está controlada.
     * @param tieneSeleccionPrevia true si el técnico ya eligió algo: no se pretilda.
     */
    fun calcular(
        partes: List<RecoveredPartItem>,
        claveCatalogo: String,
        fallas: List<String>,
        sugerencias: Sugerencias?,
        usageCount: (RecoveredPartItem) -> Int,
        saldoDe: (RecoveredPartItem) -> Int?,
        tieneSeleccionPrevia: Boolean = false,
    ): Resultado {
        // Orden de hoy: con mochila, primero lo que tiene (saldo > 0); luego usage_count desc. Estable.
        val base = partes.indices.sortedWith(
            compareByDescending<Int> { (saldoDe(partes[it]) ?: 1) > 0 }
                .thenByDescending { usageCount(partes[it]) }
        )
        val candidatosPorFalla = sugerencias?.porFalla?.get(familia(claveCatalogo))
        if (sugerencias == null || candidatosPorFalla == null || fallas.isEmpty()) {
            return Resultado(base, emptySet())
        }

        val sugeridas = LinkedHashSet<Int>()
        val tildar = LinkedHashSet<Int>()
        for (falla in fallas) {
            val candidatos = candidatosPorFalla[falla] ?: continue
            for (c in candidatos) {
                val variantes = base.filter { canon(partes[it].name) == c.name }
                if (variantes.isEmpty()) continue
                val elegida = variantes.sortedWith(
                    compareByDescending<Int> { colorOk(partes[it].name, claveCatalogo) }
                        .thenByDescending { (saldoDe(partes[it]) ?: 1) > 0 }
                        .thenByDescending {
                            val p = partes[it]
                            if (esEstetico(canon(p.name), sugerencias.estetico)) usado(p) else !usado(p)
                        }
                ).first()
                sugeridas.add(elegida)
                val sinSaldo = saldoDe(partes[elegida])?.let { it <= 0 } ?: false
                if (c.tildar && !sinSaldo) tildar.add(elegida)
            }
        }
        // Lo que el técnico NO tiene en la mochila va siempre abajo de todo, aunque sea
        // sugerido (dueño, 17-11-2026). Orden estable: dentro de cada grupo, sugeridos primero.
        val orden = (sugeridas.toList() + base.filter { it !in sugeridas })
            .sortedByDescending { (saldoDe(partes[it]) ?: 1) > 0 }
        return Resultado(orden, if (tieneSeleccionPrevia) emptySet() else tildar)
    }
}
