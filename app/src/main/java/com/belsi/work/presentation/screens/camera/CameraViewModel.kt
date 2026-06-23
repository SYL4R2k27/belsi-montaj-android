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
import com.belsi.work.data.repositories.CabinetNode
import com.belsi.work.data.repositories.ObjectTree
import com.belsi.work.data.repositories.ShiftRepository
import com.belsi.work.data.workers.PhotoUploadWorker
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
    private val prefs: com.belsi.work.data.local.PrefsManager,
    private val objectV3Repository: com.belsi.work.data.repositories.ObjectV3Repository,
    // FIX(2026-05-11) BELSI 2.0.0 build10: NetworkMonitor убран — он инжектился
    // но никогда не читался (mob аудит). Реальная network-логика идёт через
    // PhotoUploadWorker.enqueueUpload() который сам ждёт сеть (Constraints).
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

    // ── Bottom-sheet «Куда отнести фото?» (каскад Этаж/Зона/Кабинет/Окно/Этап) ──
    private val _pendingPhotoUri = MutableStateFlow<Uri?>(null)
    val pendingPhotoUri: StateFlow<Uri?> = _pendingPhotoUri.asStateFlow()

    private val _objectTree = MutableStateFlow<ObjectTree?>(null)
    val objectTree: StateFlow<ObjectTree?> = _objectTree.asStateFlow()

    private val _selectedFloorId = MutableStateFlow<String?>(null)
    val selectedFloorId: StateFlow<String?> = _selectedFloorId.asStateFlow()
    private val _selectedZoneId = MutableStateFlow<String?>(null)
    val selectedZoneId: StateFlow<String?> = _selectedZoneId.asStateFlow()
    private val _selectedCabinetId = MutableStateFlow<String?>(null)
    val selectedCabinetId: StateFlow<String?> = _selectedCabinetId.asStateFlow()
    private val _selectedWindowId = MutableStateFlow<String?>(null)
    val selectedWindowId: StateFlow<String?> = _selectedWindowId.asStateFlow()
    private val _selectedStage = MutableStateFlow("karkas")
    val selectedStage: StateFlow<String> = _selectedStage.asStateFlow()
    private val _isFinal = MutableStateFlow(false)
    val isFinal: StateFlow<Boolean> = _isFinal.asStateFlow()

    // «+Новый кабинет/окно» на лету (2.1.0 fast-follow). Guard от двойного тапа.
    private val _isCreatingNode = MutableStateFlow(false)
    val isCreatingNode: StateFlow<Boolean> = _isCreatingNode.asStateFlow()

    fun updateComment(text: String) {
        _comment.value = text
    }

    fun updateCategory(category: String) {
        _selectedCategory.value = category
    }

    /** Текущий кабинет («Сейчас работаю») — к нему авто-привязывается фото. */
    val currentCabinetLabel: String? get() = prefs.getCurrentCabinetLabel()

    /**
     * Загрузка дерева объекта + преселект кабинета/этапа.
     * Вызывается при ОТКРЫТИИ экрана камеры — чтобы привязку можно было подготовить ДО съёмки.
     */
    fun loadDestinationTree() {
        _selectedStage.value = prefs.getLastPhotoStage() ?: "karkas"
        if (_objectTree.value == null) loadObjectTree()
        else preselectFromCurrentCabinet()
    }

    /** Фото снято — кладём в очередь на отправку (привязка уже подготовлена в листе). */
    fun onPhotoCaptured(uri: Uri) {
        _pendingPhotoUri.value = uri
    }

    fun dismissDestinationSheet() {
        _pendingPhotoUri.value = null
    }

    /** Готова ли привязка к отправке: комментарий обязателен; окно — если дерево загружено. */
    fun isReadyToSend(): Boolean =
        _comment.value.isNotBlank() &&
            (_objectTree.value?.floors?.isNotEmpty() != true || !_selectedWindowId.value.isNullOrBlank())

    private fun loadObjectTree() {
        val objectId = prefs.getCurrentObjectId()
        viewModelScope.launch {
            val resolvedId = objectId ?: runCatching {
                shiftRepository.getActiveShift().getOrNull()?.siteObjectId
            }.getOrNull()
            if (resolvedId.isNullOrBlank()) return@launch
            objectV3Repository.getObjectTree(resolvedId).onSuccess { tree ->
                _objectTree.value = tree
                preselectFromCurrentCabinet()
            }
        }
    }

    /** Преселект каскада по «Сейчас работаю»: кабинет → его этаж/зона + первое окно. */
    private fun preselectFromCurrentCabinet() {
        val tree = _objectTree.value ?: return
        val curCabId = prefs.getCurrentCabinetId()
        for (floor in tree.floors) {
            for (zone in floor.zones) {
                for (cab in zone.cabinets) {
                    if (curCabId != null && cab.cabinet.id == curCabId) {
                        _selectedFloorId.value = floor.floor.id
                        _selectedZoneId.value = zone.zone.id
                        _selectedCabinetId.value = cab.cabinet.id
                        _selectedWindowId.value = cab.windows.firstOrNull()?.id
                        return
                    }
                }
            }
        }
        // Если текущего кабинета нет в дереве — выбираем первый доступный путь.
        val f = tree.floors.firstOrNull() ?: return
        _selectedFloorId.value = f.floor.id
        val z = f.zones.firstOrNull() ?: return
        _selectedZoneId.value = z.zone.id
        val c = z.cabinets.firstOrNull() ?: return
        _selectedCabinetId.value = c.cabinet.id
        _selectedWindowId.value = c.windows.firstOrNull()?.id
    }

    fun selectFloor(floorId: String) {
        if (_selectedFloorId.value == floorId) return
        _selectedFloorId.value = floorId
        _selectedZoneId.value = null
        _selectedCabinetId.value = null
        _selectedWindowId.value = null
    }

    fun selectZone(zoneId: String) {
        if (_selectedZoneId.value == zoneId) return
        _selectedZoneId.value = zoneId
        _selectedCabinetId.value = null
        _selectedWindowId.value = null
    }

    fun selectCabinet(cabinetId: String) {
        if (_selectedCabinetId.value == cabinetId) return
        _selectedCabinetId.value = cabinetId
        // авто-выбор первого окна кабинета
        val win = _objectTree.value?.floors
            ?.flatMap { it.zones }?.flatMap { it.cabinets }
            ?.firstOrNull { it.cabinet.id == cabinetId }
            ?.windows?.firstOrNull()
        _selectedWindowId.value = win?.id
    }

    fun selectWindow(windowId: String) { _selectedWindowId.value = windowId }
    fun selectStage(stage: String) { _selectedStage.value = stage }
    fun setFinal(value: Boolean) { _isFinal.value = value }

    /** Можно ли заводить кабинеты/окна (дерево реальное, не демо-фикстура). */
    fun canCreateNodes(): Boolean = _objectTree.value?.isDemo == false

    /** Подсказка следующего номера окна для выбранного кабинета. */
    fun suggestNextWindowNumber(): Int {
        val cabId = _selectedCabinetId.value ?: return 1
        val cab = _objectTree.value?.floors
            ?.flatMap { it.zones }?.flatMap { it.cabinets }
            ?.firstOrNull { it.cabinet.id == cabId }
        return (cab?.windows?.maxOfOrNull { it.windowNumber } ?: 0) + 1
    }

    /**
     * Создать новый кабинет в выбранной зоне и сразу выбрать его (2.1.0 fast-follow).
     * Оптимистичная вставка в дерево — без рефетча (сохраняет выбор этажа/зоны).
     */
    fun addCabinet(number: String) {
        val zoneId = _selectedZoneId.value
        val tree = _objectTree.value
        val trimmed = number.trim()
        if (zoneId.isNullOrBlank() || tree == null) { _errorMessage.value = "Сначала выберите зону"; return }
        if (trimmed.isBlank()) { _errorMessage.value = "Введите номер кабинета"; return }
        if (tree.isDemo) { _errorMessage.value = "Демо-режим: создание недоступно"; return }
        if (_isCreatingNode.value) return
        viewModelScope.launch {
            _isCreatingNode.value = true
            objectV3Repository.createCabinet(zoneId, trimmed)
                .onSuccess { cab ->
                    _objectTree.value = tree.copy(floors = tree.floors.map { f ->
                        f.copy(zones = f.zones.map { z ->
                            if (z.zone.id == zoneId) z.copy(cabinets = z.cabinets + CabinetNode(cab, emptyList()))
                            else z
                        })
                    })
                    _selectedCabinetId.value = cab.id
                    _selectedWindowId.value = null
                }
                .onFailure { _errorMessage.value = "Не удалось создать кабинет: ${it.message ?: "ошибка"}" }
            _isCreatingNode.value = false
        }
    }

    /**
     * Создать новое окно в выбранном кабинете и сразу выбрать его (2.1.0 fast-follow).
     */
    fun addWindow(number: Int) {
        val cabinetId = _selectedCabinetId.value
        val tree = _objectTree.value
        if (cabinetId.isNullOrBlank() || tree == null) { _errorMessage.value = "Сначала выберите кабинет"; return }
        if (number <= 0) { _errorMessage.value = "Введите номер окна"; return }
        if (tree.isDemo) { _errorMessage.value = "Демо-режим: создание недоступно"; return }
        if (_isCreatingNode.value) return
        viewModelScope.launch {
            _isCreatingNode.value = true
            objectV3Repository.createWindow(cabinetId, number)
                .onSuccess { win ->
                    _objectTree.value = tree.copy(floors = tree.floors.map { f ->
                        f.copy(zones = f.zones.map { z ->
                            z.copy(cabinets = z.cabinets.map { c ->
                                if (c.cabinet.id == cabinetId) c.copy(windows = c.windows + win) else c
                            })
                        })
                    })
                    _selectedWindowId.value = win.id
                }
                .onFailure { _errorMessage.value = "Не удалось создать окно: ${it.message ?: "ошибка"}" }
            _isCreatingNode.value = false
        }
    }

    /**
     * Офлайн-first сохранение фото:
     * 1. Сжимаем и сохраняем в filesDir/photos/ (не cacheDir!)
     * 2. Создаём PhotoEntity в Room (status=LOCAL)
     * 3. Если онлайн — загружаем сразу через worker
     * 4. Если офлайн — фото ждёт в очереди, worker подхватит при появлении сети
     */
    /**
     * Подтверждение из bottom-sheet «Куда отнести фото?»:
     * комментарий обязателен; окно обязательно если дерево объекта загружено.
     */
    fun confirmAndSave(context: Context, shiftId: String?, slotIndex: Int?) {
        val uri = _pendingPhotoUri.value ?: return
        if (_comment.value.isBlank()) {
            _errorMessage.value = "Комментарий обязателен"
            return
        }
        val treeHasData = _objectTree.value?.floors?.isNotEmpty() == true
        if (treeHasData && _selectedWindowId.value.isNullOrBlank()) {
            _errorMessage.value = "Выберите окно"
            return
        }
        prefs.setLastPhotoStage(_selectedStage.value)
        savePhoto(
            uri = uri,
            context = context,
            shiftId = shiftId,
            slotIndex = slotIndex,
            windowId = _selectedWindowId.value,
            stage = if (_selectedWindowId.value != null) _selectedStage.value else null,
            isFinal = _isFinal.value,
            cabinetId = _selectedCabinetId.value,
        )
    }

    fun savePhoto(
        uri: Uri,
        context: Context,
        shiftId: String?,
        slotIndex: Int?,
        windowId: String? = null,
        stage: String? = null,
        isFinal: Boolean = false,
        cabinetId: String? = null,
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                if (shiftId == null || slotIndex == null) {
                    _errorMessage.value = "Ошибка: отсутствуют параметры смены"
                    _isLoading.value = false
                    return@launch
                }

                // FIX(2026-05-11) BELSI 2.0.0 build9: разрешаем фото на local-* смене.
                // Раньше блокировали — это нарушало обещание offline-first: юзер в туннеле
                // не мог снять часовое фото вообще пока смена не синканётся.
                //
                // Теперь:
                //   1) PhotoEntity сохраняется с shiftId="local-<ts>" (status=LOCAL)
                //   2) SyncWorker.syncOfflineShiftStart при появлении сети синканёт смену
                //      и обновит photoDao.updateShiftId(localId, serverId)
                //   3) PhotoUploadWorker увидит обновлённый shiftId и загрузит фото
                //
                // Так получается полный offline-first сценарий: смена создана локально,
                // фото снято локально, всё доедет когда появится сеть.
                if (shiftId.startsWith("local-")) {
                    Log.i(TAG, "Photo on local shift $shiftId — будет загружено после синка смены")
                    // НЕ блокируем — продолжаем сохранение в Room. Worker подхватит.
                }

                // FIX(2026-05-01): убрана обязательная привязка к объекту.
                // Если shiftId == "pending" — авто-создаём смену БЕЗ site_object_id
                // (объект можно привязать позже из дашборда куратора).
                // Раньше тут падал NACK «выберите объект» и пользователь застревал.
                val actualShiftId: String
                if (shiftId == "pending") {
                    Log.i(TAG, "Pending shift — auto-starting without site_object_id")
                    val res = shiftRepository.startShift(siteObjectId = null)
                    val newShift = res.getOrNull()
                    if (newShift == null) {
                        _errorMessage.value = "Не удалось начать смену: ${res.exceptionOrNull()?.message ?: "сеть"}"
                        _isLoading.value = false
                        return@launch
                    }
                    actualShiftId = newShift.id
                    Log.i(TAG, "Auto-started shift $actualShiftId (no object)")
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
                // Авто-привязка к текущему кабинету: если коммент пуст — подставляем «Сейчас работаю».
                val curCabinet = prefs.getCurrentCabinetLabel()
                val photoComment = _comment.value.takeIf { it.isNotBlank() }
                    ?: curCabinet
                val photoCategory = _selectedCategory.value

                val photoEntity = PhotoEntity(
                    id = photoId,
                    shiftId = actualShiftId,
                    hourLabel = hourLabel,
                    localPath = photoFile.absolutePath,
                    status = "LOCAL",
                    slotIndex = slotIndex,
                    comment = photoComment,
                    category = photoCategory,
                    windowId = windowId,
                    stage = stage,
                    isFinal = isFinal,
                )
                photoDao.insertPhoto(photoEntity)
                Log.d(TAG, "Photo saved locally: $photoId (shift: $actualShiftId, window=$windowId, stage=$stage)")

                // Триггерим фоновую загрузку
                PhotoUploadWorker.enqueueUpload(context)

                // По факту фото помечаем выбранный (или текущий) кабинет «начат» (/v3).
                (cabinetId ?: prefs.getCurrentCabinetId())?.let { cabId ->
                    runCatching { objectV3Repository.setCabinetStatus(cabId, "in_progress") }
                }

                // Сбрасываем состояние и закрываем sheet
                _comment.value = ""
                _selectedCategory.value = "hourly"
                _pendingPhotoUri.value = null
                _isFinal.value = false

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
