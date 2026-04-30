package com.belsi.work.presentation.screens.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.local.database.dao.PhotoDao
import com.belsi.work.data.local.database.entities.PhotoEntity
import com.belsi.work.data.repositories.ShiftRepository
import com.belsi.work.data.workers.PhotoUploadWorker
import com.belsi.work.utils.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import kotlin.math.min

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val shiftRepository: ShiftRepository,
    private val photoDao: PhotoDao,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _comment = MutableStateFlow("")
    val comment: StateFlow<String> = _comment.asStateFlow()

    private val _selectedCategory = MutableStateFlow("hourly")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    fun updateComment(text: String) {
        _comment.value = text
    }

    fun updateCategory(category: String) {
        _selectedCategory.value = category
    }

    /**
     * Офлайн-first сохранение фото:
     * 1. Сжимаем и сохраняем в filesDir/photos/ (не cacheDir!)
     * 2. Создаём PhotoEntity в Room (status=LOCAL)
     * 3. Если онлайн — загружаем сразу через worker
     * 4. Если офлайн — фото ждёт в очереди, worker подхватит при появлении сети
     */
    fun savePhoto(uri: Uri, context: Context, shiftId: String?, slotIndex: Int?) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                if (shiftId == null || slotIndex == null) {
                    _errorMessage.value = "Ошибка: отсутствуют параметры смены"
                    _isLoading.value = false
                    return@launch
                }

                // Проверка: если shiftId начинается с "local-", смена ещё не синхронизирована
                if (shiftId.startsWith("local-")) {
                    _errorMessage.value = "Смена ещё синхронизируется с сервером. Подождите несколько секунд"
                    _isLoading.value = false
                    return@launch
                }

                // FIX(2026-04-30): Раньше при shiftId == "pending" CameraViewModel
                // создавал смену БЕЗ site_object_id — отсюда 0 фото с объектом.
                // Теперь требуем, чтобы смена была открыта заранее в ShiftScreen
                // (там есть выбор объекта).
                val actualShiftId: String
                if (shiftId == "pending") {
                    Log.w(TAG, "Pending shift state — refusing to auto-create without object")
                    _errorMessage.value = "Сначала начните смену и выберите объект"
                    _isLoading.value = false
                    return@launch
                } else {
                    actualShiftId = shiftId
                }

                // Сохраняем фото в постоянную директорию (не cacheDir!)
                val photosDir = File(context.filesDir, "photos")
                if (!photosDir.exists()) photosDir.mkdirs()
                val photoFile = File(photosDir, "photo_${System.currentTimeMillis()}.jpg")
                compressImage(uri, photoFile, context)

                // Формируем hour_label
                val hourLabel = generateHourLabel(shiftId, slotIndex)

                // Создаём запись в Room (LOCAL → worker загрузит)
                val photoId = UUID.randomUUID().toString()
                val photoComment = _comment.value.takeIf { it.isNotBlank() }
                val photoCategory = _selectedCategory.value

                val photoEntity = PhotoEntity(
                    id = photoId,
                    shiftId = actualShiftId,
                    hourLabel = hourLabel,
                    localPath = photoFile.absolutePath,
                    status = "LOCAL",
                    slotIndex = slotIndex,
                    comment = photoComment,
                    category = photoCategory
                )
                photoDao.insertPhoto(photoEntity)
                Log.d(TAG, "Photo saved locally: $photoId (shift: $actualShiftId)")

                // Триггерим фоновую загрузку
                PhotoUploadWorker.enqueueUpload(context)

                // Сбрасываем комментарий и категорию
                _comment.value = ""
                _selectedCategory.value = "hourly"

                // Мгновенный успех — пользователь не ждёт загрузки
                val createdId = if (shiftId == "pending") actualShiftId else null
                _navigationEvent.emit(NavigationEvent.NavigateBackWithSuccess(photoId, slotIndex, createdId))

            } catch (e: Exception) {
                Log.e(TAG, "Error saving photo", e)
                _errorMessage.value = "Ошибка: ${e.message}"
                viewModelScope.launch {
                    kotlinx.coroutines.delay(5000)
                    if (_errorMessage.value == "Ошибка: ${e.message}") {
                        _errorMessage.value = null
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun generateHourLabel(shiftId: String?, slotIndex: Int?): String {
        val now = java.time.OffsetDateTime.now()
        val startHour = if (shiftId == "pending") {
            now.hour
        } else {
            val activeShift = try {
                shiftRepository.getActiveShift().getOrNull()
            } catch (_: Exception) { null }
            val shiftStartHour = activeShift?.startAt?.let { startAt ->
                try {
                    java.time.OffsetDateTime.parse(startAt).hour
                } catch (_: Exception) { null }
            }
            ((shiftStartHour ?: now.hour) + (slotIndex ?: 0)) % 24
        }
        return now.withHour(startHour).withMinute(0).withSecond(0).withNano(0)
            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }

    /**
     * Сжимает изображение до максимального размера 1920x1080 и качества 80%
     */
    private fun compressImage(uri: Uri, outputFile: File, context: Context) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) {
                throw Exception("Не удалось загрузить изображение")
            }

            val orientation = try {
                context.contentResolver.openInputStream(uri)?.use { exifStream ->
                    ExifInterface(exifStream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            val rotatedBitmap = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(originalBitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(originalBitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(originalBitmap, 270f)
                else -> originalBitmap
            }

            val maxWidth = 1920
            val maxHeight = 1080
            val scaledBitmap = scaleBitmap(rotatedBitmap, maxWidth, maxHeight)

            FileOutputStream(outputFile).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }

            if (rotatedBitmap != originalBitmap) {
                originalBitmap.recycle()
            }
            scaledBitmap.recycle()
        } catch (e: Exception) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxWidth && height <= maxHeight) {
            return bitmap
        }

        val scale = min(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    sealed class NavigationEvent {
        object NavigateBack : NavigationEvent()
        data class NavigateBackWithSuccess(
            val photoId: String,
            val slotIndex: Int,
            val createdShiftId: String? = null
        ) : NavigationEvent()
    }

    companion object {
        private const val TAG = "CameraViewModel"
    }
}
