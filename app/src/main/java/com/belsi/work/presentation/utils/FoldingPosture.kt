package com.belsi.work.presentation.utils

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * FIX(2026-05-11) BELSI 2.0.0 build5: FoldingFeature API.
 * Брендбук foldable-tablet раздел 03 + 13 (CameraScreen TableTop posture).
 */
enum class DevicePosture {
    NORMAL,
    BOOK_FLAT,
    TABLE_TOP,
    HALF_OPENED_VERTICAL,
}

@Composable
fun rememberDevicePosture(): DevicePosture {
    val context = LocalContext.current
    val activity = context as? Activity ?: return DevicePosture.NORMAL
    var posture by remember { mutableStateOf(DevicePosture.NORMAL) }
    val scope = rememberCoroutineScope()

    DisposableEffect(activity) {
        val job: Job = scope.launch {
            WindowInfoTracker.getOrCreate(context)
                .windowLayoutInfo(activity)
                .collect { info ->
                    val fold = info.displayFeatures
                        .filterIsInstance<FoldingFeature>()
                        .firstOrNull()
                    posture = when {
                        fold == null -> DevicePosture.NORMAL
                        fold.state == FoldingFeature.State.FLAT -> DevicePosture.BOOK_FLAT
                        fold.state == FoldingFeature.State.HALF_OPENED &&
                            fold.orientation == FoldingFeature.Orientation.HORIZONTAL ->
                                DevicePosture.TABLE_TOP
                        fold.state == FoldingFeature.State.HALF_OPENED &&
                            fold.orientation == FoldingFeature.Orientation.VERTICAL ->
                                DevicePosture.HALF_OPENED_VERTICAL
                        else -> DevicePosture.NORMAL
                    }
                }
        }
        onDispose { job.cancel() }
    }
    return posture
}
