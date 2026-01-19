package com.enofir.tecnicos_app.model

import com.google.gson.annotations.SerializedName

data class TerminalLookupCs(

    // En tu log: cs.failureObservations = null
    // Si SF lo devuelve como string, queda OK. Si lo devuelve como lista, lo ajustamos luego.
    @SerializedName("failureObservations")
    val failureObservations: String? = null,

    @SerializedName("status")
    val status: String? = null,

    @SerializedName("id")
    val id: String? = null
)
