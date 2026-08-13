package com.enofir.tecnicos_app.model

import com.google.gson.annotations.SerializedName

/**
 * Verificación E2E pendiente (Feature 3): una actualización que el MDW no pudo
 * confirmar en Salesforce. La fuente de verdad es el MDW (`pending_verifications`
 * en mdw.db), no la app: por eso el gate sobrevive a que la app se cierre.
 */
data class PendingVerification(
    val id: Int = 0,
    val serial: String = "",
    val action: String = "",
    val role: String? = null,
    @SerializedName("expectedStatus")
    val expectedStatus: String? = null,
    @SerializedName("actualStatus")
    val actualStatus: String? = null,
    val mismatches: List<Map<String, Any?>> = emptyList(),
    val attempts: Int = 0,
    @SerializedName("lastError")
    val lastError: String? = null,
    @SerializedName("createdAt")
    val createdAt: String? = null,
)

/** Motivo con el que el técnico puede despachar una pendiente que no logró resolver. */
data class AckReason(
    val code: String = "",
    val label: String = "",
)

data class MisPendientesResponse(
    val pendientes: List<PendingVerification> = emptyList(),
    val reasons: List<AckReason> = emptyList(),
)

data class DespacharRequest(
    @SerializedName("reasonCode")
    val reasonCode: String,
    val detail: String? = null,
)

data class DespacharResponse(
    val ok: Boolean = false,
    /** true = ya estaba resuelta o despachada (o es de otro técnico). */
    val already: Boolean = false,
)
