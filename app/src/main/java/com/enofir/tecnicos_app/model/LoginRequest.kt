package com.enofir.tecnicos_app.model

import com.google.gson.annotations.SerializedName

/**
 * Contrato para POST /auth/login (MDW)
 */
data class LoginRequest(

    @SerializedName("username")
    val username: String,

    @SerializedName("pin")
    val pin: String
)
