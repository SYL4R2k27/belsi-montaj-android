package com.belsi.work.presentation.screens.wallet

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.belsi.work.data.repositories.DocumentDownload
import java.io.File

/**
 * Утилиты файлов кошелька Ф2.5:
 *  - копирование выбранного URI (ActivityResultContracts.GetContent) в cache-файл для multipart;
 *  - сохранение скачанного Акта в cache + шеринг через FileProvider.
 */
object WalletFileUtils {

    /** Скопировать содержимое URI в cache-файл. Возвращает File или null. */
    fun copyUriToCache(context: Context, uri: Uri): File? {
        return try {
            val name = queryDisplayName(context, uri)
            val ext = name?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() }
                ?: extFromMime(context.contentResolver.getType(uri))
            val file = File(context.cacheDir, "npd_cert_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            file.takeIf { it.length() > 0 }
        } catch (_: Exception) {
            null
        }
    }

    /** Сохранить скачанный документ Акта в cache и открыть системный «Поделиться/Открыть». */
    fun shareDocument(context: Context, doc: DocumentDownload) {
        try {
            val safeName = doc.fileName.replace(Regex("""[\\/:*?"<>|]"""), "_")
            val file = File(context.cacheDir, safeName)
            file.outputStream().use { it.write(doc.bytes) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = doc.mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, doc.fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Акт").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            // тихо — UI покажет ошибку отдельно при необходимости
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    } catch (_: Exception) { null }

    private fun extFromMime(mime: String?): String = when {
        mime == null -> "jpg"
        mime.contains("pdf") -> "pdf"
        mime.contains("png") -> "png"
        else -> "jpg"
    }
}
