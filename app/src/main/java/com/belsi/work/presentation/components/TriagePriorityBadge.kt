package com.belsi.work.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.repositories.AiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-10) BELSI 1.3.0: Бейдж AI-приоритета для support-тикета.
 *
 * Использование:
 * ```
 * TriagePriorityBadge(priority = "urgent")  // 🔴 СРОЧНО
 * TriagePriorityBadge(priority = "high")    // 🟠 Высокий
 * TriagePriorityBadge(priority = "normal")  // 🔵 Обычный
 * TriagePriorityBadge(priority = "low")     // ⚪ Низкий
 * ```
 *
 * Куратор быстро ориентируется в inbox. AI вычисляет приоритет автоматически
 * через POST /support/ai-triage/{ticket_id}, результат сохраняется в БД.
 */
@Composable
fun TriagePriorityBadge(
    priority: String,
    modifier: Modifier = Modifier,
    showAiPrefix: Boolean = true,
) {
    val (label, color, emoji) = when (priority.lowercase()) {
        "urgent" -> Triple("СРОЧНО", Color(0xFFEF4444), "🔴")
        "high" -> Triple("Высокий", Color(0xFFF59E0B), "🟠")
        "normal" -> Triple("Обычный", Color(0xFF6366F1), "🔵")
        "low" -> Triple("Низкий", Color(0xFF9CA3AF), "⚪")
        else -> Triple(priority, Color(0xFF9CA3AF), "•")
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        if (showAiPrefix) {
            Text("🤖 ", fontSize = 10.sp)
        }
        Text(emoji, fontSize = 10.sp)
        Spacer(Modifier.width(2.dp))
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

// =============================================================================
// FIX(2026-05-11) BELSI 2.0.0: AI-триаж тикетов поддержки.
//
// LazyAiTriageBadge — wrapper, который сам дёргает AI один раз для конкретного
// тикета (idempotent через request_id) и показывает бейдж когда AI ответил.
// Если AI fail — бейдж не показывается, остальная карточка тикета не ломается.
//
// На уровне экрана живёт TriageBadgesViewModel — он держит кэш per-ticket
// результатов в Map, чтобы один и тот же тикет не дёргался повторно при
// скролле LazyColumn.
//
// Использование (в любом TicketCard):
// ```
// LazyAiTriageBadge(ticketId = ticket.id)
// ```
// =============================================================================

@HiltViewModel
class TriageBadgesViewModel @Inject constructor(
    private val aiRepository: AiRepository,
) : ViewModel() {

    private val _priorities = MutableStateFlow<Map<String, String>>(emptyMap())
    val priorities: StateFlow<Map<String, String>> = _priorities.asStateFlow()

    private val _categories = MutableStateFlow<Map<String, String>>(emptyMap())
    val categories: StateFlow<Map<String, String>> = _categories.asStateFlow()

    private val inflight = mutableSetOf<String>()

    fun ensureLoaded(ticketId: String) {
        if (_priorities.value.containsKey(ticketId)) return
        if (ticketId in inflight) return
        inflight += ticketId
        viewModelScope.launch {
            aiRepository.triageTicket(ticketId)
                .onSuccess { res ->
                    _priorities.update { it + (ticketId to res.priority) }
                    _categories.update { it + (ticketId to res.category) }
                }
                .onFailure {
                    // молча — карточка живёт без AI-бейджа
                }
            inflight -= ticketId
        }
    }
}

@Composable
fun LazyAiTriageBadge(
    ticketId: String,
    modifier: Modifier = Modifier,
    viewModel: TriageBadgesViewModel = hiltViewModel(),
) {
    LaunchedEffect(ticketId) { viewModel.ensureLoaded(ticketId) }
    val priorities by viewModel.priorities.collectAsState()
    val priority = priorities[ticketId] ?: return  // ещё не загрузилось — ничего не рисуем
    TriagePriorityBadge(priority = priority, modifier = modifier)
}

/**
 * Badge для категории тикета.
 */
@Composable
fun TriageCategoryBadge(
    category: String,
    modifier: Modifier = Modifier,
) {
    val (label, color) = when (category.lowercase()) {
        "bug" -> "Bug" to Color(0xFFEF4444)
        "feature" -> "Feature" to Color(0xFF8B5CF6)
        "question" -> "Вопрос" to Color(0xFF3B82F6)
        "spam" -> "Спам" to Color(0xFF9CA3AF)
        "urgent" -> "Срочное" to Color(0xFFEF4444)
        else -> category to Color(0xFF9CA3AF)
    }
    Text(
        label,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
