package com.example.frontend.api

import android.content.Context
import com.example.frontend.utils.SessionManager
import com.google.gson.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.reflect.Type
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object APIClient {
    const val BASE_URL = "http://10.0.2.2:8080/" // Replace URL

    // Keep track of initialization state
    private var isInitialized = false
    private lateinit var appContext: Context

    // Initialize the API client
    fun initialize(context: Context) {
        if (!isInitialized) {
            appContext = context.applicationContext
            SessionManager.init(appContext)
            isInitialized = true
        }
    }

    // Interceptor for logging requests/responses
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // Create authenticator for token refresh
    private val tokenAuthenticator by lazy {
        TokenAuthenticator(appContext)
    }

    // Gson configuration for handling LocalDateTime
    val gson: Gson = GsonBuilder()
        .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeSerializer())
        .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeDeserializer())
        .create()

    // HTTP client configuration
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor())
            .authenticator(tokenAuthenticator)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // Retrofit (type-safe REST client)
    private val retrofit by lazy {
        if (!isInitialized) {
            throw IllegalStateException("APIClient must be initialized first")
        }

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    // API services
    val authService: AuthService by lazy { retrofit.create(AuthService::class.java) }
    val syncService: SyncService by lazy { retrofit.create(SyncService::class.java) }

    // Convert LocalDateTime to JSON
    class LocalDateTimeSerializer : JsonSerializer<LocalDateTime> {
        override fun serialize(src: LocalDateTime?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
            return JsonPrimitive(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(src))
        }
    }

    // Convert JSON to LocalDateTime
    class LocalDateTimeDeserializer : JsonDeserializer<LocalDateTime> {
        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): LocalDateTime {
            return LocalDateTime.parse(json?.asString, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        }
    }
}