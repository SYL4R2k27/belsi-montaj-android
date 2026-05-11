package com.belsi.work.presentation.screens.messenger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.models.ContactDTO
import com.belsi.work.data.models.MessengerMessageDTO
import com.belsi.work.data.models.ThreadDTO
import com.belsi.work.data.repositories.MessengerRepository
import com.belsi.work.data.remote.websocket.MessengerWebSocket
import com.belsi.work.data.remote.websocket.WsConnectionState
import com.belsi.work.data.remote.websocket.WsEvent
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
class ThreadListViewModel @Inject constructor(
    private val messengerRepository: MessengerRepository,
    private val messengerWebSocket: MessengerWebSocket
) : ViewModel() {

    private val _threads = MutableStateFlow<List<ThreadDTO>>(emptyList())
    val threads: StateFlow<List<ThreadDTO>> = _threads.asStateFlow()

    private val _contacts = MutableStateFlow<List<ContactDTO>>(emptyList())
    val contacts: StateFlow<List<ContactDTO>> = _contacts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // For create chat flow
    private val _createdThreadId = MutableStateFlow<String?>(null)
    val createdThreadId: StateFlow<String?> = _createdThreadId.asStateFlow()

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MessengerMessageDTO>>(emptyList())
    val searchResults: StateFlow<List<MessengerMessageDTO>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var pollingJob: Job? = null
    private var wsListenerJob: Job? = null
    private var searchJob: Job? = null

    init {
        loadThreads()
        messengerWebSocket.connect()
        listenWebSocket()
        startPolling()
    }

    fun loadThreads() {
        viewModelScope.launch {
            _isLoading.value = true
            messengerRepository.getThreads()
                .onSuccess { _threads.value = it }
                .onFailure { _error.value = it.message }
            _isLoading.value = false
        }
    }

    fun loadContacts() {
        viewModelScope.launch {
            messengerRepository.getContacts()
                .onSuccess { _contacts.value = it }
                .onFailure { _error.value = it.message }
        }
    }

    fun createDirectChat(contactId: String) {
        viewModelScope.launch {
            messengerRepository.createThread(
                type = "direct",
                participantIds = listOf(contactId)
            )
                .onSuccess { thread ->
                    loadThreads()
                    _createdThreadId.value = thread.id
                }
                .onFailure { _error.value = it.message }
        }
    }

    fun createGroupChat(name: String, participantIds: List<String>) {
        viewModelScope.launch {
            messengerRepository.createThread(
                type = "group",
                name = name,
                participantIds = participantIds
            )
                .onSuccess { thread ->
                    loadThreads()
                    _createdThreadId.value = thread.id
                }
                .onFailure { _error.value = it.message }
        }
    }

    fun clearCreatedThreadId() {
        _createdThreadId.value = null
    }

    fun clearError() {
        _error.value = null
    }

    // ---- Search ----

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            searchJob?.cancel()
            return
        }
        // Debounce search: wait 400ms after last keystroke
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400)
            _isSearching.value = true
            messengerRepository.searchMessages(query)
                .onSuccess { _searchResults.value = it }
                .onFailure { _searchResults.value = emptyList() }
            _isSearching.value = false
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _isSearching.value = false
        searchJob?.cancel()
    }

    private fun listenWebSocket() {
        wsListenerJob?.cancel()
        wsListenerJob = viewModelScope.launch {
            messengerWebSocket.events.collect { event ->
                when (event) {
                    is WsEvent.NewMessage,
                    is WsEvent.ThreadUpdated -> {
                        // Reload thread list to get updated last_message and unread_count
                        loadThreads()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                val interval = if (messengerWebSocket.connectionState.value == WsConnectionState.CONNECTED) {
                    30_000L // 30s safety check when WS is connected
                } else {
                    10_000L // 10s active polling when WS is down
                }
                delay(interval)
                messengerRepository.getThreads()
                    .onSuccess { _threads.value = it }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        wsListenerJob?.cancel()
        messengerWebSocket.disconnect()
    }
}
