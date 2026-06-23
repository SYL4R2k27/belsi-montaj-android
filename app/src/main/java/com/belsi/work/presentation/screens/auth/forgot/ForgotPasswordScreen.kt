package com.belsi.work.presentation.screens.auth.forgot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.belsi.work.data.repositories.AuthRepository
import com.belsi.work.presentation.navigation.AppRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BELSI 2.1.0 — восстановление пароля (auth-onboarding ответ 101).
 * Шаг 1: ввод телефона → POST /auth/forgot-password (код по SMS).
 * Шаг 2: ввод кода + нового пароля → POST /auth/reset-password.
 */
enum class ForgotStep { PHONE, RESET }

data class ForgotState(
    val step: ForgotStep = ForgotStep.PHONE,
    val phone: String = "",
    val code: String = "",
    val newPassword: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ForgotState())
    val state: StateFlow<ForgotState> = _state.asStateFlow()

    fun onPhone(v: String) = _state.update { it.copy(phone = v, error = null) }
    fun onCode(v: String) = _state.update { it.copy(code = v.filter { c -> c.isDigit() }.take(6), error = null) }
    fun onNewPassword(v: String) = _state.update { it.copy(newPassword = v, error = null) }

    fun requestCode() {
        val phone = _state.value.phone.trim()
        if (phone.isBlank()) { _state.update { it.copy(error = "Введите номер телефона") }; return }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            authRepository.forgotPassword(phone)
                .onSuccess { _state.update { it.copy(isLoading = false, step = ForgotStep.RESET) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Ошибка") } }
        }
    }

    fun resetPassword() {
        val s = _state.value
        if (s.code.length < 4) { _state.update { it.copy(error = "Введите код из SMS") }; return }
        if (s.newPassword.length < 6) { _state.update { it.copy(error = "Пароль не менее 6 символов") }; return }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            authRepository.resetPassword(s.phone.trim(), s.code, s.newPassword)
                .onSuccess { _state.update { it.copy(isLoading = false, done = true) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Ошибка") } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    navController: NavController,
    viewModel: ForgotPasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // Успех → назад на экран логина
    androidx.compose.runtime.LaunchedEffect(state.done) {
        if (state.done) {
            navController.navigate(AppRoute.Login.route) {
                popUpTo(AppRoute.Login.route) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Восстановление пароля") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.step == ForgotStep.PHONE) {
                Text("Введите номер телефона — пришлём код по SMS.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = state.phone,
                    onValueChange = viewModel::onPhone,
                    label = { Text("Телефон") },
                    placeholder = { Text("+7…") },
                    singleLine = true,
                    enabled = !state.isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { viewModel.requestCode() },
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (state.isLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Получить код", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Text("Код отправлен на ${state.phone}. Введите его и новый пароль.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = state.code,
                    onValueChange = viewModel::onCode,
                    label = { Text("Код из SMS") },
                    singleLine = true,
                    enabled = !state.isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.newPassword,
                    onValueChange = viewModel::onNewPassword,
                    label = { Text("Новый пароль (мин. 6)") },
                    singleLine = true,
                    enabled = !state.isLoading,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { viewModel.resetPassword() },
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (state.isLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Сменить пароль", fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = { viewModel.requestCode() }, enabled = !state.isLoading) {
                    Text("Отправить код снова")
                }
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        }
    }
}
