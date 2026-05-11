package com.belsi.work.presentation.screens.auth.roleselect

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.belsi.work.data.models.UserCapabilities
import com.belsi.work.data.models.UserDomain
import com.belsi.work.data.models.UserRole

/**
 * FIX(2026-05-05): RoleSelectScreen V2 для регистрации с большим количеством ролей.
 *
 * UX-проблема старого экрана: при 11 ролях кнопка «Продолжить» теряется на маленьких экранах.
 * Решение: Scaffold + bottomBar — кнопка ВСЕГДА закреплена внизу, не зависит от scroll.
 *
 * Логика:
 * 1. Все роли сгруппированы по доменам (4 группы).
 * 2. Можно выбрать до 3 ролей (см. UserCapabilities.MAX_ROLES_PER_USER).
 * 3. Открытые роли (installer, worker, driver) → сразу.
 *    Закрытые (curator, chief, foreman, ...) → требуется одобрение куратора (помечено).
 * 4. Кнопка «Продолжить» дизаблится при превышении лимита или при пустом выборе.
 *    Под кнопкой — индикатор «Выбрано N/3».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleSelectScreenV2(
    navController: NavController,
    onContinue: (Set<UserRole>) -> Unit = {}
) {
    var selected by remember { mutableStateOf<Set<UserRole>>(emptySet()) }
    val validation = if (selected.isEmpty()) "Выберите хотя бы одну роль"
        else UserCapabilities.validate(selected)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Выбор роли") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            // ВАЖНО: bottomBar — это Scaffold-слот, который ВСЕГДА виден снизу.
            // Кнопка «Продолжить» не съедается scroll'ом, не зависит от размера экрана.
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Индикатор количества
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 10.dp),
                    ) {
                        Text(
                            "Выбрано: ${selected.size} из ${UserCapabilities.MAX_ROLES_PER_USER}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected.size > UserCapabilities.MAX_ROLES_PER_USER)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.weight(1f))
                        if (validation != null) {
                            Text(
                                "⚠️",
                                fontSize = 14.sp,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }

                    if (validation != null) {
                        Text(
                            validation,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    // Большая кнопка «Продолжить» — фиксированный размер, ВСЕГДА видна
                    Button(
                        onClick = { onContinue(selected) },
                        enabled = validation == null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp), // важная высота — заметная кнопка
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            "Продолжить",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    ) { padding ->
        // Скроллируемая часть со списком ролей по доменам
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Кем вы работаете в BELSI?",
                    fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    "Можно выбрать до 3 ролей. Работник + Водитель + Монтажник — это типовая комбинация.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // Группируем роли по доменам, для каждого домена — заголовок + карточки
            val groups = UserRole.values().groupBy { it.domain }
            val orderedDomains = listOf(UserDomain.INSTALLATION, UserDomain.LOGISTICS, UserDomain.PRODUCTION, UserDomain.OBSERVER)

            orderedDomains.forEach { domain ->
                val rolesInDomain = groups[domain].orEmpty()
                if (rolesInDomain.isEmpty()) return@forEach

                item { DomainHeader(domain) }
                items(rolesInDomain) { role ->
                    RoleCard(
                        role = role,
                        selected = role in selected,
                        domain = domain,
                        onClick = {
                            selected = if (role in selected) selected - role
                                else selected + role
                        }
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            // Spacer внизу чтобы под bottomBar не подрезалась последняя карточка
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun DomainHeader(domain: UserDomain) {
    val color = domainColor(domain)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    ) {
        Text(domain.emoji, fontSize = 20.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            domain.title.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            letterSpacing = 1.5.sp,
        )
    }
}

@Composable
private fun RoleCard(
    role: UserRole,
    selected: Boolean,
    domain: UserDomain,
    onClick: () -> Unit,
) {
    val color = domainColor(domain)
    val closed = isRestrictedRole(role)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) color.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surface
        ),
        border = if (selected)
            androidx.compose.foundation.BorderStroke(2.dp, color) else null,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Эмоджи-иконка
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(role.emoji, fontSize = 24.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(role.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    if (closed) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFEF3C7), RoundedCornerShape(50))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                "🔒 нужно одобрение",
                                fontSize = 9.sp,
                                color = Color(0xFF92400E),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                Text(
                    role.description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            // Чекбокс
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(color, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp)) }
            } else {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                )
            }
        }
    }
}

private fun domainColor(d: UserDomain): Color = when (d) {
    UserDomain.INSTALLATION -> Color(0xFF4F46E5)  // indigo
    UserDomain.LOGISTICS -> Color(0xFF0EA5E9)     // sky
    UserDomain.PRODUCTION -> Color(0xFFD97706)    // amber
    UserDomain.OBSERVER -> Color(0xFF8B5CF6)      // violet
}

/**
 * Закрытые роли — нельзя выдать самому, нужно одобрение куратора.
 * Открытые: installer, worker, driver — эти роли пользователь может присвоить себе сам.
 */
private fun isRestrictedRole(role: UserRole): Boolean = when (role) {
    UserRole.INSTALLER, UserRole.WORKER, UserRole.DRIVER -> false
    else -> true
}
