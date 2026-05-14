package com.belsi.work.presentation.screens.factory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.belsi.work.data.local.ActiveRoleManager
import com.belsi.work.data.models.Facility
import com.belsi.work.data.repositories.ProductionRepository
// MaterialTheme.colorScheme.primary — package-internal в WorkerMainScreen.kt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FIX(2026-05-12) BELSI 2.0.0 build14: реальный переключатель фабрик.
 *
 * Раньше: список из FactoryMockData + TODO в onClick → ничего не делал.
 * Теперь:
 *  - Список фабрик с бэка через GET /production/facilities
 *  - При выборе → ActiveRoleManager.setActiveFacility(id) → DataStore
 *  - activeFacilityId — Flow, production-экраны (Chief, Supplier) подхватят через свои VM
 */
@HiltViewModel
class FacilitySwitchViewModel @Inject constructor(
    private val productionRepo: ProductionRepository,
    private val activeRoleManager: ActiveRoleManager,
) : ViewModel() {

    private val _facilities = MutableStateFlow<List<Facility>>(emptyList())
    val facilities: StateFlow<List<Facility>> = _facilities.asStateFlow()

    val activeFacilityId: StateFlow<String?> = activeRoleManager.activeFacilityId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        viewModelScope.launch {
            _loading.value = true
            productionRepo.listFacilities().onSuccess { _facilities.value = it }
            _loading.value = false
        }
    }

    fun switchTo(facilityId: String) {
        viewModelScope.launch {
            activeRoleManager.setActiveFacility(facilityId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FactoryFacilitySwitchScreen(
    navController: NavController,
    vm: FacilitySwitchViewModel = hiltViewModel(),
) {
    val facilities by vm.facilities.collectAsState()
    val currentId by vm.activeFacilityId.collectAsState()
    val loading by vm.loading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Выбор фабрики") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text(
                "Текущая фабрика влияет на ленту смен, партии, команду и push-уведомления.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            when {
                loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                facilities.isEmpty() -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Фабрик нет", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Обратитесь к куратору для создания производственного объекта",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(facilities, key = { it.id }) { f ->
                        val active = f.id == currentId
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.switchTo(f.id)
                                    navController.popBackStack()
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                  else MaterialTheme.colorScheme.surface
                            ),
                            border = if (active)
                                androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(f.name, fontWeight = FontWeight.SemiBold)
                                        if (active) {
                                            Spacer(Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    "Активная", fontSize = 10.sp, color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        }
                                    }
                                    f.address?.let {
                                        Text(
                                            it,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (active) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
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
