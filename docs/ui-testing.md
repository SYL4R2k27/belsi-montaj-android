# UI Testing — BELSI

Тестовая среда для ручного и автоматизированного UI-теста. Активируется через intent extras при запуске MainActivity. Работает **только в debug-сборках**.

> По образцу `Telegram-iOS/docs/ui-testing.md` — у Telegram отдельная test-DC, у нас — отдельная test-mode + X-Belsi-Test header.

## Запуск в test-mode

### Через adb

```bash
# Базовый запуск (без очистки данных, но logout всех сессий)
adb shell am start -n com.belsi.work.debug/com.belsi.work.MainActivity --es ui-test true

# С очисткой Room/cache
adb shell am start -n com.belsi.work.debug/com.belsi.work.MainActivity \
  --es ui-test true \
  --es clear-test-data true
```

### Через Android Studio

В Run/Debug Configurations → "Launch Options" → "Launch Flags":
```
--es ui-test true --es clear-test-data true
```

## Что происходит при активации

1. **`TestMode.isTestRun = true`** — глобальный singleton флаг.
2. **`prefsManager.clearAll()`** — полный logout. Никаких сохранённых токенов/PIN/last-login context.
3. **Если `clear-test-data=true`** — `TestMode.shouldClearTestData = true`. Room-кэш будет очищен на следующей инициализации (TODO: hook в `AppDatabase.Companion.getInstance`).
4. **`AuthInterceptor`** добавляет HTTP header `X-Belsi-Test: 1` ко всем запросам.
5. **Backend** видит этот header и **НЕ дёргает sms.ru**. Код OTP = **последние 4 цифры номера**.

## Тестовые номера и коды

| Номер | Test code |
|---|---|
| `+79991234567` | `4567` |
| `+79806506189` | `6189` |
| `+79175379911` | `9911` |
| `+79991234` | `1234` (короткий номер) |

Логика — `TestMode.testCodeFor(phone)`: фильтруем цифры из номера, берём последние 4, паддим нулями если меньше 4.

## Сценарий тестирования OTP

```bash
# 1. Запустить app в test-mode (logout будет автоматически)
adb shell am start -n com.belsi.work.debug/com.belsi.work.MainActivity --es ui-test true

# 2. На UI: ввести любой тестовый номер, например +7 999 123-45-67

# 3. Бэк вернёт `{"status":"ok","test_mode":true}` — НЕ дёрнул sms.ru.

# 4. На экране OTP ввести 4567

# 5. Бэк проверит код через otp_service.verify_code(phone, code) — пройдёт.
```

## Безопасность

⚠️ **`X-Belsi-Test: 1` header работает ТОЛЬКО на endpoint `/auth/phone`** — это единственное место где он что-то меняет (skip SMS, deterministic code).

На всех остальных endpoint'ах он **игнорируется** (но логируется для трейсинга).

⚠️ **Test-mode активируется ТОЛЬКО в `BuildConfig.DEBUG=true`** — в release APK метод `handleUiTestArgs()` — no-op.

⚠️ **Никогда не запускайте test-mode на production-устройствах реальных пользователей** — `clearAll()` сотрёт их сессии.

## Test users

В БД production-роли пока пустые (0 юзеров). Для теста workflow создавайте через прямой SQL — но **не через UI**, потому что test-mode skip только SMS, а user record по-прежнему создаётся через `/auth/register`.

Для смок-теста production-цепочки см. `docs/plans/2026-05-XX-production-smoke-test.md` (будущий план).

## Cleanup

После теста — просто:
```bash
adb shell pm clear com.belsi.work.debug
```
или перезапустите без `--es ui-test true` — `TestMode.reset()` при следующем onCreate (no extras → singleton остаётся false).

## Будущие расширения

1. **Test-DB на отдельном порту** (`<internal-db-host>:5433`) — сейчас все test-запросы идут в production-БД. Это OK для smoke-тестов, но опасно для destructive-тестов.
2. **Test JWT с особыми claim'ами** (`is_test=true`) — backend по факту JWT определит test-сессию даже без header'а.
3. **`--es delete-test-account <phone>`** — по образцу Telegram, для cleanup test-юзеров без UI.
4. **XCUITest эквивалент для Android** (Espresso suite) — пока нет.

## Внутренние invariants

- `TestMode.isTestRun` остаётся `false` пока MainActivity не вызвала `TestMode.activate()`.
- При cold-start без `--es ui-test true` приложение работает **точно как в продакшене**.
- При уничтожении процесса (force-stop) `TestMode` resets автоматически (singleton живёт в JVM).
