package com.belsi.work.presentation.components.montage

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.belsi.work.presentation.theme.BelsiWorkTheme
import com.belsi.work.presentation.theme.belsiColors

/**
 * Карточка фото-ленты курaтора (4.5). Molecule.
 * Чистая хронология, свежее сверху. [hero]=true — крупная карточка «🆕 только что».
 *
 * Фото передаётся через слот [image] (по умолчанию — Steel-плейсхолдер; интеграция
 * подставит Coil AsyncImage). Тап (⤢) → PhotoDetail.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoFeedCard(
    author: String,
    timeLabel: String,
    context: String,
    modifier: Modifier = Modifier,
    isNew: Boolean = false,
    hero: Boolean = false,
    onOpen: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    selectionActive: Boolean = false,
    image: @Composable BoxScope.() -> Unit = { DefaultPhotoPlaceholder() },
) {
    val belsi = MaterialTheme.belsiColors
    val cornerShape = RoundedCornerShape(if (hero) 14.dp else 10.dp)
    val borderMod = when {
        selected -> Modifier.border(3.dp, belsi.success, cornerShape)
        hero -> Modifier.border(2.dp, belsi.success, cornerShape)
        else -> Modifier
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (hero) 148.dp else 96.dp)
            .clip(cornerShape)
            .then(borderMod)
            .then(
                if (onOpen != null || onLongClick != null)
                    Modifier.combinedClickable(onClick = { onOpen?.invoke() }, onLongClick = onLongClick)
                else Modifier
            ),
    ) {
        image()

        // затемнение + кружок выбора в режиме мультивыбора
        if (selectionActive) {
            if (selected) {
                Box(Modifier.fillMaxSize().background(belsi.success.copy(alpha = 0.30f)))
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) belsi.success else Color.Black.copy(alpha = 0.45f))
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Text("✓", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            }
        }

        // время — справа сверху
        Pill(text = timeLabel, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))

        // 🆕 — слева сверху (прячем когда активен мультивыбор, чтобы не мешать кружку)
        if (isNew && !selectionActive) {
            Pill(
                text = "🆕 только что",
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                container = belsi.success,
                content = belsi.onSuccess,
            )
        }

        // нижний градиент-оверлей с подписью
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))))
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(author, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                Text(context, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.92f))
            }
            if (hero) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.92f))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                ) {
                    Text("⤢ Открыть", style = MaterialTheme.typography.labelMedium, color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DefaultPhotoPlaceholder() {
    val belsi = MaterialTheme.belsiColors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(belsi.brandGradientTop, belsi.brandGradientBottom))),
    )
}

@Composable
private fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = Color.Black.copy(alpha = 0.72f),
    content: Color = Color.White,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(container)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = content, fontWeight = FontWeight.Bold)
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun PhotoFeedCardPreview() {
    BelsiWorkTheme {
        Column(Modifier.padding(16.dp)) {
            PhotoFeedCard(
                author = "Курешова О.",
                timeLabel = "14:48",
                context = "М.Тульская · Каб 312/1 · Окно 1 · 🔨 каркас",
                isNew = true,
                hero = true,
                onOpen = {},
            )
        }
    }
}
