# BELSI.Work — справочник по проекту

Документ для опоры при запросах: **корректировки**, **исправление ошибок**, **доработки**.

---

## 1. Роли пользователей (UserRole)

| Роль | Код | Стартовый экран после входа | Описание |
|------|-----|-----------------------------|----------|
| **Монтажник** | `INSTALLER` | `AppRoute.Main` (main) | Работает на объектах под руководством бригадира |
| **Бригадир** | `FOREMAN` | `AppRoute.ForemanMain` (foreman_main) | Руководит монтажниками, контролирует смены |
| **Координатор** | `COORDINATOR` | `AppRoute.CoordinatorMain` (coordinator_main) | Координирует объект, контролирует бригадиров |
| **Куратор** | `CURATOR` | `AppRoute.CuratorMain` (curator_main) | Контролирует бригадиров и монтажников |

Определение старта: `MainActivity.onCreate()` → по токену и `prefsManager.getUser()?.role` выбирается маршрут. Без токена → `AppRoute.AuthPhone`.

---

## 2. Маршруты (AppRoute) и экраны

### 2.1 Есть в NavGraph (реально открываются)

| Маршрут | route | Эран (Composable) | Кто использует |
|---------|-------|-------------------|----------------|
| AuthPhone | `auth_phone` | AuthPhoneScreen | Старт неавторизованного |
| Login | `login` | LoginScreen | Вход по паролю |
| OTP | `otp/{phone}` | OTPScreen | Ввод кода после телефона |
| Terms | `terms` | TermsScreen | После OTP |
| RoleSelect | `role_select` | RoleSelectScreen | Выбор роли |
| Instructions | `instructions` | InstructionsScreen | После роли |
| InstallerInvite | `installer_invite` | InstallerInviteScreen | Для монтажника после инструкций |
| Main | `main` | MainScreen | Монтажник: табы Смена/Фото/Задачи/Профиль/Чат |
| ForemanMain | `foreman_main` | ForemanMainScreen | Бригадир |
| CoordinatorMain | `coordinator_main` | CoordinatorMainScreen | Координатор |
| CuratorMain | `curator_main` | CuratorMainScreen | Куратор |
| Settings | `settings` | SettingsScreen | Все |
| Reports | `reports` | ReportsScreen | Отчёты |
| About | `about` | AboutScreen | О приложении |
| Camera | `camera` | CameraScreen | Съёмка |
| CameraWithParams | `camera/{shiftId}/{slotIndex}` | CameraScreen(shiftId, slotIndex) | Съёмка в слот смены |
| EditProfile | `edit_profile` | EditProfileScreen | Редактирование профиля |
| Wallet | `wallet` | WalletScreen | Кошелёк |
| Withdraw | `withdraw` | WithdrawScreen | Вывод |
| Support | `support` | SupportScreen | Список тикетов поддержки |
| Chat | `chat` | InstallerChatScreen | Чат с поддержкой (внутри ChatHub) |
| CuratorChatList | `curator/chats` | CuratorChatListScreen | Список чатов куратора |
| CuratorChatConversation | `curator/chat/{ticketId}?userPhone=...` | CuratorChatConversationScreen | Чат куратора с пользователем |
| CreateTicket | `create_ticket` | CreateTicketScreen | Создание тикета |
| TicketDetail | `ticket_detail/{ticketId}` | TicketDetailScreen | Просмотр тикета |
| ShiftHistory | `shift_history` | ShiftHistoryScreen | История смен |
| CuratorPhotos | `curator/photos` | CuratorPhotosScreen | Фото куратора |
| ForemanPhotos | `foreman/photos` | ForemanPhotosScreen | Фото бригадира |
| PhotoGallery | `photo_gallery` | PhotoGalleryScreen | Галерея фото |
| PhotoDetail | `photo_detail/{photoId}` | PhotoDetailScreen | Детали фото |
| DebugSettings | `debug_settings` | DebugSettingsScreen | Отладочные настройки |
| ToolsList | `tools_list` | ToolsListScreen | Список инструментов |
| ToolIssue | `tool_issue` | ToolIssueScreen | Выдача инструмента |
| ToolIssueForInstaller | `tool_issue/{installerId}` | ToolIssueScreen(installerId) | Выдача инструмента монтажнику |
| CuratorTools | `curator/tools` | CuratorToolsScreen | Инструменты куратора |
| CuratorSupport | `curator/support` | CuratorSupportScreen | Поддержка куратора |
| CuratorUserDetail | `curator/user/{userId}` | CuratorUserDetailScreen | Карточка пользователя (куратор) |
| CuratorAnalytics | `curator/analytics` | CuratorAnalyticsScreen | Аналитика куратора |
| RedeemInvite | `redeem_invite` | RedeemInviteScreen | Активация инвайта (монтажник) |
| RequestTool | `request_tool/{foremanId}` | RequestToolScreen | Запрос инструмента у бригадира |
| InstallerDetail | `foreman/installer/{installerId}` | InstallerDetailScreen | Карточка монтажника (бригадир) |
| MessengerConversation | `messenger/conversation/{threadId}` | MessengerConversationScreen | Личный/групповой чат |
| GroupInfo | `messenger/group/{threadId}` | GroupInfoScreen | Инфо о группе мессенджера |

### 2.2 Объявлены в AppRoute, но НЕТ в NavGraph (не открываются по навигации)

| Маршрут | route | План |
|---------|-------|--------------|
| **Splash** | `splash` | Добавить composable или убрать из AppRoute |
| **Profile** | `profile` | Профиль показывается внутри MainScreen (вкладка). Отдельный route не зарегистрирован — при необходимости composable(Profile.route) → ProfileScreen |
| **ProfileSetup** | `profile_setup` | Добавить экран/маршру(план)|
| **ShiftDetail** | `shift_detail/{shiftId}` | Детали смены: нет отдельного экрана в графе — открывается через другой механизм |
| **NewTicket** | `new_ticket` | Дублирует CreateTicket/старая схема|
| **FAQ** | `faq` | Нет экрана в графе — добавить или убрать |
| **TransactionHistory** | `transaction_history` | Нет в графе — добавить при необходимости |
| **GenerateInvite** | `generate_invite` | У бригадира инвайт создаётся из ForemanMain (диалог), отдельный экран не в графе |
| **JoinTeam** | `join_team/{inviteCode}` | Нет в графе — возможно вход по коду через RedeemInvite |
| **TeamManagement** | `team_management` | Нет в графе — команда во вкладках Foreman/Coordinator |
| **ToolReturn** | `tool_return/{transactionId}` | В NavGraph есть только composable(AppRoute.ToolReturn.route) без аргумента; с аргументом **нет** — добавить composable с navArgument("transactionId") и открывать ToolReturnScreen(transactionId) |

---

## 3. Экраны по ролям и действиям

### 3.1 Монтажник (MainScreen)

- **Вкладки:** Смена (ShiftScreen), Фото (PhotosScreenSimple), Задачи (InstallerTasksScreen), Профиль (ProfileScreen), Чат (ChatHubScreen).
- **Смена:** старт/стоп смены, паузы, простой, почасовые фото, переход на камеру, история смен (через Profile → ShiftHistory).
- **Чат:** ChatHubScreen = табы «Сообщения» (ThreadListScreen → MessengerConversation) и «Поддержка» (InstallerChatScreen).
- **Профиль:** просмотр, EditProfile, Settings, ShiftHistory, Support, RedeemInvite, выход (navigate AuthPhone).

### 3.2 Бригадир (ForemanMainScreen)

- **Вкладки:** Команда (ForemanTab.TEAM), Фото (ForemanTab.PHOTOS), Задачи (ForemanTab.TASKS), Инструменты (ForemanTab.TOOLS), Чат (ForemanTab.CHAT), Профиль (ForemanTab.PROFILE).
- **Действия:** создание инвайта, просмотр команды, переход в InstallerDetail(installerId), выдача инструмента (ToolIssue / ToolIssueForInstaller), фото команды, создание/просмотр задач, ChatHub (мессенджер + поддержка), настройки.
- **Навигация:** Settings, ToolIssue, MessengerConversation, InstallerDetail, Camera (через контент).

### 3.3 Координатор (CoordinatorMainScreen)

- **Вкладки (индексы 0–6):** Объект, Фото, Задачи, Команда, Отчёты, Чат, Профиль.
- **Действия:** объект/сайт (SiteTab), фото с апрувом/реджектом (CoordinatorPhotosTab), задачи (CoordinatorTasksTab), команда (TeamTab), отчёты (ReportsTab), ChatHub, профиль; открытие камеры (AppRoute.Camera).
- **Навигация:** Settings, Camera.

### 3.4 Куратор (CuratorMainScreen)

- **Вкладки (0–7):** Обзор (DashboardTab), Бригады (ForemenTab), Люди (UsersTab), Объекты (CuratorObjectsTab), Задачи (CuratorTasksScreen), Фото (CuratorPhotosScreen), Тикеты (TicketsTab), Чат (ChatHubScreen).
- **Действия:** экспорт CSV/PDF с обзора, аналитика (CuratorAnalytics), настройки, переход в CuratorUserDetail(userId), CuratorTools, CuratorPhotos, CuratorSupport, CuratorChatConversation(ticketId, userPhone).
- **Навигация:** CuratorAnalytics, Settings, CuratorTools, CuratorPhotos, CuratorSupport, CuratorUserDetail, CuratorChatConversation.

---

## 4. Репозитории (RepositoryModule)

- AuthRepository, ShiftRepository, PhotoRepository, TeamRepository, CuratorRepository, WalletRepository, SupportRepository, UserRepository, ChatRepository, TicketRepository, InviteRepository, ToolsRepository, TaskRepository, PushRepository, MessengerRepository, ObjectsRepository, PauseRepository, CoordinatorRepository.

Все биндятся к *Impl-классам в `data/repositories/`.

---

## 5. Локальные данные и БД (Room)

- **Используемая БД:** `com.belsi.work.data.local.database.AppDatabase` (version 5).  
  Сущности: ShiftEntity, PhotoEntity, ChatMessageEntity, UserCacheEntity, ToolEntity, TaskEntity, MessengerThreadEntity, MessengerMessageEntity.  
  Провайдится в `DatabaseModule` из `data/local/database/`.

- **Не используется (мёртвый код):** `com.belsi.work.data.local.AppDatabase` (version 1) и пакет `data/local/dao/`, `data/local/entities/` (ShiftSlotEntity, TicketEntity, TicketMessageEntity, TicketDao и т.д.).  
  Рекомендация: удалить или перенести нужное в `data/local/database/`.

---

## 6. Бэкенд (backend/belsi-api)

- **Framework:** FastAPI. **БД:** PostgreSQL (SQLAlchemy), миграции Alembic.
- **Роутеры:** profile, tools, user_names, tasks, foreman_team, push_notifications, foreman, yandex_auth, sber_auth, support_chat, support, shifts_photos, photos_feed, photo_review, curator, coordinator, reports, shift_pauses, site_objects, messenger, ws_messenger, auth_login.
- **В main.py дополнительно:** `/auth/phone`, `/auth/verify`, `/shifts/start`, `/shifts/finish`, `/shifts`, `/shifts/{shift_id}`, `/foreman/invites` (create/list/redeem/cancel), `/shift/hour/photo`, `/health`.  
  На старте приложения выполняются сырые SQL-миграции (ALTER TABLE, CREATE TABLE) — лучше перенести в Alembic.
- **Конфиг:** `app/settings.py` (env: DB_*, REDIS_*, S3_*, Yandex/Sber OAuth, JWT). Зависимости не зафиксированы в репозитории (нет requirements.txt/pyproject.toml) — стоит добавить.

---

## 7. Известные проблемы и доработки

| Категория | Пункт | Действие |
|-----------|------|----------|
| Навигация | Маршруты без composable | Splash, Profile, ProfileSetup, ShiftDetail, FAQ, NewTicket, TransactionHistory, GenerateInvite, JoinTeam, TeamManagement — либо добавить экраны в NavGraph, либо убрать из AppRoute. |
| Навигация | ToolReturn с аргументом | Добавить в NavGraph: `composable(route = AppRoute.ToolReturn.route, arguments = listOf(navArgument("transactionId") { type = NavType.StringType }))` и передавать transactionId в ToolReturnScreen. |
| Код | Две Room-БД | Удалить или не использовать `data/local/AppDatabase.kt` и связанные `data/local/dao/`, `data/local/entities/` (TicketDao, ShiftSlotEntity и т.д.), чтобы не путать с актуальной БД в `data/local/database/`. |
| Сборка | Java / Gradle | В app/build.gradle.kts уже стоит Java 11 (jvmTarget "11", VERSION_11). При дублях Hilt-классов — чистить `app/build` (или `./gradlew clean`) и не копировать вручную папки в `build/generated`. |
| Бэкенд | Миграции | Вынести DDL из `main.py` startup_event в Alembic-миграции. |
| Бэкенд | Зависимости | Добавить requirements.txt или pyproject.toml. |
| Бэкенд | Мусор в репозитории | Удалить файлы в корне belsi-api: `Accept:`, `GET`, `Host:`, `User-Agent:`, `default_db=.save`. |
| UX/логика | Profile | Отдельный маршрут Profile не зарегистрирован; профиль доступен только как вкладка в Main. При необходимости глубокой ссылки «профиль» — добавить composable(Profile.route). |

---

## 8. Краткая карта файлов

- **Навигация:** `AppRoute.kt`, `NavGraph.kt`.
- **Точка входа:** `MainActivity.kt`, `BelsiWorkApp.kt`.
- **Роли/модель пользователя:** `data/models/User.kt` (UserRole, User).
- **DI:** `di/NetworkModule.kt`, `di/DatabaseModule.kt`, `di/RepositoryModule.kt`.
- **Главные экраны по ролям:** `presentation/screens/main/MainScreen.kt`, `foreman/ForemanMainScreen.kt`, `coordinator/CoordinatorMainScreen.kt`, `curator/CuratorMainScreen.kt`.
- **Чат/мессенджер:** `messenger/ChatHubScreen.kt`, `ThreadListScreen.kt`, `MessengerConversationScreen.kt`, `chat/InstallerChatScreen.kt`, `CuratorChatListScreen.kt`, `CuratorChatConversationScreen.kt`.

При запросах на **корректировки**, **исправление ошибок** и **потенциальные доработки** можно опираться на этот документ и уточнять по разделам (роли, экраны, маршруты, репозитории, бэкенд, известные проблемы).
