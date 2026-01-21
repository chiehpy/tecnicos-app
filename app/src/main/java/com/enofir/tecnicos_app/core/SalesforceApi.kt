package com.enofir.tecnicos_app.core

import com.enofir.tecnicos_app.model.LoginRequest
import com.enofir.tecnicos_app.model.LoginResponse
import com.enofir.tecnicos_app.model.RefreshRequest
import com.enofir.tecnicos_app.model.RefreshResponse
import com.enofir.tecnicos_app.model.TerminalEventRequest
import com.enofir.tecnicos_app.model.TerminalEventResponse
import com.enofir.tecnicos_app.model.TerminalLookupResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
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
}
