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

                    // Текущая активная роль: ПРИОРИТЕТ — реальная активная роль из
                    // PrefsManager (users.role, на неё опирается роутинг в MainActivity),
                    // затем primary, затем первая из списка. Раньше тут стояло
                    // roles.firstOrNull() первым — из-за чего currentRole никогда не был
                    // настоящей активной ролью (чип показывал не ту роль, а авто-пикер
                    // ролей при логине не срабатывал, т.к. currentRole != null всегда).
                    val primary = list.firstOrNull { it.isPrimary }?.role?.let {
                        runCatching { UserRole.valueOf(it.uppercase()) }.getOrNull()
                    }
                    val fallback = prefsManager.getUser()?.role
                    val active = fallback ?: primary ?: roles.firstOrNull()

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

    fun setActiveRole(role: UserRole, onSwitched: () -> Unit = {}) {
        viewModelScope.launch {
            android.util.Log.i("RoleSwitch", "setActiveRole -> ${role.name.lowercase()} ...")
            brandRepo.setActiveRole(role)
                .onSuccess {
                    android.util.Log.i("RoleSwitch", "setActiveRole OK -> ${role.name}")
                    // Обновляем кешированную роль, на которую опирается роутинг при старте
                    // (MainActivity читает prefsManager.getUser()?.role). Без этого
                    // переключение роли не меняло домашний экран до повторного логина.
                    prefsManager.getUser()?.let { u -> prefsManager.saveUser(u.copy(role = role)) }
                    prefsManager.setUserRole(role.name.lowercase())
                    _uiState.update { it.copy(currentRole = role) }
                    onSwitched()
                }
                .onFailure { e ->
                    // FIX(2026-05-22): раньше падение было НЕМЫМ (нет лога/UI) — роль не
                    // менялась и непонятно почему. Логируем HTTP-код/тело для диагностики.
                    android.util.Log.e("RoleSwitch", "setActiveRole FAILED -> ${role.name}: ${e.message}", e)
                    _uiState.update { it.copy(error = e.message) }
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
