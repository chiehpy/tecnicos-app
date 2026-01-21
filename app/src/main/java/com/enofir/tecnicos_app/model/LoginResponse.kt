package com.enofir.tecnicos_app.model

import com.google.gson.annotations.SerializedName

/**
 * Respuesta de POST /auth/login (MDW)
 *
 * Éxito: ok=true, token y user presentes
 * Error: ok=false, message y error_code presentes
 */
data class LoginResponse(

    @SerializedName("ok")
    val ok: Boolean,

    @SerializedName("token")
    val token: String? = null,

    @SerializedName("refresh_token")
    val refreshToken: String? = null,

    @SerializedName("user")
    val user: LoginUser? = null,

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("error_code")
    val errorCode: String? = null
)

data class LoginUser(

    @SerializedName("username")
    val username: String,

    @SerializedName("technicianName")
    val technicianName: String,

    @SerializedName("allowedRoles")
    val allowedRoles: List<String>
)
