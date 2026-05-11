# BELSI.Montaj

Android-приложение для управления строительными бригадами. Контроль смен, фотоотчёты, мессенджер, задачи, аналитика.

## Возможности

### Роли
- **Монтажник** — смены, фотоотчёты, таймлайн, голосовые комментарии (STT)
- **Бригадир** — управление бригадой, сводка по дню, назначение задач, перевод между объектами
- **Координатор** — дашборд с авто-обновлением, задачи с дедлайнами
- **Куратор** — модерация фото (batch), аналитика с графиками, экспорт CSV/PDF, управление пользователями

### Ключевые фичи
- **Офлайн-режим** — фото, смены и сообщения работают без сети. Автоматическая синхронизация при появлении интернета
- **Мессенджер** — WebSocket, голосовые сообщения, файлы, пересланные, reply-to, typing indicator, поиск, онлайн-статус
- **AI-анализ фото** — автоматическая проверка качества (размытость, яркость, разрешение) с push-уведомлениями
- **Фоновая загрузка** — PhotoUploadWorker + SyncWorker через WorkManager

## Стек

| Компонент | Технология |
|-----------|-----------|
| Язык | Kotlin |
| UI | Jetpack Compose + Material Design 3 |
| Архитектура | MVVM + Clean Architecture |
| DI | Hilt |
| БД | Room |
| Сеть | Retrofit + OkHttp + kotlinx.serialization |
| WebSocket | OkHttp WebSocket |
| Фон | WorkManager |
| Push | Firebase Cloud Messaging |
| Графики | Vico |
| Камера | CameraX |
| Голос | SpeechRecognizer (STT) |

## Сборка

### Требования
- Android Studio Ladybug+ (JBR 21)
- Android SDK 34+
- Kotlin 1.9.20+

## Архитектура

```
app/src/main/java/com/belsi/work/
├── data/
│   ├── firebase/          # FCM push-уведомления
│   ├── local/             # Room DB, TokenManager, PrefsManager
│   ├── models/            # DTO и data классы
│   ├── remote/            # Retrofit API, WebSocket
│   ├── repositories/      # Репозитории (офлайн-first)
│   └── workers/           # WorkManager (PhotoUpload, Sync, Reminder)
├── di/                    # Hilt модули
├── domain/                # Use cases
├── presentation/
│   ├── components/        # Переиспользуемые UI компоненты
│   ├── navigation/        # NavGraph
│   ├── screens/           # Экраны по ролям
│   └── theme/             # Material 3 тема
└── utils/                 # NetworkMonitor, ImageCompressor, STT и др.
```

## Бэкенд

FastAPI + PostgreSQL + Redis + S3 (Timeweb Cloud). Исходный код бэкенда в отдельном репозитории.

## Лицензия

Proprietary. All rights reserved.
