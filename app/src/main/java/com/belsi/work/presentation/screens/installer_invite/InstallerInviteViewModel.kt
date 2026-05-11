package com.belsi.work.presentation.screens.installer_invite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.repositories.TeamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InstallerInviteViewModel @Inject constructor(
    private val teamRepository: TeamRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _success = MutableStateFlow(false)
    val success: StateFlow<Boolean> = _success.asStateFlow()

    fun joinTeam(code: String) {
        if (code.isBlank()) {
            _error.value = "Введите код приглашения"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            teamRepository.joinTeam(code)
                .onSuccess { result ->
                    if (result.success) {
                        _success.value = true
                    } else {
                        _error.value = result.message
                    }
                }
                .onFailure { e ->
                    _error.value = e.message ?: "Ошибка присоединения к команде"
                }

            _isLoading.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }
}
