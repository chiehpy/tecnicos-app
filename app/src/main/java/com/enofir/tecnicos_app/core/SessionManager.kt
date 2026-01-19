package com.enofir.tecnicos_app.core

import android.content.Context
import com.enofir.tecnicos_app.model.LoginUser

class SessionManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Guarda la sesión desde la respuesta de login del MDW.
     */
    fun saveSession(token: String, user: LoginUser) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER, user.username)
            .putString(KEY_TECHNICIAN_NAME, user.technicianName)
            .putString(KEY_ALLOWED_ROLES, user.allowedRoles.joinToString(ROLE_SEPARATOR))
            .remove(KEY_ROLE) // Se selecciona después en Settings
            .apply()
    }

    fun setRole(role: String?) {
        prefs.edit().putString(KEY_ROLE, role).apply()
    }

    fun isLoggedIn(): Boolean {
        return !getToken().isNullOrBlank() && !getUser().isNullOrBlank()
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

    /**
     * Rol activo seleccionado por el técnico
     */
    fun getRole(): String? =
        prefs.getString(KEY_ROLE, null)

    /**
     * Roles permitidos para este técnico (devueltos por MDW en login)
     */
    fun getAllowedRoles(): List<String> {
        val raw = prefs.getString(KEY_ALLOWED_ROLES, null) ?: return emptyList()
        return raw.split(ROLE_SEPARATOR).filter { it.isNotBlank() }
    }

    /**
     * Verifica si el técnico tiene permiso para usar un rol específico
     */
    fun isRoleAllowed(role: String): Boolean {
        return getAllowedRoles().contains(role)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS = "tecnicos_app_session"
        private const val KEY_TOKEN = "token"
        private const val KEY_USER = "user"
        private const val KEY_ROLE = "role"
        private const val KEY_TECHNICIAN_NAME = "technician_name"
        private const val KEY_ALLOWED_ROLES = "allowed_roles"
        private const val ROLE_SEPARATOR = "|||"
    }
}
