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
    val errorCode: String? = null
)
