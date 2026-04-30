package com.belsi.work.presentation.screens.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.models.SupportTicket
import com.belsi.work.data.models.TicketMessage
import com.belsi.work.data.remote.api.SendMessageRequest
import com.belsi.work.data.remote.api.SupportApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TicketDetailViewModel @Inject constructor(
    private val supportApi: SupportApi
) : ViewModel() {

    private val _ticket = MutableStateFlow<SupportTicket?>(null)
    val ticket: StateFlow<SupportTicket?> = _ticket.asStateFlow()

    private val _messages = MutableStateFlow<List<TicketMessage>>(emptyList())
    val messages: StateFlow<List<TicketMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadTicket(ticketId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                android.util.Log.d("TicketDetailVM", "loadTicket: $ticketId")
                val response = supportApi.getTicket(ticketId)
                android.util.Log.d("TicketDetailVM", "loadTicket response: ${response.code()}")

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    _ticket.value = body.ticket
                    _messages.value = body.messages
                    android.util.Log.d("TicketDetailVM", "Loaded ticket with ${body.messages.size} messages")
                } else {
                    val errorBody = response.errorBody()?.string()
                    android.util.Log.e("TicketDetailVM", "loadTicket error: $errorBody")
                    _error.value = "Не удалось загрузить тикет"
                }
            } catch (e: Exception) {
                android.util.Log.e("TicketDetailVM", "loadTicket exception", e)
                _error.value = "Ошибка загрузки: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMessages(ticketId: String) {
        viewModelScope.launch {
            try {
                android.util.Log.d("TicketDetailVM", "loadMessages: $ticketId")
                val response = supportApi.getMessages(ticketId)
                android.util.Log.d("TicketDetailVM", "loadMessages response: ${response.code()}")

                if (response.isSuccessful && response.body() != null) {
                    _messages.value = response.body()!!
                    android.util.Log.d("TicketDetailVM", "Loaded ${_messages.value.size} messages")
                } else {
                    val errorBody = response.errorBody()?.string()
                    android.util.Log.e("TicketDetailVM", "loadMessages error: $errorBody")
                }
            } catch (e: Exception) {
                android.util.Log.e("TicketDetailVM", "loadMessages exception", e)
                _error.value = "Ошибка загрузки сообщений: ${e.message}"
            }
        }
    }

    fun sendMessage(ticketId: String, message: String) {
        viewModelScope.launch {
            _error.value = null

            try {
                android.util.Log.d("TicketDetailVM", "sendMessage called")
                android.util.Log.d("TicketDetailVM", "  Ticket ID: $ticketId")
                android.util.Log.d("TicketDetailVM", "  Message: $message")

                val request = SendMessageRequest(text = message)
                val response = supportApi.sendMessage(ticketId, request)

                android.util.Log.d("TicketDetailVM", "Send message response code: ${response.code()}")
                android.util.Log.d("TicketDetailVM", "Response successful: ${response.isSuccessful}")

                if (response.isSuccessful && response.body() != null) {
                    val newMessage = response.body()!!
                    android.util.Log.d("TicketDetailVM", "Message sent successfully: $newMessage")
                    // Добавляем новое сообщение в список
                    _messages.value = _messages.value + newMessage
                } else {
                    val errorBody = response.errorBody()?.string()
                    android.util.Log.e("TicketDetailVM", "Send message failed. Code: ${response.code()}, Error: $errorBody")
                    _error.value = "Не удалось отправить сообщение"
                }
            } catch (e: Exception) {
                android.util.Log.e("TicketDetailVM", "sendMessage exception", e)
                _error.value = "Ошибка отправки: ${e.message}"
            }
        }
    }

    fun closeTicket(ticketId: String) {
        viewModelScope.launch {
            _error.value = null

            try {
                val response = supportApi.closeTicket(ticketId)
                if (response.isSuccessful && response.body() != null) {
                    _ticket.value = response.body()
                } else {
                    _error.value = "Не удалось закрыть тикет"
                }
            } catch (e: Exception) {
                _error.value = "Ошибка закрытия тикета: ${e.message}"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
