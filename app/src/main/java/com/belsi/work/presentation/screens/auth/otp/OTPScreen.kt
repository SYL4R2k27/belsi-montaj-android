package com.belsi.work.presentation.screens.auth.otp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.presentation.navigation.AppRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OTPScreen(
    navController: NavController,
    phone: String,
    viewModel: OTPViewModel = hiltViewModel()
) {
    // Устанавливаем номер телефона в ViewModel
    LaunchedEffect(phone) {
        println("OTPScreen: Получен номер телефона: '$phone'")
        if (phone.isNotEmpty()) {
            viewModel.setPhone(phone)
        } else {
            println("OTPScreen: ОШИБКА - номер телефона пустой!")
        }
    }

    val otpCode by viewModel.otpCode.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val remainingTime by viewModel.remainingTime.collectAsState()
    val canResend by viewModel.canResend.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Показываем ошибки через Snackbar
    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }

    // Handle navigation events
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is NavigationEvent.NavigateToTerms -> {
                    // Новый пользователь — полный онбординг
                    navController.navigate(AppRoute.Terms.route) {
                        popUpTo(AppRoute.AuthPhone.route) { inclusive = true }
                    }
                }
                is NavigationEvent.NavigateToMain -> {
                    navController.navigate(AppRoute.Main.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
                is NavigationEvent.NavigateToForemanMain -> {
                    navController.navigate(AppRoute.ForemanMain.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
                is NavigationEvent.NavigateToCoordinatorMain -> {
                    navController.navigate(AppRoute.CoordinatorMain.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
                is NavigationEvent.NavigateToCuratorMain -> {
                    navController.navigate(AppRoute.CuratorMain.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Введите код",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Мы отправили код на номер\n$phone",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(48.dp))

                // OTP Input
                OTPInputField(
                    value = otpCode,
                    onValueChange = { viewModel.onOTPChanged(it) },
                    enabled = !isLoading,
                    length = 6
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // Timer / Resend
                if (canResend) {
                    TextButton(
                        onClick = { viewModel.resendOTP(phone) },
                        enabled = !isLoading
                    ) {
                        Text(
                            text = "Отправить код повторно",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    Text(
                        text = "Отправить повторно через ${remainingTime}с",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Verify Button (optional, auto-verifies on 6 digits)
                Button(
                    onClick = { viewModel.verifyOTP(phone) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isLoading && otpCode.length == 6,
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            "Подтвердить",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OTPInputField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    length: Int = 6
) {
    BasicTextField(
        value = value,
        onValueChange = { if (it.length <= length) onValueChange(it) },
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        decorationBox = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(length) { index ->
                    val char = value.getOrNull(index)?.toString() ?: ""
                    val isFocused = index == value.length
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .border(
                                width = 2.dp,
                                color = when {
                                    !enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    isFocused -> MaterialTheme.colorScheme.primary
                                    char.isNotEmpty() -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                    else -> MaterialTheme.colorScheme.outline
                                },
                                shape = MaterialTheme.shapes.medium
                            )
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = MaterialTheme.shapes.medium
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = char,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    )
}
