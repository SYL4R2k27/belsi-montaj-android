package com.belsi.work.data.repositories

import android.content.Context
import android.util.Log
import com.belsi.work.data.firebase.BelsiFirebaseMessagingService
import com.belsi.work.data.remote.api.PushApi
import com.belsi.work.data.remote.api.RegisterFcmTokenRequest
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Репозиторий для управления push-уведомлениями
 * Регистрирует FCM токен на сервере после авторизации
 */
interface PushRepository {
    /**
     * Регистрация FCM токена на сервере
     * Вызывается после авторизации пользователя
     */
    suspend fun registerFcmToken(): Result<Unit>

    /**
     * Удаление FCM токена с сервера
     * Вызывается при выходе пользователя
     */
    suspend fun unregisterFcmToken(): Result<Unit>

    /**
     * Получить текущий FCM токен
     */
    suspend fun getFcmToken(): String?
}

@Singleton
class PushRepositoryImpl @Inject constructor(
    private val pushApi: PushApi,
    @ApplicationContext private val context: Context
) : PushRepository {

    companion object {
        private const val TAG = "PushRepository"
    }

    override suspend fun registerFcmToken(): Result<Unit> {
        // FIX(2026-04-30): отдаём задачу WorkManager'у с EXPONENTIAL backoff.
        // Раньше при сетевой ошибке токен терялся silently — теперь WorkManager
        // ретраит до успеха, поэтому push-токен в итоге попадёт на сервер.
        return try {
            com.belsi.work.data.workers.FcmRegisterWorker.enqueue(context)
            Log.d(TAG, "FCM register enqueued via WorkManager (with retry)")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue FCM register worker", e)
            // Не фейлим авторизацию из-за этого
            Result.success(Unit)
        }
    }

    override suspend fun unregisterFcmToken(): Result<Unit> {
        return try {
            val response = pushApi.unregisterFcmToken()

            if (response.isSuccessful) {
                Log.d(TAG, "FCM token unregistered successfully")
                Result.success(Unit)
            } else {
                Log.e(TAG, "Failed to unregister FCM token: ${response.code()}")
                Result.failure(Exception("Failed to unregister FCM token"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering FCM token", e)
            Result.success(Unit)
        }
    }

    override suspend fun getFcmToken(): String? {
        return try {
            // Проверяем сохранённый токен
            val savedToken = BelsiFirebaseMessagingService.getSavedToken(context)
            if (savedToken != null) {
                return savedToken
            }

            // Получаем новый токен от Firebase
            val token = FirebaseMessaging.getInstance().token.await()
            BelsiFirebaseMessagingService.saveToken(context, token)
            Log.d(TAG, "FCM token obtained: ${token.take(20)}...")
            token
        } catch (e: Exception) {
            Log.e(TAG, "Error getting FCM token", e)
            null
        }
    }
}
