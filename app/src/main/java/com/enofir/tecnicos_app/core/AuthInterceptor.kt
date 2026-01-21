package com.enofir.tecnicos_app.core

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor que agrega el header Authorization: Bearer <token>
 * a todas las requests excepto las de autenticación.
 */
class AuthInterceptor(
    private val tokenProvider: () -> String?
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val path = originalRequest.url().encodedPath()

        // No agregar auth header a endpoints de autenticación
        if (path.contains("auth/login") || path.contains("auth/refresh")) {
            return chain.proceed(originalRequest)
        }

        val token = tokenProvider()
        if (token.isNullOrBlank()) {
            return chain.proceed(originalRequest)
        }

        val authenticatedRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        return chain.proceed(authenticatedRequest)
    }
}
