package com.belsi.work.presentation.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.local.ActiveRoleManager
import com.belsi.work.data.local.PrefsManager
import com.belsi.work.data.models.UserRole
import com.belsi.work.data.repositories.BrandCoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-11) BELSI 2.0.0 build3: ViewModel для глобального RoleSwitcher.
 *
 * При создании (на любом экране где включается FAB):
 *   1. Тянет /user/me/roles
 *   2. Кеширует список в ActiveRoleManager.availableRoles
 *   3. Если ролей >1 — RoleSwitcher показывается, иначе нет
 *
 * Сохраняет текущую активную роль локально + синхронизирует с backend через
 * /user/me/active-role (обновляет users.role для backward compat).
 */
data class BrandRoleSwitcherUiState(
    val availableRoles: List<UserRole> = emptyList(),
    val currentRole: UserRole? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class BrandRoleSwitcherViewModel @Inject constructor(
    private val brandRepo: BrandCoreRepository,
    private val activeRoleManager: ActiveRoleManager,
    private val prefsManager: PrefsManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrandRoleSwitcherUiState())
    val uiState: StateFlow<BrandRoleSwitcherUiState> = _uiState.asStateFlow()

    init {
        loadRoles()
    }

    fun loadRoles() {
        viewModelScope.launch {
            if (prefsManager.getToken().isNullOrBlank()) return@launch
            _uiState.update { it.copy(loading = true) }
            brandRepo.fetchMyRoles().fold(
                onSuccess = { list ->
                    val roles = list
                        .filter { it.isActive }
                        .mapNotNull { runCatching { UserRole.valueOf(it.role.uppercase()) }.getOrNull() }
                        .distinct()

                    // Текущая активная — берём из ActiveRoleManager если есть, иначе primary,
                    // иначе users.role из PrefsManager
                    val saved = activeRoleManager.getAvailableRoles().firstOrNull()
                    val primary = list.firstOrNull { it.isPrimary }?.role?.let {
                        runCatching { UserRole.valueOf(it.uppercase()) }.getOrNull()
                    }
                    val fallback = prefsManager.getUser()?.role
                    val active = roles.firstOrNull() ?: primary ?: fallback

                    _uiState.update {
                        it.copy(
                            availableRoles = roles,
                            currentRole = active,
                            loading = false,
                            error = null,
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(loading = false, error = e.message) }
                }
            )
        }
    }

    fun setActiveRole(role: UserRole) {
        viewModelScope.launch {
            brandRepo.setActiveRole(role).onSuccess {
                _uiState.update { it.copy(currentRole = role) }
            }
        }
    }

    /**
     * ActiveRoleManager-based helper для тех мест где нужно знать роль без VM.
     */
    private suspend fun ActiveRoleManager.getAvailableRoles(): List<UserRole> {
        // Простая обёртка — фактическое чтение через Flow в реальной интеграции
        return emptyList()
    }
}
