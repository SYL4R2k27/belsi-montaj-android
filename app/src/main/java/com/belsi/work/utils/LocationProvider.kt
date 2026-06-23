package com.belsi.work.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * FIX(2026-05-11) BELSI 2.0.0 build11: получение текущих GPS-координат.
 *
 * Бриф BELSI.Driver: при каждом событии (прибыл / доставлено / отъезд)
 * фиксируются GPS-координаты + временная метка. Это даёт логисту полную
 * картину и доказательную базу по каждой доставке.
 *
 * Стратегия:
 *  1. FusedLocationProviderClient (Google Play Services) — быстрее, точнее
 *  2. LocationManager fallback — если Play Services недоступны (rare на Android)
 *
 * Permissions: ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION — должны быть
 * запрошены runtime ДО вызова. Проверка внутри возвращает null если permission
 * нет (не падает).
 */
object LocationProvider {

    private const val TAG = "LocationProvider"

    data class Coords(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float,
        val source: String, // "fused" | "manager:gps" | "manager:network"
    )

    /**
     * Получает текущие координаты. Возвращает null если:
     *  - нет permission'а
     *  - GPS выключен и нет сетевого провайдера
     *  - не удалось получить за разумное время
     */
    suspend fun getCurrent(context: Context): Coords? {
        if (!hasPermission(context)) {
            Log.w(TAG, "no FINE/COARSE_LOCATION permission")
            return null
        }
        // 1. FusedLocationProvider (Play Services)
        val fused = tryFused(context)
        if (fused != null) return fused

        // 2. LocationManager fallback
        return tryLocationManager(context)
    }

    private fun hasPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private suspend fun tryFused(context: Context): Coords? {
        return try {
            @Suppress("MissingPermission")
            suspendCancellableCoroutine { cont ->
                val client = LocationServices.getFusedLocationProviderClient(context)
                val cts = CancellationTokenSource()
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .addOnSuccessListener { loc ->
                        cont.resume(loc?.let {
                            Coords(it.latitude, it.longitude, it.accuracy, "fused")
                        })
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "fused failed: ${e.message}")
                        cont.resume(null)
                    }
                cont.invokeOnCancellation { cts.cancel() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fused crashed: ${e.message}")
            null
        }
    }

    @Suppress("MissingPermission")
    private fun tryLocationManager(context: Context): Coords? {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            for (p in providers) {
                if (!lm.isProviderEnabled(p)) continue
                val loc: Location? = lm.getLastKnownLocation(p)
                if (loc != null) {
                    return Coords(loc.latitude, loc.longitude, loc.accuracy, "manager:$p")
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "LocationManager failed: ${e.message}")
            null
        }
    }
}
