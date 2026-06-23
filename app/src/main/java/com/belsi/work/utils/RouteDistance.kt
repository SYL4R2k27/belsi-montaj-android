package com.belsi.work.utils

import kotlin.math.*

/**
 * FIX(2026-05-11) BELSI 2.0.0 build11: расчёт расстояния и времени маршрута.
 *
 * Бриф BELSI.Driver, секция «Создание маршрута»:
 *  «Автоматический расчёт расстояния между точками (формула Хаверсина,
 *   коэффициент дорог 1.4x, средняя скорость 30 км/ч)»
 *
 * Формула Хаверсина даёт расстояние по прямой (over great-circle).
 * Коэффициент 1.4 компенсирует извилистость дорог.
 * Средняя скорость 30 км/ч — для городского трафика (Москва, MSK).
 */
object RouteDistance {

    private const val EARTH_RADIUS_KM = 6371.0
    const val ROAD_COEFFICIENT = 1.4
    const val AVG_SPEED_KMH = 30.0

    /** Расстояние "по прямой" между двумя точками в км (Haversine). */
    fun straightLineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    /** Реальное расстояние по дорогам (Haversine × 1.4). */
    fun roadDistanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        return straightLineKm(lat1, lng1, lat2, lng2) * ROAD_COEFFICIENT
    }

    /** Расчётное время в пути в минутах. */
    fun estimatedDurationMinutes(roadKm: Double): Int {
        return ((roadKm / AVG_SPEED_KMH) * 60).roundToInt()
    }

    /**
     * Общая длина маршрута (последовательность точек) в км + время в минутах.
     * Каждая точка — Pair(lat, lng). Пропускает точки без координат.
     */
    fun totalRoute(points: List<Pair<Double, Double>>): RouteEstimate {
        if (points.size < 2) return RouteEstimate(0.0, 0)
        var totalKm = 0.0
        for (i in 0 until points.size - 1) {
            val (a, b) = points[i]
            val (c, d) = points[i + 1]
            totalKm += roadDistanceKm(a, b, c, d)
        }
        return RouteEstimate(
            distanceKm = totalKm,
            durationMinutes = estimatedDurationMinutes(totalKm),
        )
    }

    data class RouteEstimate(
        val distanceKm: Double,
        val durationMinutes: Int,
    ) {
        val distanceLabel: String get() = "%.1f км".format(distanceKm)
        val durationLabel: String get() = when {
            durationMinutes < 60 -> "$durationMinutes мин"
            durationMinutes % 60 == 0 -> "${durationMinutes / 60} ч"
            else -> "${durationMinutes / 60} ч ${durationMinutes % 60} мин"
        }
    }
}
