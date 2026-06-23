package com.belsi.work.presentation.screens.driver

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.belsi.work.data.remote.dto.driver.RoutePointOutDto
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider

/**
 * Карта маршрута водителя (Yandex MapKit, FREE-tier — только отображение).
 * Точки маршрута = маркеры (цвет по типу), соединены ПРЯМОЙ линией (free, без routing API).
 * Кнопка «Открыть в Яндекс.Картах» → дип-линк rtext → консумер-приложение строит
 * реальный маршрут ПО ДОРОГАМ бесплатно (платный «детали маршрута» ключ не тратится).
 * Соблюдение условий free MapKit: логотип Яндекса (встроен в MapView) + кнопка «в Карты»
 * + ссылка на условия (в «О приложении»).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverRouteMapScreen(
    routeId: String,
    navController: NavController,
    viewModel: DriverRouteViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(routeId) { viewModel.load(routeId) }

    // MapView живёт между рекомпозициями; lifecycle привязан к экрану.
    val mapView = remember { MapView(context) }
    DisposableEffect(lifecycleOwner) {
        runCatching { MapKitFactory.initialize(context) }  // ленивый init, идемпотентен
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> { runCatching { MapKitFactory.getInstance().onStart(); mapView.onStart() } }
                Lifecycle.Event.ON_STOP -> { runCatching { mapView.onStop(); MapKitFactory.getInstance().onStop() } }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            runCatching { mapView.onStop(); MapKitFactory.getInstance().onStop() }
        }
    }

    val points = state.route?.points.orEmpty()
    val geoPoints = points.filter { it.latitude != null && it.longitude != null }

    // Рисуем маркеры + линию когда точки загрузились.
    LaunchedEffect(geoPoints.map { it.id }) {
        runCatching {
            val map = mapView.mapWindow.map
            map.mapObjects.clear()
            if (geoPoints.isEmpty()) return@runCatching
            val pts = geoPoints.map { Point(it.latitude!!, it.longitude!!) }
            // прямая линия по порядку точек
            if (pts.size >= 2) {
                map.mapObjects.addPolyline(Polyline(pts)).apply {
                    setStrokeColor(0xFF7C4DFF.toInt())
                    strokeWidth = 3.5f
                }
            }
            // маркеры
            geoPoints.forEachIndexed { i, p ->
                val color = pointColor(p.pointType, p.status)
                map.mapObjects.addPlacemark().apply {
                    geometry = pts[i]
                    setIcon(ImageProvider.fromBitmap(pinBitmap(color, (i + 1).toString())))
                }
            }
            // камера: центр по среднему + зум по разбросу
            val center = Point(pts.map { it.latitude }.average(), pts.map { it.longitude }.average())
            val spanLat = (pts.maxOf { it.latitude } - pts.minOf { it.latitude })
            val spanLon = (pts.maxOf { it.longitude } - pts.minOf { it.longitude })
            val span = maxOf(spanLat, spanLon)
            val zoom = when {
                pts.size == 1 -> 14f
                span > 1.0 -> 7f
                span > 0.3 -> 9f
                span > 0.1 -> 10.5f
                span > 0.03 -> 12f
                else -> 13.5f
            }
            map.move(CameraPosition(center, zoom, 0f, 0f))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Маршрут на карте", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${geoPoints.size} точек · линия прямая, маршрут — в Я.Картах",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
                if (state.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (geoPoints.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        modifier = Modifier.align(Alignment.Center),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(
                            "У точек маршрута нет координат — карта недоступна.\nОткройте по адресу в Я.Картах.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            // нижняя панель: дип-линк + атрибуция
            Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { openRouteInYandexMaps(context, geoPoints) },
                        enabled = geoPoints.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Map, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Открыть маршрут в Яндекс.Картах")
                    }
                    Text(
                        "© Яндекс · карты и маршрутизация",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
        }
    }
}

/** Дип-линк в консумер-приложение Яндекс.Карт: строит маршрут по дорогам (free). */
private fun openRouteInYandexMaps(context: Context, points: List<RoutePointOutDto>) {
    val coords = points
        .filter { it.latitude != null && it.longitude != null }
        .joinToString("~") { "${it.latitude},${it.longitude}" }
    if (coords.isBlank()) return
    val appUri = "yandexmaps://maps.yandex.ru/?rtext=$coords&rtt=auto"
    val webUri = "https://yandex.ru/maps/?rtext=$coords&rtt=auto"
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(appUri)))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUri)))
    }
}

private fun pointColor(type: String, status: String): Int = when {
    status == "delivered" -> 0xFF43A047.toInt()           // зелёный — выполнено
    status == "skipped" -> 0xFF9E9E9E.toInt()              // серый — пропущено
    type == "pickup" -> 0xFF1E88E5.toInt()                 // синий — забор
    type == "delivery" -> 0xFFFB8C00.toInt()               // оранжевый — доставка
    else -> 0xFF7C4DFF.toInt()                             // фиолетовый — прочее
}

/** Маленький круглый маркер с номером (без зависимости от drawable-ресурсов). */
private fun pinBitmap(color: Int, label: String): Bitmap {
    val size = 64
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    val r = size / 2f
    c.drawCircle(r, r, r - 4f, fill)
    c.drawCircle(r, r, r - 4f, border)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE
        textSize = 30f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val ty = r - (text.descent() + text.ascent()) / 2f
    c.drawText(label, r, ty, text)
    return bmp
}
