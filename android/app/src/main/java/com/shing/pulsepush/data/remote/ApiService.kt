package com.shing.pulsepush.data.remote

import com.google.gson.JsonElement
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Backend contract, base path /api/v1/ (PRD section 6 + delivery spec).
 * Paths here are relative: the OkHttp DynamicBaseUrlInterceptor rewrites
 * scheme/host/port to the user-configured server (see NetworkModule), so
 * the Retrofit instance itself is built against a placeholder base URL.
 */
interface ApiService {

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): ApiResponse<AuthData>

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<AuthData>

    @POST("api/v1/devices/bind")
    suspend fun bindDevice(@Body request: BindDeviceRequest): ApiResponse<JsonElement>

    @POST("api/v1/devices/unbind")
    suspend fun unbindDevice(@Body request: UnbindDeviceRequest): ApiResponse<JsonElement>

    @GET("api/v1/channels")
    suspend fun getChannels(): ApiResponse<JsonElement>

    @POST("api/v1/channels")
    suspend fun createChannel(@Body request: CreateChannelRequest): ApiResponse<JsonElement>

    @DELETE("api/v1/channels/{id}")
    suspend fun deleteChannel(@Path("id") id: Long): ApiResponse<JsonElement>

    @GET("api/v1/messages")
    suspend fun getMessages(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): ApiResponse<JsonElement>

    @POST("api/v1/messages/{msg_id}/read")
    suspend fun markMessageRead(@Path("msg_id") msgId: String): ApiResponse<JsonElement>
}
