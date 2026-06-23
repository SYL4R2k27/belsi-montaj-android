package com.belsi.work.presentation.screens.coordinator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
 * FIX(2026-05-05): История объекта — сквозная лента событий через все три домена.
 * Brandbook: координатор / куратор видит timeline объекта от создания партии
 * до сдачи монтажа. Каждое событие подсвечено цветом своего домена.
 *
 * FIX(2026-05-11) BELSI 2.0.0 build3: реальный backend endpoint
 * `GET /objects/{id}/timeline` агрегирует события из 8 таблиц
 * (shifts, shift_photos, shift_audit_log, delivery_requests, driver_route_points,
 *  production_batches, tasks, support_tickets). Mock остался только как fallback
 * пока история ещё пуста для объекта.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObjectHistoryScreen(
    navController: NavController,
    objectId: String,
    viewModel: ObjectHistoryViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    androidx.compose.runtime.LaunchedEffect(objectId) { viewModel.load(objectId) }
    val state by viewModel.state.collectAsState()
    // FIX(2026-05-11) BELSI 2.0.0 build7: убран mock fallback — показываем РЕАЛЬНЫЕ события
    // из /objects/{id}/timeline. Если пусто — empty state, не выдуманная история.
    val events: List<TimelineEvent> = state.events.map { it.toTimelineEvent() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("История объекта", fontSize = 16.sp,
                        fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                state.error != null -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            state.error ?: "Ошибка",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
                events.isEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.History,
                                null,
                                Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "По объекту пока нет событий",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "События появляются автоматически: создание партии, отгрузка, приёмка, начало смены, фото, отчёт.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
                else -> {
                    // Timeline — clickable
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        items(events) { e ->
                            TimelineItem(e, onClick = {
                                // FIX(2026-05-12) build17: deep-link по targetId (если есть).
                                // build15 открывал общие списки — теперь сразу на конкретный объект.
                                val tid = e.targetId
                                when (e.type) {
                                    "batch" -> if (tid != null)
                                        navController.navigate(com.belsi.work.presentation.navigation.AppRoute.BatchDetail.createRoute(tid))
                                    else
                                        navController.navigate(com.belsi.work.presentation.navigation.AppRoute.BatchList.route)
                                    "photo" -> if (tid != null)
                                        navController.navigate(com.belsi.work.presentation.navigation.AppRoute.PhotoDetail.createRoute(tid))
                                    else
                                        navController.navigate(com.belsi.work.presentation.navigation.AppRoute.CuratorPhotos.route)
                                    "shift_start", "shift_end" -> if (tid != null)
                                        navController.navigate(com.belsi.work.presentation.navigation.AppRoute.ShiftDetail.createRoute(tid))
                                    else
                                        navController.navigate(com.belsi.work.presentation.navigation.AppRoute.ShiftHistory.route)
                                    else -> {}  // delivery / route / task / ticket — пока без таргета
                                }
                            })
                        }
                    }
                }
            }
        }
    }
}

// FIX(2026-05-11) BELSI 2.0.0 build3: видимость internal — нужно ObjectHistoryViewModel
// для конверсии TimelineEventDto → TimelineEvent.
internal data class TimelineEvent(
    val time: String,
    val author: String,
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val domainColor: Color,
    // FIX(2026-05-12) BELSI 2.0.0 build15: тип для navigation
    val type: String = "other",
    // FIX(2026-05-12) build17: id цели события (batch, photo, shift) для deep-link.
    val targetId: String? = null,
)

@Composable
private fun TimelineItem(e: TimelineEvent, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick),
    ) {
        // Левая колонка — линия с точкой
        Column(
            modifier = Modifier.width(40.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .size(28.dp)
                    .background(e.domainColor, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    e.icon, contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }

        // Правая колонка — карточка события
        Card(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp).weight(1f),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.time, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = e.domainColor)
                    Spacer(Modifier.width(8.dp))
                    Text("· ${e.author}", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
                Text(e.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(e.description, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// FIX(2026-05-11) BELSI 2.0.0 build7: mockEventsFor удалён.
// История объекта строится только из реальных событий /objects/{id}/timeline.
// Пустое состояние теперь честно показывается empty-state блоком вместо выдумки.
