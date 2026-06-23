package com.belsi.work.presentation.screens.factory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.belsi.work.data.models.BrigadeMember
import com.belsi.work.data.repositories.ProductionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-12) BELSI 2.0.0 build14: вид простоев членов бригады для Старшего работника.
 *
 * Раньше SeniorMain «Простои» — это FactoryIdleReasonsScreen (форма «зафиксировать СВОЙ простой»),
 * что нелогично для бригадира. Теперь — реальный мониторинг бригады: кто на паузе, кто в простое,
 * с причинами и сколько времени.
 *
 * Источник: GET /production/brigades/mine → GET /production/brigades/{id}/members
 * (BrigadeMember содержит status, current_pause_reason, current_break_started_at).
 * Live polling 30 сек.
 */
@HiltViewModel
class SeniorWorkerIdleViewModel @Inject constructor(
    private val repo: ProductionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    data class State(
        val loading: Boolean = false,
        val members: List<BrigadeMember> = emptyList(),
        val brigadeName: String? = null,
        val error: String? = null,
    )

    init {
        load()
        // Live polling 30s
        viewModelScope.launch {
            while (true) {
                delay(30_000L)
                load(silent = true)
            }
        }
    }

    fun load(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _state.value = _state.value.copy(loading = true, error = null)
            repo.getMyBrigade().onSuccess { brigade ->
                if (brigade == null) {
                    _state.value = State(loading = false, brigadeName = null)
                    return@onSuccess
                }
                repo.getBrigadeMembers(brigade.id).onSuccess { members ->
                    _state.value = State(
                        loading = false,
                        members = members,
                        brigadeName = brigade.name,
                    )
                }.onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
            }.onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
        }
    }
}

@Composable
fun SeniorWorkerIdleScreen(
    navController: NavController,
    vm: SeniorWorkerIdleViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()

    val idle = state.members.filter { it.onIdle }
    val paused = state.members.filter { it.onPause && !it.onIdle }
    val onShift = state.members.filter { it.isOnShift && !it.onPause && !it.onIdle }
    val offline = state.members.filter { !it.isOnShift }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = Color(0xFFFEF3C7)) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    state.brigadeName?.let { "Простои · $it" } ?: "Простои бригады",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Text(
                    "🟢 ${onShift.size} работают · ⏸ ${paused.size} перерыв · ⚠ ${idle.size} простой · ⚫ ${offline.size} off",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when {
            state.loading && state.members.isEmpty() -> Box(
                Modifier.fillMaxSize(), contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.brigadeName == null -> Box(
                Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Бригада не назначена", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Обратитесь к начальнику производства",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.members.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center,
            ) { Text("В бригаде пока нет работников") }

            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (idle.isNotEmpty()) {
                    item { SectionLabel("⚠ В ПРОСТОЕ — требуют внимания", Color(0xFFEF4444)) }
                    items(idle, key = { it.userId }) { MemberRow(it, accent = Color(0xFFEF4444)) }
                }
                if (paused.isNotEmpty()) {
                    item { SectionLabel("⏸ НА ПЕРЕРЫВЕ", Color(0xFFFBBF24)) }
                    items(paused, key = { it.userId }) { MemberRow(it, accent = Color(0xFFFBBF24)) }
                }
                if (onShift.isNotEmpty()) {
                    item { SectionLabel("🟢 РАБОТАЮТ", Color(0xFF10B981)) }
                    items(onShift, key = { it.userId }) { MemberRow(it, accent = Color(0xFF10B981)) }
                }
                if (offline.isNotEmpty()) {
                    item { SectionLabel("⚫ НЕ НА СМЕНЕ", Color(0xFF94A3B8)) }
                    items(offline, key = { it.userId }) { MemberRow(it, accent = Color(0xFF94A3B8)) }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, color: Color) {
    Text(
        text,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
        style = MaterialTheme.typography.labelMedium.copy(
            letterSpacing = 0.6.sp,
            fontWeight = FontWeight.Bold,
        ),
        color = color,
    )
}

@Composable
private fun MemberRow(member: BrigadeMember, accent: Color) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    member.fullName.take(2).uppercase(),
                    color = accent,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(member.fullName, fontWeight = FontWeight.SemiBold)
                Row {
                    Text(
                        member.roleInBrigade,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    member.idleReason?.let {
                        Text(
                            " · $it",
                            fontSize = 11.sp,
                            color = accent,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
