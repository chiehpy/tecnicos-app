package com.enofir.tecnicos_app.utils

import java.text.Normalizer

/**
 * Evalúa si un terminal es irreparable según las fallas encontradas.
 *
 * IRREPARABLE = TRUE si:
 * - Condición A: Contiene "placa"
 * - O Condición B: (B1 AND B2)
 *   - B1 (frente): "display roto" O "carcasa frontal"
 *   - B2 (segunda falla): "impresora rota" O "camara frontal rota" O "camara trasera rota" O "carcasa posterior rota"
 */
object IrreparableChecker {

    /**
     * Evalúa si las fallas indican un terminal irreparable.
     * @param textoFallas El texto de fallas (puede ser separado por comas u otro delimitador)
     * @return true si el terminal es irreparable según las reglas de negocio
     */
    fun isIrreparable(textoFallas: String?): Boolean {
        if (textoFallas.isNullOrBlank()) return false

        val normalized = normalize(textoFallas)

        // Condición A: contiene "placa"
        if (normalized.contains("placa")) {
            return true
        }

        // Condición B: B1 AND B2
        val b1 = checkGrupoFrente(normalized)
        val b2 = checkGrupoSegundaFalla(normalized)

        return b1 && b2
    }

    /**
     * Evalúa si una lista de fallas seleccionadas indica un terminal irreparable.
     * @param fallas Lista de strings de fallas seleccionadas
     * @return true si el terminal es irreparable según las reglas de negocio
     */
    fun isIrreparable(fallas: List<String>?): Boolean {
        if (fallas.isNullOrEmpty()) return false
        return isIrreparable(fallas.joinToString(", "))
    }

    /**
     * Normaliza el texto: minúsculas, sin tildes, espacios múltiples reducidos.
     */
    private fun normalize(text: String): String {
        // Minúsculas
        var result = text.lowercase()

        // Quitar tildes (á -> a, é -> e, etc.)
        result = Normalizer.normalize(result, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")

        // Reducir espacios múltiples
        result = result.replace(Regex("\\s+"), " ")

        return result.trim()
    }

    /**
     * B1 (grupo frente): "display roto" O "carcasa frontal"
     */
    private fun checkGrupoFrente(normalized: String): Boolean {
        return normalized.contains("display roto") ||
               normalized.contains("carcasa frontal")
    }

    /**
     * B2 (grupo segunda falla):
     * - "impresora rota"
     * - "camara frontal rota"
     * - "camara trasera rota"
     * - "carcasa posterior rota"
     */
    private fun checkGrupoSegundaFalla(normalized: String): Boolean {
        return normalized.contains("impresora rota") ||
               normalized.contains("camara frontal rota") ||
               normalized.contains("camara trasera rota") ||
               normalized.contains("carcasa posterior rota")
    }
}
