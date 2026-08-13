package com.enofir.tecnicos_app.core

import com.enofir.tecnicos_app.model.AppVersionResponse
import com.enofir.tecnicos_app.model.CatalogsResponse
import com.enofir.tecnicos_app.model.DespacharRequest
import com.enofir.tecnicos_app.model.DespacharResponse
import com.enofir.tecnicos_app.model.LoginRequest
import com.enofir.tecnicos_app.model.MisPendientesResponse
import com.enofir.tecnicos_app.model.LoginResponse
import com.enofir.tecnicos_app.model.PhotoUploadRequest
import com.enofir.tecnicos_app.model.PhotoUploadResponse
import com.enofir.tecnicos_app.model.PrintLabelRequest
import com.enofir.tecnicos_app.model.PrintLabelResponse
import com.enofir.tecnicos_app.model.HistoryResponse
import com.enofir.tecnicos_app.model.QaNotifyRequest
import com.enofir.tecnicos_app.model.RecoveryPatchRequest
import com.enofir.tecnicos_app.model.RefreshRequest
import com.enofir.tecnicos_app.model.RefreshResponse
import com.enofir.tecnicos_app.model.TerminalEventRequest
import com.enofir.tecnicos_app.model.TerminalEventResponse
import com.enofir.tecnicos_app.model.TerminalLookupResponse
import com.enofir.tecnicos_app.model.VmCheckResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SalesforceApi {

    @Headers("Content-Type: application/json")
    @POST("auth/login")
    fun login(@Body body: LoginRequest): Call<LoginResponse>

    @Headers("Content-Type: application/json")
    @POST("auth/refresh")
    fun refresh(@Body body: RefreshRequest): Call<RefreshResponse>

    @Headers("Content-Type: application/json")
    @POST("terminal/event")
    fun terminalEvent(@Body body: TerminalEventRequest): Call<TerminalEventResponse>

    @GET("terminal/lookup")
    fun terminalLookup(@Query("serial") serial: String): Call<TerminalLookupResponse>

    @Headers("Content-Type: application/json")
    @PATCH("recovery/{id}")
    fun updateRecovery(
        @Path("id") id: String,
        @Body body: RecoveryPatchRequest
    ): Call<TerminalEventResponse>

    @Headers("Content-Type: application/json")
    @POST("print/label")
    fun printLabel(@Body body: PrintLabelRequest): Call<PrintLabelResponse>

    @GET("app/version")
    fun getAppVersion(): Call<AppVersionResponse>

    @GET("app/catalogs")
    fun getCatalogs(): Call<CatalogsResponse>

    @GET("vm/check")
    fun vmCheck(@Query("serial") serial: String): Call<VmCheckResponse>

    @Headers("Content-Type: application/json")
    @POST("terminal/notify")
    fun qaNotify(@Body body: QaNotifyRequest): Call<TerminalEventResponse>

    @GET("terminal/history")
    fun getHistory(): Call<HistoryResponse>

    @Headers("Content-Type: application/json")
    @POST("terminal/photo")
    fun uploadPhoto(@Body body: PhotoUploadRequest): Call<PhotoUploadResponse>

    // --- Verificación E2E (Feature 3) ---

    /** Pendientes que todavía bloquean al técnico del token. Gate al reabrir la app. */
    @GET("app/mis-pendientes")
    fun misPendientes(): Call<MisPendientesResponse>

    /** El técnico no pudo resolverla: desbloquea la app. La pendiente sigue abierta en el MDW. */
    @Headers("Content-Type: application/json")
    @POST("app/mis-pendientes/{id}/despachar")
    fun despacharPendiente(
        @Path("id") id: Int,
        @Body body: DespacharRequest,
    ): Call<DespacharResponse>
}
