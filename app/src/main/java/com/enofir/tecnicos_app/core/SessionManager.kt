package com.enofir.tecnicos_app.core

import android.content.Context

class SessionManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Guarda la sesión completa del técnico.
     * technicianName es el nombre real que espera Salesforce / MDW.
     */
    fun saveSession(
        token: String?,
        username: String?,
        role: String?,
        technicianName: String?
    ) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER, username)
            .putString(KEY_ROLE, role)
            .putString(KEY_TECHNICIAN_NAME, technicianName)
            .apply()
    }
    fun setRole(role: String?) {
        prefs.edit().putString(KEY_ROLE, role).apply()
    }

    fun isLoggedIn(): Boolean {
        return !getUser().isNullOrBlank()
    }

    fun getToken(): String? =
        prefs.getString(KEY_TOKEN, null)

    /**
     * Username técnico (ej: wramos)
     */
    fun getUser(): String? =
        prefs.getString(KEY_USER, null)

    /**
     * Nombre real del técnico (ej: Walter Ramos)
     * ESTE es el que se envía al MDW como technicianName
     */
    fun getTechnicianName(): String? =
        prefs.getString(KEY_TECHNICIAN_NAME, null)

    fun getRole(): String? =
        prefs.getString(KEY_ROLE, null)

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS = "tecnicos_app_session"
        private const val KEY_TOKEN = "token"
        private const val KEY_USER = "user"
        private const val KEY_ROLE = "role"
        private const val KEY_TECHNICIAN_NAME = "technician_name"
    }
}
