package com.enofir.tecnicos_app.model

import com.google.gson.annotations.SerializedName

data class TerminalLookupData(

    @SerializedName("success")
    val success: Boolean? = null,

    @SerializedName("serial")
    val serial: String? = null,

    @SerializedName("message")
    val message: String? = null,

    // Estos pueden venir null (como en tu log). Usar asset.* como fuente preferida.
    @SerializedName("imei")
    val imei: String? = null,

    @SerializedName("imei2")
    val imei2: String? = null,

    @SerializedName("cs")
    val cs: TerminalLookupCs? = null,

    @SerializedName("asset")
    val asset: TerminalLookupAsset? = null
)
