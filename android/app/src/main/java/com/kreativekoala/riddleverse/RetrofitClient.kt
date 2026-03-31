package com.kreativekoala.riddleverse

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.Response
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

/**
 * Singleton OkHttpClient to prevent memory leaks from creating multiple instances.
 * OkHttpClient maintains its own connection pool and thread pool, so reusing
 * a single instance is more efficient.
 */
object HttpClientProvider {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

object RetrofitClient {
    private const val BASE_URL = "https://puzzleverseai.com/"

    val instance: UserProgressApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(HttpClientProvider.client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UserProgressApi::class.java)
    }
}


interface UserProgressApi {
    @GET("get-user-rewards-badges")
    suspend fun getUserProgress(
        @Query("email") email: String
    ): Response<UserProgressResponse>
}

data class UserProgressResponse(
    val rewards: List<Reward>,
    val badges: List<Badge>
)

data class Badge(
    val name: String,
    val description: String,
    val earned: Boolean
)

data class Reward(
    val id: String,
    val category: String, // "theme", "emoji", "icon", "accessory"
    val name: String,
    val emoji: String,
    val unlocked: Boolean,
    val unlockHint: String
)