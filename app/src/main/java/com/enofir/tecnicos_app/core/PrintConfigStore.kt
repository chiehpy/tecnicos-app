package com.enofir.tecnicos_app.core

import android.content.Context

/**
 * Almacena la configuración de impresión de etiquetas en SharedPreferences.
 * - Temperatura (darkness): 0-30, predeterminado 17
 * - Mover imagen (lsMm): valor en mm, positivo = izquierda, negativo = derecha
 */
object PrintConfigStore {

    private const val PREFS = "print_config"
    private const val KEY_DARKNESS = "darkness"
    private const val KEY_LS_MM = "ls_mm"

    const val DEFAULT_DARKNESS = 17
    const val DEFAULT_LS_MM = 0

    const val MIN_DARKNESS = 0
    const val MAX_DARKNESS = 30

    const val MAX_SHIFT_MM = 10

    fun getDarkness(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_DARKNESS, DEFAULT_DARKNESS)
    }

    fun setDarkness(context: Context, value: Int) {
        val clamped = value.coerceIn(MIN_DARKNESS, MAX_DARKNESS)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_DARKNESS, clamped)
            .apply()
    }

    fun getLsMm(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_LS_MM, DEFAULT_LS_MM)
    }

    fun setLsMm(context: Context, value: Int) {
        val clamped = value.coerceIn(-MAX_SHIFT_MM, MAX_SHIFT_MM)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_LS_MM, clamped)
            .apply()
    }

    fun isDefaultDarkness(context: Context): Boolean {
        return getDarkness(context) == DEFAULT_DARKNESS
    }

    fun isDefaultLsMm(context: Context): Boolean {
        return getLsMm(context) == DEFAULT_LS_MM
    }

    fun resetToDefaults(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_DARKNESS, DEFAULT_DARKNESS)
            .putInt(KEY_LS_MM, DEFAULT_LS_MM)
            .apply()
    }

    /**
     * Devuelve el valor de darkness para enviar al API.
     * Si está en predeterminado, devuelve null (no se envía).
     */
    fun getDarknessForApi(context: Context): Int? {
        return if (isDefaultDarkness(context)) null else getDarkness(context)
    }

    /**
     * Devuelve el valor de lsMm para enviar al API.
     * Si está en predeterminado (0), devuelve null (no se envía).
     */
    fun getLsMmForApi(context: Context): Int? {
        return if (isDefaultLsMm(context)) null else getLsMm(context)
    }

    /**
     * Formatea el valor de lsMm para mostrar al usuario.
     * Ej: "3 mm izquierda", "2 mm derecha", "Centrado"
     */
    fun formatLsMmForDisplay(value: Int): String {
        return when {
            value > 0 -> "$value mm izquierda"
            value < 0 -> "${-value} mm derecha"
            else -> "Centrado"
        }
    }
}
