package com.belsi.work.presentation.screens.curator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.belsi.work.data.remote.dto.curator.CuratorPhotoDto
import com.belsi.work.presentation.components.AiBubble
import com.belsi.work.presentation.components.SectionHeader
import com.belsi.work.presentation.navigation.AppRoute
import com.belsi.work.presentation.theme.belsiColors

/**
 * Куратор · таб «AI» (мок 4.4) — на реальных данных:
 *  - AI-сводка дня (headline + summary + рекомендации) — CuratorViewModel.aiSummary;
 *  - фото AI-ранжирование «худшие первыми» по ai_score (asc) — реальные фото.
 *
 * Прогресс по 3 этапам (каркас/подоконник/экран) по объектам появится после импорта
 * замеров (v3) — здесь пока не показываем, чтобы не было фейка.
 */
@Composable
fun CuratorAiTab(
    viewModel: CuratorViewModel,
    navController: NavController,
) {
    val aiSummary by viewModel.aiSummary.collectAsState()
    val photos by viewModel.photos.collectAsState()
    val aiVm: CuratorAiViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val progress by aiVm.progress.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadAiSummary() }

    // худшие первыми: только с AI-оценкой, по возрастанию score
    val worstFirst = photos.filter { it.aiScore != null }.sortedBy { it.aiScore }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        aiSummary?.let { s ->
            item {
                AiBubble(
                    text = (listOf(s.headline, s.summary).filter { it.isNotBlank() }.joinToString(" · ")),
                    label = "AI · сводка дня · LIVE",
                )
            }
            if (s.recommendations.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder(),
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Рекомендации AI", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            s.recommendations.forEach { r -> Text("• $r", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = "Фото · AI-ранжирование",
                trailing = { Text("худшие первыми", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
        }

        if (worstFirst.isEmpty()) {
            item { Text("Нет фото с AI-оценкой", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(worstFirst.take(20), key = { it.id }) { photo ->
                WorstFirstRow(photo) { navController.navigate(AppRoute.CuratorPhotos.route) }
            }
        }

        item {
            SectionHeader(
                title = "Прогресс по объектам · этапы",
                trailing = { Text("🔨 🪵 🛡", style = MaterialTheme.typography.labelSmall) },
            )
        }
        if (progress.isEmpty()) {
            item { Text("Нет данных по этапам (импорт замеров не выполнен).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(progress, key = { it.objectId }) { p -> ObjectProgressRow(p) }
        }
    }
}

@Composable
private fun ObjectProgressRow(p: com.belsi.work.data.remote.dto.object_v3.ObjectProgressDto) {
    val belsi = MaterialTheme.belsiColors
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(p.name.ifBlank { "Объект" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("${p.donePercent}% · ${p.done}/${p.total} каб", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // полоса: 🟢 закончено + 🟡 начато
        Box(Modifier.fillMaxWidth().height(8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Row(Modifier.fillMaxWidth()) {
                if (p.doneFraction > 0f) Box(Modifier.fillMaxWidth(p.doneFraction).height(8.dp).background(belsi.success))
                if (p.inProgressFraction > 0f) Box(Modifier.fillMaxWidth(p.inProgressFraction / (1f - p.doneFraction).coerceAtLeast(0.001f)).height(8.dp).background(belsi.warning))
            }
        }
        if (p.problem > 0) {
            Text("🔴 проблемных: ${p.problem}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun WorstFirstRow(photo: CuratorPhotoDto, onClick: () -> Unit) {
    val belsi = MaterialTheme.belsiColors
    val score = photo.aiScore ?: 0
    val scoreColor = when {
        score >= 80 -> belsi.success
        score >= 50 -> belsi.warning
        else -> MaterialTheme.colorScheme.error
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (photo.photoUrl.isNotBlank()) {
                    AsyncImage(model = photo.photoUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(photo.userName?.takeIf { it.isNotBlank() } ?: photo.userPhone ?: "—", style = MaterialTheme.typography.titleSmall)
                photo.aiComment?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(scoreColor).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("✨$score", style = MaterialTheme.typography.labelMedium, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
