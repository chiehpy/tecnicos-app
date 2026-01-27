package com.enofir.tecnicos_app.model

import com.google.gson.annotations.SerializedName

data class RecoveryPatchRequest(
    @SerializedName("recovered_parts")
    val recoveredParts: String
)
