package com.enofir.tecnicos_app.model

import com.enofir.tecnicos_app.core.CatalogsStore

object FailureObservationsCatalog {

    const val NONE = "Sin falla"

    val OPTIONS: List<String> get() = CatalogsStore.failureObservations
}
