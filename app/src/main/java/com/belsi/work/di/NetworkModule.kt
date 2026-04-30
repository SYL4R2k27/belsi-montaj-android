package com.belsi.work.di

import com.belsi.work.BuildConfig
import com.belsi.work.data.local.TokenManager
import com.belsi.work.data.remote.api.*
import com.belsi.work.data.remote.interceptor.AuthInterceptor
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
            coerceInputValues = true
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        tokenManager: TokenManager,
        authInterceptor: AuthInterceptor
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // BODY показывает полный запрос и ответ включая заголовки и тело
            // Это критично для диагностики проблем с API
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val requestBuilder = request.newBuilder()

                // Публичные эндпоинты, которые НЕ требуют токена
                val publicEndpoints = listOf(
                    "/auth/phone",
                    "/auth/verify",
                    "/auth/login",
                    "/auth/yandex/start",
                    "/auth/yandex/callback",
                    "/auth/sber/callback"
                )

                val isPublicEndpoint = publicEndpoints.any { request.url.encodedPath.contains(it) }

                // Добавляем токен только если это НЕ публичный эндпоинт
                if (!isPublicEndpoint) {
                    val token = tokenManager.getTokenSync()
                    if (token != null) {
                        requestBuilder.addHeader("Authorization", "Bearer $token")
                    }
                }

                // НЕ устанавливаем Content-Type для multipart запросов
                // Retrofit сам установит правильный Content-Type с boundary
                val isMultipart = request.body?.contentType()?.type == "multipart"

                if (!isMultipart) {
                    // Только для не-multipart запросов устанавливаем JSON
                    if (request.header("Content-Type") == null) {
                        requestBuilder.addHeader("Content-Type", "application/json")
                    }
                }

                requestBuilder.addHeader("Accept", "application/json")

                // App version headers — сервер сохраняет в users.app_version,
                // чтобы видеть, у кого какая версия приложения, и понимать
                // обновился ли пользователь после релиза.
                requestBuilder.addHeader("X-App-Version", BuildConfig.VERSION_NAME)
                requestBuilder.addHeader("X-App-Build", BuildConfig.VERSION_CODE.toString())
                requestBuilder.addHeader("X-App-Platform", "android")

                chain.proceed(requestBuilder.build())
            }
            .addInterceptor(authInterceptor) // Обработка 401 ошибок
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .connectionPool(okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES))
            .protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
            // Автоматический retry для network failures
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi {
        return retrofit.create(AuthApi::class.java)
    }

    @Provides
    @Singleton
    fun provideUserApi(retrofit: Retrofit): UserApi {
        return retrofit.create(UserApi::class.java)
    }

    @Provides
    @Singleton
    fun provideShiftApi(retrofit: Retrofit): ShiftApi {
        return retrofit.create(ShiftApi::class.java)
    }

    @Provides
    @Singleton
    fun providePhotoApi(retrofit: Retrofit): PhotoApi {
        return retrofit.create(PhotoApi::class.java)
    }

    @Provides
    @Singleton
    fun provideSupportApi(retrofit: Retrofit): SupportApi {
        return retrofit.create(SupportApi::class.java)
    }

    @Provides
    @Singleton
    fun provideWalletApi(retrofit: Retrofit): WalletApi {
        return retrofit.create(WalletApi::class.java)
    }

    @Provides
    @Singleton
    fun provideInviteApi(retrofit: Retrofit): InviteApi {
        return retrofit.create(InviteApi::class.java)
    }

    @Provides
    @Singleton
    fun provideTeamApi(retrofit: Retrofit): TeamApi {
        return retrofit.create(TeamApi::class.java)
    }

    @Provides
    @Singleton
    fun provideCuratorApi(retrofit: Retrofit): CuratorApi {
        return retrofit.create(CuratorApi::class.java)
    }

    @Provides
    @Singleton
    fun provideChatApi(retrofit: Retrofit): ChatApi {
        return retrofit.create(ChatApi::class.java)
    }

    @Provides
    @Singleton
    fun provideToolsApi(retrofit: Retrofit): ToolsApi {
        return retrofit.create(ToolsApi::class.java)
    }

    @Provides
    @Singleton
    fun provideTasksApi(retrofit: Retrofit): TasksApi {
        return retrofit.create(TasksApi::class.java)
    }

    @Provides
    @Singleton
    fun provideReportApi(retrofit: Retrofit): ReportApi {
        return retrofit.create(ReportApi::class.java)
    }

    @Provides
    @Singleton
    fun providePushApi(retrofit: Retrofit): PushApi {
        return retrofit.create(PushApi::class.java)
    }

    @Provides
    @Singleton
    fun providePauseApi(retrofit: Retrofit): PauseApi {
        return retrofit.create(PauseApi::class.java)
    }

    @Provides
    @Singleton
    fun provideMessengerApi(retrofit: Retrofit): MessengerApi {
        return retrofit.create(MessengerApi::class.java)
    }

    @Provides
    @Singleton
    fun provideObjectsApi(retrofit: Retrofit): ObjectsApi {
        return retrofit.create(ObjectsApi::class.java)
    }

    @Provides
    @Singleton
    fun provideCoordinatorApi(retrofit: Retrofit): CoordinatorApi {
        return retrofit.create(CoordinatorApi::class.java)
    }

    @Provides
    @Singleton
    fun provideVersionApi(retrofit: Retrofit): com.belsi.work.data.remote.api.VersionApi {
        return retrofit.create(com.belsi.work.data.remote.api.VersionApi::class.java)
    }

}
