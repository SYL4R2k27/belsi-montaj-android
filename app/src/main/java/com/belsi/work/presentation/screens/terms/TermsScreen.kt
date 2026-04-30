package com.belsi.work.presentation.screens.terms

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.presentation.navigation.AppRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsScreen(
    navController: NavController,
    viewModel: TermsViewModel = hiltViewModel()
) {
    val acceptedTOS by viewModel.acceptedTOS.collectAsState()
    val acceptedPrivacy by viewModel.acceptedPrivacy.collectAsState()
    val acceptedEULA by viewModel.acceptedEULA.collectAsState()

    val canContinue = acceptedTOS && acceptedPrivacy && acceptedEULA

    var showTOS by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showEULA by remember { mutableStateOf(false) }

    // Navigation — после принятия условий на выбор роли (для новых пользователей)
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is TermsNavigationEvent.NavigateToRoleSelect -> {
                    navController.navigate(AppRoute.RoleSelect.route) {
                        popUpTo(AppRoute.Terms.route) { inclusive = true }
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1C1B1F),
                        Color(0xFF2D1B4E)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Header
            Text(
                text = "Условия и положения",
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Ознакомьтесь: ~5 минут",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Terms Long Text Card
            TermsContentCard()

            Spacer(modifier = Modifier.height(24.dp))

            // Checkbox rows
            CheckRow(
                title = "Принимаю Условия использования",
                isChecked = acceptedTOS,
                onToggle = { viewModel.toggleTOS() },
                onViewDocument = { showTOS = true }
            )
            Spacer(modifier = Modifier.height(12.dp))

            CheckRow(
                title = "Принимаю Политику конфиденциальности",
                isChecked = acceptedPrivacy,
                onToggle = { viewModel.togglePrivacy() },
                onViewDocument = { showPrivacy = true }
            )
            Spacer(modifier = Modifier.height(12.dp))

            CheckRow(
                title = "Принимаю Лицензионное соглашение",
                isChecked = acceptedEULA,
                onToggle = { viewModel.toggleEULA() },
                onViewDocument = { showEULA = true }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Continue button
            val buttonColor by animateColorAsState(
                targetValue = if (canContinue) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f),
                label = "buttonColor"
            )

            Button(
                onClick = { viewModel.acceptAndProceed() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = canContinue,
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    contentColor = Color.White,
                    disabledContainerColor = Color.White.copy(alpha = 0.15f),
                    disabledContentColor = Color.White.copy(alpha = 0.4f)
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    "Продолжить",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    // Bottom Sheets for documents
    if (showTOS) {
        DocumentSheet(
            title = "Условия использования",
            text = TermsText.TOS,
            onDismiss = { showTOS = false }
        )
    }
    if (showPrivacy) {
        DocumentSheet(
            title = "Политика конфиденциальности",
            text = TermsText.PRIVACY,
            onDismiss = { showPrivacy = false }
        )
    }
    if (showEULA) {
        DocumentSheet(
            title = "Лицензионное соглашение",
            text = TermsText.EULA,
            onDismiss = { showEULA = false }
        )
    }
}

@Composable
private fun TermsContentCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Условия использования",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(16.dp))

            BulletPoint(1, "Использование сервиса:",
                "BELSI.Work предоставляет инструменты учёта рабочего времени и фото-отчётности. Используйте сервис только в законных целях.")
            Spacer(modifier = Modifier.height(12.dp))

            BulletPoint(2, "Обязанности пользователя:",
                "Храните конфиденциальность учётной записи и пароля. Вы отвечаете за действия, совершённые с вашей учётной записью.")
            Spacer(modifier = Modifier.height(12.dp))

            BulletPoint(3, "Точность данных:",
                "Предоставляйте точные и полные данные, включая учёт времени и фотоотчётов.")
            Spacer(modifier = Modifier.height(12.dp))

            BulletPoint(4, "Интеллектуальная собственность:",
                "Контент и функциональность BELSI.Work защищены законами об авторском праве.")
            Spacer(modifier = Modifier.height(12.dp))

            BulletPoint(5, "Конфиденциальность:",
                "Ознакомьтесь с Политикой конфиденциальности, чтобы понять, как мы обрабатываем персональные данные.")
            Spacer(modifier = Modifier.height(12.dp))

            BulletPoint(6, "Изменения условий:",
                "Мы можем обновлять условия — продолжая пользоваться сервисом, вы принимаете изменения.")
            Spacer(modifier = Modifier.height(12.dp))

            BulletPoint(7, "Прекращение использования:",
                "Доступ может быть приостановлен при нарушении условий.")
        }
    }
}

@Composable
private fun BulletPoint(number: Int, title: String, text: String) {
    Column {
        Text(
            text = "$number. $title",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun CheckRow(
    title: String,
    isChecked: Boolean,
    onToggle: () -> Unit,
    onViewDocument: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f),
        animationSpec = spring(),
        label = "checkRowBg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(bgColor)
            .clickable { onToggle() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Checkbox icon
        Icon(
            imageVector = if (isChecked) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = if (isChecked) "Принято" else "Не принято",
            tint = if (isChecked) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(28.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        IconButton(
            onClick = onViewDocument,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Посмотреть документ",
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentSheet(
    title: String,
    text: String,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF2D2B30),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Закрыть")
            }
        }
    }
}

private object TermsText {
    val TOS = """
Полный текст «Условия использования».

BELSI.Work предоставляет платформу для учёта рабочего времени, управления фотоотчётами и координации команд монтажников.

Используя наш сервис, вы соглашаетесь:
• Предоставлять точную информацию о выполненной работе
• Соблюдать правила безопасности на рабочих объектах
• Не нарушать авторские права при использовании сервиса
• Использовать сервис только в законных целях

Мы оставляем за собой право изменять эти условия в любое время. Продолжая использовать сервис после изменений, вы автоматически принимаете обновлённые условия.
    """.trimIndent()

    val PRIVACY = """
Полный текст «Политика конфиденциальности». Описание обработки персональных данных и сроков хранения.

BELSI.Work серьёзно относится к защите ваших персональных данных.

Мы собираем:
• Контактную информацию (телефон, email)
• Данные о рабочих сменах и отработанном времени
• Геолокацию во время активной смены
• Фотографии рабочих объектов

Мы используем эти данные для:
• Учёта рабочего времени и расчёта оплаты
• Контроля качества выполненных работ
• Улучшения нашего сервиса
• Выполнения юридических обязательств

Ваши данные хранятся в защищённых системах и не передаются третьим лицам без вашего согласия, за исключением случаев, предусмотренных законом.
    """.trimIndent()

    val EULA = """
Полный текст «Лицензионное соглашение пользователя (EULA)».

Это лицензионное соглашение регулирует использование мобильного приложения BELSI.Work.

Предоставление лицензии:
Мы предоставляем вам ограниченную, неисключительную, непередаваемую лицензию на использование приложения BELSI.Work на ваших личных устройствах.

Ограничения:
Вы НЕ можете:
• Копировать, модифицировать или создавать производные работы на основе приложения
• Осуществлять обратную разработку, декомпиляцию или дизассемблирование
• Сдавать в аренду, передавать или распространять приложение
• Удалять уведомления об авторских правах

Интеллектуальная собственность:
Все права на приложение, включая код, дизайн и контент, принадлежат BELSI.

Прекращение действия:
Мы можем прекратить действие вашей лицензии в случае нарушения условий этого соглашения.
    """.trimIndent()
}
