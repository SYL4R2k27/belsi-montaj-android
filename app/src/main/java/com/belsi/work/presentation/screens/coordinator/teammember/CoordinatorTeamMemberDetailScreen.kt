package com.belsi.work.presentation.screens.coordinator.teammember

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.belsi.work.data.remote.api.CoordinatorApi
import com.belsi.work.data.remote.dto.coordinator.CoordinatorTeamMemberDetailDto
import com.belsi.work.data.remote.dto.coordinator.TeamMemberPhotoDto
import com.belsi.work.data.remote.dto.coordinator.TeamMemberShiftDto
import com.belsi.work.data.remote.dto.coordinator.TeamMemberTaskDto
import com.belsi.work.presentation.navigation.AppRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-12) build18 P2: детальная карточка участника команды координатора.
 *
 * Раньше: TeamMemberCard в CoordinatorMainScreen не имела onClick — координатор не мог
 * открыть детали по своим работникам. Теперь — кликабельна, открывает этот экран
 * через GET /coordinator/team/{userId}.
 */
@HiltViewModel
class CoordinatorTeamMemberDetailViewModel @Inject constructor(
    private val coordinatorApi: CoordinatorApi,
) : ViewModel() {

    data class State(
        val loading: Boolean = false,
        val detail: CoordinatorTeamMemberDetailDto? = null,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun load(userId: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val resp = coordinatorApi.getTeamMemberDetail(userId)
                if (resp.isSuccessful) {
                    _state.update { it.copy(loading = false, detail = resp.body()) }
                } else {
                    _state.update {
                        it.copy(
                            loading = false,
                            error = "HTTP ${resp.code()}: ${resp.errorBody()?.string() ?: resp.message()}",
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Ошибка загрузки") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoordinatorTeamMemberDetailScreen(
    userId: String,
    onBack: () -> Unit,
    onPhotoClick: (photoId: String) -> Unit,
    onShiftClick: (shiftId: String) -> Unit,
    vm: CoordinatorTeamMemberDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(userId) { vm.load(userId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.detail?.user?.fullName ?: state.detail?.user?.phone ?: "Участник команды") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.error != null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) { Text(state.error ?: "Ошибка", color = MaterialTheme.colorScheme.error) }

            state.detail != null -> {
                val d = state.detail!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { UserCard(d) }
                    if (d.activeShift != null) {
                        item {
                            ActiveShiftCard(d.activeShift!!) { sid ->
                                onShiftClick(sid)
                            }
                        }
                    }
                    item { StatRow(photosToday = d.photosToday, openTasks = d.openTasks.size, recentShifts = d.recentShifts.size) }

                    if (d.openTasks.isNotEmpty()) {
                        item { SectionTitle("Открытые задачи") }
                        items(d.openTasks, key = { it.id }) { t -> TaskRow(t) }
                    }

                    if (d.recentPhotos.isNotEmpty()) {
                        item { SectionTitle("Последние фото (${d.recentPhotos.size})") }
                        item {
                            PhotosGridRow(photos = d.recentPhotos, onPhotoClick = onPhotoClick)
                        }
                    }

                    if (d.recentShifts.isNotEmpty()) {
                        item { SectionTitle("Последние смены") }
                        items(d.recentShifts, key = { it.id }) { s ->
                            ShiftRow(s) { onShiftClick(s.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserCard(d: CoordinatorTeamMemberDetailDto) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                d.user.fullName ?: d.user.phone,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(d.user.phone, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            Spacer(Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            ) {
                Text(
                    when (d.user.role.lowercase()) {
                        "foreman" -> "Бригадир"
                        "installer" -> "Монтажник"
                        "coordinator" -> "Координатор"
                        "curator" -> "Куратор"
                        else -> d.user.role
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ActiveShiftCard(shift: TeamMemberShiftDto, onClick: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(shift.id) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.PlayCircle, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Активная смена", fontSize = 11.sp, color = Color.Gray)
                Text("Начало: ${shift.startAt ?: "—"}", fontWeight = FontWeight.SemiBold)
                Text("Статус: ${shift.status}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StatRow(photosToday: Int, openTasks: Int, recentShifts: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatChip(label = "Фото сегодня", value = photosToday.toString(), modifier = Modifier.weight(1f))
        StatChip(label = "Задач открыто", value = openTasks.toString(), modifier = Modifier.weight(1f))
        StatChip(label = "Смен (10)", value = recentShifts.toString(), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
private fun SectionTitle(t: String) {
    Text(t, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.Gray)
}

@Composable
private fun TaskRow(t: TeamMemberTaskDto) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                when (t.status) {
                    "in_progress" -> Icons.Default.PlayCircle
                    else -> Icons.Default.CheckCircle
                },
                null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, fontWeight = FontWeight.Medium)
                Text(
                    "Статус: ${t.status} · Приоритет: ${t.priority}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }
        }
    }
}

@Composable
private fun PhotosGridRow(photos: List<TeamMemberPhotoDto>, onPhotoClick: (String) -> Unit) {
    LazyHorizontalGrid(
        rows = GridCells.Fixed(1),
        modifier = Modifier.fillMaxWidth().height(120.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(photos, key = { it.id }) { p ->
            Card(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onPhotoClick(p.id) },
            ) {
                AsyncImage(
                    model = p.photoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

@Composable
private fun ShiftRow(shift: TeamMemberShiftDto, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Schedule, null, tint = Color.Gray)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${shift.startAt ?: "—"}", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Text(
                    "${shift.durationHours?.let { "%.1f ч".format(it) } ?: "—"} · ${shift.status}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }
        }
    }
}
