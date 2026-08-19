package com.enofir.tecnicos_app.model

import com.google.gson.annotations.SerializedName

data class CatalogsResponse(
    @SerializedName("failure_observations") val failureObservations: List<String>,
    @SerializedName("qa_options") val qaOptions: List<String>,
    @SerializedName("recovered_parts") val recoveredParts: Map<String, List<RecoveredPartItem>>,
    val statuses: List<String>,
    @SerializedName("substatus_reparacion") val substatusReparacion: List<String>,
    @SerializedName("repaired_parts") val repairedParts: Map<String, List<RecoveredPartItem>>? = null,

    // ── Catálogo dinámico 2.0 ────────────────────────────────────────────────
    // Todo nullable: contra un MDW viejo llegan null y la app cae al comportamiento
    // anterior (lista plana sin filtrar), que es exactamente como funcionaba antes.
    @SerializedName("catalog_version") val catalogVersion: Int? = null,

    /** Agrupamiento del árbol. DISJUNTO: una falla pertenece a lo sumo a un grupo. */
    @SerializedName("failure_groups") val failureGroups: List<FailureGroup>? = null,

    /** Tags semánticos por falla (irreparable, requiere_abrir, estetico, ...). */
    @SerializedName("failure_families") val failureFamilies: Map<String, List<String>>? = null,

    /** Qué encontró un rol al COMPLETAR su paso. rol -> fallas. */
    @SerializedName("failure_diagnostico") val failureDiagnostico: Map<String, List<String>>? = null,

    /** Con qué razón un rol le pasa la terminal a otro. rol -> claveDestino -> razones. */
    @SerializedName("failure_rebote") val failureRebote: Map<String, Map<String, List<String>>>? = null,

    /** Igual que el anterior pero por rama del Programador: llaves|firmware|ambas. */
    @SerializedName("failure_rebote_programador")
    val failureRebotePorRama: Map<String, Map<String, List<String>>>? = null,

    /** Razones que sostienen sacar la terminal de la línea. rol -> fallas. */
    @SerializedName("failure_irreparable") val failureIrreparable: Map<String, List<String>>? = null,

    /** claveDestino -> {estado, subestado, rol}. La clave incluye el subestado. */
    val destinos: Map<String, DestinoInfo>? = null,
)

/** Grupo del árbol de fallas. El MDW garantiza que los grupos son disjuntos. */
data class FailureGroup(
    val nombre: String = "",
    val miembros: List<String> = emptyList(),
)

/**
 * A quién le llega la terminal con ese (estado, subestado).
 *
 * El subestado NO es decoración: "Reparación Técnica" sola no dice si va al técnico de
 * Reparación o al Programador — lo define el subestado.
 */
data class DestinoInfo(
    val estado: String? = null,
    val subestado: String? = null,
    val rol: String? = null,
)
