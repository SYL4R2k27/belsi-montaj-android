# 2026-05-13 — Tool Return Flow — Phase 2 (Android UI)

## Контекст / Зачем

После Phase 1 (backend) — Android UI для return-flow. State machine, action-кнопки по статусу, новые экраны.

## Скоуп Phase 2

### Входит
- 9 новых методов в `ToolTransferRepository`
- 5 новых экранов
- Расширение `ToolTransferDetailScreen` action-кнопками по статусу
- 5 routes в `AppRoute` + `NavGraph`
- 1 новый `CuratorReturnsViewModel`
- Обновление существующего `ToolTransferDetailViewModel` (+6 методов)

### НЕ входит
- Реальная камера (placeholder `"pending://camera"`) — Phase 3
- Push deep-link на pickup/deliver — Phase 3
- Driver-picker — Phase 3
- Тестирование на устройстве

## DTOs (`ToolTransferDto.kt` +7 классов)

```kotlin
@Serializable data class ReturnRequestBody(reason, photoUrl)
@Serializable data class ReturnAssignDriverBody(driverUserId, routeId)
@Serializable data class ReturnPickupBody(photoUrl, comment)
@Serializable data class ReturnDeliverBody(photoUrl)
@Serializable data class ReturnAcceptBody(acceptedQuantity, photoUrl, comment)
@Serializable data class ReturnRejectBody(reason)
@Serializable data class CuratorResolveRejectionBody(action, comment)

// ToolTransferDto +15 полей (return_* timestamps + photo_urls + by/reason)
```

## API methods (`ToolTransferApi.kt` +9)

Соответствуют backend endpoints (см. Phase 1).

## Repository methods (`ToolTransferRepository.kt` +9 suspend методов)

```kotlin
returnRequest(transferId, reason, photoUrl): Result<Unit>
returnAssignDriver(transferId, driverUserId, routeId): Result<Unit>
returnPickup(transferId, photoUrl, comment): Result<Unit>
returnDeliver(transferId, photoUrl): Result<Unit>
returnAccept(transferId, acceptedQuantity, photoUrl, comment): Result<Unit>
returnReject(transferId, reason): Result<Unit>
curatorInReturn(): Result<List<ToolTransferDto>>
curatorResolveRejection(transferId, action, comment): Result<Unit>
curatorCancelReturn(transferId, reason): Result<Unit>
```

## ViewModels

### ToolTransferDetailViewModel (+6 return-методов)
Каждый с `onSuccess: () -> Unit` callback для navigation:
- `returnRequest`, `returnPickup`, `returnDeliver`, `returnAccept`, `returnReject`, `returnAssignDriver`

### CuratorReturnsViewModel (новый)
- `state: StateFlow<CuratorReturnsState>` (transfers, isLoading, isActing, error)
- `load()`, `resolveRejection(id, action, comment)`, `cancelReturn(id, reason)`

## Новые экраны (5 в файле `ToolReturnScreens.kt`)

| Экран | Кто открывает | Что делает |
|---|---|---|
| `ToolReturnRequestScreen` | installer/foreman/driver/supplier/curator | вводит причину → returning_requested |
| `ToolReturnPickupScreen` | driver | фото-placeholder + комментарий → returning_in_transit |
| `ToolReturnDeliverScreen` | driver | фото-placeholder → returning_delivered |
| `ToolReturnAcceptScreen` | supplier | принятое кол-во + комментарий → returned |
| `CuratorReturnsScreen` | curator | список + actions «Всё-таки принять» / «Списать» |

## Расширение DetailScreen — action-кнопки по статусу

```kotlin
when (t.status) {
    "delivered", "in_transit" → [Принять] / [Отказать]
    "accepted"   → [Взять в работу] + [⏪ Вернуть на завод]   // НОВОЕ
    "in_use"     → [Положить обратно] + [⏪ Вернуть на завод] // НОВОЕ
    
    // НОВЫЕ статусы:
    "returning_requested"  → ⏳ ждём назначения водителя
    "returning"            → 📦 driver: «Забрал с объекта» → ToolReturnPickup
    "returning_in_transit" → 🏭 driver: «Доставил» → ToolReturnDeliver
    "returning_delivered"  → ✅ supplier: «Принять» → ToolReturnAccept
                            + кнопка «Отклонить возврат»
    "return_rejected"      → ⚠ красная карточка «Куратор должен разобрать»
    "returned"             → ✅ «Инструмент успешно возвращён в фонд»
}
```

## Routes (5 новых)

```kotlin
object ToolReturnRequest : AppRoute("tools/return-request/{transferId}")
object ToolReturnPickup  : AppRoute("tools/return-pickup/{transferId}")
object ToolReturnDeliver : AppRoute("tools/return-deliver/{transferId}")
object ToolReturnAccept  : AppRoute("tools/return-accept/{transferId}")
object CuratorReturns    : AppRoute("curator/returns")
```

В `NavGraph.kt` — composable для каждого.

## Outcome

- **Файлов изменено:** 8
- **Строк добавлено:** 1098 (+ удалено 14)
- **Новых экранов:** 5
- **Новых routes:** 5
- **Compile:** BUILD SUCCESSFUL
- **APK:** установлен на Tecno KL4

## Open issues / TODO → Phase 3

- ⚠ **Фото placeholder `"pending://camera"`** — нужно реальная интеграция через TakePicture intent + `/tools/photos` upload
- ⚠ **Driver-picker UI отсутствует** — supplier при assign-driver не имеет выбора, только через backend напрямую
- ⚠ **Push deep-link на pickup/deliver конкретные экраны** не сделан — открывается DetailScreen (это даже OK для UX)
- ⚠ **CuratorMainScreen entry-point для CuratorReturns** не добавлен

## Связанные plan-файлы

- `2026-05-13-tool-return-flow-phase1-backend.md` — backend
- `2026-05-14-tool-return-flow-phase3-camera-push.md` — Phase 3

## Commit
`0bb671c` в `release/2.0.1-internal`
