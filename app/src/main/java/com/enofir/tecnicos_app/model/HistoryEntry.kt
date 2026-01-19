package com.enofir.tecnicos_app.model

data class HistoryEntry(
    val ts: Long,           // timestamp (System.currentTimeMillis)
    val serial: String,
    val role: String,
    val action: String,     // ASSIGN / COMPLETE
    val ok: Boolean,
    val message: String?
)
