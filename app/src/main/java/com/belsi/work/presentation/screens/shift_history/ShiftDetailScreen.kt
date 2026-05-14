package com.belsi.work.presentation.screens.shift_history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.belsi.work.data.remote.api.ShiftApi
import com.belsi.work.data.remote.api.ShiftDetailResponse
import com.belsi.work.data.remote.api.ShiftPhotoResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-12) build17 P2: ShiftDetail composable + ViewModel.
 * Раньше AppRoute.ShiftDetail существовал, но никуда не вёл (не было composable).
 * Теперь: смена → детали + фото в гриде с переходом на PhotoDetail.
 */
@HiltViewModel
class ShiftDetailViewModel @Inject constructor(
    private val shiftApi: ShiftApi,
) : ViewModel() {

    data class State(
        val loading: Boolean = false,
        val shift: ShiftDetailResponse? = null,
        val photos: List<ShiftPhotoResponse> = emptyList(),
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun load(shiftId: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val shiftResp = shiftApi.getShift(shiftId)
                val photoResp = shiftApi.getShiftPhotos(shiftId)
                if (shiftResp.isSuccessful) {
                    _state.update {
                        it.copy(
                            loading = false,
                            shift = shiftResp.body(),
                            photos = if (photoResp.isSuccessful) photoResp.body() ?: emptyList() else emptyList(),
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            loading = false,
                            error = "HTTP ${shiftResp.code()}: ${shiftResp.errorBody()?.string() ?: shiftResp.message()}",
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Ошибка загрузки смены") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftDetailScreen(
    shiftId: String,
    onBack: () -> Unit,
    onPhotoClick: (photoId: String) -> Unit,
    vm: ShiftDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(shiftId) { vm.load(shiftId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Смена") },
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
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.error != null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(state.error ?: "Ошибка", color = MaterialTheme.colorScheme.error)
            }

            state.shift != null -> {
                val s = state.shift!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Смена #${s.id.take(8)}",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Начало: ${s.start_at}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            s.end_at?.let {
                                Text("Окончание: $it", style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                "Статус: ${s.status}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            s.location?.let {
                                Text("Локация: $it", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            s.notes?.let {
                                Spacer(Modifier.height(8.dp))
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Text(
                        "Фото смены (${state.photos.size})",
                        fontWeight = FontWeight.SemiBold,
                    )

                    if (state.photos.isEmpty()) {
                        Text(
                            "Фото не загружены",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(state.photos, key = { it.id }) { p ->
                                Card(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onPhotoClick(p.id) }
                                ) {
                                    AsyncImage(
                                        model = p.photo_url,
                                        contentDescription = p.hour_label,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
