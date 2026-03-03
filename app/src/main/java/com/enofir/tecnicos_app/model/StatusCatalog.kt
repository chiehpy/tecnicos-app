package com.enofir.tecnicos_app.model

import com.enofir.tecnicos_app.core.CatalogsStore

object StatusCatalog {

    val OPTIONS: List<String> get() = CatalogsStore.statuses

    const val REVISION_INICIAL = "Revisión inicial"
    const val REPARACION_TECNICA = "Reparación Técnica"
    const val LIMPIEZA = "Limpieza"
    const val TESTEO = "Testeo"
    const val IRREPARABLE = "Irreparable"

    val SUBSTATUS_REPARACION: List<String> get() = CatalogsStore.substatusReparacion
}
