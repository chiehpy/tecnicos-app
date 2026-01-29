package com.enofir.tecnicos_app.model

import com.google.gson.annotations.SerializedName

/**
 * Respuesta del endpoint GET /app/version
 */
data class AppVersionResponse(
    @SerializedName("ok")
    val ok: Boolean = false,

    @SerializedName("version")
    val version: String? = null,

    @SerializedName("url")
    val url: String? = null,

    @SerializedName("message")
    val message: String? = null
)
