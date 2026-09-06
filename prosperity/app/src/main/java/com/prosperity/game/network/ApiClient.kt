package com.prosperity.game.network

import com.prosperity.game.data.TokenStore
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds the Retrofit/OkHttp stack against whatever server URL and JWT are
 * currently in [TokenStore]. [rebuild] must be called after the server URL
 * changes (e.g. the user edits it on the login screen) since Retrofit's
 * base URL is fixed at construction time.
 */
class ApiClient(private val tokenStore: TokenStore) {

    var service: ApiService = build()
        private set

    fun rebuild() {
        service = build()
    }

    private fun build(): ApiService {
        val authInterceptor = Interceptor { chain ->
            val token = tokenStore.getToken()
            val request = chain.request().newBuilder().apply {
                if (token != null) addHeader("Authorization", "Bearer $token")
            }.build()
            chain.proceed(request)
        }
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(tokenStore.getServerUrl())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofit.create(ApiService::class.java)
    }
}
