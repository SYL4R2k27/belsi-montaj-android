package com.belsi.work.data.offline

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FIX(2026-05-05): Локальное хранилище для pending-фото.
 *
 * Фото попадают сюда когда сеть упала и юзер сделал снимок. Файл живёт на
 * Internal Storage устройства, переживает kill приложения и rebooting.
 * Когда сеть появилась — Worker читает файл и шлёт на /photos/upload.
 *
 * Лимит: 500 МБ (защита от забивки storage). При превышении — удаляются
 * старейшие файлы. Это hard-limit чтобы не сломать устройство юзера.
 *
 * Каталог: /data/data/com.belsi.work/files/pending_photos/{uuid}.jpg
 */
@Singleton
class PhotoFileStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val storageDir: File by lazy {
        File(context.filesDir, "pending_photos").apply { if (!exists()) mkdirs() }
    }

    companion object {
        const val MAX_TOTAL_SIZE_BYTES = 500L * 1024 * 1024  // 500 МБ
        private const val TAG = "PhotoFileStorage"
    }

    /**
     * Сохранить фото из stream в pending-каталог.
     * Возвращает абсолютный путь к файлу — кладётся в PendingAction.UploadPhoto.localFilePath.
     *
     * @throws IOException если не хватает места (>500 МБ pending) после попытки очистки
     */
    fun savePending(stream: InputStream): String {
        cleanupIfOverLimit()
        val file = File(storageDir, "${UUID.randomUUID()}.jpg")
        file.outputStream().use { out ->
            stream.copyTo(out)
        }
        android.util.Log.d(TAG, "Saved pending photo: ${file.name} (${file.length()} bytes)")
        return file.absolutePath
    }

    /** Сохранить фото из массива байт (когда уже есть в памяти). */
    fun savePending(bytes: ByteArray): String {
        cleanupIfOverLimit()
        val file = File(storageDir, "${UUID.randomUUID()}.jpg")
        file.writeBytes(bytes)
        android.util.Log.d(TAG, "Saved pending photo: ${file.name} (${bytes.size} bytes)")
        return file.absolutePath
    }

    /** Прочитать файл по абсолютному пути. Null если файла нет (юзер очистил storage). */
    fun read(absolutePath: String): ByteArray? {
        val f = File(absolutePath)
        return if (f.exists() && f.parentFile == storageDir) f.readBytes() else null
    }

    /** Удалить файл (после успешной отправки или при cancel). */
    fun delete(absolutePath: String): Boolean {
        val f = File(absolutePath)
        if (!f.exists() || f.parentFile != storageDir) return false
        return f.delete()
    }

    /** Сколько занимают все pending-фото на диске (для UI / диагностики). */
    fun totalSize(): Long = storageDir.listFiles()?.sumOf { it.length() } ?: 0L

    /** Сколько pending-файлов лежит на диске (может расходиться с pending_actions БД при сбоях). */
    fun count(): Int = storageDir.listFiles()?.size ?: 0

    /**
     * Если на диске больше MAX_TOTAL_SIZE_BYTES — удаляем старейшие файлы пока
     * не уложимся. Это защита от полного забивания пользовательского устройства.
     * Удалённые файлы потом не отправятся (записи в БД останутся как failed).
     */
    private fun cleanupIfOverLimit() {
        val files = storageDir.listFiles() ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_TOTAL_SIZE_BYTES) return

        val sortedByAge = files.sortedBy { it.lastModified() }
        var i = 0
        while (total > MAX_TOTAL_SIZE_BYTES && i < sortedByAge.size) {
            val f = sortedByAge[i]
            total -= f.length()
            f.delete()
            android.util.Log.w(TAG, "cleanup: deleted old pending photo ${f.name}")
            i++
        }
    }

    /** Проверить — был ли файл удалён cleanup'ом или юзером. Используется Worker'ом. */
    fun exists(absolutePath: String): Boolean {
        val f = File(absolutePath)
        return f.exists() && f.parentFile == storageDir
    }
}
