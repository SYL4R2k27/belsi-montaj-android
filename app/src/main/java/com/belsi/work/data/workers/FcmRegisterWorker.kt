package com.belsi.work.data.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.belsi.work.data.firebase.BelsiFirebaseMessagingService
import com.belsi.work.data.remote.api.PushApi
import com.belsi.work.data.remote.api.RegisterFcmTokenRequest
import com.google.firebase.messaging.FirebaseMessaging
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * FIX(2026-04-30): Воркер для регистрации FCM-токена с retry.
 *
 * Раньше [com.belsi.work.data.repositories.PushRepository.registerFcmToken]
 * вызывался один раз и при сетевой ошибке silently терял токен — отсюда у
 * 6 installers и 2 curators в БД нет fcm_token (push'и не доходят).
 *
 * Теперь:
 * - Repository ставит OneTimeWork с EXPONENTIAL backoff (30с, 1м, 2м, ...)
 * - WorkManager сам ретраит при network failure / 5xx
 * - Retry до успеха (нет лимита попыток)
 */
@HiltWorker
class FcmRegisterWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val pushApi: PushApi,
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "fcm_register_work"
        private const val TAG = "FcmRegisterWorker"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<FcmRegisterWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            // Получаем токен (либо из кеша, либо у Firebase)
            val saved = BelsiFirebaseMessagingService.getSavedToken(context)
            val token = saved ?: try {
                val t = FirebaseMessaging.getInstance().token.await()
                BelsiFirebaseMessagingService.saveToken(context, t)
                t
            } catch (e: Exception) {
                Log.e(TAG, "Failed to obtain FCM token from Firebase", e)
                return Result.retry()
            }

            if (token.isNullOrBlank()) {
                Log.w(TAG, "Empty FCM token — retrying later")
                return Result.retry()
            }

            val response = pushApi.registerFcmToken(RegisterFcmTokenRequest(token))
            if (response.isSuccessful) {
                Log.d(TAG, "FCM token registered: ${token.take(20)}...")
                Result.success()
            } else {
                Log.e(TAG, "Server returned ${response.code()} — will retry")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error registering FCM token, will retry", e)
            Result.retry()
        }
    }
}
