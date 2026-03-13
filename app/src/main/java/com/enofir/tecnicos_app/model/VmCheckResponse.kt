package com.enofir.tecnicos_app.model

import com.google.gson.annotations.SerializedName

data class VmCheckResponse(
    @SerializedName("exists")
    val exists: Boolean
)
