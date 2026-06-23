package com.belsi.work.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * FIX(2026-05-11) BELSI 2.0.0 build11: помощники для интеграций с внешними приложениями.
 *
 * Бриф BELSI.Driver:
 *  - Кнопка «Открыть в навигаторе» → Яндекс.Навигатор → Яндекс.Карты → fallback браузер
 *  - Кнопка звонка контактному лицу точки
 *
 * Brandbook р.07 (Driver): водитель не должен вводить координаты вручную —
 * приложение само строит URL и открывает нужную карту.
 */
object NavigationIntents {

    private const val TAG = "NavigationIntents"

    /**
     * Открыть навигатор по списку точек маршрута.
     *
     * Стратегия:
     *  1. yandexnavi://build_route_on_map (Яндекс Навигатор) — если установлен
     *  2. yandexmaps://maps.yandex.ru/?rtext=... (Яндекс Карты) — если установлены
     *  3. https://yandex.ru/maps/?rtext=...&rtt=auto — браузер fallback
     *
     * Формат: список точек как (lat,lng) пар.
     */
    fun openYandexNavigation(
        context: Context,
        points: List<Pair<Double, Double>>,
    ): Boolean {
        if (points.isEmpty()) return false

        // Яндекс.Навигатор принимает только финальную точку через lat_to/lon_to,
        // промежуточные точки нужно использовать через Карты.
        if (points.size == 1) {
            val (lat, lng) = points.first()
            val naviUri = Uri.parse("yandexnavi://build_route_on_map?lat_to=$lat&lon_to=$lng")
            if (tryOpen(context, naviUri)) return true
        }

        // Яндекс.Карты через intent
        val rtext = points.joinToString("~") { "${it.first},${it.second}" }
        val mapsUri = Uri.parse("yandexmaps://maps.yandex.ru/?rtext=$rtext&rtt=auto")
        if (tryOpen(context, mapsUri)) return true

        // Браузер fallback
        val webUri = Uri.parse("https://yandex.ru/maps/?rtext=$rtext&rtt=auto")
        return tryOpen(context, webUri)
    }

    /**
     * Открыть карту на одной точке (без навигации).
     */
    fun openYandexMapPoint(context: Context, lat: Double, lng: Double, label: String? = null): Boolean {
        val z = "16"
        val pt = "$lng,$lat"
        val app = Uri.parse("yandexmaps://maps.yandex.ru/?ll=$pt&z=$z&pt=$pt,pm2blm" +
            (if (label != null) "&text=${Uri.encode(label)}" else ""))
        if (tryOpen(context, app)) return true
        val web = Uri.parse("https://yandex.ru/maps/?ll=$pt&z=$z&pt=$pt")
        return tryOpen(context, web)
    }

    /**
     * Позвонить по телефону. Использует ACTION_DIAL — не требует runtime-permission
     * (открывает экран набора, юзер сам нажимает «вызов»).
     */
    fun makePhoneCall(context: Context, phoneNumber: String): Boolean {
        val cleaned = phoneNumber.replace(Regex("[^+\\d]"), "")
        if (cleaned.isBlank()) return false
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleaned"))
        return tryOpen(context, intent)
    }

    private fun tryOpen(context: Context, uri: Uri): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return tryOpen(context, intent)
    }

    private fun tryOpen(context: Context, intent: Intent): Boolean {
        return try {
            ContextCompat.startActivity(context, intent, null)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No app to handle ${intent.data}: ${e.message}")
            false
        }
    }
}
