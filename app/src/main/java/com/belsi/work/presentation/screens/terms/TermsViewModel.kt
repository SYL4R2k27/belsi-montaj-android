package com.belsi.work.presentation.screens.terms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.local.PrefsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TermsViewModel @Inject constructor(
    private val prefsManager: PrefsManager
) : ViewModel() {

    private val _acceptedTOS = MutableStateFlow(false)
    val acceptedTOS: StateFlow<Boolean> = _acceptedTOS.asStateFlow()

    private val _acceptedPrivacy = MutableStateFlow(false)
    val acceptedPrivacy: StateFlow<Boolean> = _acceptedPrivacy.asStateFlow()

    private val _acceptedEULA = MutableStateFlow(false)
    val acceptedEULA: StateFlow<Boolean> = _acceptedEULA.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<TermsNavigationEvent>()
    val navigationEvent: SharedFlow<TermsNavigationEvent> = _navigationEvent.asSharedFlow()

    fun toggleTOS() {
        _acceptedTOS.value = !_acceptedTOS.value
    }

    fun togglePrivacy() {
        _acceptedPrivacy.value = !_acceptedPrivacy.value
    }

    fun toggleEULA() {
        _acceptedEULA.value = !_acceptedEULA.value
    }

    fun acceptAndProceed() {
        if (!_acceptedTOS.value || !_acceptedPrivacy.value || !_acceptedEULA.value) return

        viewModelScope.launch {
            prefsManager.setTermsAccepted(true)
            // Новый пользователь — переходит на выбор роли
            _navigationEvent.emit(TermsNavigationEvent.NavigateToRoleSelect)
        }
    }
}

sealed class TermsNavigationEvent {
    object NavigateToRoleSelect : TermsNavigationEvent()
}
