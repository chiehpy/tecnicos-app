package com.enofir.tecnicos_app.model

import com.google.gson.annotations.SerializedName

data class TerminalEventResponse(
    @SerializedName("ok")
    val ok: Boolean,

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("salesforce_id")
    val salesforceId: String? = null,

    @SerializedName("error_code")
    val errorCode: String? = null,

    @SerializedName("previous_technician")
    val previousTechnician: String? = null,

    // Verificación E2E (Feature 3): read-back que hace el MDW (null en versiones viejas del MDW)
    @SerializedName("verified")
    val verified: Boolean? = null,

    // Estado resultante persistido que reporta el MDW (para la 2da barrera de la app)
    @SerializedName("status")
    val status: String? = null
)
