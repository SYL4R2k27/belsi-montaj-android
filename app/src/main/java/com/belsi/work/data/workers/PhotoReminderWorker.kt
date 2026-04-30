package com.belsi.work.data.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.belsi.work.MainActivity
import com.belsi.work.R
import com.belsi.work.data.local.PrefsManager
import com.belsi.work.data.local.database.dao.PhotoDao
import com.belsi.work.data.repositories.ShiftRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Worker для ежечасных напоминаний о фотоотчёте
 * Проверяет есть ли активная смена и напоминает сделать фото
 */
@HiltWorker
class PhotoReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val shiftRepository: ShiftRepository,
    private val prefsManager: PrefsManager,
    private val photoDao: PhotoDao
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "photo_reminder_work"
        private const val TAG = "PhotoReminderWorker"
        private const val CHANNEL_ID = "photo_reminder_channel"
        private const val NOTIFICATION_ID = 2001

        /**
         * Запланировать периодические напоминания (каждый час)
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val request = PeriodicWorkRequestBuilder<PhotoReminderWorker>(
                1, TimeUnit.HOURS,
                15, TimeUnit.MINUTES // flex interval
            )
                .setConstraints(constraints)
                .setInitialDelay(1, TimeUnit.HOURS) // Первое напоминание через час
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )

            Log.d(TAG, "Photo reminder scheduled")
        }

        /**
         * Отменить напоминания (при завершении смены)
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Photo reminder cancelled")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Checking if photo reminder needed...")

        try {
            // Проверяем есть ли активная смена
            val activeShift = shiftRepository.getActiveShift().getOrNull()

            if (activeShift != null) {
                val shiftId = activeShift.id

                // Умная проверка: есть ли фото за текущий час?
                val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val currentHourLabel = String.format("%02d:00", currentHour)
                val photos = photoDao.getPhotosByShiftId(shiftId)
                val hasPhotoForCurrentHour = photos.any { photo ->
                    photo.hourLabel?.contains(currentHourLabel) == true
                }

                if (hasPhotoForCurrentHour) {
                    Log.d(TAG, "Photo already exists for hour $currentHourLabel, skipping reminder")
                } else {
                    Log.d(TAG, "No photo for hour $currentHourLabel, showing reminder")
                    showPhotoReminderNotification()
                }
            } else {
                Log.d(TAG, "No active shift, skipping reminder")
                // Если нет активной смены - отменяем напоминания
                cancel(applicationContext)
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking shift", e)
            return Result.retry()
        }
    }

    private fun showPhotoReminderNotification() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Создаём канал для Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Напоминания о фото",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Ежечасные напоминания о фотоотчёте"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Intent для открытия приложения
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Время для фотоотчёта!")
            .setContentText("Не забудьте сделать фото для отчёта о работе")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Прошёл час с последнего фото. Пожалуйста, сделайте фотоотчёт о текущей работе."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "Photo reminder notification shown")
    }
}
