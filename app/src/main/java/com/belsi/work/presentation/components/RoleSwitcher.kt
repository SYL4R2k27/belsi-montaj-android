package com.belsi.work.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.belsi.work.data.models.User
import com.belsi.work.data.models.UserRole

/**
 * FIX(2026-05-03): Переключатель ролей в шапке (Brandbook · Variant 1.D).
 *
 * Поведение:
 *  - Если у юзера одна capability — composable не показывается (просто меню «⋮» без подменю ролей).
 *  - Если две — в [DropdownMenu] вверху список ролей с галкой ✓ на активной и точкой ● если в другой роли есть фоновый процесс.
 *  - Тап на не-активную роль → [onRequestSwitch] с целевой ролью (выше уровнем покажется диалог-подтверждение).
 *
 * @param user текущий пользователь (для capabilities)
 * @param activeRole сейчас активная роль
 * @param onRequestSwitch вызывается когда юзер выбрал новую роль (для показа диалога-подтверждения)
 * @param backgroundProcessRoles роли в которых есть незавершённый процесс (показываем точку ●).
 *        Например: если ты сейчас Driver, а в Installer открыта смена → передай `setOf(UserRole.INSTALLER)`.
 * @param extraMenuItems дополнительные пункты меню (Профиль, Настройки, Выйти) — подаются как лямбды.
 */
@Composable
fun ToolbarRoleSwitcher(
    user: User,
    activeRole: UserRole?,
    onRequestSwitch: (UserRole) -> Unit,
    backgroundProcessRoles: Set<UserRole> = emptySet(),
    extraMenuItems: @Composable (close: () -> Unit) -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    val capabilities = user.effectiveCapabilities()

    // Точка-индикатор на иконке если в одной из других ролей есть фоновый процесс
    val showDot = backgroundProcessRoles.any { it != activeRole && it in capabilities }

    Box(modifier = Modifier.size(40.dp)) {
        Surface(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            onClick = { menuOpen = true }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Меню",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        if (showDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
            )
        }
    }

    DropdownMenu(
        expanded = menuOpen,
        onDismissRequest = { menuOpen = false },
        modifier = Modifier.widthIn(min = 240.dp)
    ) {
        // Заголовок секции (только для дуальных)
        if (capabilities.size > 1) {
            Text(
                "АКТИВНАЯ РОЛЬ",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            capabilities.forEach { role ->
                val isActive = role == activeRole
                val hasProcess = role in backgroundProcessRoles && !isActive
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(role.emoji, style = MaterialTheme.typography.titleMedium)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    role.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                if (hasProcess) {
                                    Text(
                                        "● Активный процесс",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            if (isActive) {
                                Text(
                                    "✓",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    },
                    onClick = {
                        menuOpen = false
                        if (!isActive) onRequestSwitch(role)
                    }
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        }

        // Дополнительные пункты меню от вызывающего экрана
        extraMenuItems { menuOpen = false }
    }
}

// ─── Helper: отображает текущую роль текстом перед заголовком экрана ───────

/**
 * Маленький префикс «💼 Водитель» / «🔨 Монтажник» над заголовком экрана.
 * Показывается только если у юзера 2+ capabilities (статус роли видеть полезно
 * чтобы юзер не путался в каком режиме сейчас).
 */
@Composable
fun RoleStatusPrefix(
    user: User,
    activeRole: UserRole?,
) {
    if (!user.isDualRole() || activeRole == null) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(activeRole.emoji, style = MaterialTheme.typography.labelMedium)
        Text(
            activeRole.title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp
            ),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

