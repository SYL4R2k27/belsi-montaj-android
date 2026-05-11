package com.belsi.work.presentation.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.local.PrefsManager
import com.belsi.work.data.repositories.AuthRepository
import com.belsi.work.data.repositories.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val prefsManager: PrefsManager,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _logoutEvent = MutableSharedFlow<Unit>()
    val logoutEvent: SharedFlow<Unit> = _logoutEvent.asSharedFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            _error.value = null

            userRepository.getProfile()
                .onSuccess { user ->
                    android.util.Log.d("ProfileViewModel", "Profile loaded: fullName=${user.fullName}, email=${user.email}, avatar=${user.avatarUrl}")
                    _uiState.value = ProfileUiState(
                        fullName = user.fullName ?: "",
                        phone = user.phone ?: "",
                        email = user.email ?: "",
                        role = user.role?.name ?: "",
                        balance = user.balance ?: 0.0,
                        avatarUrl = user.avatarUrl,
                        isLoading = false
                    )
                }
                .onFailure { e ->
                    android.util.Log.e("ProfileViewModel", "Failed to load profile", e)
                    _error.value = e.message ?: "Ошибка загрузки профиля"
                    // Fallback to local prefs
                    val user = prefsManager.getUser()
                    _uiState.value = ProfileUiState(
                        fullName = user?.fullName ?: "",
                        phone = user?.phone ?: "",
                        email = user?.email ?: "",
                        role = user?.role?.name ?: "",
                        balance = user?.balance ?: 0.0,
                        avatarUrl = user?.avatarUrl,
                        isLoading = false
                    )
                }
        }
    }

    fun updateProfile(fullName: String, email: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, updateSuccess = false)
            _error.value = null

            android.util.Log.d("ProfileViewModel", "Updating profile: fullName=$fullName, email=$email")

            userRepository.updateProfile(fullName, email)
                .onSuccess { user ->
                    android.util.Log.d("ProfileViewModel", "Profile updated successfully: fullName=${user.fullName}, email=${user.email}")
                    _uiState.value = ProfileUiState(
                        fullName = user.fullName ?: "",
                        phone = user.phone ?: "",
                        email = user.email ?: "",
                        role = user.role?.name ?: "",
                        balance = user.balance ?: 0.0,
                        avatarUrl = user.avatarUrl,
                        isLoading = false,
                        updateSuccess = true
                    )
                }
                .onFailure { e ->
                    android.util.Log.e("ProfileViewModel", "Failed to update profile", e)
                    _error.value = e.message ?: "Ошибка обновления профиля"
                    _uiState.value = _uiState.value.copy(isLoading = false, updateSuccess = false)
                }
        }
    }

    /**
     * Сбросить флаг успешного обновления
     */
    fun resetUpdateSuccess() {
        _uiState.value = _uiState.value.copy(updateSuccess = false)
    }

    /**
     * Загрузить аватар пользователя
     */
    fun uploadAvatar(avatarFile: File) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploadingAvatar = true)
            _error.value = null

            android.util.Log.d("ProfileViewModel", "Uploading avatar: ${avatarFile.absolutePath}")

            userRepository.uploadAvatar(avatarFile)
                .onSuccess { avatarUrl ->
                    android.util.Log.d("ProfileViewModel", "Avatar uploaded successfully: $avatarUrl")
                    _uiState.value = _uiState.value.copy(
                        avatarUrl = avatarUrl,
                        isUploadingAvatar = false
                    )
                }
                .onFailure { e ->
                    android.util.Log.e("ProfileViewModel", "Failed to upload avatar", e)
                    _error.value = e.message ?: "Ошибка загрузки аватара"
                    _uiState.value = _uiState.value.copy(isUploadingAvatar = false)
                }
        }
    }

    /**
     * Полный logout: удаляем FCM токен, очищаем DataStore и PrefsManager
     */
    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _logoutEvent.emit(Unit)
        }
    }

    fun refresh() {
        loadProfile()
    }

    fun clearError() {
        _error.value = null
    }
}

data class ProfileUiState(
    val fullName: String = "",
    val phone: String = "",
    val email: String = "",
    val role: String = "",
    val balance: Double = 0.0,
    val avatarUrl: String? = null,
    val isLoading: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val updateSuccess: Boolean = false
)
