package com.enofir.tecnicos_app.model

import com.google.gson.annotations.SerializedName

/**
 * Respuesta de POST /auth/refresh (MDW)
 *
 * Éxito: ok=true, token presente (nuevo access token)
 * Error: ok=false
 */
data class RefreshResponse(

    @SerializedName("ok")
    val ok: Boolean,

    @SerializedName("token")
    val token: String? = null
)
