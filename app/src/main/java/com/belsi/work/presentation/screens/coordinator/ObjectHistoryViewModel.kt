package com.belsi.work.presentation.screens.coordinator

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.remote.dto.brand.TimelineEventDto
import com.belsi.work.data.repositories.BrandCoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-11) BELSI 2.0.0 build3: реальная история объекта через
 * `GET /objects/{id}/timeline`. Брендбук ecosystem 03.
 */
data class ObjectHistoryUiState(
    val objectId: String = "",
    val events: List<TimelineEventDto> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ObjectHistoryViewModel @Inject constructor(
    private val brandRepo: BrandCoreRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ObjectHistoryUiState())
    val state: StateFlow<ObjectHistoryUiState> = _state.asStateFlow()

    fun load(objectId: String, types: String? = null) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, objectId = objectId) }
            brandRepo.objectTimeline(objectId, types = types, limit = 200).fold(
                onSuccess = { list ->
                    _state.update { it.copy(loading = false, events = list) }
                },
                onFailure = { e ->
                    _state.update { it.copy(loading = false, error = e.message) }
                }
            )
        }
    }
}

/**
 * Конвертация DTO → TimelineEvent (внутренний тип ObjectHistoryScreen с компонентами UI).
 */
internal fun TimelineEventDto.toTimelineEvent(): TimelineEvent {
    val color = when (domain) {
        "installation" -> Color(0xFF4F46E5)   // indigo · монтаж
        "logistics"    -> Color(0xFFD97706)   // amber · логистика
        "production"   -> Color(0xFF10B981)   // emerald · производство
        "curator"      -> Color(0xFF8B5CF6)   // violet · куратор
        else           -> Color(0xFF64748B)   // slate · прочее
    }
    val ico: ImageVector = when (type) {
        "shift_start", "shift_end" -> Icons.Default.AccessTime
        "photo" -> Icons.Default.PhotoCamera
        "audit" -> Icons.Default.Edit
        "delivery", "delivery_complete" -> Icons.Default.LocalShipping
        "batch" -> Icons.Default.Inventory
        "task" -> Icons.Default.CheckCircle
        "ticket" -> Icons.Default.Support
        else -> Icons.Default.Circle
    }
    // ISO datetime → "HH:mm"
    val time = occurredAt.takeIf { it.length >= 16 }?.substring(11, 16) ?: "—"

    return TimelineEvent(
        time = time,
        author = actorName ?: "—",
        title = title,
        description = detail ?: "",
        icon = ico,
        domainColor = color,
        type = type,  // build15: type для navigation в clickable timeline
        targetId = targetId,  // build17: id целевого объекта для deep-link
    )
}

// TimelineEvent определён в ObjectHistoryScreen.kt (internal data class).
// VM не дублирует определение — использует тот же тип.
