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
 * - appOk: solo para role == "Verificar Apps" (false = SIN APPS)
 * - firmwareOk / llaveOk: solo para role == "Programador (carga de firmwares)" (true = realizado, omitido si no aplica)
 * - spareParts / caseId: solo para role == "Reparación" (lista de PNs usados del stock + SF Case ID)
 *
 * MODIFY:
 * - action, serial, targetStatus
 * - targetSubstatus: opcional (ej. "Carga de firmware + Inyección")
 * - recoveredParts: opcional (Recovery) (máx 500 chars)
 *
 * REJECT:
 * - action, serial, role="QA", qaObservations
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
    val targetSubstatus: String? = null,

    // ===== REJECT (QA) =====
    @SerializedName("qaObservations")
    val qaObservations: String? = null,

    // ===== MODIFY (Recovery) =====
    // Repuestos recuperados (máx 500 chars)
    @SerializedName("recoveredParts")
    val recoveredParts: String? = null,

    // ===== COMPLETE (Verificar Apps) =====
    // null = omitido (APPS OK por defecto), false = SIN APPS
    @SerializedName("appOk")
    val appOk: Boolean? = null,

    // ===== COMPLETE (Programador) =====
    // null = omitido (no aplica), true = operación realizada
    @SerializedName("firmwareOk")
    val firmwareOk: Boolean? = null,

    @SerializedName("llaveOk")
    val llaveOk: Boolean? = null,

    // ===== COMPLETE (Reparación) =====
    // Lista de part numbers utilizados del stock (ej. ["102040064U", "111016685U"])
    @SerializedName("spareParts")
    val spareParts: List<String>? = null,

    // SF Case ID — requerido cuando spareParts no está vacío
    @SerializedName("caseId")
    val caseId: String? = null,

    // Tiempo de reparación HH:MM (desde que se abre la actividad hasta COMPLETE)
    @SerializedName("repair_time")
    val repairTime: String? = null,

    // Fallas adicionales encontradas por el técnico de Reparación (van a InitialDiagnosis__c)
    @SerializedName("initialDiagnosis")
    val initialDiagnosis: List<String>? = null,

    // Comentarios libres (van a Comentarios__c)
    @SerializedName("comments")
    val comments: String? = null,

    // ===== COMPLETE (Revisión inicial) =====
    // Pregunta de pre-test: ¿el firmware es menor a 2.3.0?
    // true = menor a 2.3.0, false = 2.3.0 o superior, null = omitido.
    // El Apex traduce este booleano a un string y lo anexa a Comentarios__c.
    @SerializedName("firmwareBelow230")
    val firmwareBelow230: Boolean? = null,

    // Valor anterior del técnico (para RELEASE)
    @SerializedName("previousValue")
    val previousValue: String? = null
)
