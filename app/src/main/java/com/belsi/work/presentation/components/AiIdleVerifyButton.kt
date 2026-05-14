package com.belsi.work.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.models.AiIdleVerifyResponse
import com.belsi.work.data.repositories.AiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-10) BELSI 1.3.0: AI-проверка причины простоя.
 *
 * Куратор/координатор/бригадир может проверить заявленную причину простоя
 * по последнему фото с объекта. AI отвечает: подтверждается ли причина,
 * suspicion_score (0=честный, 100=фейк), comment.
 *
 * Endpoint: POST /shift/ai-verify-idle/{pause_id}
 */

@HiltViewModel
class AiIdleVerifyViewModel @Inject constructor(
    private val aiRepository: AiRepository,
) : ViewModel() {

    private val _resultByPause = MutableStateFlow<Map<String, IdleVerifyState>>(emptyMap())
    val resultByPause: StateFlow<Map<String, IdleVerifyState>> = _resultByPause.asStateFlow()

    fun verify(pauseId: String) {
        // Если уже грузим этот pause — ничего не делаем
        if (_resultByPause.value[pauseId]?.loading == true) return

        viewModelScope.launch {
            _resultByPause.value = _resultByPause.value + (pauseId to IdleVerifyState(loading = true))
            aiRepository.verifyIdle(pauseId).onSuccess { result ->
                _resultByPause.value = _resultByPause.value + (pauseId to IdleVerifyState(
                    loading = false,
                    result = result,
                ))
            }.onFailure {
                _resultByPause.value = _resultByPause.value + (pauseId to IdleVerifyState(
                    loading = false,
                    error = it.message ?: "Ошибка",
                ))
            }
        }
    }
}

data class IdleVerifyState(
    val loading: Boolean = false,
    val result: AiIdleVerifyResponse? = null,
    val error: String? = null,
)

/**
 * Кнопка с inline-результатом для карточки простоя.
 *
 * UX:
 * 1. До нажатия: оранжевая кнопка «🤖 Проверить»
 * 2. Loading: spinner
 * 3. После: цветная карточка с результатом + suspicion_score (0=зелёный, 100=красный)
 */
@Composable
fun AiIdleVerifyButton(
    pauseId: String,
    pauseReason: String?,
    modifier: Modifier = Modifier,
    viewModel: AiIdleVerifyViewModel = hiltViewModel(),
) {
    val resultByPause by viewModel.resultByPause.collectAsState()
    val state = resultByPause[pauseId]

    Column(modifier = modifier.fillMaxWidth()) {
        when {
            state?.loading == true -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("AI проверяет…", fontSize = 13.sp)
                }
            }
            state?.result != null -> {
                IdleVerifyResultCard(state.result, pauseReason)
            }
            state?.error != null -> {
                Text(
                    "AI: ${state.error}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            else -> {
                OutlinedButton(
                    onClick = { viewModel.verify(pauseId) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFF59E0B),
                    ),
                ) {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("AI проверить причину", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun IdleVerifyResultCard(
    result: AiIdleVerifyResponse,
    pauseReason: String?,
) {
    val suspicion = result.suspicionScore
    val accentColor = when {
        suspicion < 30 -> Color(0xFF10B981)  // зелёный — честно
        suspicion < 70 -> Color(0xFFF59E0B)  // жёлтый — подозрительно
        else -> Color(0xFFEF4444)             // красный — вероятный фейк
    }
    val statusText = when {
        suspicion < 30 -> "✅ Подтверждено"
        suspicion < 70 -> "⚠ Подозрительно"
        else -> "🚨 Вероятный фейк"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accentColor.copy(alpha = 0.12f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🤖 ", fontSize = 14.sp)
            Text(
                statusText,
                fontWeight = FontWeight.SemiBold,
                color = accentColor,
                fontSize = 13.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "Suspicion ${suspicion}%",
                fontSize = 11.sp,
                color = accentColor,
                fontWeight = FontWeight.Medium,
            )
        }
        if (result.comment.isNotBlank()) {
            Text(result.comment, fontSize = 12.sp)
        }
        // FIX(2026-05-12) build19 hotfix: "XeroCode" убран из UI AI-блоков.
        Text(
            "AI-анализ",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}
