package com.enofir.tecnicos_app.model

import com.google.gson.annotations.SerializedName

data class TerminalLookupAsset(

    @SerializedName("productName")
    val productName: String? = null,

    @SerializedName("productId")
    val productId: String? = null,

    @SerializedName("imei")
    val imei: String? = null,

    @SerializedName("imei2")
    val imei2: String? = null,

    @SerializedName("serialNumber")
    val serialNumber: String? = null,

    @SerializedName("id")
    val id: String? = null
)
