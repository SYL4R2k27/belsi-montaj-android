package com.belsi.work.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.belsi.work.data.models.UserRole

/**
 * FIX(2026-05-11) BELSI 2.0.0 build3: bottom-sheet выбора активной роли.
 *
 * Брендбук раздел 06 (ecosystem): «При логине: если ролей >1 → bottom-sheet
 * "Выбери роль". Если одна — сразу в главный экран.»
 *
 * Также используется внутри RoleSwitcher (меню "⋮" в шапке).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleSelectBottomSheet(
    availableRoles: List<UserRole>,
    currentRole: UserRole?,
    onRoleSelected: (UserRole) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Выбери роль на сессию",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "У вас ${availableRoles.size} активных ролей. Переключиться можно в любой момент.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            availableRoles.forEach { role ->
                RoleRow(
                    role = role,
                    isCurrent = role == currentRole,
                    onClick = { onRoleSelected(role) },
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RoleRow(
    role: UserRole,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val (emoji, label, description) = role.brandbookTriple()
    val bg = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 22.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                description,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isCurrent) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Активна",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * Brandbook (ecosystem 04, BELSI.Команда): эмодзи + название + описание для каждой роли.
 */
private fun UserRole.brandbookTriple(): Triple<String, String, String> = when (this) {
    UserRole.INSTALLER         -> Triple("🔨", "Монтажник",          "Смены, фото, задачи на объекте")
    UserRole.FOREMAN           -> Triple("🛠", "Бригадир",            "Команда, задачи, приёмка партий")
    UserRole.COORDINATOR       -> Triple("📋", "Координатор объекта", "История объекта, запросы на материалы")
    UserRole.CURATOR           -> Triple("👑", "Куратор",             "Сквозной обзор всех доменов")
    UserRole.DRIVER            -> Triple("💼", "Водитель",            "Маршруты, доставка, фото")
    UserRole.LOGISTICIAN       -> Triple("🚛", "Логист",              "Заявки, маршруты, водители")
    UserRole.PRODUCTION_CHIEF  -> Triple("🏭", "Начальник производства","Углич, партии, простои")
    UserRole.SENIOR_WORKER     -> Triple("👥", "Старший работник",    "Группа, задачи группе")
    UserRole.WORKER            -> Triple("👷", "Работник",            "Смена, фото, простой")
    UserRole.SUPPLIER          -> Triple("📦", "Комплектатор",        "Склад, выдача материалов")
    UserRole.ENGINEER          -> Triple("📐", "Инженер",             "Чертежи, тех.задачи")
}
