package com.enofir.tecnicos_app.model

import com.google.gson.annotations.SerializedName

data class PrintLabelResponse(
    @SerializedName("ok")
    val ok: Boolean,

    @SerializedName("zpl")
    val zpl: String?,

    @SerializedName("serial")
    val serial: String?,

    @SerializedName("model")
    val model: String?,

    @SerializedName("message")
    val message: String?
)
