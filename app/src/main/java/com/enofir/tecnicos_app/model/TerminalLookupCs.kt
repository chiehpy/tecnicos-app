package com.enofir.tecnicos_app.model

import com.google.gson.annotations.SerializedName

data class TerminalLookupCs(

    @SerializedName("failureObservations")
    val failureObservations: String? = null,

    @SerializedName("qaObservations")
    val qaObservations: String? = null,

    @SerializedName("substatus")
    val substatus: String? = null,

    @SerializedName("status")
    val status: String? = null,

    @SerializedName("id")
    val id: String? = null,

    @SerializedName("qaRejectCount")
    val qaRejectCount: Int? = null
)
