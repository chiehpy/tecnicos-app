package com.enofir.tecnicos_app.core

import java.util.concurrent.atomic.AtomicLong

/**
 * Mantiene el offset entre el reloj del dispositivo y la hora real (la del MDW),
 * para que cálculos de tiempo "en vivo" (ej. cuentas regresivas) no dependan de
 * que el reloj del teléfono del técnico esté bien configurado.
 *
 * El offset se actualiza a partir del header HTTP "Date" de cada respuesta del
 * MDW (ver [ServerTimeInterceptor]), que ya lo envía automáticamente cualquier
 * request (incluido "health", sin auth). No hace falta un endpoint dedicado.
 */
object ServerTimeSync {

    // offsetMillis = horaServidor - horaDispositivo en el momento de la última respuesta.
    // Arranca en 0: hasta la primera respuesta del MDW, nowMillis() cae al reloj local.
    private val offsetMillis = AtomicLong(0L)

    @Volatile
    private var synced = false

    fun updateFromServerTime(serverMillis: Long, receivedAtMillis: Long = System.currentTimeMillis()) {
        offsetMillis.set(serverMillis - receivedAtMillis)
        synced = true
    }

    /** Hora actual corregida contra el servidor (o la del dispositivo si todavía no hubo respuesta del MDW). */
    fun nowMillis(): Long = System.currentTimeMillis() + offsetMillis.get()

    /** True si ya se recibió al menos una respuesta del MDW con header Date. */
    fun isSynced(): Boolean = synced
}
