package com.belsi.work.presentation.screens.tools

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.belsi.work.data.remote.api.ToolsApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject

/**
 * FIX(2026-05-14) BELSI 2.0.1: shared photo capture + upload компонент
 * для всех return-экранов (request / pickup / deliver / accept).
 *
 * Flow:
 *  1. Tap по placeholder-карточке → системная камера через TakePicture intent
 *  2. После съёмки — Uri фото в локальном кэше
 *  3. Авто-upload через POST /tools/photos → возвращает photoUrl
 *  4. Родительский экран использует photoUrl при submit
 *
 * Использование:
 *   val capture = rememberReturnPhotoCapture()
 *   ReturnPhotoCard(
 *     capture = capture,
 *     title = "Фото при заборе",
 *     description = "Сфотографируй инструмент в руках на объекте",
 *   )
 *   // ... позже:
 *   onSubmit { viewModel.returnPickup(transferId, photoUrl = capture.photoUrl!!, ...) }
 */

data class ReturnPhotoCaptureState(
    val photoUri: Uri? = null,
    val photoUrl: String? = null,
    val isUploading: Boolean = false,
    val error: String? = null,
) {
    val isReady: Boolean get() = photoUrl != null
}

@HiltViewModel
class ReturnPhotoCaptureViewModel @Inject constructor(
    private val toolsApi: ToolsApi,
) : ViewModel() {

    private val _state = MutableStateFlow(ReturnPhotoCaptureState())
    val state: StateFlow<ReturnPhotoCaptureState> = _state.asStateFlow()

    fun onPhotoTaken(uri: Uri, file: File) {
        _state.value = _state.value.copy(
            photoUri = uri, isUploading = true, error = null, photoUrl = null,
        )
        viewModelScope.launch {
            try {
                val part = MultipartBody.Part.createFormData(
                    name = "photo",
                    filename = file.name,
                    body = file.asRequestBody("image/jpeg".toMediaTypeOrNull()),
                )
                val resp = toolsApi.uploadToolPhoto(part)
                if (resp.isSuccessful && resp.body() != null) {
                    _state.value = _state.value.copy(
                        photoUrl = resp.body()!!.photoUrl,
                        isUploading = false,
                    )
                } else {
                    _state.value = _state.value.copy(
                        isUploading = false,
                        error = "Загрузка не удалась (HTTP ${resp.code()})",
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isUploading = false,
                    error = "Ошибка загрузки: ${e.message}",
                )
            }
        }
    }

    fun reset() {
        _state.value = ReturnPhotoCaptureState()
    }
}

class ReturnPhotoCaptureController(
    private val viewModel: ReturnPhotoCaptureViewModel,
    val context: Context,
) {
    val state: StateFlow<ReturnPhotoCaptureState> = viewModel.state
    fun onPhotoTaken(uri: Uri, file: File) = viewModel.onPhotoTaken(uri, file)
    fun reset() = viewModel.reset()
}

@Composable
fun rememberReturnPhotoCapture(): ReturnPhotoCaptureController {
    val context = LocalContext.current
    val vm: ReturnPhotoCaptureViewModel = hiltViewModel()
    return remember(vm) { ReturnPhotoCaptureController(vm, context) }
}

/**
 * Карточка с фото для return-flow.
 * - Пустая: «Сделать фото» CTA → запускает камеру.
 * - С фото: превью + статус (uploading / uploaded / error) + «переснять».
 */
@Composable
fun ReturnPhotoCard(
    capture: ReturnPhotoCaptureController,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val state by capture.state.collectAsState()
    val context = capture.context

    var photoFile by remember { mutableStateOf<File?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoFile != null) {
            capture.onPhotoTaken(Uri.fromFile(photoFile!!), photoFile!!)
        }
    }

    val launchCamera: () -> Unit = {
        val file = File(context.cacheDir, "tool_return_${System.currentTimeMillis()}.jpg")
        photoFile = file
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        cameraLauncher.launch(uri)
    }

    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        onClick = launchCamera,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                when {
                    state.isUploading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("загрузка…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    state.isReady -> {
                        AssistChip(
                            onClick = {},
                            label = { Text("✓ готово") },
                            enabled = false,
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                        )
                    }
                    state.error != null -> {
                        AssistChip(
                            onClick = launchCamera,
                            label = { Text("ошибка, повторить") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                        )
                    }
                }
            }

            if (state.photoUri != null) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(8.dp)),
                ) {
                    AsyncImage(
                        model = state.photoUri,
                        contentDescription = "Фото",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    // overlay «переснять» в углу
                    TextButton(
                        onClick = launchCamera,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        ),
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("переснять", style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "→ Нажмите чтобы сделать фото",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (state.error != null && state.photoUri == null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    state.error!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
