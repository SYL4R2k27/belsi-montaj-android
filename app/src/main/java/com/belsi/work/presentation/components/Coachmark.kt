package com.belsi.work.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay

/**
 * FIX(2026-05-21) BELSI 2.1.0: интерактивное обучение первого запуска (coachmarks).
 *
 * Spotlight-оверлей: затемняет экран, «прорезает» дырку над целевым элементом
 * (через offscreen-слой + BlendMode.Clear) и показывает карточку-подсказку. Шаги
 * переключаются «Далее», есть «Пропустить». Показывается ОДИН раз на роль
 * (флаг в [com.belsi.work.data.local.PrefsManager]).
 *
 * Использование на экране:
 *   val coach = rememberCoachmark("installer", installerCoachSteps)
 *   ... Modifier.coachmarkTarget(coach, "camera") на элементах ...
 *   Box(Modifier.fillMaxSize()) { <экран>; CoachmarkOverlay(coach) }
 */
data class CoachmarkStep(
    val targetKey: String?,   // null → карточка по центру без подсветки
    val title: String,
    val text: String,
)

class CoachmarkController {
    val targets = mutableStateMapOf<String, Rect>()
    var overlayOrigin by mutableStateOf(Offset.Zero)
    var steps by mutableStateOf<List<CoachmarkStep>>(emptyList())
    var index by mutableStateOf(0)
    var visible by mutableStateOf(false)
    private var onComplete: (() -> Unit)? = null

    fun start(s: List<CoachmarkStep>, onComplete: () -> Unit) {
        if (s.isEmpty()) { onComplete(); return }
        steps = s
        index = 0
        this.onComplete = onComplete
        visible = true
    }

    fun next() { if (index < steps.lastIndex) index++ else finish() }

    fun finish() {
        if (!visible) return
        visible = false
        onComplete?.invoke()
        onComplete = null
    }

    fun setTarget(key: String, rect: Rect) { targets[key] = rect }
}

@Composable
fun rememberCoachmarkController(): CoachmarkController = remember { CoachmarkController() }

/** Помечает элемент как цель подсветки с ключом [key]. */
fun Modifier.coachmarkTarget(controller: CoachmarkController, key: String): Modifier =
    this.onGloballyPositioned { controller.setTarget(key, it.boundsInRoot()) }

/**
 * Создаёт контроллер и запускает обучение при первом запуске для [roleKey].
 * После завершения/пропуска — помечает roleKey как пройденный (больше не показывается).
 */
@Composable
fun rememberCoachmark(roleKey: String, steps: List<CoachmarkStep>): CoachmarkController {
    val controller = rememberCoachmarkController()
    val vm: CoachmarkViewModel = hiltViewModel()
    LaunchedEffect(roleKey) {
        if (!vm.isSeen(roleKey)) {
            delay(450) // дать layout измериться, чтобы первая подсветка сразу попала в цель
            controller.start(steps) { vm.markSeen(roleKey) }
        }
    }
    return controller
}

@Composable
fun CoachmarkOverlay(controller: CoachmarkController) {
    if (!controller.visible || controller.steps.isEmpty()) return
    val density = LocalDensity.current
    val step = controller.steps[controller.index]
    val targetRoot = step.targetKey?.let { controller.targets[it] }
    val local = targetRoot?.translate(-controller.overlayOrigin.x, -controller.overlayOrigin.y)
    val padPx = with(density) { 8.dp.toPx() }
    val radiusPx = with(density) { 16.dp.toPx() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { controller.overlayOrigin = it.boundsInRoot().topLeft }
            .pointerInput(Unit) { detectTapGestures { /* поглощаем тапы по затемнению */ } }
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawBehind {
                drawRect(Color.Black.copy(alpha = 0.80f))
                if (local != null) {
                    drawRoundRect(
                        color = Color.Transparent,
                        topLeft = Offset(local.left - padPx, local.top - padPx),
                        size = androidx.compose.ui.geometry.Size(
                            local.width + padPx * 2,
                            local.height + padPx * 2,
                        ),
                        cornerRadius = CornerRadius(radiusPx, radiusPx),
                        blendMode = BlendMode.Clear,
                    )
                }
            }
    ) {
        val screenH = with(density) { maxHeight.toPx() }
        val targetCenterY = local?.center?.y
        val align = when {
            targetCenterY == null -> Alignment.Center
            targetCenterY < screenH / 2f -> Alignment.BottomCenter
            else -> Alignment.TopCenter
        }

        Card(
            modifier = Modifier
                .align(align)
                .padding(20.dp)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    step.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    step.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { controller.finish() }) { Text("Пропустить") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${controller.index + 1}/${controller.steps.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Button(onClick = { controller.next() }) {
                            Text(if (controller.index == controller.steps.lastIndex) "Готово" else "Далее")
                        }
                    }
                }
            }
        }
    }
}
