package com.enofir.tecnicos_app.core

import android.os.Handler
import android.os.Looper
import com.enofir.tecnicos_app.model.TerminalEventResponse
import retrofit2.Call
import java.text.Normalizer
import java.util.concurrent.Executors

/**
 * Verificación E2E de una actualización de estado (Feature 3), lado app.
 *
 * Para cada envío de evento (ASSIGN/COMPLETE/MODIFY/REJECT/RELEASE):
 *  - Barrera 1: exige `ok && verified` (verified = read-back que ya hace el MDW).
 *  - Barrera 2: lookup independiente de la app y compara el Status persistido en SF.
 *  - Reintenta automáticamente hasta [MAX_AUTO_RETRIES] con backoff, SOLO ante fallos
 *    transitorios / no confirmados. Los errores de negocio (serial no encontrado,
 *    validaciones) son definitivos → no se reintentan.
 *  - Si tras los reintentos no se confirma, devuelve [Result.Failed] → la UI muestra
 *    error + botón "Reintentar" (bloqueante). El MDW, en paralelo, lleva el flag
 *    interno y el barrido periódico escala por mail si nadie lo resuelve.
 *
 * Todo corre en un worker; los callbacks se postean al main thread.
 */
object EventVerifier {

    const val MAX_AUTO_RETRIES = 3

    // Gap antes de cada reintento (índice = intento fallido - 1)
    private val backoffMs = longArrayOf(800, 2000, 4000)

    // Errores definitivos del MDW/Apex: reintentar no cambia el resultado.
    private val NON_RETRYABLE_CODES = setOf(
        "SF_NOT_FOUND",
        "INVALID_ACTION",
        "INVALID_REQUEST",
        "INVALID_ROLE",
        "INVALID_TARGET_STATUS",
        "INVALID_TARGET_SUBSTATUS",
        "UNKNOWN_PN",
        "SF_PARTS_ERROR",
    )

    sealed class Result {
        data class Success(val response: TerminalEventResponse) : Result()
        data class Failed(
            val lastMessage: String,
            val attempts: Int,
            val lastResponse: TerminalEventResponse? = null,
        ) : Result()
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    /**
     * @param serial          serial de la terminal (para el lookup de la 2da barrera)
     * @param expectedStatus  estado esperado si la app lo conoce (MODIFY → targetStatus).
     *                        Si es null, se usa el `status` que devuelve el MDW.
     * @param callFactory     crea un Call nuevo por intento (Retrofit Calls no se reusan).
     * @param onResult        callback en main thread con Success/Failed.
     */
    fun run(
        serial: String,
        expectedStatus: String?,
        maxAttempts: Int = MAX_AUTO_RETRIES,
        callFactory: () -> Call<TerminalEventResponse>,
        onResult: (Result) -> Unit,
    ) {
        worker.execute {
            var lastMessage = "No se pudo confirmar la actualización en Salesforce"
            var lastResponse: TerminalEventResponse? = null
            var attempt = 0
            while (attempt < maxAttempts) {
                attempt++
                val outcome = try {
                    attemptOnce(serial, expectedStatus, callFactory)
                } catch (e: Exception) {
                    // Fallo de red / excepción → transitorio, reintentable
                    AttemptResult(ok = false, message = e.message ?: "Error de conexión", response = null, retryable = true)
                }
                if (outcome.ok && outcome.response != null) {
                    val resp = outcome.response
                    mainHandler.post { onResult(Result.Success(resp)) }
                    return@execute
                }
                lastMessage = outcome.message
                lastResponse = outcome.response
                if (!outcome.retryable) break            // error definitivo → no reintentar
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(backoffMs[(attempt - 1).coerceIn(0, backoffMs.size - 1)])
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
            }
            val finalMsg = lastMessage
            val finalResponse = lastResponse
            val finalAttempts = attempt
            mainHandler.post { onResult(Result.Failed(finalMsg, finalAttempts, finalResponse)) }
        }
    }

    private data class AttemptResult(
        val ok: Boolean,
        val message: String,
        val response: TerminalEventResponse?,
        val retryable: Boolean,
    )

    private fun attemptOnce(
        serial: String,
        expectedStatus: String?,
        callFactory: () -> Call<TerminalEventResponse>,
    ): AttemptResult {
        // Barrera 1: enviar evento y exigir ok && verified
        val resp = callFactory().execute()
        val body = resp.body()
        if (!resp.isSuccessful || body == null) {
            val msg = resp.errorBody()?.string()?.trim()?.takeIf { it.isNotEmpty() }
                ?: body?.message
                ?: "Error del servidor (${resp.code()})"
            // 5xx / sin código → transitorio; 4xx → determinístico
            return AttemptResult(false, msg, body, retryable = resp.code() >= 500 || resp.code() == 0)
        }
        if (!body.ok) {
            val retryable = (body.errorCode ?: "") !in NON_RETRYABLE_CODES
            return AttemptResult(false, body.message ?: "Error del servidor", body, retryable)
        }
        // verified == false ⇒ el MDW no pudo confirmar el read-back (transitorio).
        // null ⇒ no aplica / MDW viejo ⇒ no bloquea.
        if (body.verified == false) {
            return AttemptResult(false, body.message ?: "El MDW no pudo confirmar el cambio en Salesforce", body, retryable = true)
        }

        // Barrera 2: lookup independiente + comparación del estado persistido
        val target = expectedStatus?.takeIf { it.isNotBlank() } ?: body.status
        if (!target.isNullOrBlank()) {
            val lookup = try {
                ApiClient.lookup(serial).execute()
            } catch (e: Exception) {
                return AttemptResult(false, "No se pudo re-consultar el estado en Salesforce", body, retryable = true)
            }
            val actual = lookup.body()?.data?.cs?.status
            if (actual.isNullOrBlank()) {
                return AttemptResult(false, "No se pudo re-consultar el estado en Salesforce", body, retryable = true)
            }
            if (!statusMatches(actual, target)) {
                return AttemptResult(false, "El estado en Salesforce no coincide (esperado \"$target\", actual \"$actual\")", body, retryable = true)
            }
        }

        return AttemptResult(true, body.message ?: "OK", body, retryable = false)
    }

    private fun norm(s: String?): String {
        val t = (s ?: "").trim().lowercase()
        return Normalizer.normalize(t, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    }

    private fun statusMatches(actual: String?, expected: String?): Boolean {
        val a = norm(actual)
        val e = norm(expected)
        if (a.isEmpty() || e.isEmpty()) return false
        if (a == e) return true
        // Parche de renombrado en SF: "Pendiente de facturación" ~ "Pendiente de Envío"
        val pend = setOf("pendiente de facturacion", "pendiente de envio")
        return a in pend && e in pend
    }
}
