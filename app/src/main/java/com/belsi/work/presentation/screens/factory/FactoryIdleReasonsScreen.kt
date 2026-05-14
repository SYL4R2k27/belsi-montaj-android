package com.belsi.work.presentation.screens.factory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.belsi.work.data.models.IdleReason
import com.belsi.work.data.offline.OfflineQueuedException
import com.belsi.work.data.repositories.BatchRepository
import com.belsi.work.data.repositories.PauseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-05): Экран выбора причины простоя для производства.
 * Brandbook: 8 типовых причин + «Другое» (свободный ввод).
 * Список меняется по домену юзера — для производственных ролей конкретно эти.
 */
/**
 * FIX(2026-05-05): VM для FactoryIdleReasonsScreen.
 * Дёргает /shift/idle-reasons?domain=production, при ошибке (например 404
 * на 1.2.5 проде) — UI делает fallback на FactoryMockData.
 */
@HiltViewModel
class FactoryIdleReasonsViewModel @Inject constructor(
    private val repo: BatchRepository,
    private val pauseRepo: PauseRepository,
) : ViewModel() {
    private val _reasons = MutableStateFlow<List<IdleReason>>(emptyList())
    val reasons: StateFlow<List<IdleReason>> = _reasons.asStateFlow()

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    private val _submitResult = MutableStateFlow<SubmitResult?>(null)
    val submitResult: StateFlow<SubmitResult?> = _submitResult.asStateFlow()

    sealed class SubmitResult {
        object Success : SubmitResult()
        data class OfflineQueued(val message: String) : SubmitResult()
        data class Error(val message: String) : SubmitResult()
    }

    init {
        viewModelScope.launch {
            repo.getIdleReasons(domain = "production").onSuccess { _reasons.value = it }
        }
    }

    /**
     * FIX(2026-05-11) BELSI 2.0.0 build9: реальный вызов /shift/idle/start
     * вместо TODO. На бэкенде это создаёт shift_pauses + дёргает push кураторам/руководителям.
     * Offline-fallback: action идёт в pending_actions очередь.
     */
    fun submitIdle(reason: String) {
        viewModelScope.launch {
            _submitting.value = true
            pauseRepo.startIdle(reason)
                .onSuccess {
                    _submitResult.value = SubmitResult.Success
                }
                .onFailure { e ->
                    _submitResult.value = if (e is OfflineQueuedException) {
                        SubmitResult.OfflineQueued("📤 Простой «$reason» отправится когда появится сеть")
                    } else {
                        SubmitResult.Error("Ошибка: ${e.message}")
                    }
                }
            _submitting.value = false
        }
    }

    fun clearSubmitResult() { _submitResult.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FactoryIdleReasonsScreen(
    navController: NavController,
    viewModel: FactoryIdleReasonsViewModel = hiltViewModel(),
) {
    val apiReasons by viewModel.reasons.collectAsState()
    val submitting by viewModel.submitting.collectAsState()
    val submitResult by viewModel.submitResult.collectAsState()
    // FIX(2026-05-11) BELSI 2.0.0 build9: backend жив, fallback оставляем как safety net.
    val reasons: List<String> = if (apiReasons.isNotEmpty()) {
        apiReasons.map { it.label }
    } else {
        FactoryMockData.productionIdleReasons
    }
    var selected by remember { mutableStateOf<String?>(null) }
    var customText by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(submitResult) {
        when (val r = submitResult) {
            is FactoryIdleReasonsViewModel.SubmitResult.Success -> {
                snackbarHostState.showSnackbar("Простой зафиксирован")
                viewModel.clearSubmitResult()
                navController.popBackStack()
            }
            is FactoryIdleReasonsViewModel.SubmitResult.OfflineQueued -> {
                snackbarHostState.showSnackbar(r.message)
                viewModel.clearSubmitResult()
                navController.popBackStack()
            }
            is FactoryIdleReasonsViewModel.SubmitResult.Error -> {
                snackbarHostState.showSnackbar(r.message)
                viewModel.clearSubmitResult()
            }
            null -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Причина простоя") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            )
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Column(Modifier.padding(16.dp)) {
                    if (selected == "Другое") {
                        OutlinedTextField(
                            value = customText,
                            onValueChange = { customText = it },
                            label = { Text("Опишите проблему") },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            singleLine = false,
                            minLines = 2,
                        )
                    }
                    Button(
                        onClick = {
                            // FIX(2026-05-11) BELSI 2.0.0 build9: реальный вызов
                            // POST /shift/idle/start { reason }. Backend создаст
                            // запись в shift_pauses + дёрнет push кураторам.
                            val finalReason = if (selected == "Другое") customText.trim() else selected ?: return@Button
                            viewModel.submitIdle(finalReason)
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = !submitting && selected != null && (selected != "Другое" || customText.isNotBlank()),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onError,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            if (submitting) "Отправляем…" else "Начать простой",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        "⚠️ Push уйдёт Старшему и Начальнику производства",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Выберите причину — она будет видна Старшему и Начальнику. Время простоя считается отдельно.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(reasons) { reason ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selected = reason },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected == reason) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surface
                    ),
                    border = if (selected == reason)
                        androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == reason,
                            onClick = { selected = reason },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(reason, fontSize = 16.sp,
                            fontWeight = if (selected == reason) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}
