package com.enofir.tecnicos_app.model

import com.google.gson.annotations.SerializedName

data class PrintLabelRequest(
    @SerializedName("serial")
    val serial: String,

    @SerializedName("darkness")
    val darkness: Int? = null,

    @SerializedName("ls_mm")
    val lsMm: Int? = null
)
