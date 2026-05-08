package com.enofir.tecnicos_app.model

import com.enofir.tecnicos_app.core.CatalogsStore

object FailureObservationsCatalog {

    const val NONE = "Sin falla"

    data class FallaCategory(val name: String, val subOptions: List<String>)

    val CATEGORIES: List<FallaCategory> = listOf(
        FallaCategory("Carcasa posterior", listOf(
            "Carcasa posterior rayada",
            "Carcasa posterior golpeada",
            "Carcasa posterior rota",
            "Carcasa posterior desgastada"
        )),
        FallaCategory("Carcasa frontal", listOf(
            "Carcasa frontal rayada",
            "Carcasa frontal golpeada",
            "Carcasa frontal rota",
            "Carcasa frontal desgastada"
        )),
        FallaCategory("Tapa de bateria", listOf(
            "Tapa de bateria rayada",
            "Tapa de bateria golpeada",
            "Tapa de bateria rota",
            "Tapa de bateria desgastada",
            "Sin tapa de bateria"
        )),
        FallaCategory("Tapa de impresora", listOf(
            "Tapa de impresora rayada",
            "Tapa de impresora golpeada",
            "Tapa de impresora rota",
            "Tapa de impresora desgastada",
            "Sin tapa de impresora"
        )),
        FallaCategory("Display", listOf(
            "Display rayado",
            "Display golpeado",
            "Display roto"
        )),
        FallaCategory("Placa dañada", listOf(
            "Placa dañada - no bootea",
            "Placa dañada - no enciende",
            "Placa dañada - no anda impresora",
            "Placa dañada - sobrecalienta",
            "Placa dañada - sulfatada",
            "Placa dañada - tamper permanente"
        )),
    )

    private val CATEGORY_SUB_OPTIONS: Set<String> = CATEGORIES.flatMap { it.subOptions }.toSet()

    /** Opciones planas — no son sub-opciones de ninguna categoría. Se muestran como checkboxes directos. */
    val FLAT_OPTIONS: List<String>
        get() = CatalogsStore.failureObservations.filter { it !in CATEGORY_SUB_OPTIONS }

    /** Lista completa (para repair dialog y compatibilidad con eventos anteriores) */
    val OPTIONS: List<String> get() = CatalogsStore.failureObservations
}
