# Развёртывание BELSI API 1.3.0 (BELSI.Команда)

> **Принцип**: ничего не запускать на проде до явной команды. Этот документ —
> чек-лист для аккуратного, без-простойного развёртывания.

## 0. Предусловия

- Прод 1.2.5 (versionCode=11) работает, 14 активных монтажников
- alembic_version в БД = `425f115b569b` (init_schema)
- Реальная схема БД ушла дальше: ai_comment в shift_photos, role_change_log
  таблица, latitude/longitude в site_objects, signup-* колонки, last_seen,
  password_hash, fcm_token, current_site_object_id и т.д. — всё это было
  добавлено raw SQL без обновления alembic_version

## 1. Бэкап (обязательный)

```bash
# Бэкап кода
ssh root@94.228.123.95 'cp -r /opt/belsi-api/app /opt/belsi-api/app.bak.before_1.3.0_$(date +%s)'

# Бэкап БД
ssh root@94.228.123.95 'PGPASSWORD="..." pg_dump -h 192.168.56.5 -U gen_user default_db | gzip > /root/db_before_1.3.0.sql.gz'
```

## 2. Сверка alembic с реальной схемой

Прод alembic_version = `425f115b569b`, но фактически применены миграции
`a1b2c3d4e5f6` и `b2c3d4e5f6g7` (мы это видим по существующим колонкам).
Чтобы alembic не пытался их применить заново (column already exists):

```bash
ssh root@94.228.123.95 'cd /opt/belsi-api && ./venv/bin/alembic stamp b2c3d4e5f6g7'
```

После этого `alembic current` показывает `b2c3d4e5f6g7`.

## 3. Применение новых 1.3.0 миграций

```bash
ssh root@94.228.123.95 'cd /opt/belsi-api && ./venv/bin/alembic upgrade head'
```

Применятся:
- `c3d4e5f6g7h8` yandex_profile_enrich → +4 колонки в users (birthday,
  avatar_url, oauth_provider, oauth_subject) — все nullable, безопасно
- `d4e5f6g7h8i9` production_batches → +3 таблицы (production_batches,
  batch_status_history, shift_idle_reason_catalog) + lunch_seconds,
  break_seconds, facility_id, domain в shifts — все аддитивно

Все миграции 1.3.0 используют `IF NOT EXISTS` где возможно, поэтому
безопасно даже на частично применённых состояниях.

## 4. Деплой кода (с сохранением !)

**КРИТИЧНО**: 5 файлов в /opt/belsi-api/app которые НЕ в репо подтянуты:
- `auth_login.py` (password login)
- `shift_admin.py` (curator shift admin)
- `user_stats.py` (user stats)
- `version_router.py` (force-update)
- `admin_devices.py` (FCM device management)

Они **уже в репо** на ветке feature/driver-integration. Если деплоить через
git pull — всё в порядке. Если через rsync — НЕ удалять /opt/belsi-api/app
целиком, копировать с overwrite:

```bash
rsync -av --delete-after backend/belsi-api/app/ root@94.228.123.95:/opt/belsi-api/app/
# (--delete-after безопасно, так как все файлы прода уже в репо)
```

## 5. Перезапуск

```bash
ssh root@94.228.123.95 'systemctl restart belsi-api'
sleep 3
ssh root@94.228.123.95 'systemctl status belsi-api | head -15'
```

## 6. Smoke-тесты (curl)

```bash
# Старые 1.2.5 endpoints должны продолжать работать
curl -sI https://api.belsi.ru/version/check       # 200
curl -sI https://api.belsi.ru/openapi.json        # 200

# Новые 1.3.0 endpoints должны появиться
curl -sI https://api.belsi.ru/production/batches  # 401 (auth required) — OK
curl -sI https://api.belsi.ru/shift/idle-reasons  # 401 — OK
```

## 7. Откат (если что-то пошло не так)

```bash
# Восстановить код
ssh root@94.228.123.95 'rm -rf /opt/belsi-api/app && mv /opt/belsi-api/app.bak.before_1.3.0_* /opt/belsi-api/app'

# Откатить alembic
ssh root@94.228.123.95 'cd /opt/belsi-api && ./venv/bin/alembic downgrade b2c3d4e5f6g7'

# Перезапустить
ssh root@94.228.123.95 'systemctl restart belsi-api'
```

## 8. После деплоя backend (но до APK)

Прод-монтажники на 1.2.5 продолжают работать как раньше:
- Все 1.2.5 endpoints на месте
- Новые колонки в users — nullable, не мешают INSERT'ам 1.2.5
- Новые таблицы изолированы (старый код их не трогает)

Только после смоук-тестов и согласования — релиз APK 1.3.0.

## 9. AI-интеграция XeroCode (BELSI 1.3.0)

XeroCode v1.4 уже задеплоен на xerocode.ru (2026-05-10).

### 9.1 Создать service-account на XeroCode

```bash
curl -X POST https://xerocode.ru/api/admin/service-accounts \
  -H "Authorization: Bearer <YOUR_USER_JWT>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "belsi-prod",
    "description": "BELSI.Монтаж production AI integration",
    "allowed_endpoints": ["analyze-image", "generate", "transcribe", "usage"],
    "rate_limit_per_minute": 60,
    "rate_limit_per_day": 5000,
    "monthly_budget_usd": 50.0
  }'
```

⚠ `token_plaintext` показывается ОДИН РАЗ. Сохранить.

### 9.2 Smoke-тест локально (рекомендуется до деплоя BELSI)

```bash
export XEROCODE_SERVICE_TOKEN=belsi_xxxxx_yyyyy
python3 backend/belsi-api/scripts/smoke_test_xerocode.py
```

Должны пройти 5/5: health, usage, templates, analyze-image, generate.

### 9.3 Применить миграцию ai_analyses на проде

```bash
ssh root@94.228.123.95 'cd /opt/belsi-api && ./venv/bin/alembic upgrade head'
```

Применит f6g7h8i9j0k1 (создаст таблицу ai_analyses).

### 9.4 Добавить токен в .env BELSI

```bash
ssh root@94.228.123.95 'cat >> /opt/belsi-api/.env << EOF

# XeroCode AI Gateway (BELSI 1.3.0)
XEROCODE_API_URL=https://xerocode.ru/api/v1/external
XEROCODE_SERVICE_TOKEN=belsi_xxxxx_yyyyy
XEROCODE_TIMEOUT_SEC=30
XEROCODE_ENABLED=true
EOF'

ssh root@94.228.123.95 'systemctl restart belsi-api'
```

### 9.5 Smoke-проверка на проде BELSI

```bash
# Куратор делает запрос: POST /curator/ai-daily-summary
# Через минуту проверить:
ssh root@94.228.123.95 'PGPASSWORD="..." psql -h 192.168.56.5 -U gen_user -d default_db -c "
  SELECT id, analysis_type, model_used, provider_used, cost_usd, created_at
  FROM ai_analyses
  ORDER BY created_at DESC LIMIT 5
"'
```

### 9.6 Эмердженси-вырубание AI

Если что-то пошло не так:
```bash
ssh root@94.228.123.95 "sed -i 's/^XEROCODE_ENABLED=.*/XEROCODE_ENABLED=false/' /opt/belsi-api/.env"
ssh root@94.228.123.95 "systemctl restart belsi-api"
```

AI-вызовы становятся no-op, BELSI работает в режиме 1.2.5 (старый Pillow для photo_analysis).

### 9.7 Мониторинг 2 недели после деплоя

Раз в день проверять:
```bash
ssh root@94.228.123.95 'PGPASSWORD="..." psql -h 192.168.56.5 -U gen_user -d default_db -c "
  SELECT
    analysis_type,
    provider_used,
    COUNT(*) AS calls,
    AVG(duration_ms) AS avg_ms,
    SUM(cost_usd) AS total_cost,
    SUM(CASE WHEN paid_fallback_used THEN 1 ELSE 0 END) AS paid_calls
  FROM ai_analyses
  WHERE created_at > NOW() - INTERVAL '"'"'1 day'"'"'
  GROUP BY analysis_type, provider_used
  ORDER BY 3 DESC
"'
```

Что важно:
- **paid_calls должно быть 0** (allow_paid_fallback=False по умолчанию)
- **provider_used = groq** должен быть в большинстве случаев
- **total_cost суток < $1** при normal use
- Если вижу паттерн ошибок — переключить primary провайдер в matrix v1.5
