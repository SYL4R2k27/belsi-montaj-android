package com.belsi.work.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.min

/**
 * Утилита для сжатия фотографий перед загрузкой на сервер
 *
 * Проблема: HTTP 413 (слишком большой файл)
 * Решение: сжимаем JPEG и уменьшаем разрешение до разумного размера
 */
object ImageCompressor {

    private const val MAX_WIDTH = 1920
    private const val MAX_HEIGHT = 1920
    private const val MAX_FILE_SIZE_MB = 5 // 5MB max
    private const val JPEG_QUALITY_HIGH = 85
    private const val JPEG_QUALITY_MEDIUM = 70
    private const val JPEG_QUALITY_LOW = 50

    /**
     * Сжать изображение до приемлемого размера
     *
     * @param context Application context
     * @param sourceFile Исходный файл изображения
     * @param outputFile Выходной файл (может быть тот же самый)
     * @return Result с путем к сжатому файлу или ошибкой
     */
    fun compressImage(
        context: Context,
        sourceFile: File,
        outputFile: File = sourceFile
    ): Result<File> {
        return try {
            // Проверяем, нужно ли сжатие
            val fileSizeMB = sourceFile.length() / (1024.0 * 1024.0)

            if (fileSizeMB <= MAX_FILE_SIZE_MB) {
                // Файл уже достаточно маленький, но проверим разрешение
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(sourceFile.absolutePath, options)

                if (options.outWidth <= MAX_WIDTH && options.outHeight <= MAX_HEIGHT) {
                    // Изображение подходит по размеру
                    return Result.success(sourceFile)
                }
            }

            // Читаем EXIF для корректной ориентации
            val exif = ExifInterface(sourceFile.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            // Декодируем с уменьшением
            val bitmap = decodeSampledBitmap(sourceFile, MAX_WIDTH, MAX_HEIGHT)
                ?: return Result.failure(IOException("Не удалось декодировать изображение"))

            // Поворачиваем если нужно
            val rotatedBitmap = rotateBitmap(bitmap, orientation)

            // Сохраняем с прогрессивным сжатием
            val result = saveCompressedBitmap(rotatedBitmap, outputFile)

            if (bitmap != rotatedBitmap) {
                bitmap.recycle()
            }
            rotatedBitmap.recycle()

            result
        } catch (e: Exception) {
            Result.failure(IOException("Ошибка сжатия изображения: ${e.message}", e))
        }
    }

    private fun decodeSampledBitmap(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            // Сначала декодируем с inJustDecodeBounds=true чтобы узнать размеры
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)

            // Вычисляем inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)

            // Декодируем с inSampleSize
            options.inJustDecodeBounds = false
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }

        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: OutOfMemoryError) {
            bitmap
        }
    }

    private fun saveCompressedBitmap(bitmap: Bitmap, outputFile: File): Result<File> {
        return try {
            // Пробуем сохранить с высоким качеством
            var quality = JPEG_QUALITY_HIGH
            var success = saveBitmapWithQuality(bitmap, outputFile, quality)

            // Если файл все еще большой, снижаем качество
            while (!success && quality >= JPEG_QUALITY_LOW) {
                quality -= 15
                success = saveBitmapWithQuality(bitmap, outputFile, quality)
            }

            if (success) {
                Result.success(outputFile)
            } else {
                Result.failure(IOException("Не удалось сжать файл до приемлемого размера"))
            }
        } catch (e: Exception) {
            Result.failure(IOException("Ошибка сохранения: ${e.message}", e))
        }
    }

    private fun saveBitmapWithQuality(bitmap: Bitmap, file: File, quality: Int): Boolean {
        return try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            // Проверяем размер
            val fileSizeMB = file.length() / (1024.0 * 1024.0)
            fileSizeMB <= MAX_FILE_SIZE_MB
        } catch (e: Exception) {
            false
        }
    }
}
