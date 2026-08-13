package com.enofir.tecnicos_app.core

import android.app.Activity
import android.util.Log
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.enofir.tecnicos_app.model.AckReason
import com.enofir.tecnicos_app.model.DespacharResponse
import com.enofir.tecnicos_app.model.MisPendientesResponse
import com.enofir.tecnicos_app.model.PendingVerification
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Gate de verificación E2E (Feature 3), lado app.
 *
 * Cuando una actualización no se pudo confirmar en Salesforce, el técnico queda
 * bloqueado hasta que la despacha. La lista de pendientes la sirve el MDW
 * (`GET /app/mis-pendientes`), no la app: por eso el bloqueo **sobrevive a cerrar
 * la app**, y por eso [checkOnResume] lo vuelve a mostrar al reabrirla.
 *
 * Dos entradas distintas:
 *  - [showAfterFailure] — en el momento del fallo, dentro de la pantalla de la
 *    terminal. Ahí el reintento **sí** está disponible (la app todavía tiene el
 *    payload del evento en memoria).
 *  - [checkOnResume] — al reabrir la app. Ahí el reintento **no** está disponible:
 *    el payload original se perdió, así que la única salida es despachar. El
 *    técnico puede volver a escanear la terminal y rehacer la acción.
 *
 * Despachar NO resuelve la pendiente: la fila sigue abierta en el MDW hasta que
 * Salesforce cuadre (nadie en el laboratorio puede arreglar una caída de SF a mano).
 */
object PendingGate {

    private const val TAG = "PendingGate"

    /** Fallback si el MDW no manda el catálogo (MDW viejo). */
    private val DEFAULT_REASONS = listOf(
        AckReason("SIN_CONEXION", "Sin conexión / el sistema no responde"),
        AckReason("SIGUE_FALLANDO", "Reintenté y sigue fallando"),
        AckReason("OTRO", "Otro motivo"),
    )

    /**
     * Diálogo en pantalla, para no apilar dos si onResume corre dos veces seguidas.
     * Se guarda la instancia y no un booleano a propósito: con un flag, una activity
     * destruida con el diálogo abierto lo dejaba en `true` para siempre y el gate no
     * volvía a aparecer nunca.
     */
    private var current: AlertDialog? = null

    private val dialogShowing: Boolean
        get() = current?.isShowing == true

    private fun show(builder: AlertDialog.Builder) {
        val dialog = builder.create()
        dialog.setOnDismissListener { if (current === dialog) current = null }
        current = dialog
        dialog.show()
    }

    /**
     * Chequeo al reabrir la app. Silencioso si no hay nada pendiente o si el MDW
     * no tiene la verificación prendida (devuelve lista vacía).
     */
    fun checkOnResume(activity: Activity) {
        if (dialogShowing) return
        ApiClient.misPendientes().enqueue(object : Callback<MisPendientesResponse> {
            override fun onResponse(
                call: Call<MisPendientesResponse>,
                response: Response<MisPendientesResponse>,
            ) {
                val body = response.body()
                if (!response.isSuccessful || body == null) return
                val pendientes = body.pendientes
                if (pendientes.isEmpty()) return
                if (activity.isFinishing || activity.isDestroyed) return
                showBlocking(
                    activity = activity,
                    pendientes = pendientes,
                    reasons = body.reasons.ifEmpty { DEFAULT_REASONS },
                    onRetry = null,   // al reabrir no hay payload para reintentar
                )
            }

            override fun onFailure(call: Call<MisPendientesResponse>, t: Throwable) {
                // Sin conexión no se puede saber si hay pendientes: no se bloquea a ciegas.
                Log.w(TAG, "No se pudo consultar mis-pendientes: ${t.message}")
            }
        })
    }

    /**
     * Diálogo bloqueante tras agotarse los reintentos automáticos de [EventVerifier].
     * Busca en el MDW la pendiente de este serial para poder despacharla.
     *
     * @param onRetry  reintento del evento original (la pantalla lo tiene en memoria).
     * @param onDespachada  se llama si el técnico la despachó → la UI se desbloquea.
     */
    fun showAfterFailure(
        activity: Activity,
        serial: String,
        message: String,
        onRetry: () -> Unit,
        onDespachada: () -> Unit,
    ) {
        if (dialogShowing) return
        ApiClient.misPendientes().enqueue(object : Callback<MisPendientesResponse> {
            override fun onResponse(
                call: Call<MisPendientesResponse>,
                response: Response<MisPendientesResponse>,
            ) {
                if (activity.isFinishing || activity.isDestroyed) return
                val body = response.body()
                val mias = body?.pendientes
                    ?.filter { it.serial.equals(serial.trim(), ignoreCase = true) }
                    .orEmpty()
                if (mias.isEmpty()) {
                    // El MDW no registró pendiente (verificación apagada, o ya se resolvió
                    // sola entre el fallo y esta consulta) → sólo queda reintentar.
                    showRetryOnly(activity, message, onRetry)
                    return
                }
                showBlocking(
                    activity = activity,
                    pendientes = mias,
                    reasons = body?.reasons?.ifEmpty { DEFAULT_REASONS } ?: DEFAULT_REASONS,
                    onRetry = onRetry,
                    onDespachada = onDespachada,
                )
            }

            override fun onFailure(call: Call<MisPendientesResponse>, t: Throwable) {
                if (activity.isFinishing || activity.isDestroyed) return
                showRetryOnly(activity, message, onRetry)
            }
        })
    }

    /** Sin pendiente en el MDW con la que trabajar: reintentar es la única salida. */
    private fun showRetryOnly(activity: Activity, message: String, onRetry: () -> Unit) {
        show(
            AlertDialog.Builder(activity)
                .setTitle("No se pudo confirmar en Salesforce")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("Reintentar") { d, _ ->
                    d.dismiss()
                    onRetry()
                }
        )
    }

    private fun showBlocking(
        activity: Activity,
        pendientes: List<PendingVerification>,
        reasons: List<AckReason>,
        onRetry: (() -> Unit)?,
        onDespachada: (() -> Unit)? = null,
    ) {
        val pendiente = pendientes.first()
        val restantes = pendientes.size - 1

        val detalle = buildString {
            append("Terminal ").append(pendiente.serial).append("\n")
            append("Acción: ").append(pendiente.action)
            pendiente.role?.takeIf { it.isNotBlank() }?.let { append(" (").append(it).append(")") }
            append("\n")
            if (!pendiente.expectedStatus.isNullOrBlank()) {
                append("Esperado: ").append(pendiente.expectedStatus).append("\n")
            }
            if (!pendiente.actualStatus.isNullOrBlank()) {
                append("En Salesforce: ").append(pendiente.actualStatus).append("\n")
            }
            append("Intentos: ").append(pendiente.attempts)
            if (restantes > 0) {
                append("\n\nTenés ").append(restantes).append(" más sin confirmar.")
            }
            append("\n\nNo se pudo confirmar el cambio en Salesforce. ")
            append(
                if (onRetry != null) "Reintentá, o indicá por qué no pudiste resolverlo."
                else "Indicá por qué no pudiste resolverlo. Para rehacer la acción, volvé a escanear la terminal."
            )
        }

        val builder = AlertDialog.Builder(activity)
            .setTitle("Actualización sin confirmar")
            .setMessage(detalle)
            .setCancelable(false)
            .setNegativeButton("No pude resolverlo") { d, _ ->
                d.dismiss()
                pickReason(activity, pendiente, reasons) {
                    onDespachada?.invoke()
                    // Cadena: si quedaban más, seguir con la siguiente.
                    val resto = pendientes.drop(1)
                    if (resto.isNotEmpty()) {
                        showBlocking(activity, resto, reasons, onRetry, onDespachada)
                    }
                }
            }

        if (onRetry != null) {
            builder.setPositiveButton("Reintentar") { d, _ ->
                d.dismiss()
                onRetry()
            }
        }

        show(builder)
    }

    private fun pickReason(
        activity: Activity,
        pendiente: PendingVerification,
        reasons: List<AckReason>,
        onDone: () -> Unit,
    ) {
        val labels = reasons.map { it.label }.toTypedArray()
        show(
            AlertDialog.Builder(activity)
                .setTitle("¿Por qué no se pudo resolver?")
                .setCancelable(false)
                .setItems(labels) { d, which ->
                    d.dismiss()
                    val reason = reasons[which]
                    if (reason.code == "OTRO") {
                        askDetail(activity, pendiente, reason, onDone)
                    } else {
                        despachar(activity, pendiente, reason.code, null, onDone)
                    }
                }
        )
    }

    private fun askDetail(
        activity: Activity,
        pendiente: PendingVerification,
        reason: AckReason,
        onDone: () -> Unit,
    ) {
        val input = EditText(activity).apply { hint = "Qué pasó" }
        show(
            AlertDialog.Builder(activity)
                .setTitle("Otro motivo")
                .setView(input)
                .setCancelable(false)
                .setPositiveButton("Confirmar") { d, _ ->
                    d.dismiss()
                    despachar(activity, pendiente, reason.code, input.text?.toString(), onDone)
                }
        )
    }

    private fun despachar(
        activity: Activity,
        pendiente: PendingVerification,
        reasonCode: String,
        detail: String?,
        onDone: () -> Unit,
    ) {
        ApiClient.despacharPendiente(pendiente.id, reasonCode, detail)
            .enqueue(object : Callback<DespacharResponse> {
                override fun onResponse(
                    call: Call<DespacharResponse>,
                    response: Response<DespacharResponse>,
                ) {
                    if (activity.isFinishing || activity.isDestroyed) return
                    if (response.isSuccessful) {
                        onDone()
                        return
                    }
                    // El MDW rechazó el despacho: no se puede desbloquear en silencio.
                    retryDespachar(activity, pendiente, reasonCode, detail, onDone,
                        "El sistema no aceptó el despacho (HTTP ${response.code()}).")
                }

                override fun onFailure(call: Call<DespacharResponse>, t: Throwable) {
                    if (activity.isFinishing || activity.isDestroyed) return
                    retryDespachar(activity, pendiente, reasonCode, detail, onDone,
                        "Sin conexión con el sistema: ${t.message}")
                }
            })
    }

    /**
     * Si el despacho no llegó al MDW, el técnico sigue bloqueado: desbloquear en
     * local dejaría al MDW sin registro y la pendiente reaparecería al reabrir.
     */
    private fun retryDespachar(
        activity: Activity,
        pendiente: PendingVerification,
        reasonCode: String,
        detail: String?,
        onDone: () -> Unit,
        message: String,
    ) {
        show(
            AlertDialog.Builder(activity)
                .setTitle("No se pudo registrar")
                .setMessage("$message\n\nHay que registrarlo para poder continuar.")
                .setCancelable(false)
                .setPositiveButton("Reintentar") { d, _ ->
                    d.dismiss()
                    despachar(activity, pendiente, reasonCode, detail, onDone)
                }
        )
    }
}
