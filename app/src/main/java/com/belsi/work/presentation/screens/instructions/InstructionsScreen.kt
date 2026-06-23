package com.belsi.work.presentation.screens.instructions

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.presentation.navigation.AppRoute
import com.belsi.work.presentation.theme.belsiColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructionsScreen(
    navController: NavController,
    viewModel: InstructionsViewModel = hiltViewModel()
) {
    val acknowledgedSteps by viewModel.acknowledgedSteps.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val isAllDone = progress >= 0.999f

    var showHintFor by remember { mutableStateOf<Instruction?>(null) }
    var showNotReadyAlert by remember { mutableStateOf(false) }

    // Navigation
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is InstructionsNavigationEvent.NavigateToMain -> {
                    navController.navigate(AppRoute.Main.route) {
                        popUpTo(AppRoute.AuthPhone.route) { inclusive = true }
                    }
                }
                is InstructionsNavigationEvent.NavigateToForemanMain -> {
                    navController.navigate(AppRoute.ForemanMain.route) {
                        popUpTo(AppRoute.AuthPhone.route) { inclusive = true }
                    }
                }
                is InstructionsNavigationEvent.NavigateToCoordinatorMain -> {
                    navController.navigate(AppRoute.CoordinatorMain.route) {
                        popUpTo(AppRoute.AuthPhone.route) { inclusive = true }
                    }
                }
                is InstructionsNavigationEvent.NavigateToCuratorMain -> {
                    navController.navigate(AppRoute.CuratorMain.route) {
                        popUpTo(AppRoute.AuthPhone.route) { inclusive = true }
                    }
                }
                is InstructionsNavigationEvent.NavigateToInstallerInvite -> {
                    navController.navigate(AppRoute.InstallerInvite.route) {
                        popUpTo(AppRoute.AuthPhone.route) { inclusive = true }
                    }
                }
            }
        }
    }

    // Alert dialog
    if (showNotReadyAlert) {
        AlertDialog(
            onDismissRequest = { showNotReadyAlert = false },
            title = { Text("Подготовка не завершена") },
            text = { Text("Отметьте все пункты чек-листа перед началом рабочей смены.") },
            confirmButton = {
                TextButton(onClick = { showNotReadyAlert = false }) {
                    Text("Ок")
                }
            }
        )
    }

    // Hint bottom sheet
    if (showHintFor != null) {
        InstructionHintSheet(
            instruction = showHintFor!!,
            onDismiss = { showHintFor = null }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1B1F))
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Header
            Text(
                text = "Подготовка к смене",
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Перед началом работы ознакомься с правилами и убедись, что всё готово.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Work Rules Card
            WorkRulesCard()

            Spacer(modifier = Modifier.height(24.dp))

            // Checklist Card
            ChecklistCard(
                progress = progress,
                acknowledgedSteps = acknowledgedSteps,
                onToggle = { viewModel.toggleInstruction(it) },
                onShowHint = { showHintFor = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Continue Button
            Button(
                onClick = {
                    if (isAllDone) {
                        viewModel.proceedToMain()
                    } else {
                        showNotReadyAlert = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAllDone) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    text = if (isAllDone) "Перейти к смене" else "Отметьте все пункты",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (isAllDone) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun WorkRulesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.08f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.belsiColors.warning,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Важно знать перед началом",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))

            RuleSection(
                icon = Icons.Default.AccountBalanceWallet,
                iconColor = MaterialTheme.belsiColors.success,
                title = "Оплата труда",
                items = listOf(
                    "Работа оплачивается почасово за фактически отработанное время",
                    "После сдачи смены — 2 часа на приёмку по качеству",
                    "Оплата начисляется после одобрения куратором"
                )
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(12.dp))

            RuleSection(
                icon = Icons.Default.Coffee,
                iconColor = Color(0xFF8D6E63),
                title = "Перекуры",
                items = listOf(
                    "10 минут каждые 2 часа непрерывной работы",
                    "Перекуры не оплачиваются — ставь таймер на паузу",
                    "Превышение времени перекура фиксируется системой"
                )
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(12.dp))

            RuleSection(
                icon = Icons.Default.CameraAlt,
                iconColor = MaterialTheme.belsiColors.info,
                title = "Контроль качества",
                items = listOf(
                    "GPS-трекинг во время активной смены",
                    "Фото каждый час — обязательное требование",
                    "Фото должны быть чёткими, объект полностью в кадре"
                )
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(12.dp))

            RuleSection(
                icon = Icons.Default.CheckCircle,
                iconColor = MaterialTheme.belsiColors.success,
                title = "Самоконтроль перед отправкой",
                items = listOf(
                    "Проверь чёткость всех фото",
                    "Убедись что время отчёта совпадает с фактическим",
                    "Заполни все обязательные поля"
                )
            )
        }
    }
}

@Composable
private fun RuleSection(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    items: List<String>
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier.padding(start = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEach { item ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 7.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.4f))
                    )
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChecklistCard(
    progress: Float,
    acknowledgedSteps: Set<Instruction>,
    onToggle: (Instruction) -> Unit,
    onShowHint: (Instruction) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.08f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Progress header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Готовность к смене",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.1f)
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(12.dp))

            // Checklist items
            Instruction.entries.forEach { step ->
                InstructionCheckRow(
                    instruction = step,
                    isChecked = acknowledgedSteps.contains(step),
                    onToggle = { onToggle(step) },
                    onShowHint = { onShowHint(step) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun InstructionCheckRow(
    instruction: Instruction,
    isChecked: Boolean,
    onToggle: () -> Unit,
    onShowHint: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
        animationSpec = spring(),
        label = "checkBg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(bgColor)
            .clickable { onToggle() }
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Checkbox
        Icon(
            imageVector = if (isChecked) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = if (isChecked) "Отмечено" else "Не отмечено",
            tint = if (isChecked) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Text
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = instruction.title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = instruction.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }

        // Info button
        IconButton(
            onClick = onShowHint,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "Подробнее",
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstructionHintSheet(
    instruction: Instruction,
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
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = instruction.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = instruction.title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = instruction.hintText,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Понятно")
            }
        }
    }
}
