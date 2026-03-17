package com.enofir.tecnicos_app.model

import com.google.gson.annotations.SerializedName

data class RecoveredPartItem(
    val pn: String,
    val name: String,
    @SerializedName("sf_id") val sfId: String? = null,
    val stock: Int? = null
)
