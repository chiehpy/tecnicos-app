package com.enofir.tecnicos_app.model

import com.google.gson.annotations.SerializedName

data class PhotoUploadResponse(
    @SerializedName("ok") val ok: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("sf_id") val sfId: String?,
)
