package com.enofir.tecnicos_app.model

import com.google.gson.annotations.SerializedName

data class CatalogsResponse(
    @SerializedName("failure_observations") val failureObservations: List<String>,
    @SerializedName("qa_options") val qaOptions: List<String>,
    @SerializedName("recovered_parts") val recoveredParts: Map<String, List<RecoveredPartItem>>,
    val statuses: List<String>,
    @SerializedName("substatus_reparacion") val substatusReparacion: List<String>
)
