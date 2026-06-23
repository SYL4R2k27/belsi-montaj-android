package com.belsi.work

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.belsi.work.data.local.database.dao.PhotoDao
import com.belsi.work.data.offline.OfflineQueueRepository
import com.belsi.work.data.workers.PhotoReminderWorker
import com.belsi.work.data.workers.PhotoUploadWorker
import com.belsi.work.data.workers.SyncWorker
import com.belsi.work.utils.NetworkEvent
import com.belsi.work.utils.NetworkMonitor
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class BelsiWorkApp : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var networkMonitor: NetworkMonitor
    @Inject lateinit var photoDao: PhotoDao
    @Inject lateinit var offlineQueue: OfflineQueueRepository
    @Inject lateinit var prefsManager: com.belsi.work.data.local.PrefsManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // Yandex MapKit: ключ задаём в Application ДО любого MapView. setApiKey дёшев
        // (только сохраняет строку, нативные либы НЕ грузит — это делает initialize()
        // лениво в экране карты водителя). Ключ — из BuildConfig (local.properties, gitignored).
        runCatching {
            if (BuildConfig.YANDEX_MAPKIT_KEY.isNotBlank()) {
                com.yandex.mapkit.MapKitFactory.setApiKey(BuildConfig.YANDEX_MAPKIT_KEY)
            }
        }

        // FIX(2026-05-22) perf: прогреваем EncryptedSharedPreferences (Keystore+AES) ВНЕ
        // main-треда, ДО того как MainActivity.onCreate их прочитает для startDestination.
        // Application.onCreate выполняется раньше Activity.onCreate → даём фору; `by lazy`
        // SYNCHRONIZED гарантирует отсутствие гонки. Снимает janky первый кадр.
        appScope.launch { runCatching { prefsManager.warmUp() } }

        // Сбросить застрявшие фото (retryCount >= 5) и немедленно загрузить
        appScope.launch {
            val reset = photoDao.resetFailedPhotos()
            if (reset > 0) {
                Log.d("BelsiWorkApp", "Reset $reset stuck photos, triggering upload")
                PhotoUploadWorker.enqueueUpload(this@BelsiWorkApp)
            }
        }

        // Запланировать периодические workers
        PhotoUploadWorker.schedulePeriodic(this)
        SyncWorker.schedulePeriodicSync(this)
        // FIX(2026-05-11) BELSI 2.0.0 build9: PhotoReminderWorker раньше определялся
        // но НИГДЕ не вызывался schedule() — напоминания о часовом фото не работали.
        // Теперь подключаем в onCreate (одно из 12 P0-fix-ов из аудита).
        PhotoReminderWorker.schedule(this)

        // FIX(2026-05-11) BELSI 2.0.0 build8: запустить PendingSyncWorker на старте app —
        // если pending_actions остались с прошлой сессии (kill / reboot), они начнут
        // обрабатываться как только появится сеть (constraint NetworkType.CONNECTED).
        // Без этого pending после рестарта замораживались до следующего enqueue.
        offlineQueue.scheduleWorker()
        Log.d("BelsiWorkApp", "PendingSyncWorker scheduled on app start")

        // Гигиена БД: чистим failed-actions старше 7 дней
        appScope.launch {
            try {
                offlineQueue.cleanupOldFailed()
            } catch (e: Exception) {
                Log.w("BelsiWorkApp", "cleanupOldFailed: ${e.message}")
            }
        }

        // При появлении сети — триггерить загрузку и синхронизацию
        appScope.launch {
            networkMonitor.networkEvents.collect { event ->
                when (event) {
                    is NetworkEvent.Connected -> {
                        Log.d("BelsiWorkApp", "Network connected, triggering sync")
                        PhotoUploadWorker.enqueueUpload(this@BelsiWorkApp)
                        SyncWorker.enqueueNow(this@BelsiWorkApp)
                        // FIX(2026-05-11) build8: pending pause/idle/break — тоже синкаем
                        offlineQueue.scheduleWorker()
                    }
                    is NetworkEvent.Disconnected -> {
                        Log.d("BelsiWorkApp", "Network disconnected")
                    }
                }
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)
            .build()

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "image_cache"))
                    .maxSizePercent(0.1)
                    .build()
            }
            .build()
    }
}
