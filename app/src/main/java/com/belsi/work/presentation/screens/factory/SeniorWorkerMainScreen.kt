package com.belsi.work.presentation.screens.factory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.data.models.BrigadeMember
import com.belsi.work.presentation.components.role.RoleEmptyState
import com.belsi.work.presentation.components.role.RoleStatCard
import com.belsi.work.presentation.components.role.RoleStatusDot
import com.belsi.work.presentation.components.role.Severity
import com.belsi.work.presentation.components.role.colors

/**
 * SeniorWorkerMainScreen — дашборд старшего работника.
 *
 * FIX(2026-05-12) build19 hotfix: переведён на единую дизайн-систему
 * (RoleStatCard / RoleStatusDot / Severity). Удалены 5 hardcoded Color(0xFF...).
 * Статусы: на смене=SUCCESS, перерыв=WARNING, простой=ERROR.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeniorWorkerMainScreen(
    navController: NavController,
    viewModel: SeniorWorkerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    @Suppress("DEPRECATION")
    val brigade = state.selectedBrigade

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column {
                    Text("Старший работник", fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            state.brigades.isEmpty() -> "Бригада не назначена"
                            state.brigades.size == 1 -> brigade?.name ?: ""
                            else -> "${brigade?.name ?: "—"} (${state.brigades.size} бригад)"
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } },
                actions = {
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            if (state.loading && state.members.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            if (brigade == null) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RoleEmptyState(
                            emoji = "👷",
                            title = "Бригада не назначена",
                            subtitle = "Обратитесь к начальнику производства",
                        )
                        if (state.error != null) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                state.error ?: "",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
                return@Column
            }

            // FIX(2026-05-14) BELSI 2.0.1: переключатель между бригадами (если >1)
            if (state.brigades.size > 1) {
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.brigades, key = { it.id }) { b ->
                        val isSelected = b.id == state.selectedBrigadeId
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectBrigade(b.id) },
                            label = {
                                Text("${b.name} (${b.activeCount}/${b.membersCount})")
                            },
                        )
                    }
                }
                HorizontalDivider()
            }

            // Стат-карточки текущей бригады
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RoleStatCard("В бригаде", "${brigade.membersCount}", Severity.PRIMARY, modifier = Modifier.weight(1f))
                RoleStatCard("На смене",  "${brigade.activeCount}", Severity.SUCCESS, modifier = Modifier.weight(1f))
                RoleStatCard("Простой",   "${brigade.idleCount}",   Severity.ERROR,   modifier = Modifier.weight(1f))
            }

            HorizontalDivider()
            Text(
                if (state.brigades.size > 1) "Состав «${brigade.name}»" else "Состав бригады",
                modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.members) { member ->
                    BrigadeMemberCard(member)
                }
            }
        }
    }
}

@Composable
private fun BrigadeMemberCard(member: BrigadeMember) {
    val severity = memberSeverity(member)
    val (statusFg, _) = severity.colors()
    val statusText = memberStatusText(member)
    val primaryColor = MaterialTheme.colorScheme.primary

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Аватар — primary-цвет фирменный
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(primaryColor.copy(alpha = 0.15f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    member.fullName.take(1),
                    fontWeight = FontWeight.Bold,
                    color = primaryColor,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(member.fullName, fontWeight = FontWeight.SemiBold)
                Text(statusText, fontSize = 12.sp, color = statusFg)
                if (member.idleReason != null) {
                    Text(
                        "Причина: ${member.idleReason}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            RoleStatusDot(severity = severity)
        }
    }
}

private fun memberStatusText(member: BrigadeMember): String = when {
    member.onIdle -> "Простой"
    member.onPause -> "Перерыв"
    member.isOnShift -> "На смене"
    else -> "Не на смене"
}

private fun memberSeverity(member: BrigadeMember): Severity = when {
    member.onIdle -> Severity.ERROR
    member.onPause -> Severity.WARNING
    member.isOnShift -> Severity.SUCCESS
    else -> Severity.NEUTRAL
}
