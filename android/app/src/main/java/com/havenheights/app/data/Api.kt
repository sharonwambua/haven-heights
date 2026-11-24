package com.havenheights.app.data

import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.havenheights.app.BuildConfig
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface HavenApi {
    @POST("auth/login") suspend fun login(@Body body: LoginRequest): AuthResponse
    @POST("auth/change-password") suspend fun changePassword(@Body body: ChangePasswordBody)

    @POST("devices") suspend fun registerDevice(@Body body: DeviceBody)
    @POST("devices/unregister") suspend fun unregisterDevice(@Body body: DeviceBody)

    @GET("me") suspend fun me(): Me
    @PATCH("me") suspend fun updateMe(@Body body: ProfileUpdate): Me
    @PATCH("estate") suspend fun updateEstate(@Body body: EstateUpdate): Estate
    @GET("dashboard") suspend fun dashboard(): Dashboard

    @GET("bills") suspend fun bills(): List<Bill>
    @POST("bills") suspend fun createBill(@Body body: NewBill): Bill
    @POST("bills/generate-rent") suspend fun generateRent(): RentRun
    @PATCH("bills/{id}/status") suspend fun setBillStatus(@Path("id") id: Int, @Body body: BillStatusBody): Bill
    @DELETE("bills/{id}") suspend fun deleteBill(@Path("id") id: Int)
    @POST("bills/{id}/submit-payment") suspend fun submitPayment(@Path("id") id: Int, @Body body: SubmitPaymentBody): Bill
    @POST("bills/{id}/review") suspend fun reviewBill(@Path("id") id: Int, @Body body: ReviewBody): Bill

    @GET("notifications") suspend fun inbox(): Inbox
    @POST("notifications/read") suspend fun markInboxRead()

    @PATCH("leases/{id}") suspend fun updateLease(@Path("id") id: Int, @Body body: LeaseUpdate): Tenant
    @Multipart @POST("leases/{id}/file")
    suspend fun uploadLease(@Path("id") id: Int, @Part file: MultipartBody.Part): Tenant
    @GET("leases/{id}/file") suspend fun leaseFile(@Path("id") id: Int): ResponseBody

    @GET("overview") suspend fun overview(): Overview

    @GET("tenants") suspend fun tenants(): List<Tenant>
    @POST("tenants") suspend fun registerTenant(@Body body: TenantCreate): TenantCreated
    @PATCH("tenants/{id}/move-out") suspend fun moveOut(@Path("id") id: Int): Tenant

    @Multipart
    @POST("readings/scan")
    suspend fun scanReading(@Part photo: MultipartBody.Part): ReadingSuggestion

    @Multipart
    @POST("readings")
    suspend fun saveReading(
        @Part("tenancy_id") tenancyId: RequestBody,
        @Part("value") value: RequestBody,
        @Part("confirm_large_jump") confirm: RequestBody,
        @Part("source") source: RequestBody,
        @Part("recorded_at") recordedAt: RequestBody?,
        @Part photo: MultipartBody.Part?,
    ): Reading

    @GET("tenants/{id}/readings") suspend fun readings(@Path("id") id: Int): List<Reading>
    @GET("readings/mine") suspend fun myUsage(): MyUsage
    @GET("readings/{id}/photo") suspend fun photo(@Path("id") id: Int): ResponseBody
    @DELETE("readings/{id}") suspend fun deleteReading(@Path("id") id: Int)

    @GET("requests") suspend fun requests(): List<MaintenanceRequest>
    @POST("requests") suspend fun createRequest(@Body body: NewRequest): MaintenanceRequest
    @PATCH("requests/{id}/status")
    suspend fun updateRequest(@Path("id") id: Int, @Body body: StatusUpdate): MaintenanceRequest

    @GET("announcements") suspend fun announcements(): List<Announcement>
    @POST("announcements") suspend fun postAnnouncement(@Body body: NewAnnouncement): Announcement
    @DELETE("announcements/{id}") suspend fun deleteAnnouncement(@Path("id") id: Int)
}

/** One Gson for the network and the local cache: camelCase fields <-> snake_case JSON. */
object HavenJson {
    val gson: Gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()
}

object ApiFactory {
    fun create(session: SessionStore): HavenApi {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                session.token?.let { builder.header("Authorization", "Bearer $it") }
                chain.proceed(builder.build())
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                else HttpLoggingInterceptor.Level.NONE
            })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS) // meter photos can be several MB
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(HavenJson.gson))
            .build()
            .create(HavenApi::class.java)
    }
}
