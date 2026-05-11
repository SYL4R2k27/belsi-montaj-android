package com.belsi.work.presentation.screens.factory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

/**
 * FIX(2026-05-05): Переключатель производственных площадок.
 * Brandbook: один пользователь может работать на нескольких фабриках.
 * Меняется текущая фабрика → меняется лента смен/партий/команды.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FactoryFacilitySwitchScreen(navController: NavController) {
    val facilities = FactoryMockData.facilities
    val current = facilities.firstOrNull { it.active }?.id

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Выбор фабрики") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AmberPrimary.copy(alpha = 0.1f)
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text(
                "Текущая фабрика влияет на ленту смен, партии, команду и push-уведомления.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(facilities) { f ->
                    val active = f.id == current
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // TODO: ActiveRoleManager.setActiveFacility(f.id)
                                navController.popBackStack()
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (active) AmberPrimary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        border = if (active)
                            androidx.compose.foundation.BorderStroke(2.dp, AmberPrimary) else null,
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = if (active) AmberPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(f.name, fontWeight = FontWeight.SemiBold)
                                    if (active) {
                                        Spacer(Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(AmberPrimary, RoundedCornerShape(50))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "Активная", fontSize = 10.sp, color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                                Text(f.address, fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (active) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = AmberPrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
