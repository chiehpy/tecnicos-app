package com.enofir.tecnicos_app.model

import com.google.gson.annotations.SerializedName

data class PhotoUploadRequest(
    @SerializedName("serial") val serial: String,
    @SerializedName("base64") val base64: String,
    @SerializedName("filename") val filename: String,
)
