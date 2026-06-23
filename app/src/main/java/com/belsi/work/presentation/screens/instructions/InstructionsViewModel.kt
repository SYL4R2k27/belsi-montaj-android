package com.belsi.work.presentation.screens.instructions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.local.PrefsManager
import com.belsi.work.data.models.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InstructionsViewModel @Inject constructor(
    private val prefsManager: PrefsManager
) : ViewModel() {

    private val _acknowledgedSteps = MutableStateFlow<Set<Instruction>>(emptySet())
    val acknowledgedSteps: StateFlow<Set<Instruction>> = _acknowledgedSteps.asStateFlow()

    val progress: StateFlow<Float> = _acknowledgedSteps.map { steps ->
        if (Instruction.entries.isEmpty()) 0f
        else steps.size.toFloat() / Instruction.entries.size.toFloat()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    private val _navigationEvent = MutableSharedFlow<InstructionsNavigationEvent>()
    val navigationEvent: SharedFlow<InstructionsNavigationEvent> = _navigationEvent.asSharedFlow()

    fun toggleInstruction(instruction: Instruction) {
        val current = _acknowledgedSteps.value.toMutableSet()
        if (current.contains(instruction)) {
            current.remove(instruction)
        } else {
            current.add(instruction)
        }
        _acknowledgedSteps.value = current
    }

    fun proceedToMain() {
        viewModelScope.launch {
            prefsManager.setOnboardingCompleted(true)

            val currentUser = prefsManager.getUser()
            val event = when (currentUser?.role) {
                UserRole.INSTALLER -> {
                    if (currentUser.foremanId == null) {
                        InstructionsNavigationEvent.NavigateToInstallerInvite
                    } else {
                        InstructionsNavigationEvent.NavigateToMain
                    }
                }
                UserRole.FOREMAN -> InstructionsNavigationEvent.NavigateToForemanMain
                UserRole.COORDINATOR -> InstructionsNavigationEvent.NavigateToCoordinatorMain
                UserRole.CURATOR -> InstructionsNavigationEvent.NavigateToCuratorMain
                else -> InstructionsNavigationEvent.NavigateToMain
            }
            _navigationEvent.emit(event)
        }
    }
}

sealed class InstructionsNavigationEvent {
    object NavigateToMain : InstructionsNavigationEvent()
    object NavigateToForemanMain : InstructionsNavigationEvent()
    object NavigateToCoordinatorMain : InstructionsNavigationEvent()
    object NavigateToCuratorMain : InstructionsNavigationEvent()
    object NavigateToInstallerInvite : InstructionsNavigationEvent()
}
