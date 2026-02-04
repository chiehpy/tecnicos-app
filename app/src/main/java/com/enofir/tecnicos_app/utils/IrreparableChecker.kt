package com.enofir.tecnicos_app.utils

import java.text.Normalizer

/**
 * Evalúa si un terminal es irreparable según las fallas encontradas y el cliente.
 *
 * Clientes normales (ej: N910):
 * - Condición A: Contiene "placa"
 * - O Condición B: (B1 AND B2)
 *   - B1 (frente): "display roto" O "carcasa frontal"
 *   - B2 (segunda falla): "impresora rota" O "camara frontal rota" O "camara trasera rota" O "carcasa posterior rota"
 *
 * Mercado Libre SA:
 * - Condición A: Contiene "placa"
 * - O Condición B1 sola: "display roto" O "carcasa frontal"
 */
object IrreparableChecker {

    private const val MERCADO_LIBRE = "Mercado Libre SA"

    /**
     * Evalúa si las fallas indican un terminal irreparable.
     * @param textoFallas El texto de fallas (puede ser separado por comas u otro delimitador)
     * @param accountName El nombre de la cuenta/cliente
     * @return true si el terminal es irreparable según las reglas de negocio
     */
    fun isIrreparable(textoFallas: String?, accountName: String? = null): Boolean {
        if (textoFallas.isNullOrBlank()) return false

        val normalized = normalize(textoFallas)
        val isMercadoLibre = accountName?.trim().equals(MERCADO_LIBRE, ignoreCase = true)

        // Condición A: contiene "placa" (aplica a todos)
        if (normalized.contains("placa")) {
            return true
        }

        val b1 = checkGrupoFrente(normalized)

        // Para Mercado Libre SA: solo necesita B1 (display roto o carcasa frontal)
        if (isMercadoLibre) {
            return b1
        }

        // Para otros clientes: necesita B1 AND B2
        val b2 = checkGrupoSegundaFalla(normalized)
        return b1 && b2
    }

    /**
     * Evalúa si una lista de fallas seleccionadas indica un terminal irreparable.
     * @param fallas Lista de strings de fallas seleccionadas
     * @param accountName El nombre de la cuenta/cliente
     * @return true si el terminal es irreparable según las reglas de negocio
     */
    fun isIrreparable(fallas: List<String>?, accountName: String? = null): Boolean {
        if (fallas.isNullOrEmpty()) return false
        return isIrreparable(fallas.joinToString(", "), accountName)
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
