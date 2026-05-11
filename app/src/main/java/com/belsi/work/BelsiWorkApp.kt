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

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

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

        // При появлении сети — триггерить загрузку и синхронизацию
        appScope.launch {
            networkMonitor.networkEvents.collect { event ->
                when (event) {
                    is NetworkEvent.Connected -> {
                        Log.d("BelsiWorkApp", "Network connected, triggering sync")
                        PhotoUploadWorker.enqueueUpload(this@BelsiWorkApp)
                        SyncWorker.enqueueNow(this@BelsiWorkApp)
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
