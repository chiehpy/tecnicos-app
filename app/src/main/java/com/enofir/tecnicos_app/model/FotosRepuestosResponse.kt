package com.enofir.tecnicos_app.model

import com.google.gson.annotations.SerializedName

/**
 * Índice de fotos de repuestos (GET app/repuestos/fotos del MDW).
 * Id de Repuesto__c (15 caracteres) -> versión (fecha de modificación de la foto en el server).
 * Un repuesto que no figura no tiene foto.
 */
data class FotosRepuestosResponse(
    @SerializedName("fotos") val fotos: Map<String, Long>? = null,
)
