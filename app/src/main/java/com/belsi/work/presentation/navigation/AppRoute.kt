package com.belsi.work.presentation.navigation

sealed class AppRoute(val route: String) {
    // Auth Flow
    // FIX(2026-05-12) build19: Splash удалён — используется installSplashScreen() в MainActivity (системный).
    object AuthPhone : AppRoute("auth_phone")
    object Login : AppRoute("login")
    object ForgotPassword : AppRoute("forgot_password")
    object SignUp : AppRoute("signup")
    object OTP : AppRoute("otp/{phone}") {
        fun createRoute(phone: String) = "otp/$phone"
    }
    
    // Onboarding
    object RoleSelect : AppRoute("role_select")
    object Terms : AppRoute("terms")
    object Instructions : AppRoute("instructions")
    object InstallerInvite : AppRoute("installer_invite")
    // FIX(2026-05-12) build17: ProfileSetup был объявлен, но composable не зарегистрирован — удалён.
    
    // Main App
    object Main : AppRoute("main")
    object ForemanMain : AppRoute("foreman_main")
    object CoordinatorMain : AppRoute("coordinator_main")
    object CuratorMain : AppRoute("curator_main")

    // FIX(2026-05-13) release/2.0.1-internal: убран DriverPlayground (sandbox-route).
    // Production routes для Driver/Logistician ниже.
    object DriverHome : AppRoute("driver/home")
    object DriverPointDetail : AppRoute("driver/point/{pointId}") {
        fun createRoute(pointId: String) = "driver/point/$pointId"
    }
    object DriverRouteMap : AppRoute("driver/route/{routeId}/map") {
        fun createRoute(routeId: String) = "driver/route/$routeId/map"
    }
    object LogisticianHome : AppRoute("logistician/home")
    object LogistDriverList : AppRoute("logistician/drivers")
    object LogistDriverDetail : AppRoute("logistician/driver/{driverId}") {
        fun createRoute(driverId: String) = "logistician/driver/$driverId"
    }
    object LogistRouteDetail : AppRoute("logistician/route/{routeId}") {
        fun createRoute(routeId: String) = "logistician/route/$routeId"
    }
    object LogistCreateRoute : AppRoute("logistician/route/new")
    object LogistRequestDetail : AppRoute("logistician/request/{requestId}") {
        fun createRoute(requestId: String) = "logistician/request/$requestId"
    }
    
    // Shift
    object ShiftDetail : AppRoute("shift_detail/{shiftId}") {
        fun createRoute(shiftId: String) = "shift_detail/$shiftId"
    }
    object ShiftHistory : AppRoute("shift_history")

    // Photos
    object PhotoDetail : AppRoute("photo_detail/{photoId}") {
        fun createRoute(photoId: String) = "photo_detail/$photoId"
    }
    object Camera : AppRoute("camera") // Simplified for now
    object CameraWithParams : AppRoute("camera/{shiftId}/{slotIndex}") {
        fun createRoute(shiftId: String, slotIndex: Int) = "camera/$shiftId/$slotIndex"
    }
    object CuratorPhotos : AppRoute("curator/photos")
    object ForemanPhotos : AppRoute("foreman/photos")
    object PhotoGallery : AppRoute("photo_gallery")
    
    // Profile
    object Profile : AppRoute("profile")
    object EditProfile : AppRoute("edit_profile")
    object Settings : AppRoute("settings")
    object DebugSettings : AppRoute("debug_settings")
    object ChangePassword : AppRoute("change_password")
    
    // Support
    object Support : AppRoute("support")
    object Chat : AppRoute("chat")  // Installer chat
    object TicketDetail : AppRoute("ticket_detail/{ticketId}") {
        fun createRoute(ticketId: String) = "ticket_detail/$ticketId"
    }
    object CreateTicket : AppRoute("create_ticket")
    object About : AppRoute("about")
    // FIX(2026-05-12) build17: NewTicket / FAQ удалены (composable не было).

    // Curator Chat
    object CuratorChatList : AppRoute("curator/chats")
    object CuratorChatConversation : AppRoute("curator/chat/{ticketId}?userPhone={userPhone}") {
        fun createRoute(ticketId: String, userPhone: String? = null): String {
            return if (userPhone != null) {
                "curator/chat/$ticketId?userPhone=${java.net.URLEncoder.encode(userPhone, "UTF-8")}"
            } else {
                "curator/chat/$ticketId"
            }
        }
    }
    
    // Wallet
    object Wallet : AppRoute("wallet")
    object Withdraw : AppRoute("withdraw")
    // FIX(2026-06-15) Ф2: куратор «Принять кабинет → начислить» по cabinetId
    object WalletAccrue : AppRoute("wallet/accrue/{cabinetId}") {
        fun createRoute(cabinetId: String) = "wallet/accrue/$cabinetId"
    }
    // FIX(2026-06-15) Ф2.5: куратор «Месячные акты» (период + очередь по людям)
    object WalletActs : AppRoute("wallet/acts")
    // FIX(2026-06-16) Ф3: куратор-хаб расчёта (часы/метры + штраф/бонус + выплаты Rocket Work + баланс)
    object WalletF3Hub : AppRoute("wallet/f3")
    // FIX(2026-06-16) Ф3: часовые ставки (глобальная + индивидуальные)
    object WalletF3HourRates : AppRoute("wallet/f3/hour-rates")
    // FIX(2026-06-16) Ф3: очередь начислений по монтажнику/периоду (пересчёт/правка/одобрить/void)
    object WalletF3Earnings : AppRoute("wallet/f3/earnings")
    // FIX(2026-06-16) Ф3: выплаты — собрать/одобрить/отправить-RW/выплачено/отмена + гейты
    object WalletF3Payouts : AppRoute("wallet/f3/payouts")
    // FIX(2026-06-16) Ф3: баланс компании + прогноз «нужно X / на счету Y»
    object WalletF3Balance : AppRoute("wallet/f3/balance")
    // FIX(2026-05-12) build17: TransactionHistory удалён (composable не зарегистрирован).

    // FIX(2026-05-12) build17: GenerateInvite / JoinTeam / TeamManagement удалены
    // (composable не зарегистрированы; функция инвайтов — через CoordCreateRequest и RedeemInvite).

    // Tools
    object ToolsList : AppRoute("tools_list")
    object ToolIssue : AppRoute("tool_issue")
    object ToolIssueForInstaller : AppRoute("tool_issue/{installerId}") {
        fun createRoute(installerId: String) = "tool_issue/$installerId"
    }
    // FIX(2026-05-12) build17: ToolReturn убран (был placeholder без composable).
    object RequestTool : AppRoute("request_tool/{foremanId}") {
        fun createRoute(foremanId: String) = "request_tool/$foremanId"
    }

    // Installer
    object RedeemInvite : AppRoute("redeem_invite")
    // FIX(2026-05-20) BELSI 2.1.0: задачи монтажника вынесены из таб-бара в ⋮-меню
    object InstallerTasks : AppRoute("installer_tasks")

    // Curator Tools
    object CuratorTools : AppRoute("curator/tools")
    object CuratorSupport : AppRoute("curator/support")

    // Curator User Detail (Foreman or Installer)
    object CuratorUserDetail : AppRoute("curator/user/{userId}") {
        fun createRoute(userId: String) = "curator/user/$userId"
    }

    // Curator Shift Admin (re-open / edit time / audit)
    object CuratorShiftAdmin : AppRoute("curator/shift_admin/{userId}?name={name}") {
        fun createRoute(userId: String, userName: String? = null): String {
            return if (userName != null) {
                "curator/shift_admin/$userId?name=${java.net.URLEncoder.encode(userName, "UTF-8")}"
            } else {
                "curator/shift_admin/$userId"
            }
        }
    }

    // Foreman - Installer Detail
    object InstallerDetail : AppRoute("foreman/installer/{installerId}") {
        fun createRoute(installerId: String) = "foreman/installer/$installerId"
    }

    // Curator Analytics
    object CuratorAnalytics : AppRoute("curator/analytics")
    object AiDashboard : AppRoute("curator/ai-dashboard")
    // FIX(2026-05-10) BELSI 1.3.0: AI-поиск фото для куратора
    object AiPhotoSearch : AppRoute("curator/ai-photo-search-ui")
    // BELSI 2.1.0: универсальный поиск куратора (разделы + люди + объекты + фото)
    object CuratorSearch : AppRoute("curator/search")

    // Reports
    object Reports : AppRoute("reports")

    // Messenger
    object MessengerConversation : AppRoute("messenger/conversation/{threadId}") {
        fun createRoute(threadId: String) = "messenger/conversation/$threadId"
    }
    object GroupInfo : AppRoute("messenger/group/{threadId}") {
        fun createRoute(threadId: String) = "messenger/group/$threadId"
    }

    // ────────── BELSI.Команда — производство мебели (FIX 2026-05-05) ──────────
    // Главные экраны 5 производственных ролей
    object WorkerMain : AppRoute("factory/worker_main")
    object SeniorWorkerMain : AppRoute("factory/senior_worker_main")
    object ProductionChiefMain : AppRoute("factory/chief_main")
    object SupplierMain : AppRoute("factory/supplier_main")
    object EngineerMain : AppRoute("factory/engineer_main")

    // Экраны производства — общие
    // FIX(2026-05-12) build19: FactoryShift / FactoryShiftReport удалены — composable не зарегистрированы,
    // фактически смена производственного работника живёт в WorkerMainScreen.
    object FactoryIdleReasons : AppRoute("factory/idle/reasons")
    object FactoryFacilitySwitch : AppRoute("factory/facility/switch")

    // Партии (Pipeline) — общие для производства/логистики/монтажа
    object BatchList : AppRoute("batches")
    object BatchDetail : AppRoute("batches/{batchId}") {
        fun createRoute(batchId: String) = "batches/$batchId"
    }
    object BatchCreate : AppRoute("batches/new")

    // Регистрация V2 — выбор доменов и ролей
    object RoleSelectV2 : AppRoute("role_select_v2")
    // FIX(2026-05-12) build19: DomainSelect удалён — composable не было, домен выбирается внутри RoleSelectV2.

    // История объекта (для координатора и куратора)
    object ObjectHistory : AppRoute("object_history/{objectId}") {
        fun createRoute(objectId: String) = "object_history/$objectId"
    }

    // FIX(2026-05-12) build17: реальный экран создания заявки на доставку координатором.
    object CoordCreateRequest : AppRoute("coordinator/create_request?batchId={batchId}") {
        fun createRoute(batchId: String? = null): String {
            return if (batchId != null) "coordinator/create_request?batchId=$batchId"
            else "coordinator/create_request"
        }
    }

    // FIX(2026-05-12) build18 P2: детальная карточка участника команды координатора.
    object CoordTeamMemberDetail : AppRoute("coordinator/team_member/{userId}") {
        fun createRoute(userId: String) = "coordinator/team_member/$userId"
    }

    // FIX(2026-05-12) build19: FacilityPhotosFeed удалён — composable не зарегистрирован,
    // лента фото производства живёт внутри ProductionChiefMainScaffold через FacilityPhotosFeedScreen компонент.

    // FIX(2026-05-05): Очередь pending-действий (offline-first для шатдаунов)
    object PendingActions : AppRoute("pending_actions")

    // FIX(2026-05-12) build19 hotfix: универсальный просмотрщик юр-документов.
    // type = tos | privacy | eula | ai_consent (см. LegalTexts.DocumentType).
    object LegalDocument : AppRoute("legal_document/{type}") {
        fun createRoute(type: String) = "legal_document/$type"
    }

    // ─── Tool-transfer pipeline (Этап 3) ─────────────────────────
    // Универсальный hub: incoming / outgoing / inventory + FAB "Сформировать"
    object ToolTransferHub : AppRoute("tools/hub?tab={tab}") {
        fun createRoute(tab: String = "incoming") = "tools/hub?tab=$tab"
    }
    // Комплектатор формирует передачу на объект
    object ToolTransferCreate : AppRoute("tools/transfer/new?siteObjectId={siteObjectId}&batchId={batchId}") {
        fun createRoute(siteObjectId: String? = null, batchId: String? = null): String {
            val params = listOfNotNull(
                siteObjectId?.let { "siteObjectId=$it" },
                batchId?.let { "batchId=$it" },
            ).joinToString("&")
            return if (params.isEmpty()) "tools/transfer/new" else "tools/transfer/new?$params"
        }
    }
    // Приёмщик на объекте — список ожидающих + детали
    object ToolTransferIncoming : AppRoute("tools/transfers/incoming")
    object ToolTransferDetail : AppRoute("tools/transfer/{transferId}") {
        fun createRoute(transferId: String) = "tools/transfer/$transferId"
    }

    // ─── Tool kits / Тележка (FIX 2026-05-18) ──────────────────────
    /** Список kit-шаблонов (любая роль может посмотреть). */
    object ToolKitList : AppRoute("tool-kits")

    /** Detail kit'а — supplier видит «Выдать», curator+ видит редактирование. */
    object ToolKitDetail : AppRoute("tool-kits/{kitId}") {
        fun createRoute(kitId: String) = "tool-kits/$kitId"
    }

    /** Supplier dispatch — форма выдачи (объект, водитель, team_count). */
    object ToolKitDispatch : AppRoute("tool-kits/{kitId}/dispatch") {
        fun createRoute(kitId: String) = "tool-kits/$kitId/dispatch"
    }

    /** Просмотр batch (driver / receiver / chief) — 60 позиций одной тележки. */
    object ToolKitBatch : AppRoute("tool-kits/batch/{batchId}") {
        fun createRoute(batchId: String) = "tool-kits/batch/$batchId"
    }

    /** Создание нового kit'а (curator+). */
    object ToolKitCreate : AppRoute("tool-kits/new")

    // ─── Return flow (FIX 2026-05-14 BELSI 2.0.1) ─────────────────
    object ToolReturnRequest : AppRoute("tools/return-request/{transferId}") {
        fun createRoute(transferId: String) = "tools/return-request/$transferId"
    }
    object ToolReturnPickup : AppRoute("tools/return-pickup/{transferId}") {
        fun createRoute(transferId: String) = "tools/return-pickup/$transferId"
    }
    object ToolReturnDeliver : AppRoute("tools/return-deliver/{transferId}") {
        fun createRoute(transferId: String) = "tools/return-deliver/$transferId"
    }
    object ToolReturnAccept : AppRoute("tools/return-accept/{transferId}") {
        fun createRoute(transferId: String) = "tools/return-accept/$transferId"
    }
    object CuratorReturns : AppRoute("curator/returns")
    /** FIX(2026-05-14) BELSI 2.0.1: единая лента AI-алертов куратора. */
    object CuratorAlertsFeed : AppRoute("curator/alerts/feed")

    // ─── Universal ObjectScreen v3 (FIX 2026-05-20 BELSI 2.1.0) ───
    // Floor → Zone → Cabinet → Window (3 этапа). Общий паттерн coordinator + curator.
    object ObjectV3 : AppRoute("object_v3/{objectId}") {
        fun createRoute(objectId: String) = "object_v3/$objectId"
    }
}
