package com.belsi.work.presentation.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.belsi.work.data.local.PrefsManager
import com.belsi.work.presentation.navigation.AppRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val prefsManager = remember { PrefsManager(context) }

    var notificationsEnabled by remember { mutableStateOf(true) }
    var locationEnabled by remember { mutableStateOf(true) }
    var autoUploadPhotos by remember { mutableStateOf(true) }
    var aiAnalysisVisible by remember { mutableStateOf(prefsManager.isAiAnalysisVisible()) }

    // Скрытый доступ к DEBUG settings через 5 тапов по версии
    var debugTapCount by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    // State для диалога подтверждения выхода
    var showLogoutDialog by remember { mutableStateOf(false) }

    // State для диалога установки пароля
    var showPasswordDialog by remember { mutableStateOf(false) }
    var hasPassword by remember { mutableStateOf(prefsManager.hasAppPassword()) }

    // Наблюдаем за состоянием выхода
    val isLoggingOut by viewModel.isLoggingOut.collectAsState()
    val logoutSuccess by viewModel.logoutSuccess.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()

    // Показ snackbar сообщений
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    LaunchedEffect(debugTapCount) {
        if (debugTapCount >= 5) {
            navController.navigate("debug_settings")
            debugTapCount = 0
        } else if (debugTapCount > 0) {
            // Сбрасываем счетчик через 2 секунды
            kotlinx.coroutines.delay(2000)
            debugTapCount = 0
        }
    }

    // Обработка успешного выхода
    LaunchedEffect(logoutSuccess) {
        if (logoutSuccess) {
            // Очищаем весь стек навигации и переходим на экран авторизации
            navController.navigate(AppRoute.AuthPhone.route) {
                popUpTo(0) { inclusive = true }
            }
            viewModel.resetLogoutSuccess()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Notifications Section
            Text(
                text = "Уведомления",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    SettingsSwitchItem(
                        icon = Icons.Default.Notifications,
                        title = "Уведомления",
                        description = "Получать push-уведомления",
                        checked = notificationsEnabled,
                        onCheckedChange = { notificationsEnabled = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Location Section
            Text(
                text = "Геолокация",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    SettingsSwitchItem(
                        icon = Icons.Default.LocationOn,
                        title = "Геолокация",
                        description = "Добавлять координаты к фото",
                        checked = locationEnabled,
                        onCheckedChange = { locationEnabled = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Photo Settings Section
            Text(
                text = "Фотографии",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    SettingsSwitchItem(
                        icon = Icons.Default.CloudUpload,
                        title = "Автозагрузка",
                        description = "Автоматически загружать фото на сервер",
                        checked = autoUploadPhotos,
                        onCheckedChange = { autoUploadPhotos = it }
                    )

                    HorizontalDivider()

                    SettingsSwitchItem(
                        icon = Icons.Default.AutoAwesome,
                        title = "AI-анализ фото",
                        description = "Показывать комментарии ИИ на карточках фото",
                        checked = aiAnalysisVisible,
                        onCheckedChange = {
                            aiAnalysisVisible = it
                            prefsManager.setAiAnalysisVisible(it)
                        }
                    )

                    // Автоодобрение — только для куратора
                    val user = prefsManager.getUser()
                    if (user?.role == com.belsi.work.data.models.UserRole.CURATOR) {
                        HorizontalDivider()
                        var autoApproveThreshold by remember { mutableStateOf(0) }
                        val thresholdLabel = when (autoApproveThreshold) {
                            0 -> "Выключено"
                            80 -> "80+ (мягкий)"
                            85 -> "85+ (средний)"
                            90 -> "90+ (строгий)"
                            95 -> "95+ (очень строгий)"
                            else -> "$autoApproveThreshold+"
                        }
                        SettingsMenuItem(
                            icon = Icons.Default.VerifiedUser,
                            title = "Автоодобрение по AI",
                            value = thresholdLabel,
                            onClick = {
                                // Циклический переключатель: 0 → 80 → 85 → 90 → 95 → 0
                                autoApproveThreshold = when (autoApproveThreshold) {
                                    0 -> 80; 80 -> 85; 85 -> 90; 90 -> 95; else -> 0
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Security Section
            Text(
                text = "Настройки",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    SettingsMenuItem(
                        icon = Icons.Default.Lock,
                        title = if (hasPassword) "Изменить пароль приложения" else "Установить пароль приложения",
                        onClick = { showPasswordDialog = true }
                    )

                    if (hasPassword) {
                        Divider()

                        SettingsMenuItem(
                            icon = Icons.Default.LockOpen,
                            title = "Удалить пароль",
                            onClick = {
                                prefsManager.removeAppPassword()
                                hasPassword = false
                                viewModel.showSnackbar("Пароль удалён")
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Reports Section
            Text(
                text = "Отчеты",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                SettingsMenuItem(
                    icon = Icons.Default.Assessment,
                    title = "Отчеты по сменам",
                    value = "Формирование отчетов в Excel и PDF",
                    onClick = { navController.navigate(com.belsi.work.presentation.navigation.AppRoute.Reports.route) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App Info Section
            Text(
                text = "О приложении",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    SettingsMenuItem(
                        icon = Icons.Default.Info,
                        title = "Версия приложения",
                        value = "${com.belsi.work.BuildConfig.VERSION_NAME} (${com.belsi.work.BuildConfig.VERSION_CODE})",
                        onClick = {
                            // Скрытый доступ к DEBUG экрану через 5 тапов
                            debugTapCount++
                        }
                    )

                    Divider()

                    SettingsMenuItem(
                        icon = Icons.Default.Security,
                        title = "Политика конфиденциальности",
                        onClick = { /* Navigate to privacy policy */ }
                    )

                    Divider()

                    SettingsMenuItem(
                        icon = Icons.Default.Article,
                        title = "Пользовательское соглашение",
                        onClick = { /* Navigate to terms */ }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Danger Zone
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column {
                    SettingsMenuItem(
                        icon = Icons.Default.Delete,
                        title = "Очистить кэш",
                        onClick = { /* Clear cache */ },
                        isDestructive = true
                    )

                    Divider()

                    SettingsMenuItem(
                        icon = Icons.Default.ExitToApp,
                        title = "Выйти из аккаунта",
                        onClick = { showLogoutDialog = true },
                        isDestructive = true
                    )
                }
            }
        }
    }

    // Диалог установки/изменения пароля
    if (showPasswordDialog) {
        var newPassword by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = {
                showPasswordDialog = false
                newPassword = ""
                confirmPassword = ""
                errorMessage = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(if (hasPassword) "Изменить пароль" else "Установить пароль")
            },
            text = {
                Column {
                    Text(
                        "Введите новый пароль для защиты приложения:",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = {
                            newPassword = it
                            errorMessage = null
                        },
                        label = { Text("Новый пароль") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            errorMessage = null
                        },
                        label = { Text("Подтвердите пароль") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when {
                            newPassword.length < 4 -> {
                                errorMessage = "Пароль должен содержать минимум 4 символа"
                            }
                            newPassword != confirmPassword -> {
                                errorMessage = "Пароли не совпадают"
                            }
                            else -> {
                                prefsManager.setAppPassword(newPassword)
                                hasPassword = true
                                showPasswordDialog = false
                                viewModel.showSnackbar("Пароль успешно установлен")
                            }
                        }
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPasswordDialog = false
                        newPassword = ""
                        confirmPassword = ""
                        errorMessage = null
                    }
                ) {
                    Text("Отмена")
                }
            }
        )
    }

    // Диалог подтверждения выхода
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text("Выход из аккаунта")
            },
            text = {
                Text("Вы уверены, что хотите выйти из аккаунта? Вам потребуется снова войти, чтобы использовать приложение.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    enabled = !isLoggingOut
                ) {
                    if (isLoggingOut) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onError
                        )
                    } else {
                        Text("Выйти")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false },
                    enabled = !isLoggingOut
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun SettingsMenuItem(
    icon: ImageVector,
    title: String,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    isDestructive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (onClick != null) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
