package com.enofir.tecnicos_app.core

import com.enofir.tecnicos_app.model.TerminalEventRequest
import com.enofir.tecnicos_app.model.TerminalEventResponse
import com.enofir.tecnicos_app.model.TerminalLookupResponse
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    var baseUrl: String = "http://167.234.226.219:8000/"
    var enableHttpLog: Boolean = true

    val allowedRoles: Set<String> = setOf(
        "Limpieza",
        "QA",
        "Revisión inicial",
        "Programador (carga de firmwares)"
    )

    @Volatile
    private var retrofitInstance: Retrofit? = null

    private fun retrofit(): Retrofit {
        val existing = retrofitInstance
        if (existing != null) return existing

        synchronized(this) {
            val existing2 = retrofitInstance
            if (existing2 != null) return existing2

            val logger = HttpLoggingInterceptor().apply {
                level = if (enableHttpLog) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(logger)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build()

            val created = Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build()

            retrofitInstance = created
            return created
        }
    }

    private fun api(): SalesforceApi =
        retrofit().create(SalesforceApi::class.java)

    private fun requireValidRole(role: String) {
        require(allowedRoles.contains(role)) { "INVALID_ROLE (client): '$role' no está en allowedRoles" }
    }

    fun assign(serial: String, role: String, technicianName: String): Call<TerminalEventResponse> {
        val s = serial.trim()
        val r = role.trim()
        val t = technicianName.trim()

        require(s.isNotEmpty()) { "serial vacío" }
        require(t.isNotEmpty()) { "technicianName vacío" }
        requireValidRole(r)

        val payload = TerminalEventRequest(
            action = "ASSIGN",
            serial = s,
            role = r,
            technicianName = t
        )

        return api().terminalEvent(payload)
    }

    fun complete(serial: String, role: String, failureObservations: List<String>? = null): Call<TerminalEventResponse> {
        val s = serial.trim()
        val r = role.trim()

        require(s.isNotEmpty()) { "serial vacío" }
        requireValidRole(r)

        val payload = TerminalEventRequest(
            action = "COMPLETE",
            serial = s,
            role = r,
            technicianName = null,
            failureObservations = failureObservations
        )

        return api().terminalEvent(payload)
    }

    fun lookup(serial: String): Call<TerminalLookupResponse> {
        val s = serial.trim()
        require(s.isNotEmpty()) { "serial vacío" }
        return api().terminalLookup(s)
    }

    fun modify(serial: String, targetStatus: String, targetSubstatus: String? = null): Call<TerminalEventResponse> {
        val s = serial.trim()
        val ts = targetStatus.trim()
        val tss = targetSubstatus?.trim()?.takeIf { it.isNotEmpty() }

        require(s.isNotEmpty()) { "serial vacío" }
        require(ts.isNotEmpty()) { "targetStatus vacío" }

        val payload = TerminalEventRequest(
            action = "MODIFY",
            serial = s,
            targetStatus = ts,
            targetSubstatus = tss
        )

        return api().terminalEvent(payload)
    }

    fun reset() {
        retrofitInstance = null
    }
}
