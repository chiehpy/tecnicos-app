package com.enofir.tecnicos_app.model

import com.google.gson.annotations.SerializedName

data class HistoryResponse(
    @SerializedName("ok")   val ok: Boolean,
    @SerializedName("data") val data: List<HistoryEntryRemote>? = null,
    @SerializedName("message") val message: String? = null
)

data class HistoryEntryRemote(
    @SerializedName("ts")      val ts: Long,
    @SerializedName("startTs") val startTs: Long = 0,
    @SerializedName("serial")  val serial: String,
    @SerializedName("action")  val action: String,
    @SerializedName("role")    val role: String,
    @SerializedName("ok")      val ok: Boolean = true,
    @SerializedName("message") val message: String? = null
)
