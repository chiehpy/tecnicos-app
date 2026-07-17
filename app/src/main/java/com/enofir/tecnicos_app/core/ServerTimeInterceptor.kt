package com.enofir.tecnicos_app.core

import okhttp3.Interceptor
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Interceptor de OkHttp que lee el header HTTP "Date" de cada respuesta del MDW
 * y actualiza [ServerTimeSync] con ese valor. Es de solo lectura: no modifica el
 * request ni la response.
 */
class ServerTimeInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        response.header("Date")?.let { dateHeader ->
            parseHttpDate(dateHeader)?.let { serverMillis ->
                ServerTimeSync.updateFromServerTime(serverMillis)
            }
        }
        return response
    }

    private fun parseHttpDate(value: String): Long? {
        return try {
            // Formato HTTP-date estándar (RFC 7231), ej: "Fri, 17 Jul 2026 19:19:21 GMT".
            val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("GMT")
            }
            format.parse(value)?.time
        } catch (_: Exception) {
            // Header con formato inesperado: se ignora, se sigue usando el offset previo.
            null
        }
    }
}
