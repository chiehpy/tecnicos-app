package com.enofir.tecnicos_app.core

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.enofir.tecnicos_app.model.AppVersionResponse
import com.enofir.tecnicos_app.model.CatalogsResponse
import com.enofir.tecnicos_app.model.LoginRequest
import com.enofir.tecnicos_app.model.LoginResponse
import com.enofir.tecnicos_app.model.PrintLabelRequest
import com.enofir.tecnicos_app.model.PrintLabelResponse
import com.enofir.tecnicos_app.model.HistoryResponse
import com.enofir.tecnicos_app.model.QaNotifyRequest
import com.enofir.tecnicos_app.model.RecoveryPatchRequest
import com.enofir.tecnicos_app.model.TerminalEventRequest
import com.enofir.tecnicos_app.model.TerminalEventResponse
import com.enofir.tecnicos_app.model.TerminalLookupResponse
import com.enofir.tecnicos_app.model.VmCheckResponse
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    @Volatile
    var baseUrl: String = "http://167.234.226.219:8000/"
        private set

    @Volatile
    var enableHttpLog: Boolean = true

    /**
     * Listener para notificar cuando la autenticación falla y se debe
     * redirigir al usuario al login.
     */
    interface AuthFailedListener {
        fun onAuthFailed()
    }

    @Volatile
    private var retrofitInstance: Retrofit? = null

    @Volatile
    private var sessionManager: SessionManager? = null

    @Volatile
    private var appContext: Context? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private val authFailedListeners = mutableSetOf<AuthFailedListener>()

    const val ACTION_AUTH_FAILED = "com.enofir.tecnicos_app.AUTH_FAILED"

    /**
     * Inicializa el ApiClient con el contexto de la aplicación.
     * DEBE llamarse en Application.onCreate() o antes de usar la API.
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        sessionManager = SessionManager(context.applicationContext)
    }

    /**
     * Si cambiás la URL (Settings), hay que recrear Retrofit.
     */
    fun setBaseUrl(newBaseUrl: String) {
        val v = newBaseUrl.trim()
        require(v.isNotEmpty()) { "baseUrl vacía" }

        // Asegurar barra al final, Retrofit la requiere para rutas relativas.
        val normalized = if (v.endsWith("/")) v else "$v/"

        // Si cambia, invalida retrofit
        if (normalized != baseUrl) {
            baseUrl = normalized
            retrofitInstance = null
        }
    }

    fun addAuthFailedListener(listener: AuthFailedListener) {
        synchronized(authFailedListeners) {
            authFailedListeners.add(listener)
        }
    }

    fun removeAuthFailedListener(listener: AuthFailedListener) {
        synchronized(authFailedListeners) {
            authFailedListeners.remove(listener)
        }
    }

    private fun notifyAuthFailed() {
        mainHandler.post {
            sessionManager?.clear()

            synchronized(authFailedListeners) {
                authFailedListeners.toList().forEach { it.onAuthFailed() }
            }

            // Broadcast para que cualquier Activity pueda escuchar
            appContext?.let { ctx ->
                val intent = Intent(ACTION_AUTH_FAILED)
                ctx.sendBroadcast(intent)
            }
        }
    }

    private fun retrofit(): Retrofit {
        val existing = retrofitInstance
        if (existing != null) return existing

        synchronized(this) {
            val existing2 = retrofitInstance
            if (existing2 != null) return existing2

            val session = sessionManager
                ?: throw IllegalStateException("ApiClient no inicializado. Llama a ApiClient.init(context) primero.")

            val logger = HttpLoggingInterceptor().apply {
                level = if (enableHttpLog) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            }

            val authInterceptor = AuthInterceptor(
                tokenProvider = { session.getToken() }
            )

            val tokenAuthenticator = TokenAuthenticator(
                baseUrl = baseUrl,
                refreshTokenProvider = { session.getRefreshToken() },
                onTokenRefreshed = { newToken -> session.updateToken(newToken) },
                onAuthFailed = { notifyAuthFailed() }
            )

            val client = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(logger)
                .authenticator(tokenAuthenticator)
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

    fun login(username: String, pin: String): Call<LoginResponse> {
        val u = username.trim()
        val p = pin.trim()

        require(u.isNotEmpty()) { "username vacío" }
        require(p.isNotEmpty()) { "pin vacío" }

        val payload = LoginRequest(
            username = u,
            pin = p
        )

        return api().login(payload)
    }

    fun assign(serial: String, role: String, technicianName: String): Call<TerminalEventResponse> {
        val s = serial.trim()
        val r = role.trim()
        val t = technicianName.trim()

        require(s.isNotEmpty()) { "serial vacío" }
        require(r.isNotEmpty()) { "role vacío" }
        require(t.isNotEmpty()) { "technicianName vacío" }

        val payload = TerminalEventRequest(
            action = "ASSIGN",
            serial = s,
            role = r,
            technicianName = t
        )

        return api().terminalEvent(payload)
    }

    fun complete(
        serial: String,
        role: String,
        failureObservations: List<String>? = null,
        appOk: Boolean? = null,
        firmwareOk: Boolean? = null,
        llaveOk: Boolean? = null,
        spareParts: List<String>? = null,
        caseId: String? = null,
        repairTime: String? = null,
        initialDiagnosis: List<String>? = null,
        technicianName: String? = null
    ): Call<TerminalEventResponse> {
        val s = serial.trim()
        val r = role.trim()

        require(s.isNotEmpty()) { "serial vacío" }
        require(r.isNotEmpty()) { "role vacío" }

        val payload = TerminalEventRequest(
            action = "COMPLETE",
            serial = s,
            role = r,
            technicianName = technicianName?.trim()?.takeIf { it.isNotEmpty() },
            failureObservations = failureObservations?.takeIf { it.isNotEmpty() },
            appOk = appOk,
            firmwareOk = firmwareOk,
            llaveOk = llaveOk,
            spareParts = spareParts?.takeIf { it.isNotEmpty() },
            caseId = caseId,
            repairTime = repairTime,
            initialDiagnosis = initialDiagnosis?.takeIf { it.isNotEmpty() }
        )

        return api().terminalEvent(payload)
    }

    fun lookup(serial: String): Call<TerminalLookupResponse> {
        val s = serial.trim()
        require(s.isNotEmpty()) { "serial vacío" }
        return api().terminalLookup(s)
    }

    /**
     * MODIFY (Recovery u otros cambios de estado):
     * - action="MODIFY", serial, targetStatus (req)
     * - targetSubstatus (opt)
     * - technicianName (opt, solo permitido cuando targetStatus="Pendiente de facturación")
     * - recoveredParts (opt, máx 500 chars)
     * - failureObservations (opt, si MDW lo usa en algún flujo)
     */
    fun modify(
        serial: String,
        targetStatus: String,
        targetSubstatus: String? = null,
        technicianName: String? = null,
        recoveredParts: String? = null,
        failureObservations: List<String>? = null,
        initialDiagnosis: List<String>? = null,
        role: String? = null
    ): Call<TerminalEventResponse> {
        val s = serial.trim()
        val ts = targetStatus.trim()
        val tss = targetSubstatus?.trim()?.takeIf { it.isNotEmpty() }
        val tech = technicianName?.trim()?.takeIf { it.isNotEmpty() }

        val parts = recoveredParts
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                require(it.length <= 500) { "recoveredParts excede 500 caracteres" }
                it
            }

        require(s.isNotEmpty()) { "serial vacío" }
        require(ts.isNotEmpty()) { "targetStatus vacío" }

        val payload = TerminalEventRequest(
            action = "MODIFY",
            serial = s,
            role = role?.trim()?.takeIf { it.isNotEmpty() },
            targetStatus = ts,
            targetSubstatus = tss,
            technicianName = tech,
            recoveredParts = parts,
            failureObservations = failureObservations?.takeIf { it.isNotEmpty() },
            initialDiagnosis = initialDiagnosis?.takeIf { it.isNotEmpty() }
        )

        return api().terminalEvent(payload)
    }

    /**
     * REJECT (QA): guarda observaciones QA (QAObservations__c) vía MDW.
     * Payload esperado:
     * {
     *   "action":"REJECT",
     *   "serial":"...",
     *   "role":"QA",
     *   "qaObservations":"A;B;C"
     * }
     */
    fun reject(
        serial: String,
        role: String,
        qaObservations: String,
        technicianName: String? = null
    ): Call<TerminalEventResponse> {
        val s = serial.trim()
        val r = role.trim()
        val qa = qaObservations.trim()

        require(s.isNotEmpty()) { "serial vacío" }
        require(r.isNotEmpty()) { "role vacío" }
        require(qa.isNotEmpty()) { "qaObservations vacío" }

        val payload = TerminalEventRequest(
            action = "REJECT",
            serial = s,
            role = r,
            technicianName = technicianName?.trim()?.takeIf { it.isNotEmpty() },
            qaObservations = qa
        )

        return api().terminalEvent(payload)
    }

    /**
     * Actualiza los repuestos recuperados de un terminal (Recovery).
     * PATCH /recovery/{id}
     */
    fun updateRecovery(recordId: String, recoveredParts: String): Call<TerminalEventResponse> {
        val id = recordId.trim()
        val parts = recoveredParts.trim()

        require(id.isNotEmpty()) { "recordId vacío" }
        require(parts.isNotEmpty()) { "recoveredParts vacío" }

        val payload = RecoveryPatchRequest(recoveredParts = parts)
        return api().updateRecovery(id, payload)
    }

    /**
     * Obtiene el ZPL para imprimir etiqueta de un terminal.
     * POST /print/label
     */
    fun printLabel(serial: String, darkness: Int? = null, lsMm: Int? = null): Call<PrintLabelResponse> {
        val s = serial.trim()
        require(s.isNotEmpty()) { "serial vacío" }

        val payload = PrintLabelRequest(serial = s, darkness = darkness, lsMm = lsMm)
        return api().printLabel(payload)
    }

    /**
     * Obtiene la versión más reciente de la app disponible.
     * GET /app/version
     */
    fun getAppVersion(): Call<AppVersionResponse> {
        return api().getAppVersion()
    }

    fun fetchCatalogs(): Call<CatalogsResponse> {
        return api().getCatalogs()
    }

    fun vmCheck(serial: String): Call<VmCheckResponse> {
        val s = serial.trim()
        require(s.isNotEmpty()) { "serial vacío" }
        return api().vmCheck(s)
    }

    fun getHistory(): Call<HistoryResponse> = api().getHistory()

    fun qaNotify(serial: String, missing: List<String>): Call<TerminalEventResponse> {
        val s = serial.trim()
        require(s.isNotEmpty()) { "serial vacío" }
        require(missing.isNotEmpty()) { "missing vacío" }
        return api().qaNotify(QaNotifyRequest(serial = s, missing = missing))
    }

    /**
     * Resetea Retrofit (por ejemplo luego de cambiar baseUrl o togglear logs).
     * Si querés reset total, pasá clearInit=true (requiere init() de nuevo).
     */
    fun reset(clearInit: Boolean = false) {
        retrofitInstance = null
        if (clearInit) {
            sessionManager = null
            appContext = null
        }
    }
}
