package com.enofir.tecnicos_app.model

data class QaNotifyRequest(
    val serial: String,
    val missing: List<String>
)
