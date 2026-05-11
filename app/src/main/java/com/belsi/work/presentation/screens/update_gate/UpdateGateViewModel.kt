package com.belsi.work.presentation.screens.update_gate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.BuildConfig
import com.belsi.work.data.local.PrefsManager
import com.belsi.work.data.remote.api.VersionPolicyDto
import com.belsi.work.data.repositories.ConsentItem
import com.belsi.work.data.repositories.UpdateGateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-11) BELSI 2.0.0: ViewModel для Update Gate (B5).
 *
 * Жизненный цикл:
 * 1. MainActivity при старте дёргает [checkVersion] → VersionApi.
 * 2. Если update_required=true → MainActivity показывает [UpdateGateDialog].
 * 3. Юзер отмечает 6 чекбоксов → [setChecked].
 * 4. Все 6 отмечены → [canSubmit] = true → кнопка «Скачать» активна.
 * 5. Тап «Скачать» → [submitConsentAndDownload] → POST /audit/update-consent.
 * 6. При HTTP 200 + can_download=true → onDownloadReady(url) (фронт открывает Intent).
 * 7. При ошибке → state.error отображается в диалоге.
 *
 * Чекбоксы не помнятся между сессиями — при пересоздании ViewModel
 * состояние снова в дефолте (все false).
 */
@HiltViewModel
class UpdateGateViewModel @Inject constructor(
    private val repository: UpdateGateRepository,
    private val prefsManager: PrefsManager,
) : ViewModel() {

    private val _state = MutableStateFlow(UpdateGateState())
    val state: StateFlow<UpdateGateState> = _state.asStateFlow()

    /**
     * Решает, надо ли показывать gate. Две ветки:
     *
     * 1. **First-launch 2.0.0** (приоритет 1): если локально нет записи
     *    [PrefsManager.hasUpdateConsent] для текущей версии — показываем
     *    gate с пометкой «from 1.2.5 → to <текущая>». Это закрывает
     *    юридический пробел для юзеров, которые обновились с 1.2.5 → 2.0.0
     *    через простой `UpdateBanner` (без 6 чекбоксов согласия).
     *
     * 2. **Версия сервера новее** (приоритет 2): если /version вернул
     *    update_required=true или update_recommended=true — стандартный
     *    путь: gate с from=current, to=latest_version. Это для будущих
     *    апгрейдов 2.0.x → 2.1.0, 2.x → 3.0 и т.д.
     */
    fun checkVersion() {
        if (_state.value.checking) return

        val currentVersion = BuildConfig.VERSION_NAME

        // FIX(2026-05-11) BELSI 2.0.0 (a): first-launch для текущей версии.
        // Если согласие НЕ записано локально — принудительно показываем gate,
        // даже если /version вернёт update_recommended=false.
        if (!prefsManager.hasUpdateConsent(currentVersion)) {
            _state.update {
                it.copy(
                    shouldShowGate = true,
                    // Синтетическая «policy» с пустым downloadUrl: ничего скачивать
                    // не нужно (юзер уже на новой версии), gate работает в режиме
                    // «зарегистрируй согласие» — после submitConsent просто закроется.
                    policy = VersionPolicyDto(
                        latestVersion = currentVersion,
                        latestBuild = BuildConfig.VERSION_CODE,
                        minSupportedBuild = BuildConfig.VERSION_CODE,
                        recommendedBuild = BuildConfig.VERSION_CODE,
                        clientBuild = BuildConfig.VERSION_CODE,
                        updateRequired = false,
                        updateRecommended = false,
                        downloadUrl = "",  // не используется — это first-launch consent
                        changelog = null,
                    ),
                    fromVersion = "1.2.5",  // эвристика для текста gate-экрана
                )
            }
            return
        }

        // Стандартный путь — server-driven version check
        _state.update { it.copy(checking = true, error = null) }
        viewModelScope.launch {
            repository.getVersionPolicy()
                .onSuccess { policy ->
                    _state.update {
                        it.copy(
                            checking = false,
                            policy = policy,
                            fromVersion = currentVersion,
                            shouldShowGate = policy.updateRequired || policy.updateRecommended,
                        )
                    }
                }
                .onFailure { e ->
                    // При сбое — НЕ показываем диалог, приложение работает в обычном режиме.
                    _state.update {
                        it.copy(checking = false, error = e.message, shouldShowGate = false)
                    }
                }
        }
    }

    /** Переключить чекбокс. */
    fun setChecked(itemId: String, checked: Boolean) {
        _state.update {
            val newChecks = it.checks.toMutableMap()
            newChecks[itemId] = checked
            it.copy(checks = newChecks)
        }
    }

    /** Скрыть диалог (тап «Назад»). Не отказывает «навсегда» — при следующем checkVersion покажется снова. */
    fun dismiss() {
        _state.update {
            it.copy(shouldShowGate = false, checks = emptyMap(), submitting = false, error = null)
        }
    }

    /**
     * Отправить согласие → при успехе вызывает [onReady] с URL для скачивания.
     * Каждый чекбокс становится отдельной записью в audit-логе.
     */
    fun submitConsentAndDownload(
        appBuild: Int,
        deviceInfo: String?,
        onReady: (downloadUrl: String) -> Unit,
    ) {
        val cur = _state.value
        val policy = cur.policy ?: return
        if (!cur.canSubmit) return

        _state.update { it.copy(submitting = true, error = null) }

        viewModelScope.launch {
            val items = UpdateGateContent.checkboxes.map {
                ConsentItem(id = it.id, text = it.text)
            }
            repository.submitConsent(
                fromVersion = cur.fromVersion,
                toVersion = policy.latestVersion,
                items = items,
                deviceInfo = deviceInfo,
                appBuild = appBuild,
            ).onSuccess { resp ->
                if (resp.canDownload) {
                    // FIX(2026-05-11) BELSI 2.0.0 (a): локальный флаг — gate не покажется
                    // снова до следующего major-апгрейда.
                    prefsManager.setUpdateConsentSigned(policy.latestVersion)

                    // First-launch consent: downloadUrl пуст — мы УЖЕ на этой версии,
                    // скачивать нечего, просто закрываем диалог.
                    if (policy.downloadUrl.isNotBlank()) {
                        onReady(policy.downloadUrl)
                    }
                    _state.update { it.copy(submitting = false, shouldShowGate = false) }
                } else {
                    _state.update {
                        it.copy(submitting = false, error = "Сервер не разрешил скачивание")
                    }
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(submitting = false, error = "Не удалось зарегистрировать согласие: ${e.message}")
                }
            }
        }
    }
}

/**
 * State экрана обновления. Чекбоксы хранятся в Map<id, Boolean>,
 * чтобы не плодить отдельные поля под каждый.
 */
data class UpdateGateState(
    val fromVersion: String = "1.2.5",
    val checking: Boolean = false,
    val submitting: Boolean = false,
    val policy: VersionPolicyDto? = null,
    val shouldShowGate: Boolean = false,
    val checks: Map<String, Boolean> = emptyMap(),
    val error: String? = null,
) {
    val canSubmit: Boolean
        get() = !submitting && UpdateGateContent.checkboxes.all { checks[it.id] == true }
}
