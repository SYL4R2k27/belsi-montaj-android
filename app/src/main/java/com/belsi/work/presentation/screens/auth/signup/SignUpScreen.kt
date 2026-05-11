package com.belsi.work.presentation.screens.auth.signup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.presentation.navigation.AppRoute

/**
 * FIX(2026-05-04): SignUp wizard — три шага с прогресс-индикатором.
 * Шаг 1: телефон + пароль + повтор
 * Шаг 2: имя + фамилия + email (опц.)
 * Шаг 3: выбор роли → submit
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    navController: NavController,
    viewModel: SignUpViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short) }
    }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                SignUpNavigationEvent.NavigateToMain ->
                    navController.navigate(AppRoute.Main.route) { popUpTo(0) { inclusive = true } }
                SignUpNavigationEvent.NavigateToForemanMain ->
                    navController.navigate(AppRoute.ForemanMain.route) { popUpTo(0) { inclusive = true } }
                SignUpNavigationEvent.NavigateToCoordinatorMain ->
                    navController.navigate(AppRoute.CoordinatorMain.route) { popUpTo(0) { inclusive = true } }
                SignUpNavigationEvent.NavigateToCuratorMain ->
                    navController.navigate(AppRoute.CuratorMain.route) { popUpTo(0) { inclusive = true } }
                SignUpNavigationEvent.NavigateToTerms ->
                    navController.navigate(AppRoute.Terms.route) { popUpTo(AppRoute.AuthPhone.route) { inclusive = true } }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Регистрация") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (state.step == SignUpStep.Credentials) navController.popBackStack()
                            else viewModel.back()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        // FIX(2026-05-11) BELSI 2.0.0: max-width 560dp для регистрации на Z Fold/планшете
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        )
                    )
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()                       // FIX(2026-05-05): клавиатура не съедает кнопки
                .navigationBarsPadding()            // FIX(2026-05-05): системная нижняя панель
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            ProgressBar(state.step)
            Spacer(Modifier.height(24.dp))

            when (state.step) {
                SignUpStep.Credentials -> CredentialsStep(state, viewModel)
                SignUpStep.Name -> NameStep(state, viewModel)
                SignUpStep.Role -> RoleStep(state, viewModel)
            }
        }
        }  // FIX(2026-05-11): close Box max-width wrapper
    }
}

@Composable
private fun ProgressBar(step: SignUpStep) {
    val progress = when (step) {
        SignUpStep.Credentials -> 1f / 3f
        SignUpStep.Name -> 2f / 3f
        SignUpStep.Role -> 1f
    }
    val stepText = when (step) {
        SignUpStep.Credentials -> "Шаг 1 из 3"
        SignUpStep.Name -> "Шаг 2 из 3"
        SignUpStep.Role -> "Шаг 3 из 3"
    }
    Column {
        Text(
            stepText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun CredentialsStep(state: SignUpUiState, vm: SignUpViewModel) {
    var pwVisible by remember { mutableStateOf(false) }
    var pwConfirmVisible by remember { mutableStateOf(false) }

    Text(
        "Создайте учётку",
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "Телефон будет вашим логином. Пароль не короче 8 символов, должен содержать буквы и цифры.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(24.dp))

    OutlinedTextField(
        value = state.phone,
        onValueChange = vm::setPhone,
        label = { Text("Телефон") },
        placeholder = { Text("+7 999 123-45-67") },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
    )
    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = state.password,
        onValueChange = vm::setPassword,
        label = { Text("Пароль") },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (pwVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { pwVisible = !pwVisible }) {
                Icon(
                    if (pwVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    null
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
    )
    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = state.passwordConfirm,
        onValueChange = vm::setPasswordConfirm,
        label = { Text("Повторите пароль") },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (pwConfirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { pwConfirmVisible = !pwConfirmVisible }) {
                Icon(
                    if (pwConfirmVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    null
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        isError = state.passwordConfirm.isNotEmpty() && state.passwordConfirm != state.password,
        supportingText = if (state.passwordConfirm.isNotEmpty() && state.passwordConfirm != state.password) {
            { Text("Пароли не совпадают", color = MaterialTheme.colorScheme.error) }
        } else null,
    )
    Spacer(Modifier.height(24.dp))

    Button(
        onClick = { vm.nextFromCredentials() },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text("Далее", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
private fun NameStep(state: SignUpUiState, vm: SignUpViewModel) {
    Text(
        "Как вас зовут?",
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "Имя видят бригадир и куратор. Email — опциональный, на будущее (восстановление пароля).",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(24.dp))

    OutlinedTextField(
        value = state.firstName,
        onValueChange = vm::setFirstName,
        label = { Text("Имя") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
    )
    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = state.lastName,
        onValueChange = vm::setLastName,
        label = { Text("Фамилия") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
    )
    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = state.email,
        onValueChange = vm::setEmail,
        label = { Text("Email (опционально)") },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
    )
    Spacer(Modifier.height(24.dp))

    Button(
        onClick = { vm.nextFromName() },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text("Далее", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
private fun RoleStep(state: SignUpUiState, vm: SignUpViewModel) {
    Text(
        "Кем вы работаете?",
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "Можно изменить позже через куратора.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(24.dp))

    val roles = listOf(
        Triple("installer", "🔨 Монтажник", "Работает на объектах под бригадиром"),
        Triple("foreman", "🛠 Бригадир", "Руководит командой монтажников"),
        Triple("coordinator", "📋 Координатор", "Ведёт объекты, ставит задачи"),
        Triple("curator", "👑 Куратор", "Контролирует всё, аналитика"),
    )

    roles.forEach { (key, title, desc) ->
        val selected = state.role == key
        OutlinedCard(
            onClick = { vm.setRole(key) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = if (selected)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surface
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    Spacer(Modifier.height(2.dp))
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (selected) {
                    Text("✓", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    Spacer(Modifier.height(24.dp))

    Button(
        onClick = { vm.submit() },
        enabled = !state.isLoading,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text("Создать учётку", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}
