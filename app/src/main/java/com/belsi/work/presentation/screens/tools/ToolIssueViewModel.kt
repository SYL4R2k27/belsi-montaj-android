package com.belsi.work.presentation.screens.tools

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.belsi.work.data.local.TokenManager
import com.belsi.work.data.models.Tool
import com.belsi.work.data.models.ToolTransaction
import com.belsi.work.data.repositories.ToolsRepository
import com.belsi.work.utils.NetworkUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * ViewModel для выдачи инструмента
 * Используется бригадиром и монтажником (для самовыдачи)
 */
@HiltViewModel
class ToolIssueViewModel @Inject constructor(
    private val toolsRepository: ToolsRepository,
    private val tokenManager: TokenManager,
    private val teamRepository: com.belsi.work.data.repositories.TeamRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ToolIssueUiState())
    val uiState: StateFlow<ToolIssueUiState> = _uiState.asStateFlow()

    init {
        loadTools()
        autoFillInstallerIdIfNeeded()
        loadTeamMembersIfNeeded()
    }

    /**
     * Загрузить список команды для бригадира/куратора
     */
    private fun loadTeamMembersIfNeeded() {
        viewModelScope.launch {
            val userRole = tokenManager.getUserRole()
            if (userRole == "foreman" || userRole == "curator") {
                android.util.Log.d("ToolIssueViewModel", "Loading team members for role: $userRole")
                _uiState.value = _uiState.value.copy(isLoadingTeam = true)

                teamRepository.getTeamMembers()
                    .onSuccess { members ->
                        android.util.Log.d("ToolIssueViewModel", "Loaded ${members.size} team members")
                        _uiState.value = _uiState.value.copy(
                            teamMembers = members,
                            isLoadingTeam = false,
                            canPickUser = true  // Показываем picker вместо ручного ввода
                        )
                    }
                    .onFailure { error ->
                        android.util.Log.e("ToolIssueViewModel", "Failed to load team members", error)
                        _uiState.value = _uiState.value.copy(
                            isLoadingTeam = false,
                            error = "Не удалось загрузить список команды: ${error.message}"
                        )
                    }
            }
        }
    }

    /**
     * Автоматически заполнить installer_id для самовыдачи
     * Если пользователь - installer, автоматически устанавливаем его ID
     */
    private fun autoFillInstallerIdIfNeeded() {
        viewModelScope.launch {
            val userRole = tokenManager.getUserRole()
            if (userRole == "installer") {
                // Для installer автоматически устанавливаем его собственный ID
                val userId = tokenManager.getUserId()
                if (userId != null) {
                    android.util.Log.d("ToolIssueViewModel", "Auto-filling installer_id for self-issue: $userId")
                    _uiState.value = _uiState.value.copy(
                        installerId = userId,
                        isSelfIssue = true  // Флаг что это самовыдача
                    )
                }
            }
        }
    }

    /**
     * Загрузить список доступных инструментов
     */
    fun loadTools() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingTools = true, error = null)

            // Загружаем ВСЕ инструменты (убираем фильтр status=available)
            // TODO: После добавления инструментов на сервере вернуть фильтр status="available"
            toolsRepository.getTools(status = null)
                .onSuccess { tools ->
                    android.util.Log.d("ToolIssueViewModel", "Loaded ${tools.size} tools (all statuses)")
                    _uiState.value = _uiState.value.copy(
                        isLoadingTools = false,
                        availableTools = tools,
                        error = null
                    )
                }
                .onFailure { error ->
                    android.util.Log.e("ToolIssueViewModel", "Failed to load tools", error)
                    _uiState.value = _uiState.value.copy(
                        isLoadingTools = false,
                        error = error.message ?: "Не удалось загрузить инструменты"
                    )
                }
        }
    }

    /**
     * Выбрать инструмент (deprecated - для обратной совместимости)
     */
    fun selectTool(tool: Tool) {
        _uiState.value = _uiState.value.copy(selectedTool = tool)
    }

    /**
     * Переключить выбор инструмента (для множественного выбора)
     */
    fun toggleToolSelection(tool: Tool) {
        val currentSelection = _uiState.value.selectedTools.toMutableList()

        if (currentSelection.any { it.id == tool.id }) {
            // Убираем из выбора
            currentSelection.removeAll { it.id == tool.id }
            android.util.Log.d("ToolIssueViewModel", "Removed tool: ${tool.name}, total: ${currentSelection.size}")
        } else {
            // Добавляем в выбор (если не превышен лимит)
            if (currentSelection.size < ToolIssueUiState.MAX_TOOLS) {
                currentSelection.add(tool)
                android.util.Log.d("ToolIssueViewModel", "Added tool: ${tool.name}, total: ${currentSelection.size}")
            } else {
                android.util.Log.w("ToolIssueViewModel", "Cannot add tool: limit reached (${ToolIssueUiState.MAX_TOOLS})")
                _uiState.value = _uiState.value.copy(
                    error = "Максимум ${ToolIssueUiState.MAX_TOOLS} инструментов за раз"
                )
                return
            }
        }

        _uiState.value = _uiState.value.copy(
            selectedTools = currentSelection,
            error = null
        )
    }

    /**
     * Очистить выбор всех инструментов
     */
    fun clearToolSelection() {
        _uiState.value = _uiState.value.copy(selectedTools = emptyList())
    }

    /**
     * Проверить выбран ли инструмент
     */
    fun isToolSelected(toolId: String): Boolean {
        return _uiState.value.selectedTools.any { it.id == toolId }
    }

    /**
     * Установить ID монтажника
     */
    fun setInstallerId(installerId: String) {
        _uiState.value = _uiState.value.copy(installerId = installerId)
    }

    /**
     * Установить комментарий
     */
    fun setComment(comment: String) {
        _uiState.value = _uiState.value.copy(comment = comment)
    }

    /**
     * Установить URI фото
     */
    fun setPhotoUri(uri: Uri?) {
        _uiState.value = _uiState.value.copy(photoUri = uri)
    }

    /**
     * Выдать инструменты (один или несколько)
     */
    fun issueTool(context: Context, photoFile: File?) {
        val state = _uiState.value

        // Валидация - проверяем новый список selectedTools
        if (state.selectedTools.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Выберите инструменты")
            return
        }

        if (state.installerId.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Укажите монтажника")
            return
        }

        // Проверка интернета
        if (!NetworkUtils.isNetworkAvailable(context)) {
            _uiState.value = _uiState.value.copy(
                error = "Нет подключения к интернету. Проверьте соединение"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isIssuing = true, error = null)

            try {
                // Шаг 1: Загрузить фото если есть (одно фото для всех инструментов)
                var photoUrl: String? = null
                if (photoFile != null && photoFile.exists()) {
                    android.util.Log.d("ToolIssueViewModel", "Uploading photo first...")
                    toolsRepository.uploadToolPhoto(photoFile)
                        .onSuccess { response ->
                            photoUrl = response.photoUrl
                            android.util.Log.d("ToolIssueViewModel", "Photo uploaded: $photoUrl")
                        }
                        .onFailure { error ->
                            android.util.Log.e("ToolIssueViewModel", "Photo upload failed", error)
                            _uiState.value = _uiState.value.copy(
                                isIssuing = false,
                                error = "Ошибка загрузки фото: ${error.message}"
                            )
                            return@launch
                        }
                }

                // Шаг 2: Выдать все выбранные инструменты
                android.util.Log.d("ToolIssueViewModel", "Issuing ${state.selectedTools.size} tools to ${state.installerId}")

                var successCount = 0
                var failedTools = mutableListOf<String>()

                for (tool in state.selectedTools) {
                    toolsRepository.issueTool(
                        toolId = tool.id,
                        installerId = state.installerId,
                        comment = state.comment.ifBlank { null },
                        photoUrl = photoUrl
                    )
                        .onSuccess { transaction ->
                            successCount++
                            android.util.Log.d("ToolIssueViewModel", "Tool issued successfully: ${tool.name}")
                        }
                        .onFailure { error ->
                            failedTools.add(tool.name)
                            android.util.Log.e("ToolIssueViewModel", "Failed to issue tool: ${tool.name}", error)
                        }
                }

                // Результат
                if (failedTools.isEmpty()) {
                    // Все успешно выдано
                    android.util.Log.d("ToolIssueViewModel", "All $successCount tools issued successfully")

                    // Генерируем PDF отчет
                    try {
                        val installerMember = _uiState.value.teamMembers.find { it.userId == state.installerId }
                        val installerName = installerMember?.displayName() ?: "Неизвестно"
                        val installerPhone = installerMember?.phone ?: ""
                        val currentUserName = tokenManager.getUserId() ?: "Неизвестно"

                        val pdfResult = com.belsi.work.utils.PdfGenerator.generateToolIssueReport(
                            context = context,
                            installerName = installerName,
                            installerPhone = installerPhone,
                            tools = state.selectedTools,
                            issuedBy = currentUserName,
                            comment = state.comment.ifBlank { null }
                        )

                        pdfResult.onSuccess { pdfFile ->
                            android.util.Log.d("ToolIssueViewModel", "PDF report generated: ${pdfFile.absolutePath}")
                        }.onFailure { pdfError ->
                            android.util.Log.e("ToolIssueViewModel", "Failed to generate PDF", pdfError)
                        }
                    } catch (pdfException: Exception) {
                        android.util.Log.e("ToolIssueViewModel", "PDF generation error", pdfException)
                    }

                    _uiState.value = _uiState.value.copy(
                        isIssuing = false,
                        issuedTransaction = null, // Для множественной выдачи не используем одну транзакцию
                        error = null
                    )
                    // После успешной выдачи экран автоматически закроется (LaunchedEffect проверяет isIssuing)
                } else {
                    // Частичная или полная ошибка
                    val errorMessage = if (successCount > 0) {
                        "Выдано: $successCount. Ошибки: ${failedTools.joinToString(", ")}"
                    } else {
                        "Не удалось выдать инструменты: ${failedTools.joinToString(", ")}"
                    }
                    android.util.Log.e("ToolIssueViewModel", errorMessage)
                    _uiState.value = _uiState.value.copy(
                        isIssuing = false,
                        error = errorMessage
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("ToolIssueViewModel", "Unexpected error", e)
                _uiState.value = _uiState.value.copy(
                    isIssuing = false,
                    error = "Непредвиденная ошибка: ${e.message}"
                )
            }
        }
    }

    /**
     * Сбросить ошибку
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Сбросить состояние после успешной выдачи
     */
    fun reset() {
        _uiState.value = ToolIssueUiState()
    }
}

/**
 * UI состояние для выдачи инструмента
 */
data class ToolIssueUiState(
    val isLoadingTools: Boolean = false,
    val availableTools: List<Tool> = emptyList(),
    val selectedTool: Tool? = null,  // Deprecated - используем selectedTools
    val selectedTools: List<Tool> = emptyList(),  // Список выбранных инструментов (до 40)
    val installerId: String = "",
    val comment: String = "",
    val photoUri: Uri? = null,
    val isIssuing: Boolean = false,
    val issuedTransaction: ToolTransaction? = null,
    val error: String? = null,
    val isSelfIssue: Boolean = false,  // Флаг самовыдачи (installer выдает себе)
    val teamMembers: List<com.belsi.work.data.remote.dto.team.ForemanTeamMemberDto> = emptyList(),  // Список команды
    val isLoadingTeam: Boolean = false,  // Загрузка команды
    val canPickUser: Boolean = false  // Можно выбрать из списка (для foreman/curator)
) {
    companion object {
        const val MAX_TOOLS = 40  // Максимум инструментов для одновременной выдачи
    }

    val canIssue: Boolean
        get() = selectedTools.isNotEmpty() && installerId.isNotBlank() && !isIssuing

    val hasTools: Boolean
        get() = availableTools.isNotEmpty()

    val selectedCount: Int
        get() = selectedTools.size

    val canSelectMore: Boolean
        get() = selectedTools.size < MAX_TOOLS
}
