# 2026-05-13 — Tool Return Flow — Phase 1 (Backend)

## Контекст / Зачем

В системе была реализована forward-передача инструмента: Комплектатор → Водитель → Объект → Монтажник. **Но возврата не было.**

Старая система (`tool_transactions`) поддерживала только локальный возврат внутри одной бригады. Новая (`tool_transfers`) имела статусы `returning` / `returned` / `lost` в `CHECK constraint`, но **без endpoint'ов**.

Пользователь утверждение: «Нужно сделать максимально и без урезания. У нас есть система кто как передает — думаю эти же люди в таком же формате просто обратный порядок будут возвращать на завод. Плюс ещё куратора можно реализовать эту возможность».

## Скоуп Phase 1 (только backend)

### Входит
- БД миграция: расширение `tool_transfers` (+17 колонок + 4 новых статуса)
- 9 новых endpoint'ов для return-flow + curator-функций
- Push-нотификации (6 типов) + deep-link data
- Object timeline integration

### НЕ входит (отложено в Phase 2/3)
- Android UI
- Camera integration
- Реальное тестирование с устройства

## State machine (расширенная)

```
forward (как было):
pending → in_transit → delivered → accepted → in_use
                                      ↑↓
                                    (release)

return flow (НОВОЕ):
                  ┌── от любого accepted/in_use ──┐
                  ↓
accepted/in_use → returning_requested  (инициатор: installer/foreman/curator/supplier/driver)
                  ↓ [supplier/curator назначают водителя]
                  returning            (водитель назначен, ждёт pickup)
                  ↓ [водитель забрал с объекта]
                  returning_in_transit
                  ↓ [водитель доехал до завода]
                  returning_delivered
                  ↓ [комплектатор принял]
                  returned             ← финальный статус
                  
                  ↘ [комплектатор отказал]
                  return_rejected      → разбор через куратора
```

## Backend changes

### Миграция: `migration_tool_return.sql`

**Новые статусы в CHECK constraint:**
- `returning_requested`
- `returning_in_transit`
- `returning_delivered`
- `return_rejected`

**+17 колонок в `tool_transfers`:**
```sql
return_requested_at         timestamp
return_requested_by         uuid
return_reason               text
return_request_photo_url    text
return_driver_user_id       uuid
return_pickup_at            timestamp
return_pickup_photo_url     text
return_delivered_at         timestamp
return_delivered_photo_url  text
return_accepted_at          timestamp
return_accepted_by          uuid
return_accept_photo_url     text
return_accepted_quantity    integer
return_rejected_at          timestamp
return_reject_reason        text
return_route_id             uuid
return_route_point_id       uuid
```

**Индексы:**
```sql
idx_tool_transfers_return_driver   ON tool_transfers(return_driver_user_id) WHERE return_driver_user_id IS NOT NULL;
idx_tool_transfers_in_return       ON tool_transfers(status) WHERE status IN ('returning_*');
```

⚠ **Колонка `updated_at` была пропущена в этой миграции!** Найдено в Phase 3 при реальном тесте через SQL. Добавлено отдельным ALTER.

### Endpoints (9 новых)

| Метод | Endpoint | Кто | Status transition |
|---|---|---|---|
| POST | `/tools/transfers/{id}/return-request` | все 5 ролей | accepted\|in_use → **returning_requested** |
| POST | `/supplier/tool-transfers/{id}/return-assign-driver` | supplier\|curator | returning_requested → **returning** |
| POST | `/tools/transfers/{id}/return-pickup` | driver (assigned) | returning → **returning_in_transit** |
| POST | `/tools/transfers/{id}/return-deliver` | driver (assigned) | returning_in_transit → **returning_delivered** |
| POST | `/supplier/tool-transfers/{id}/return-accept` | supplier | returning_delivered → **returned** ✅ |
| POST | `/supplier/tool-transfers/{id}/return-reject` | supplier | returning_delivered → **return_rejected** ⚠️ |
| GET | `/curator/tool-transfers/in-return` | curator | overview active returns |
| POST | `/curator/tool-transfers/{id}/resolve-rejection` | curator | return_rejected → returned\|lost |
| POST | `/curator/tool-transfers/{id}/cancel-return` | curator | returning* → in_use\|accepted (откат) |

### Permissions matrix

| Роль | Может инициировать | Может назначить водителя | Может pickup/deliver | Может accept |
|---|:---:|:---:|:---:|:---:|
| installer (в руках) | ✅ | — | — | — |
| foreman бригады | ✅ | — | — | — |
| curator | ✅ (force) | ✅ | — | — (через resolve) |
| supplier | ✅ (отозвать) | ✅ | — | ✅ |
| driver | ✅ | — | ✅ (assigned only) | — |

### Push-нотификации (7 типов)

```
tool_transfer_return_requested      → комплектатору + куратору
tool_transfer_return_driver_assigned → водителю
tool_transfer_return_picked_up      → комплектатору
tool_transfer_return_ready_to_accept → комплектатору
tool_transfer_return_completed      → инициатору + куратору
tool_transfer_return_rejected       → куратору (требует разбора)
```

Все push содержат `data.kind = tool_transfer_*` + `data.transfer_id` для deep-link.

### Object Timeline

В `brand_core.py` (через `_add_return_timeline_event`):
- `tool_return_requested` — на объекте откуда возвращаем
- `tool_return_picked_up` — водитель забрал
- `tool_return_delivered` — на завод
- `tool_return_accepted` — финал

## Code locations

**Backend файл:** `/opt/belsi-api/app/tool_transfers.py` (расширен на ~650 строк).

**Хелперы добавленные:**
- `_check_can_initiate_return(db, user, row)` — проверка прав
- `_notify_about_return(db, transfer_id, event, tool_name, recipients, extra)` — push
- `_add_return_timeline_event(db, transfer_id, event, site_object_id, actor_id, comment)`
- `_get_supplier_user_ids(db)` / `_get_curator_user_ids(db)` — массовая рассылка

## Outcome

- **Файлов изменено:** 1 (`tool_transfers.py`) + 1 миграция SQL
- **Endpoints добавлено:** 9 (плюс 1 для list_drivers в Phase 3)
- **Pydantic schemas:** 7 (`ReturnRequest`, `ReturnAssignDriverRequest`, ...)
- **Время реализации:** ~2 часа

## Open issues / TODO

- ⚠ **Critical:** `updated_at` колонка отсутствовала — нашли в Phase 3 при тесте. Зафиксено через `ALTER TABLE tool_transfers ADD COLUMN updated_at`. **Проверять колонки заранее в новых миграциях.**

## Связанные plan-файлы

- `2026-05-13-tool-return-flow-phase2-android.md` — Android UI
- `2026-05-14-tool-return-flow-phase3-camera-push.md` — реальная камера + push deep-link

## Commit
`5fe8ace ... e9d3e92` в `release/2.0.1-internal`
