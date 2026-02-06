package com.enofir.tecnicos_app.model

data class HistoryEntry(
    val ts: Long,           // timestamp fin (System.currentTimeMillis)
    val serial: String,
    val role: String,
    val action: String,     // ASSIGN / COMPLETE / MODIFY / REJECT
    val ok: Boolean,
    val message: String?,
    val startTs: Long = 0   // timestamp inicio (0 si no se registró)
)
