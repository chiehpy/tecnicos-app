package com.enofir.tecnicos_app.model

import com.google.gson.annotations.SerializedName

/**
 * Saldo de la mochila del técnico (GET app/mochila del MDW, pasamanos de Enofir_MochilaAPI).
 * Sin mochila: tieneMochila=false e items vacío.
 */
data class MochilaResponse(
    @SerializedName("tecnico") val tecnico: String? = null,
    @SerializedName("tieneMochila") val tieneMochila: Boolean = false,
    @SerializedName("activa") val activa: Boolean = false,
    @SerializedName("mochilaId") val mochilaId: String? = null,
    @SerializedName("items") val items: List<MochilaItem> = emptyList(),
    /** Ids de Repuesto__c que no se controlan por mochila (ej. film): la app no los bloquea. */
    @SerializedName("sinControl") val sinControl: List<String>? = null,
)

data class MochilaItem(
    /** Id de Repuesto__c: es el mismo que `sf_id` del catálogo de repuestos. */
    @SerializedName("repuestoId") val repuestoId: String? = null,
    @SerializedName("nombre") val nombre: String? = null,
    @SerializedName("cantidad") val cantidad: Int = 0,
)
