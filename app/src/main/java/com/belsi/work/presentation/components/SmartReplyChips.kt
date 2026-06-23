package com.belsi.work.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.models.SmartReplyDto
import com.belsi.work.data.models.SmartReplyRequest
import com.belsi.work.data.repositories.AiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-10) BELSI 1.3.0: AI-подсказки коротких ответов в чате.
 *
 * 3 чипа с ответами на основе контекста чата (последнее сообщение + история).
 * Тап на чип → текст автоматически вставляется в input или сразу отправляется.
 *
 * Endpoint: POST /messenger/ai-suggest-replies
 *
 * Использование (в ConversationScreen):
 * ```
 * SmartReplyChips(
 *   threadId = thread.id,
 *   incomingText = lastMessage.text,
 *   senderName = lastMessage.senderName,
 *   senderRole = lastMessage.senderRole,
 *   onReplyTap = { text -> messageInput = text },  // или sendMessage(text)
 * )
 * ```
 */

@HiltViewModel
class SmartReplyViewModel @Inject constructor(
    private val aiRepository: AiRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SmartReplyState())
    val state: StateFlow<SmartReplyState> = _state.asStateFlow()

    fun load(
        threadId: String,
        incomingMessageId: String?,
        incomingText: String,
        senderName: String,
        senderRole: String,
    ) {
        // Не запрашиваем повторно для одного и того же сообщения
        if (_state.value.lastMessageId == incomingMessageId && _state.value.replies.isNotEmpty()) return

        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, lastMessageId = incomingMessageId)
            aiRepository.suggestReplies(
                SmartReplyRequest(
                    threadId = threadId,
                    incomingMessageId = incomingMessageId,
                    incomingText = incomingText,
                    senderName = senderName,
                    senderRole = senderRole,
                )
            ).onSuccess { response ->
                _state.value = SmartReplyState(
                    loading = false,
                    replies = response.replies,
                    lastMessageId = incomingMessageId,
                )
            }.onFailure {
                _state.value = SmartReplyState(loading = false, lastMessageId = incomingMessageId)
            }
        }
    }
}

data class SmartReplyState(
    val loading: Boolean = false,
    val replies: List<SmartReplyDto> = emptyList(),
    val lastMessageId: String? = null,
)

@Composable
fun SmartReplyChips(
    threadId: String,
    incomingMessageId: String?,
    incomingText: String?,
    senderName: String?,
    senderRole: String?,
    onReplyTap: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SmartReplyViewModel = hiltViewModel(),
) {
    if (incomingText.isNullOrBlank() || senderName.isNullOrBlank() || senderRole.isNullOrBlank()) return

    val state by viewModel.state.collectAsState()

    LaunchedEffect(incomingMessageId, incomingText) {
        viewModel.load(threadId, incomingMessageId, incomingText, senderName, senderRole)
    }

    if (state.loading || state.replies.isEmpty()) {
        // Не показываем skeleton — иначе плодим место. При появлении чипов
        // они сами появятся.
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🤖", fontSize = 12.sp)
        state.replies.forEach { reply ->
            val toneColor = when (reply.tone) {
                "agree" -> Color(0xFF10B981)
                "delay" -> Color(0xFFF59E0B)
                "reject" -> Color(0xFFEF4444)
                else -> MaterialTheme.colorScheme.primary  // info
            }
            SuggestionChip(
                onClick = { onReplyTap(reply.text) },
                label = {
                    Text(
                        reply.text,
                        fontSize = 12.sp,
                        color = toneColor,
                    )
                },
                border = SuggestionChipDefaults.suggestionChipBorder(
                    enabled = true,
                    borderColor = toneColor.copy(alpha = 0.5f),
                ),
            )
        }
    }
}
