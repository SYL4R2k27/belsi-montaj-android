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
     * 1. **По пунктам согласия** (приоритет 1): спрашиваем ТОЛЬКО НЕпринятые
     *    пункты. FIX(2026-06-16): раньше показ был привязан к ВЕРСИИ приложения
     *    ([PrefsManager.hasUpdateConsent]) → плашка вылазила при КАЖДОМ
     *    обновлении, даже если тексты пунктов не менялись. Теперь источник
     *    истины — сервер `GET /audit/consents/accepted` (множество id,
     *    которые юзер КОГДА-ЛИБО принимал) с офлайн-фолбэком на локальный кэш.
     *    Если все пункты [UpdateGateContent.checkboxes] уже приняты — плашку
     *    НЕ показываем (это и есть фикс). Если есть непринятые — показываем
     *    только их.
     *
     * 2. **Версия сервера новее** (приоритет 2): если /version вернул
     *    update_required=true или update_recommended=true — стандартный
     *    путь принудительного скачивания новой версии. Это для будущих
     *    апгрейдов 2.0.x → 2.1.0, 2.x → 3.0 и т.д.
     */
    fun checkVersion() {
        if (_state.value.checking) return

        val currentVersion = BuildConfig.VERSION_NAME

        _state.update { it.copy(checking = true, error = null) }
        viewModelScope.launch {
            // FIX(2026-06-16): item-based consent gate.
            // Множество принятых id — с сервера (источник истины), фолбэк на кэш.
            // ВАЖНО (152-ФЗ): если сервер недоступен И локального кэша нет →
            // accepted=пусто → покажем все пункты (лучше переспросить, чем
            // пропустить). repository.getAcceptedConsentIds() при сбое вернёт
            // null → берём кэш (по умолчанию пустой Set).
            val serverResult = repository.getAcceptedConsentIds()
            val accepted: Set<String> = serverResult.getOrNull()
                ?: prefsManager.getAcceptedConsentIds()

            // При успехе сервера — обновляем локальный кэш для будущих офлайн-стартов.
            serverResult.getOrNull()?.let { prefsManager.setAcceptedConsentIds(it) }

            val pending = UpdateGateContent.checkboxes.filter { it.id !in accepted }

            if (pending.isEmpty()) {
                // Все пункты согласия уже приняты — плашку «по пунктам» НЕ
                // показываем (главный фикс). НО priority-2 остаётся: если сервер
                // /version требует принудительного обновления (updateRequired/
                // updateRecommended) — показываем gate для скачивания новой версии.
                repository.getVersionPolicy()
                    .onSuccess { policy ->
                        _state.update {
                            it.copy(
                                checking = false,
                                policy = policy,
                                fromVersion = currentVersion,
                                // pending пуст → все чекбоксы уже приняты; canSubmit
                                // станет true сразу (нет обязательных пунктов).
                                pendingIds = emptySet(),
                                shouldShowGate = policy.updateRequired || policy.updateRecommended,
                            )
                        }
                    }
                    .onFailure { e ->
                        // Сбой /version — НЕ показываем диалог, работаем обычно.
                        _state.update {
                            it.copy(checking = false, error = e.message, shouldShowGate = false)
                        }
                    }
            } else {
                // Есть непринятые — показываем gate только под них.
                // Синтетическая «policy» с пустым downloadUrl: ничего скачивать
                // не нужно (юзер уже на этой версии), gate работает в режиме
                // «зарегистрируй согласие» — после submitConsent просто закроется.
                _state.update {
                    it.copy(
                        checking = false,
                        shouldShowGate = true,
                        pendingIds = pending.map { p -> p.id }.toSet(),
                        policy = VersionPolicyDto(
                            latestVersion = currentVersion,
                            latestBuild = BuildConfig.VERSION_CODE,
                            minSupportedBuild = BuildConfig.VERSION_CODE,
                            recommendedBuild = BuildConfig.VERSION_CODE,
                            clientBuild = BuildConfig.VERSION_CODE,
                            updateRequired = false,
                            updateRecommended = false,
                            downloadUrl = "",  // не используется — это consent-режим
                            changelog = null,
                        ),
                        fromVersion = currentVersion,
                    )
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
            // FIX(2026-06-16): шлём ТОЛЬКО непринятые (pending) пункты — бэкенд
            // принимает любое число items (>=1). Если pending пуст (priority-2,
            // принудительное обновление) — все 9 пунктов уже приняты, шлём их
            // как подтверждение (бэкенд снимает требование 9 — но пустой список
            // отправлять нельзя).
            val pendingChecks = UpdateGateContent.checkboxes.filter { it.id in cur.pendingIds }
            val itemsSource = pendingChecks.ifEmpty { UpdateGateContent.checkboxes }
            val items = itemsSource.map {
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
                    // FIX(2026-06-16): обновляем кэш принятых id (объединяем
                    // существующий кэш с только что принятыми pending) — чтобы
                    // при следующем старте плашка не показалась снова даже офлайн.
                    val mergedAccepted = prefsManager.getAcceptedConsentIds() +
                        items.map { it.id }.toSet()
                    prefsManager.setAcceptedConsentIds(mergedAccepted)
                    // Совместимость: per-version флаг оставляем как есть.
                    prefsManager.setUpdateConsentSigned(policy.latestVersion)

                    // Consent-режим: downloadUrl пуст — мы УЖЕ на этой версии,
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
    // FIX(2026-06-16): id пунктов, которые юзеру нужно принять В ЭТОТ раз
    // (НЕпринятые ранее). Дефолт — все пункты (до загрузки accepted с сервера).
    val pendingIds: Set<String> = UpdateGateContent.checkboxes.map { it.id }.toSet(),
    val error: String? = null,
) {
    /** Submit разрешён, когда отмечены ВСЕ pending-пункты (если их нет — сразу true). */
    val canSubmit: Boolean
        get() = !submitting &&
            UpdateGateContent.checkboxes.filter { it.id in pendingIds }.all { checks[it.id] == true }
}
