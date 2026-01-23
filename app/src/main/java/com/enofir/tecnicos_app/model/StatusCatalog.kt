package com.enofir.tecnicos_app.model

object StatusCatalog {

    /**
     * Estados válidos para MODIFY (en orden de flujo).
     * Nota: El MDW debe aceptar todos estos valores en targetStatus
     */
    val OPTIONS: List<String> = listOf(
        "Revisión inicial",
        "Reparación Técnica",
        "Limpieza",
        "Testeo",
        "Irreparable"
    )

    const val REVISION_INICIAL = "Revisión inicial"
    const val REPARACION_TECNICA = "Reparación Técnica"
    const val LIMPIEZA = "Limpieza"
    const val TESTEO = "Testeo"
    const val IRREPARABLE = "Irreparable"

    /**
     * Substatus válidos para "Reparación Técnica"
     */
    val SUBSTATUS_REPARACION: List<String> = listOf(
        "Carga de firmware",
        "Reparación",
        "Carga de firmware + Inyección"
    )
}
