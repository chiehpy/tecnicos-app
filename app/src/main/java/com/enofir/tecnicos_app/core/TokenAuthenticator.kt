package com.enofir.tecnicos_app.core

import android.util.Log
import com.enofir.tecnicos_app.model.RefreshRequest
import com.enofir.tecnicos_app.model.RefreshResponse
import com.google.gson.Gson
import okhttp3.Authenticator
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.Route
import java.util.concurrent.TimeUnit

/**
 * Authenticator que maneja respuestas 401 intentando refrescar el token.
 * Si el refresh falla, invoca el callback onAuthFailed para que la app
 * redirija al usuario al login.
 */
class TokenAuthenticator(
    private val baseUrl: String,
    private val refreshTokenProvider: () -> String?,
    private val onTokenRefreshed: (String) -> Unit,
    private val onAuthFailed: () -> Unit
) : Authenticator {

    companion object {
        private const val TAG = "TokenAuthenticator"
    }

    private val gson = Gson()

    // Cliente HTTP simple sin authenticator para evitar recursión
    private val refreshClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var isRefreshing = false

    override fun authenticate(route: Route?, response: Response): Request? {
        val path = response.request().url().encodedPath()

        // No intentar refresh si el 401 viene del propio refresh endpoint
        if (path.contains("auth/refresh") || path.contains("auth/login")) {
            Log.w(TAG, "401 en endpoint de auth, no se intenta refresh")
            onAuthFailed()
            return null
        }

        // Evitar múltiples refreshes simultáneos
        synchronized(this) {
            if (isRefreshing) {
                Log.d(TAG, "Refresh ya en progreso, esperando...")
                return null
            }
            isRefreshing = true
        }

        try {
            val refreshToken = refreshTokenProvider()
            if (refreshToken.isNullOrBlank()) {
                Log.w(TAG, "No hay refresh token disponible")
                onAuthFailed()
                return null
            }

            Log.d(TAG, "Intentando refresh token...")

            val newToken = doRefresh(refreshToken)
            if (newToken != null) {
                Log.d(TAG, "Refresh exitoso, reintentando request original")
                onTokenRefreshed(newToken)

                // Reintentar la request original con el nuevo token
                return response.request().newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
            } else {
                Log.w(TAG, "Refresh falló")
                onAuthFailed()
                return null
            }
        } finally {
            synchronized(this) {
                isRefreshing = false
            }
        }
    }

    private fun doRefresh(refreshToken: String): String? {
        return try {
            val requestBody = RefreshRequest(refreshToken)
            val jsonBody = gson.toJson(requestBody)
            val mediaType = MediaType.parse("application/json; charset=utf-8")
            val body = RequestBody.create(mediaType, jsonBody)

            val request = Request.Builder()
                .url("${baseUrl}auth/refresh")
                .post(body)
                .header("Content-Type", "application/json")
                .build()

            val response = refreshClient.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body()?.string()
                if (responseBody != null) {
                    val refreshResponse = gson.fromJson(responseBody, RefreshResponse::class.java)
                    if (refreshResponse.ok && !refreshResponse.token.isNullOrBlank()) {
                        return refreshResponse.token
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error durante refresh: ${e.message}", e)
            null
        }
    }
}
