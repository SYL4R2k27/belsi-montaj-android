package com.belsi.work.presentation.screens.driver

/**
 * FIX(2026-05-04): Внутренняя навигация playground-режима.
 * Используется в RoleTestPlaygroundScreen как sealed-state.
 * Не связано с глобальным NavGraph — только local-stack.
 */
sealed class PlaygroundDestination {

    // ─── Driver flow ───────────────────────────
    object DriverHome : PlaygroundDestination()
    data class DriverPointDetail(val pointId: String) : PlaygroundDestination()
    data class DriverCamera(val pointId: String) : PlaygroundDestination()

    // ─── Logistician flow ──────────────────────
    object LogisticianHome : PlaygroundDestination()
    data class LogistRequestDetail(val requestId: String) : PlaygroundDestination()
    data class LogistDriverDetail(val driverId: String) : PlaygroundDestination()
    object LogistCreateRoute : PlaygroundDestination()
    data class LogistRouteDetail(val routeId: String) : PlaygroundDestination()

    // ─── Coordinator/Foreman demo ──────────────
    object CoordHome : PlaygroundDestination()
    object CoordCreateRequest : PlaygroundDestination()

    // ─── Cross-role ────────────────────────────
    data class HistoryOfObject(val objectName: String) : PlaygroundDestination()

    // ─── Производство (BELSI.Команда) — FIX 2026-05-05 ──
    object WorkerHome : PlaygroundDestination()
    object SeniorWorkerHome : PlaygroundDestination()
    object ProductionChiefHome : PlaygroundDestination()
    object SupplierHome : PlaygroundDestination()
    object EngineerHome : PlaygroundDestination()
    object FactoryIdleReasons : PlaygroundDestination()
    object FactoryFacilitySwitch : PlaygroundDestination()

    // ─── Регистрация V2 ────────────────────────
    object RoleSelectV2 : PlaygroundDestination()
}
