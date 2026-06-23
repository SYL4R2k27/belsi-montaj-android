package com.belsi.work.presentation.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.belsi.work.presentation.utils.shouldUseTwoPane

/**
 * BELSI List-Detail Scaffold — master-detail с адаптивным переключением между
 * single-pane (телефон) и two-pane (Z Fold 5 / tablet).
 *
 * Поведение:
 *  - COMPACT (телефон, ширина <600dp): тап в списке вызывает [onSelect],
 *    хост-экран должен сам открыть отдельный экран деталей. Detail-pane
 *    в scaffold НЕ рендерится.
 *  - MEDIUM/EXPANDED/XLARGE (Z Fold 5 unfolded, планшет): список занимает
 *    левую часть, деталь — правую. Деталь анимированно появляется при
 *    [selectedId] != null, закрывается через [onSelect] (null) или через
 *    кнопку × в [detailHeader].
 *
 * Поддерживает любой тип ID-ключа (строка, число, UUID).
 *
 * @param selectedId текущий выбранный элемент (null = деталь скрыта)
 * @param onSelect колбэк выбора. На телефоне срабатывает на тап и хост
 *                 должен сам навигировать; на планшете — обновляет selectedId.
 * @param listContent контент списка. Получает [isWide]=true если рядом
 *                    отображается detail-pane (полезно для регулировки
 *                    отступов, ширины карточек).
 * @param detailContent контент деталей. Вызывается только при !isCompact.
 *                      Получает selectedId — может быть null (placeholder).
 * @param listMinWidth минимальная ширина списка в two-pane. Default 320dp.
 * @param detailWeight соотношение ширин list:detail. Default 1f:1.3f.
 */
@Composable
fun BelsiListDetailScaffold(
    selectedId: String?,
    onSelect: (String?) -> Unit,
    listContent: @Composable (isWide: Boolean) -> Unit,
    detailContent: @Composable (selectedId: String?) -> Unit,
    modifier: Modifier = Modifier,
    emptyDetailHint: String = "Выберите элемент из списка",
) {
    // FIX(2026-05-25): ДВЕ панели только с 840dp (Material EXPANDED), а не с 540dp.
    // На Galaxy Z Fold раскрытом (~740dp даже при зафиксированном масштабе) две панели
    // 1:1.3 были слишком узкими → карточки переносились по буквам, стат уезжал
    // вертикально. Теперь Z Fold = одна колонка на всю ширину (тап → деталь отдельно),
    // две панели — только на настоящих планшетах. Рейл остаётся с 540dp (он не мешает).
    val isWide = shouldUseTwoPane()

    if (!isWide) {
        // Single-pane: только список, тапы → хост-экран сам навигирует
        Box(modifier = modifier.fillMaxSize()) {
            listContent(false)
        }
        return
    }

    // Two-pane: список слева, деталь справа
    Row(modifier = modifier.fillMaxSize()) {
        // Список — weight 1
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            listContent(true)
        }

        // Visual divider
        Surface(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight(),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        ) {}

        // Detail pane — weight 1.3.
        // Crossfade плавно переключает placeholder ↔ детальный контент.
        // (AnimatedVisibility внутри Row даёт overload-конфликт с RowScope.)
        Box(
            modifier = Modifier
                .weight(1.3f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Crossfade(
                targetState = selectedId,
                animationSpec = tween(durationMillis = 220),
                label = "detail-pane",
            ) { sid ->
                if (sid != null) {
                    Column(Modifier.fillMaxSize()) {
                        BelsiDetailPaneHeader(onClose = { onSelect(null) })
                        Box(Modifier.fillMaxSize()) {
                            detailContent(sid)
                        }
                    }
                } else {
                    EmptyDetailPlaceholder(hint = emptyDetailHint)
                }
            }
        }
    }
}

/**
 * Универсальный header для detail-pane с кнопкой закрытия.
 * Используется внутри [BelsiListDetailScaffold], но можно вызывать
 * отдельно если detailContent сам хочет кастомный header.
 */
@Composable
fun BelsiDetailPaneHeader(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Закрыть",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                "Детали",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyDetailPlaceholder(hint: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Default.TouchApp,
                contentDescription = null,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .height(48.dp)
                    .width(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            Text(
                hint,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
