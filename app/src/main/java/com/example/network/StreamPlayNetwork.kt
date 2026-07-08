package com.example.network

import com.example.models.Channel
import com.example.models.Comment
import com.example.models.UserProfile
import com.example.models.Video
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class AuthRequest(
    val email: String,
    val password: String,
    val displayName: String? = null
)

@JsonClass(generateAdapter = true)
data class GoogleAuthRequest(
    val email: String,
    val displayName: String? = null,
    val avatarUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    val token: String,
    val user: UserProfile
)

@JsonClass(generateAdapter = true)
data class LikeRequest(
    val isLike: Boolean
)

@JsonClass(generateAdapter = true)
data class LikeResponse(
    val success: Boolean,
    val likes: Long,
    val dislikes: Long
)

@JsonClass(generateAdapter = true)
data class CommentRequest(
    val content: String
)

@JsonClass(generateAdapter = true)
data class SubscribeResponse(
    val success: Boolean,
    val isSubscribed: Boolean
)

@JsonClass(generateAdapter = true)
data class SummaryRequest(
    val title: String,
    val description: String?
)

@JsonClass(generateAdapter = true)
data class SummaryResponse(
    val summary: String
)

@JsonClass(generateAdapter = true)
data class QuizRequest(
    val title: String,
    val category: String?
)

@JsonClass(generateAdapter = true)
data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val answerIndex: Int,
    val explanation: String
)

@JsonClass(generateAdapter = true)
data class QuizResponse(
    val questions: List<QuizQuestion>
)

interface StreamPlayApiService {
    // --- Auth Endpoints ---
    @POST("api/auth/register")
    suspend fun register(@Body request: AuthRequest): AuthResponse

    @POST("api/auth/login")
    suspend fun login(@Body request: AuthRequest): AuthResponse

    @POST("api/auth/google")
    suspend fun loginWithGoogle(@Body request: GoogleAuthRequest): AuthResponse

    @POST("api/auth/guest")
    suspend fun loginAsGuest(): AuthResponse

    @GET("api/auth/profile")
    suspend fun getProfile(): UserProfile

    @PUT("api/auth/profile")
    suspend fun updateProfile(@Body profile: UserProfile): UserProfile

    // --- Video Endpoints ---
    @GET("api/videos")
    suspend fun getVideos(
        @Query("category") category: String? = null,
        @Query("isShort") isShort: Boolean? = null,
        @Query("query") query: String? = null
    ): List<Video>

    @GET("api/videos/{id}")
    suspend fun getVideoById(@Path("id") id: String): Video

    @Multipart
    @POST("api/videos/upload")
    suspend fun uploadVideo(
        @Part video: MultipartBody.Part,
        @Part("title") title: RequestBody,
        @Part("description") description: RequestBody,
        @Part("category") category: RequestBody,
        @Part("isShort") isShort: RequestBody
    ): Video

    @POST("api/videos/{id}/like")
    suspend fun likeVideo(
        @Path("id") id: String,
        @Body request: LikeRequest
    ): LikeResponse

    @GET("api/videos/{id}/comments")
    suspend fun getComments(@Path("id") id: String): List<Comment>

    @POST("api/videos/{id}/comments")
    suspend fun postComment(
        @Path("id") id: String,
        @Body request: CommentRequest
    ): Comment

    @GET("api/videos/creator/{id}")
    suspend fun getCreatorChannel(@Path("id") id: String): Channel

    @POST("api/videos/creator/{id}/subscribe")
    suspend fun toggleSubscription(@Path("id") id: String): SubscribeResponse

    // --- AI Endpoints ---
    @POST("api/ai/summary")
    suspend fun getSummary(@Body request: SummaryRequest): SummaryResponse

    @POST("api/ai/quiz")
    suspend fun getQuiz(@Body request: QuizRequest): QuizResponse
}

object StreamPlayRetrofitClient {
    // Dynamic URL fallback matching standard development container context
    private const val BASE_URL = "https://ais-dev-gcqrwcvq4r43ocywbvgn7x-1075067522372.asia-southeast1.run.app/"

    private var authToken: String? = null

    fun setAuthToken(token: String?) {
        authToken = token
    }

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val requestBuilder = original.newBuilder()
        authToken?.let {
            requestBuilder.header("Authorization", "Bearer $it")
        }
        chain.proceed(requestBuilder.build())
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val service: StreamPlayApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(StreamPlayApiService::class.java)
    }
}
