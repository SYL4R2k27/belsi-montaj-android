package com.belsi.work.presentation.navigation

sealed class AppRoute(val route: String) {
    // Auth Flow
    object Splash : AppRoute("splash")
    object AuthPhone : AppRoute("auth_phone")
    object Login : AppRoute("login")
    object SignUp : AppRoute("signup")
    object OTP : AppRoute("otp/{phone}") {
        fun createRoute(phone: String) = "otp/$phone"
    }
    
    // Onboarding
    object RoleSelect : AppRoute("role_select")
    object Terms : AppRoute("terms")
    object Instructions : AppRoute("instructions")
    object InstallerInvite : AppRoute("installer_invite")
    object ProfileSetup : AppRoute("profile_setup")
    
    // Main App
    object Main : AppRoute("main")
    object ForemanMain : AppRoute("foreman_main")
    object CoordinatorMain : AppRoute("coordinator_main")
    object CuratorMain : AppRoute("curator_main")

    // Driver Integration (Этап A — sandbox с мок-данными)
    object DriverPlayground : AppRoute("driver_playground")
    
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
    object NewTicket : AppRoute("new_ticket")
    object CreateTicket : AppRoute("create_ticket")
    object FAQ : AppRoute("faq")
    object About : AppRoute("about")

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
    object TransactionHistory : AppRoute("transaction_history")
    
    // Invite/Team
    object GenerateInvite : AppRoute("generate_invite")
    object JoinTeam : AppRoute("join_team/{inviteCode}") {
        fun createRoute(inviteCode: String) = "join_team/$inviteCode"
    }
    object TeamManagement : AppRoute("team_management")

    // Tools
    object ToolsList : AppRoute("tools_list")
    object ToolIssue : AppRoute("tool_issue")
    object ToolIssueForInstaller : AppRoute("tool_issue/{installerId}") {
        fun createRoute(installerId: String) = "tool_issue/$installerId"
    }
    object ToolReturn : AppRoute("tool_return/{transactionId}") {
        fun createRoute(transactionId: String) = "tool_return/$transactionId"
    }
    object RequestTool : AppRoute("request_tool/{foremanId}") {
        fun createRoute(foremanId: String) = "request_tool/$foremanId"
    }

    // Installer
    object RedeemInvite : AppRoute("redeem_invite")

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
    object FactoryShift : AppRoute("factory/shift")
    object FactoryShiftReport : AppRoute("factory/shift/report/{shiftId}") {
        fun createRoute(shiftId: String) = "factory/shift/report/$shiftId"
    }
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
    object DomainSelect : AppRoute("domain_select")

    // История объекта (для координатора и куратора)
    object ObjectHistory : AppRoute("object_history/{objectId}") {
        fun createRoute(objectId: String) = "object_history/$objectId"
    }

    // Лента всех фото фабрики (Начальник производства)
    object FacilityPhotosFeed : AppRoute("factory/photos_feed")

    // FIX(2026-05-05): Очередь pending-действий (offline-first для шатдаунов)
    object PendingActions : AppRoute("pending_actions")
}
