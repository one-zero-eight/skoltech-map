package com.example.skoltechmapmeasurements.network

import com.example.skoltechmapmeasurements.model.Measurement
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface MeasurementApiService {
    @POST("measurement")
    suspend fun sendMeasurement(@Body measurement: Measurement): Response<Unit>
}

object RetrofitClient {
    private var apiService: MeasurementApiService? = null
    
    fun buildApiService(baseUrl: String): MeasurementApiService {
        if (apiService == null || !baseUrl.contains(apiService.hashCode().toString())) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            apiService = retrofit.create(MeasurementApiService::class.java)
        }

        return apiService!!
    }
}