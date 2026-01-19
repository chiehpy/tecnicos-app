package com.enofir.tecnicos_app.model

import com.google.gson.annotations.SerializedName

data class TerminalLookupResponse(
    @SerializedName("ok")
    val ok: Boolean,

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("data")
    val data: TerminalLookupData? = null,

    @SerializedName("error_code")
    val errorCode: String? = null
)
