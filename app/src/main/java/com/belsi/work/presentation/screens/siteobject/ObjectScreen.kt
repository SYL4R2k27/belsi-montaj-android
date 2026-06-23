package com.belsi.work.presentation.screens.siteobject

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.foundation.clickable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.belsi.work.data.remote.dto.object_v3.WindowDto
import com.belsi.work.data.repositories.CabinetNode
import com.belsi.work.data.repositories.FloorNode
import com.belsi.work.presentation.components.BelsiTabBar
import com.belsi.work.presentation.components.BrandGradientHeader
import com.belsi.work.presentation.components.KpiCell
import com.belsi.work.presentation.components.SectionHeader
import com.belsi.work.presentation.components.montage.CabinetCard
import com.belsi.work.presentation.components.montage.WindowUi
import com.belsi.work.presentation.components.montage.ZoneSection
import com.belsi.work.presentation.theme.CabinetStatus
import com.belsi.work.presentation.theme.StageStatus
import com.belsi.work.data.models.UserRole
import com.belsi.work.presentation.navigation.AppRoute

private val OBJECT_TABS = listOf("Сейчас", "Этажи", "Партии", "Фото", "Контакты", "История")

/**
 * Universal ObjectScreen (модель v3) — общий паттерн для coordinator + curator.
 * Phase 2 «всё рабочее»: при отсутствии backend репозиторий отдаёт демо-дерево.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObjectScreen(
    navController: NavController,
    objectId: String,
    viewModel: ObjectViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    val role by viewModel.activeRole.collectAsState()
    val canAccrue = role == UserRole.CURATOR || role == UserRole.COORDINATOR

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Объект", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            BelsiTabBar(
                tabs = OBJECT_TABS,
                selectedIndex = ui.selectedTab,
                onSelect = viewModel::selectTab,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )

            if (ui.tree?.isDemo == true) {
                Text(
                    text = "Демо-данные · backend /v3 ещё не подключён",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }

            when {
                ui.loading -> CenterBox { CircularProgressIndicator() }
                ui.error != null -> CenterBox { Text(ui.error ?: "Ошибка") }
                else -> when (ui.selectedTab) {
                    0 -> NowTab(objectId = objectId, floors = ui.tree?.floors.orEmpty())
                    1 -> FloorsTab(
                        floors = ui.tree?.floors.orEmpty(),
                        canAccrue = canAccrue,
                        onAccrue = { cabinetId -> navController.navigate(AppRoute.WalletAccrue.createRoute(cabinetId)) },
                        onSetStatus = { cabinetId, status, comment -> viewModel.setCabinetStatus(cabinetId, status, comment) },
                    )
                    else -> CenterBox {
                        Text(
                            "«${OBJECT_TABS[ui.selectedTab]}» — в разработке (Phase 3-5)",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NowTab(objectId: String, floors: List<FloorNode>) {
    val cabinets = floors.flatMap { it.zones }.flatMap { it.cabinets }
    val windows = cabinets.flatMap { it.windows }
    val approved = windows.count {
        it.statusKarkas == "approved" && it.statusPodokonnik == "approved" && it.statusEkran == "approved"
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BrandGradientHeader(title = "Объект", subtitle = "ID: $objectId")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpiCell("${floors.size}", "этажей", modifier = Modifier.weight(1f))
            KpiCell("${cabinets.size}", "кабинетов", modifier = Modifier.weight(1f))
            KpiCell("${windows.size}", "окон", modifier = Modifier.weight(1f))
            KpiCell("$approved", "approved", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun FloorsTab(
    floors: List<FloorNode>,
    canAccrue: Boolean,
    onAccrue: (cabinetId: String) -> Unit,
    onSetStatus: (cabinetId: String, status: String, comment: String?) -> Unit,
) {
    var selected by remember { mutableStateOf<com.belsi.work.data.repositories.CabinetNode?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
    ) {
        items(floors, key = { it.floor.id }) { floorNode ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(title = "${floorNode.floor.floorNumber}-й этаж")
                floorNode.zones.forEach { zoneNode ->
                    ZoneSection(
                        zoneCode = zoneNode.zone.zoneCode,
                        cabinetCount = zoneNode.cabinets.size,
                        totalPm = zoneNode.zone.totalPm,
                    ) {
                        zoneNode.cabinets.forEach { cabNode ->
                            Box(Modifier.clickable { selected = cabNode }) {
                                CabinetCard(
                                    cabinetNumber = cabNode.cabinet.cabinetNumber,
                                    totalLengthMm = cabNode.cabinet.totalLengthMm,
                                    readyKeys = cabNode.cabinet.readyKeys(),
                                    windows = cabNode.windows.map { it.toWindowUi() },
                                    status = CabinetStatus.fromDb(cabNode.cabinet.status),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    selected?.let { node ->
        CabinetDetailSheet(
            node = node,
            onDismiss = { selected = null },
            canAccrue = canAccrue,
            onAccrue = { onAccrue(node.cabinet.id); selected = null },
            onSetStatus = { status, comment ->
                onSetStatus(node.cabinet.id, status, comment)
                selected = null
            },
        )
    }
}

private fun WindowDto.toWindowUi() = WindowUi(
    number = windowNumber,
    widthMm = widthMm,
    karkas = StageStatus.fromDb(statusKarkas),
    podokonnik = StageStatus.fromDb(statusPodokonnik),
    ekran = StageStatus.fromDb(statusEkran),
)

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
