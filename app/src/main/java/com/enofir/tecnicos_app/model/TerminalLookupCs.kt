package com.enofir.tecnicos_app.model

import com.google.gson.annotations.SerializedName

data class TerminalLookupCs(

    @SerializedName("accountName")
    val accountName: String? = null,

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
    val qaRejectCount: Int? = null,

    // Diagnóstico final
    @SerializedName("finalDiagnosis")
    val finalDiagnosis: String? = null,

    // Técnicos que intervinieron
    @SerializedName("dismantledBy")
    val dismantledBy: String? = null,

    @SerializedName("pretestReviewedBy")
    val pretestReviewedBy: String? = null,

    @SerializedName("programmedBy")
    val programmedBy: String? = null,

    @SerializedName("cleanedBy")
    val cleanedBy: String? = null,

    @SerializedName("qaPerformedBy")
    val qaPerformedBy: String? = null,

    @SerializedName("batteryTestedBy")
    val batteryTestedBy: String? = null,

    @SerializedName("receivedBy")
    val receivedBy: String? = null,

    @SerializedName("repairTechnician")
    val repairTechnician: String? = null
)
