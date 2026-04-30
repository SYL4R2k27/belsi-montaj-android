package com.belsi.work.presentation.screens.role

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.local.PrefsManager
import com.belsi.work.data.models.UserRole
import com.belsi.work.data.repositories.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoleSelectViewModel @Inject constructor(
    private val prefsManager: PrefsManager,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _selectedRole = MutableStateFlow<UserRole?>(null)
    val selectedRole: StateFlow<UserRole?> = _selectedRole.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    fun onRoleSelected(role: UserRole) {
        _selectedRole.value = role
    }

    fun confirmRole() {
        val role = _selectedRole.value ?: return

        viewModelScope.launch {
            _isLoading.value = true

            // Сохраняем роль на сервере И локально
            userRepository.updateMyRole(role)
                .onSuccess { user ->
                    android.util.Log.d("RoleSelectVM", "Role saved to server: ${user.role}")
                }
                .onFailure { e ->
                    android.util.Log.w("RoleSelectVM", "Failed to save role to server, saved locally: ${e.message}")
                    // Роль всё равно сохранена локально внутри updateMyRole
                }

            // After role selection, go to instructions screen
            _navigationEvent.emit(NavigationEvent.NavigateToInstructions)

            _isLoading.value = false
        }
    }
}

sealed class NavigationEvent {
    object NavigateToMain : NavigationEvent()
    object NavigateToForemanMain : NavigationEvent()
    object NavigateToCoordinatorMain : NavigationEvent()
    object NavigateToCuratorMain : NavigationEvent()
    object NavigateToInstallerInvite : NavigationEvent()
    object NavigateToInstructions : NavigationEvent()
}
