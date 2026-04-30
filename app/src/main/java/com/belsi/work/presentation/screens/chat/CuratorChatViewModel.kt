package com.belsi.work.presentation.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.models.ChatMessageDTO
import com.belsi.work.data.models.ChatSummary
import com.belsi.work.data.repositories.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CuratorChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CuratorChatUiState())
    val uiState: StateFlow<CuratorChatUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    init {
        loadInbox()
        startPolling()
    }

    /**
     * Автообновление каждые 5 секунд для списка чатов
     */
    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(5000) // 5 seconds
                loadInbox(isRefresh = true)
            }
        }
    }

    /**
     * Загрузить список чатов через API inbox
     * Это дает доступ к номерам телефонов пользователей
     */
    private fun loadInbox(isRefresh: Boolean = false) {
        if (!isRefresh) {
            _uiState.value = _uiState.value.copy(isLoading = true)
        }

        viewModelScope.launch {
            chatRepository.getInbox(limit = 50, offset = 0)
                .onSuccess { tickets ->
                    val chatSummaries = tickets.map { ticket ->
                        ChatSummary(
                            ticketId = ticket.ticketId,
                            lastMessage = ticket.lastMessageText ?: "",
                            lastMessageDate = parseLastMessageDate(ticket.lastMessageAt),
                            unreadCount = ticket.unreadCount,
                            messageCount = 0, // Не возвращается в inbox
                            userName = null, // Используем телефон для идентификации
                            userPhone = ticket.userPhone
                        )
                    }
                    _uiState.value = _uiState.value.copy(
                        chatSummaries = chatSummaries,
                        isLoading = false,
                        errorMessage = null
                    )
                }
                .onFailure { e ->
                    android.util.Log.e("CuratorChatVM", "Failed to load inbox", e)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Ошибка загрузки чатов"
                    )
                }
        }
    }

    /**
     * Парсинг даты последнего сообщения из строки
     */
    private fun parseLastMessageDate(dateStr: String?): java.time.OffsetDateTime? {
        if (dateStr.isNullOrBlank()) return null
        return try {
            ChatMessageDTO.parseISO8601WithFallback(dateStr)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Очистить ошибку
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Ручное обновление
     */
    fun refresh() {
        loadInbox()
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}

data class CuratorChatUiState(
    val chatSummaries: List<ChatSummary> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
