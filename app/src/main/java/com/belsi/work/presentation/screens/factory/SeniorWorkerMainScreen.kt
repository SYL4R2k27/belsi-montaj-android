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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.data.models.BrigadeMember

/**
 * FIX(2026-05-06): SeniorWorkerMainScreen — подключён к API.
 * Загружает свою бригаду через GET /production/brigades/mine
 * и членов через GET /production/brigades/{id}/members.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeniorWorkerMainScreen(
    navController: NavController,
    viewModel: SeniorWorkerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val brigade = state.brigade

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column {
                    Text("Старший работник", fontWeight = FontWeight.Bold)
                    Text(
                        brigade?.name ?: "Бригада не назначена",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } },
                actions = {
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AmberPrimary.copy(alpha = 0.1f))
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
                        Text("👷", fontSize = 56.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Бригада не назначена",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Обратитесь к начальнику производства",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

            // Стат-карточки
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniStat("В бригаде", "${brigade.membersCount}", AmberPrimary)
                MiniStat("На смене", "${brigade.activeCount}", Color(0xFF10B981))
                MiniStat("Простой", "${brigade.idleCount}", Color(0xFFF43F5E))
            }

            HorizontalDivider()
            Text(
                "Состав бригады",
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Аватар
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(AmberPrimary.copy(alpha = 0.2f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    member.fullName.take(1),
                    fontWeight = FontWeight.Bold,
                    color = AmberPrimary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(member.fullName, fontWeight = FontWeight.SemiBold)
                val (statusText, statusColor) = memberStatus(member)
                Text(statusText, fontSize = 12.sp, color = statusColor)
                if (member.idleReason != null) {
                    Text(
                        "Причина: ${member.idleReason}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box(modifier = Modifier.size(10.dp).background(memberStatus(member).second, RoundedCornerShape(5.dp)))
        }
    }
}

private fun memberStatus(member: BrigadeMember): Pair<String, Color> = when {
    member.onIdle -> "Простой" to Color(0xFFF43F5E)
    member.onPause -> "Перерыв" to Color(0xFFFBBF24)
    member.isOnShift -> "На смене" to Color(0xFF10B981)
    else -> "Не на смене" to Color.Gray
}

@Composable
private fun RowScope.MiniStat(label: String, value: String, color: Color) {
    Card(
        modifier = Modifier.weight(1f),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
