package com.belsi.work.data.firebase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.belsi.work.MainActivity
import com.belsi.work.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Сервис push-уведомлений для BELSI.Work
 * Поддерживает:
 * - OTP коды для авторизации
 * - Напоминания о сменах и фотоотчётах
 * - Уведомления о новых задачах
 * - MessagingStyle для сообщений мессенджера (API 30+ conversation bubbles)
 * - Conversation shortcuts для ранжирования уведомлений
 * - Сообщения в чате поддержки
 * - Ответы в тикетах поддержки
 */
class BelsiFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "BelsiFirebase"
        private const val CHANNEL_ID_OTP = "otp_channel"
        private const val CHANNEL_ID_GENERAL = "general_channel"
        private const val CHANNEL_ID_TASKS = "tasks_channel"
        private const val CHANNEL_ID_CHAT = "chat_channel"
        private const val CHANNEL_ID_MESSENGER = "messenger_channel"
        private const val NOTIFICATION_ID_OTP = 1001
        private const val SHORTCUT_CATEGORY = "com.belsi.work.category.MESSENGER_CONVERSATION"

        /**
         * Получить сохранённый FCM токен
         */
        fun getSavedToken(context: Context): String? {
            val prefs = context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
            return prefs.getString("fcm_token", null)
        }

        /**
         * Сохранить FCM токен
         */
        fun saveToken(context: Context, token: String) {
            val prefs = context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("fcm_token", token).apply()
            Log.d(TAG, "FCM token saved")
        }

        /**
         * Получить последний OTP код (для автозаполнения)
         */
        fun getLastOtpCode(context: Context): String? {
            val prefs = context.getSharedPreferences("otp_prefs", Context.MODE_PRIVATE)
            val timestamp = prefs.getLong("otp_timestamp", 0)
            // OTP действителен 5 минут
            if (System.currentTimeMillis() - timestamp > 5 * 60 * 1000) {
                return null
            }
            return prefs.getString("last_otp", null)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        saveToken(applicationContext, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Message received from: ${remoteMessage.from}")

        // Обрабатываем data payload
        if (remoteMessage.data.isNotEmpty()) {
            handleMessage(remoteMessage.data)
        }

        // Обрабатываем notification payload (если приложение в foreground)
        remoteMessage.notification?.let { notification ->
            showNotification(
                title = notification.title ?: "Belsi.Монтаж",
                body = notification.body ?: "",
                channelId = CHANNEL_ID_GENERAL
            )
        }
    }

    /**
     * Обработка входящего сообщения
     */
    private fun handleMessage(data: Map<String, String>) {
        Log.d(TAG, "Message data: $data")

        when (data["type"]) {
            "otp" -> handleOtpMessage(data)
            "shift_reminder" -> handleShiftReminder(data)
            "photo_reminder" -> handlePhotoReminder(data)
            "task_assigned" -> handleTaskAssigned(data)
            "chat_message" -> handleChatMessage(data)
            "messenger_message" -> handleMessengerMessage(data)
            "support_reply" -> handleSupportReply(data)
            "problem_photo" -> handleProblemPhoto(data)
            else -> {
                data["title"]?.let { title ->
                    showNotification(
                        title = title,
                        body = data["body"] ?: "",
                        channelId = CHANNEL_ID_GENERAL
                    )
                }
            }
        }
    }

    /**
     * Обработка OTP сообщения
     */
    private fun handleOtpMessage(data: Map<String, String>) {
        val otpCode = data["code"] ?: return
        val phone = data["phone"] ?: ""

        Log.d(TAG, "Received OTP: $otpCode for phone: $phone")

        // Сохраняем OTP для автозаполнения
        saveOtpCode(otpCode)

        showNotification(
            title = "Код подтверждения",
            body = "Ваш код: $otpCode",
            channelId = CHANNEL_ID_OTP,
            notificationId = NOTIFICATION_ID_OTP
        )
    }

    /**
     * Напоминание о начале смены
     */
    private fun handleShiftReminder(data: Map<String, String>) {
        val message = data["message"] ?: "Не забудьте начать смену"
        showNotification(
            title = "Напоминание о смене",
            body = message,
            channelId = CHANNEL_ID_GENERAL
        )
    }

    /**
     * Напоминание о фотоотчёте
     */
    private fun handlePhotoReminder(data: Map<String, String>) {
        val message = data["message"] ?: "Пожалуйста, загрузите фотоотчёт"
        showNotification(
            title = "Фотоотчёт",
            body = message,
            channelId = CHANNEL_ID_GENERAL
        )
    }

    /**
     * Уведомление о новой задаче
     */
    private fun handleTaskAssigned(data: Map<String, String>) {
        val taskId = data["task_id"]
        val taskTitle = data["task_title"] ?: "Новая задача"
        val assignedBy = data["assigned_by"] ?: ""
        val priority = data["priority"] ?: "normal"
        val description = data["description"]

        val body = buildString {
            append(taskTitle)
            if (assignedBy.isNotEmpty()) {
                append("\nОт: $assignedBy")
            }
            if (!description.isNullOrEmpty()) {
                append("\n${description.take(100)}")
            }
        }

        showTaskNotification(
            title = if (priority == "high") "Срочная задача!" else "Новая задача",
            body = body,
            taskId = taskId
        )
    }

    /**
     * Уведомление о новом сообщении в мессенджере (MessagingStyle)
     * Поддерживает:
     * - API 30+ conversation bubbles
     * - Conversation shortcuts для ранжирования
     * - Группировка сообщений по thread_id
     */
    private fun handleMessengerMessage(data: Map<String, String>) {
        val threadId = data["thread_id"] ?: return
        val senderId = data["sender_id"] ?: return
        val senderName = data["sender_name"] ?: "Неизвестный"
        val message = data["message"] ?: ""
        val isGroup = data["is_group"] == "true"
        val threadName = data["thread_name"] ?: senderName
        val timestamp = data["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis()

        ensureMessengerChannel()

        // Создаём Person для отправителя
        val sender = Person.Builder()
            .setName(senderName)
            .setKey(senderId)
            .build()

        // Публикуем conversation shortcut (требуется для API 30+ MessagingStyle)
        publishConversationShortcut(threadId, threadName, isGroup)

        // Создаём MessagingStyle уведомление
        val style = NotificationCompat.MessagingStyle(
            Person.Builder().setName("Вы").build()
        ).also {
            if (isGroup) {
                it.conversationTitle = threadName
                it.isGroupConversation = true
            }
            it.addMessage(message, timestamp, sender)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_messenger", true)
            putExtra("thread_id", threadId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, threadId.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val shortcutId = "messenger_$threadId"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID_MESSENGER)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setShortcutId(shortcutId)
            .setLocusId(LocusIdCompat(shortcutId))

        // Bubbles API (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bubbleIntent = PendingIntent.getActivity(
                this,
                threadId.hashCode() + 1000,
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("open_messenger", true)
                    putExtra("thread_id", threadId)
                },
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val bubbleData = NotificationCompat.BubbleMetadata.Builder(
                bubbleIntent,
                IconCompat.createWithResource(this, R.drawable.ic_notification)
            )
                .setDesiredHeight(600)
                .setAutoExpandBubble(false)
                .setSuppressNotification(false)
                .build()

            builder.setBubbleMetadata(bubbleData)
        }

        val notification = builder.build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Используем threadId.hashCode() как notification ID для группировки по диалогу
        notificationManager.notify(threadId.hashCode(), notification)
        Log.d(TAG, "Messenger notification shown (MessagingStyle): thread=$threadId sender=$senderName")
    }

    /**
     * Публикует conversation shortcut для поддержки MessagingStyle на API 30+
     */
    private fun publishConversationShortcut(threadId: String, threadName: String, isGroup: Boolean) {
        val shortcutId = "messenger_$threadId"

        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("open_messenger", true)
            putExtra("thread_id", threadId)
        }

        val person = Person.Builder()
            .setName(threadName)
            .setKey(threadId)
            .build()

        val shortcut = ShortcutInfoCompat.Builder(this, shortcutId)
            .setShortLabel(threadName)
            .setLongLabel(if (isGroup) "Группа: $threadName" else threadName)
            .setIntent(intent)
            .setLongLived(true)
            .setLocusId(LocusIdCompat(shortcutId))
            .setPerson(person)
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_notification))
            .setCategories(setOf(SHORTCUT_CATEGORY))
            .build()

        ShortcutManagerCompat.pushDynamicShortcut(this, shortcut)
        Log.d(TAG, "Conversation shortcut published: $shortcutId")
    }

    /**
     * Создаёт канал для уведомлений мессенджера
     */
    private fun ensureMessengerChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.getNotificationChannel(CHANNEL_ID_MESSENGER) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID_MESSENGER,
                    "Мессенджер",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Сообщения в мессенджере"
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    /**
     * Уведомление о новом сообщении в чате поддержки (legacy BigTextStyle)
     */
    private fun handleChatMessage(data: Map<String, String>) {
        val ticketId = data["ticket_id"]
        val senderName = data["sender_name"] ?: "Новое сообщение"
        val senderPhone = data["sender_phone"] ?: ""
        val message = data["message"] ?: ""
        val isFromCurator = data["is_curator"] == "true"

        val title = if (isFromCurator) "Сообщение от куратора" else senderName
        val body = if (message.length > 100) "${message.take(100)}..." else message

        showChatNotification(
            title = title,
            body = body,
            ticketId = ticketId,
            senderPhone = senderPhone
        )
    }

    /**
     * Уведомление об ответе в тикете поддержки
     */
    private fun handleSupportReply(data: Map<String, String>) {
        val ticketId = data["ticket_id"]
        val ticketSubject = data["ticket_subject"] ?: "Поддержка"
        val message = data["message"] ?: "Новый ответ в тикете"

        showChatNotification(
            title = "Ответ: $ticketSubject",
            body = message,
            ticketId = ticketId,
            senderPhone = null
        )
    }

    /**
     * Показать уведомление о чате поддержки
     */
    private fun showChatNotification(
        title: String,
        body: String,
        ticketId: String?,
        senderPhone: String?
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_CHAT,
                "Сообщения",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о новых сообщениях"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            ticketId?.let { putExtra("ticket_id", it) }
            senderPhone?.let { putExtra("sender_phone", it) }
            putExtra("open_chat", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, ticketId?.hashCode() ?: 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_CHAT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(ticketId?.hashCode() ?: System.currentTimeMillis().toInt(), notification)
        Log.d(TAG, "Chat notification shown: $title")
    }

    /**
     * Показать уведомление о задаче
     */
    private fun showTaskNotification(
        title: String,
        body: String,
        taskId: String?
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_TASKS,
                "Задачи",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о новых задачах"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            taskId?.let { putExtra("task_id", it) }
            putExtra("open_tasks", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, taskId?.hashCode() ?: 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_TASKS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body.split("\n").firstOrNull() ?: body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        notificationManager.notify(taskId?.hashCode() ?: System.currentTimeMillis().toInt(), notification)
        Log.d(TAG, "Task notification shown: $title")
    }

    /**
     * Уведомление о проблемном фото (AI-анализ нашёл проблему)
     */
    private fun handleProblemPhoto(data: Map<String, String>) {
        val photoId = data["photo_id"]
        val aiComment = data["ai_comment"] ?: "Обнаружена проблема с качеством фото"
        val installerName = data["installer_name"] ?: "Монтажник"
        val siteName = data["site_name"] ?: ""

        val title = "⚠️ Проблемное фото"
        val body = buildString {
            append("$installerName: $aiComment")
            if (siteName.isNotEmpty()) append("\nОбъект: $siteName")
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "photo_quality_channel",
                "Качество фото",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о проблемных фото"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            photoId?.let { putExtra("photo_id", it) }
            putExtra("open_photos", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, photoId?.hashCode() ?: 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, "photo_quality_channel")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body.split("\n").firstOrNull() ?: body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .build()

        notificationManager.notify(photoId?.hashCode() ?: System.currentTimeMillis().toInt(), notification)
        Log.d(TAG, "Problem photo notification shown: $aiComment")
    }

    /**
     * Показать уведомление (generic)
     */
    private fun showNotification(
        title: String,
        body: String,
        channelId: String,
        notificationId: Int = System.currentTimeMillis().toInt()
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = when (channelId) {
                CHANNEL_ID_OTP -> NotificationChannel(
                    channelId,
                    "Коды подтверждения",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Уведомления с кодами OTP"
                }
                else -> NotificationChannel(
                    channelId,
                    "Общие уведомления",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Уведомления приложения BELSI.Work"
                }
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(
                if (channelId == CHANNEL_ID_OTP)
                    NotificationCompat.PRIORITY_HIGH
                else
                    NotificationCompat.PRIORITY_DEFAULT
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    /**
     * Сохранить OTP код для автозаполнения
     */
    private fun saveOtpCode(code: String) {
        val prefs = getSharedPreferences("otp_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("last_otp", code)
            .putLong("otp_timestamp", System.currentTimeMillis())
            .apply()
        Log.d(TAG, "OTP code saved for autofill")
    }
}
