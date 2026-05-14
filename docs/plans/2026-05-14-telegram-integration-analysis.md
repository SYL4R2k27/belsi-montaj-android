# 2026-05-14 — Telegram Integration Analysis (telegram-login-android + tdlib/td)

> **Статус:** только анализ. Реализацию отложили.
> При возврате к этой теме — этот файл уже содержит проработанный путь.

## Контекст / Зачем

Анализ двух репозиториев Telegram для возможной интеграции:
1. `https://github.com/TelegramMessenger/telegram-login-android` — OAuth SDK (Kotlin)
2. `https://github.com/tdlib/td` — TDLib (C++ ядро Telegram-клиента)

---

## 1. telegram-login-android

### Что это
- **271 строка чистого Kotlin** OAuth SDK
- Один публичный класс `org.telegram.login.TelegramLogin`
- Apache 2.0 license
- Min SDK 23 (Android 6+)
- Поставляется через GitHub Packages: `org.telegram:login-sdk:1.0.0`

### Архитектура

```
init(clientId, redirectUri, scopes)
   ↓
startLogin(context)
   ↓
generate codeVerifier + codeChallenge (PKCE)
   ↓
GET https://oauth.telegram.org/crossapp?... → tg:// URL
   ↓
Intent(VIEW, tg://) — открывает Telegram app
   ↓ (если не установлен)
CustomTabsIntent → https://oauth.telegram.org/auth?...
   ↓ пользователь подтверждает в Telegram
   ↓
Telegram → app:// или https://app-login.tg.dev/tglogin?code=...
   ↓
onNewIntent → handleLoginResponse(uri)
   ↓
POST /token (grant_type=authorization_code + code + code_verifier)
   ↓
JWT id_token → onSuccess(LoginData)
   ↓
[наш backend] валидирует JWT через /jwks → создаёт/матчит юзера
```

### Что нужно для интеграции в BELSI

| Шаг | Что | Время |
|---|---|---|
| 1 | Регистрация бота через `@BotFather` → получить `client_id` | 10 мин |
| 2 | Добавить SHA-256 fingerprint нашего release keystore в BotFather | 5 мин |
| 3 | Подключить SDK через github-packages в `app/build.gradle.kts` | 15 мин |
| 4 | `TelegramLogin.init()` в `BelsiWorkApp.onCreate` | 5 мин |
| 5 | Кнопка «Войти через Telegram» в `LoginScreen` | 30 мин |
| 6 | `<intent-filter>` в `AndroidManifest.xml` для callback URL | 10 мин |
| 7 | Backend endpoint `POST /auth/telegram-login` — валидация JWT | 2 часа |
| 8 | Тест на Tecno | 30 мин |

**Итого: ~4 часа работы.**

### Backend плагин (что писать)

```python
# /opt/belsi-api/app/telegram_auth.py
from jwt import PyJWKClient
import jwt as pyjwt

JWKS_URL = "https://oauth.telegram.org/jwks"
EXPECTED_AUDIENCE = "<BELSI_CLIENT_ID>"

@router.post("/auth/telegram-login")
async def telegram_login(payload: TelegramLoginRequest, db: Session = Depends(get_db)):
    """Валидация id_token JWT от Telegram + create/match user."""
    jwks_client = PyJWKClient(JWKS_URL)
    signing_key = jwks_client.get_signing_key_from_jwt(payload.id_token).key
    
    try:
        claims = pyjwt.decode(
            payload.id_token,
            signing_key,
            algorithms=["RS256"],
            audience=EXPECTED_AUDIENCE,
            issuer="https://oauth.telegram.org",
        )
    except pyjwt.PyJWTError as e:
        raise HTTPException(401, f"Invalid Telegram JWT: {e}")
    
    telegram_id = claims["sub"]
    phone = claims.get("phone_number")
    first_name = claims.get("given_name")
    last_name = claims.get("family_name")
    
    # Match по oauth_provider + oauth_subject
    user = db.execute(
        text("""
            SELECT * FROM users
            WHERE oauth_provider = 'telegram' AND oauth_subject = :tid
            LIMIT 1
        """),
        {"tid": telegram_id},
    ).mappings().first()
    
    if not user and phone:
        # Fallback: match по phone (если до этого регистрировался через SMS)
        normalized = normalize_phone(phone)
        user = db.execute(
            text("SELECT * FROM users WHERE phone = :p LIMIT 1"),
            {"p": normalized},
        ).mappings().first()
        if user:
            # Привязываем Telegram identity к существующему юзеру
            db.execute(
                text("UPDATE users SET oauth_provider='telegram', oauth_subject=:tid WHERE id=:uid"),
                {"tid": telegram_id, "uid": user["id"]},
            )
    
    if not user:
        # Создаём нового
        new_user_id = db.execute(text("""
            INSERT INTO users (phone, first_name, last_name, role, oauth_provider, oauth_subject, signup_status)
            VALUES (:p, :fn, :ln, 'installer', 'telegram', :tid, 'active')
            RETURNING id
        """), {
            "p": phone or f"telegram:{telegram_id}",
            "fn": first_name, "ln": last_name, "tid": telegram_id,
        }).scalar()
    
    db.commit()
    # Issue JWT
    return {"access_token": create_jwt_token(...), "is_new": user is None}
```

### Преимущества для BELSI

- **В России Telegram ≫ Яндекс** по охвату у строителей/монтажников
- **Native flow** — Telegram app обрабатывает consent на устройстве
- **PKCE** — нет client_secret на клиенте, безопасно для open-source
- **phone_number scope** — авто-получаем телефон без SMS (если юзер дал согласие)
- **`oauth_provider` колонка уже есть** в users — не нужна миграция

### Риски

- Telegram app должен быть установлен — fallback на Custom Tabs работает, но UX хуже
- BotFather может потребовать модерацию для приложений, использующих `phone` scope
- JWKS endpoint может иметь rate-limit при массовом логине — нужно кэшировать ключи

---

## 2. tdlib/td

### Что это
- **C++17 библиотека** ядра Telegram-клиента
- **1489 .cpp/.h файлов**, 38 МБ source
- Cross-platform: Android (NDK + JNI), iOS, Windows, macOS, Linux, WebAssembly
- BSL 1.0 license
- **Реализует MTProto** — нативный протокол Telegram (не Bot API)

### Что внутри `example/android/`
- `Dockerfile` — изолированный build (Ubuntu 22)
- `build-tdlib.sh` (117 строк) — pipeline сборки .so для armv7/armv8/x86/x86_64
- `build-openssl.sh` — собственная сборка OpenSSL 1.1.1 (smaller binary)
- `fetch-sdk.sh` — скачивание Android SDK + NDK
- `CMakeLists.txt` — корневой CMake

### Что внутри `example/java/`
- `Client.java` (259 строк) — **главный async API**:
  ```java
  long queryId = atomicLong.incrementAndGet();
  handlers.put(queryId, resultHandler);
  nativeSend(clientId, queryId, query);  // JNI вызов
  // Async — результат придёт в handlers.remove(queryId).onResult(...)
  ```
- `JsonClient.java` (79 строк) — JSON-вариант (без TdApi классов)
- `example/Example.java` (791 строка) — **полный flow**:
  - Login по номеру телефона
  - Двухфакторка
  - Получение списка чатов
  - Отправка/получение сообщений
  - Updates handler (новые сообщения, изменения статуса)

### Use cases для BELSI

#### Сценарий 1: «Корпоративный Telegram-канал ↔ BELSI messenger»
- Куратор пишет в Telegram-бот → message попадает в нашу таблицу `messages`
- Монтажник получает push в Telegram (если без приложения BELSI)
- **Сложность:** требует TDLib (не Bot API)

#### Сценарий 2: «Telegram как fallback хостинг media»
- Telegram бесплатно хостит файлы и фото
- Voice-сообщения BELSI → upload в Telegram channel → URL в наш `messages.voice_url`
- Экономит S3/CDN bandwidth
- **Сложность:** требует TDLib

#### Сценарий 3: «Telegram-id как часть профиля»
- Связать BELSI-юзера с Telegram-аккаунтом
- Auto-add в корпоративный канал/группу при найме
- **Сложность:** можно сделать через Bot API (без TDLib) — намного проще

### Реальные затраты

| Что | Сложность |
|---|---|
| NDK toolchain (armv7/v8/x86/x86_64 cross-compile) | ⚙️⚙️⚙️ |
| OpenSSL build для Android | ⚙️⚙️ |
| TDLib build (~30 мин один раз) | ⚙️⚙️ |
| ~50 МБ к APK (4 arch × ~12 МБ) | 📦📦 |
| Encryption key management (TDLib шифрует локальную БД) | 🔐🔐🔐 |
| Async API + Updates state machine | 🧠🧠🧠 |
| MTProto rate limits | 🕐 |
| **Production-ready интеграция** | **2-3 месяца** |

### Рекомендация

**TDLib НЕ внедрять сейчас.** Это серьёзный фундамент, который имеет смысл только если в roadmap есть **«BELSI = первый класс Telegram-клиент для бригадиров»**.

Что **можно** взять прямо сейчас:

- **`Client.java` (259 строк)** — архитектурный референс для async-pattern. Если будем переписывать наш `messenger/ConversationViewModel` с polling на WebSocket или event-stream — `ConcurrentHashMap<queryId, ResultHandler>` + `AtomicLong` это правильный паттерн.
- **`Example.java` (791 строка)** — reference для понимания как pure-Java Telegram-клиент устроен (login flow → chat list → messages → updates).

### Альтернативный путь — Bot API

Для сценария 3 (профили + push) и частично сценария 1 — можно обойтись **без TDLib** через Telegram Bot API:
- `python-telegram-bot` или прямые HTTP-вызовы
- ~200 строк на бэке
- Бот шлёт push в Telegram, юзер отвечает → webhook в наш бэк
- **Не требует TDLib, не требует NDK builds**

Это гораздо проще и достаточно для 80% корпоративных use case'ов.

---

## Итоговая рекомендация

### Применять сейчас (если решим)

**🟢 telegram-login-android — 4 часа работы:**
- Третий способ авторизации (password / Yandex / Telegram)
- Native flow для бригад без корпоративной почты
- Backend endpoint + JWT validation + match по `oauth_subject`

### Backlog (отложено)

**🟡 TDLib — 2-3 месяца работы:**
- Только если решим что BELSI должен быть полноценным Telegram-клиентом
- Альтернатива через Bot API закрывает 80% use case'ов значительно дешевле

### Взять как референс прямо сейчас

- `Client.java` (259 строк) — async query pattern для async-event-driven messengers (если будем переписывать наш polling-based на WebSocket)

---

## Файлы / артефакты

- `/tmp/telegram-login-android/` — клонировано, 320 КБ
- `/tmp/td/` — клонировано, 38 МБ (без истории)

Эти клоны не сохраняются в репо BELSI — только этот документ.

## Commit
Анализ остался в файле — без code-изменений в репо.
