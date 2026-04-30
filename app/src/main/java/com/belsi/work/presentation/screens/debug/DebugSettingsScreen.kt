package com.belsi.work.presentation.screens.debug

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.presentation.theme.belsiColors

/**
 * DEBUG Settings Screen
 *
 * Доступ: 5x тап по версии приложения в экране настроек
 *
 * Функции:
 * - Просмотр текущего токена и пользователя
 * - Очистка кэша и данных
 * - Принудительный логаут
 * - HTTP логирование (вкл/выкл)
 * - Мок данные для тестирования
 * - Информация о сборке
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSettingsScreen(
    navController: NavController,
    viewModel: DebugSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Показ сообщений
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("DEBUG Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    titleContentColor = MaterialTheme.colorScheme.onError,
                    navigationIconContentColor = MaterialTheme.colorScheme.onError
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Предупреждение
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Режим разработчика",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "Эти настройки только для отладки",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Информация о приложении
            item {
                SectionHeader("Информация")
            }

            item {
                InfoCard(
                    title = "Версия приложения",
                    value = uiState.appVersion
                )
            }

            item {
                InfoCard(
                    title = "Build Type",
                    value = if (uiState.isDebugBuild) "DEBUG" else "RELEASE"
                )
            }

            item {
                InfoCard(
                    title = "API Base URL",
                    value = uiState.apiBaseUrl
                )
            }

            // Токен и пользователь
            item {
                SectionHeader("Аутентификация")
            }

            item {
                DebugInfoCard(
                    title = "Access Token",
                    value = uiState.accessToken ?: "Нет токена",
                    icon = Icons.Default.Key,
                    onCopyClick = { viewModel.copyToClipboard(uiState.accessToken ?: "") }
                )
            }

            item {
                DebugInfoCard(
                    title = "User ID",
                    value = uiState.userId ?: "Не авторизован",
                    icon = Icons.Default.Person,
                    onCopyClick = { viewModel.copyToClipboard(uiState.userId ?: "") }
                )
            }

            item {
                DebugInfoCard(
                    title = "Роль",
                    value = uiState.userRole ?: "Не установлена",
                    icon = Icons.Default.Badge
                )
            }

            // Настройки логирования
            item {
                SectionHeader("Логирование")
            }

            item {
                DebugSwitchCard(
                    title = "HTTP Logging",
                    subtitle = "Логировать все HTTP запросы/ответы",
                    checked = uiState.httpLoggingEnabled,
                    onCheckedChange = { viewModel.toggleHttpLogging(it) }
                )
            }

            item {
                DebugSwitchCard(
                    title = "Verbose Logs",
                    subtitle = "Подробные логи в Logcat",
                    checked = uiState.verboseLogsEnabled,
                    onCheckedChange = { viewModel.toggleVerboseLogs(it) }
                )
            }

            // Действия
            item {
                SectionHeader("Действия")
            }

            item {
                DebugActionCard(
                    title = "Очистить кэш",
                    subtitle = "Удалить все кэшированные данные",
                    icon = Icons.Default.DeleteSweep,
                    color = MaterialTheme.belsiColors.warning,
                    onClick = { viewModel.clearCache() }
                )
            }

            item {
                DebugActionCard(
                    title = "Очистить все данные",
                    subtitle = "Сбросить приложение (без выхода)",
                    icon = Icons.Default.RestartAlt,
                    color = MaterialTheme.colorScheme.error,
                    onClick = { viewModel.clearAllData() }
                )
            }

            item {
                DebugActionCard(
                    title = "Принудительный логаут",
                    subtitle = "Выйти и очистить сессию",
                    icon = Icons.Default.Logout,
                    color = MaterialTheme.colorScheme.error,
                    onClick = {
                        viewModel.forceLogout()
                        navController.navigate("auth_phone") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            // Мок данные
            item {
                SectionHeader("Тестирование")
            }

            item {
                DebugActionCard(
                    title = "Загрузить мок смены",
                    subtitle = "Создать тестовую смену",
                    icon = Icons.Default.Schedule,
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { viewModel.loadMockShift() }
                )
            }

            item {
                DebugActionCard(
                    title = "Загрузить мок фото",
                    subtitle = "Создать тестовые фотографии",
                    icon = Icons.Default.CameraAlt,
                    color = MaterialTheme.belsiColors.info,
                    onClick = { viewModel.loadMockPhotos() }
                )
            }

            item {
                DebugActionCard(
                    title = "Загрузить мок чат",
                    subtitle = "Создать тестовые сообщения",
                    icon = Icons.Default.Chat,
                    color = MaterialTheme.belsiColors.success,
                    onClick = { viewModel.loadMockChat() }
                )
            }

            // Статистика
            item {
                SectionHeader("Статистика")
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MiniStatCard(
                        modifier = Modifier.weight(1f),
                        title = "Запросов",
                        value = uiState.totalApiCalls.toString()
                    )
                    MiniStatCard(
                        modifier = Modifier.weight(1f),
                        title = "Ошибок",
                        value = uiState.totalApiErrors.toString()
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun InfoCard(
    title: String,
    value: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun DebugInfoCard(
    title: String,
    value: String,
    icon: ImageVector,
    onCopyClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth()
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
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = value.take(40) + if (value.length > 40) "..." else "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            if (onCopyClick != null && value.isNotBlank() && value != "Нет токена" && value != "Не авторизован") {
                IconButton(onClick = onCopyClick) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Копировать",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DebugSwitchCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun DebugActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
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
                tint = color,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = color
            )
        }
    }
}

@Composable
private fun MiniStatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
