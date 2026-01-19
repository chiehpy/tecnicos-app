package com.enofir.tecnicos_app.model

import com.google.gson.annotations.SerializedName

/**
 * Contrato para POST /terminal/event (MDW)
 *
 * ASSIGN:
 * - action, serial, role, technicianName
 *
 * COMPLETE:
 * - action, serial, role
 * - failureObservations: solo para role == "Revisión inicial"
 *
 * MODIFY:
 * - action, serial, targetStatus
 * - targetSubstatus: opcional (ej. "Carga de firmware + Inyección")
 *
 * Nota: campos null no se serializan (Gson por defecto no incluye nulls
 * salvo que se configure serializeNulls()).
 */
data class TerminalEventRequest(

    @SerializedName("action")
    val action: String,

    @SerializedName("serial")
    val serial: String,

    @SerializedName("role")
    val role: String? = null,

    @SerializedName("technicianName")
    val technicianName: String? = null,

    @SerializedName("failureObservations")
    val failureObservations: List<String>? = null,

    @SerializedName("targetStatus")
    val targetStatus: String? = null,

    @SerializedName("targetSubstatus")
    val targetSubstatus: String? = null
)
