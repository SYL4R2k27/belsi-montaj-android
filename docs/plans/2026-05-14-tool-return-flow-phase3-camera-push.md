# 2026-05-14 — Tool Return Flow — Phase 3 (Camera + Push + Driver-picker)

## Контекст / Зачем

После Phase 1 (backend) и Phase 2 (Android UI с placeholder фото) — финальный полишинг:
- Реальная камера вместо `"pending://camera"` placeholder
- Driver-picker bottom-sheet (раньше supplier мог assign только через backend)
- Push deep-link на CuratorReturns при return-событиях
- Entry-point в curator-меню «⏪ Возвраты инструмента»

## Скоуп Phase 3

### Входит
- `ReturnPhotoCapture.kt` — shared composable + ViewModel для photo capture+upload
- Driver-picker BottomSheet в DetailScreen
- Backend endpoint `GET /tools/transfers/drivers` (supplier+curator)
- Backend `/tools/photos` — расширены роли (+driver, +logistician, +supplier)
- MainActivity push deep-link: `kind=*_return_*` → `CuratorReturns`
- CuratorMainScreen overflow menu: «⏪ Возвраты инструмента»
- Real-data testing на «Малая Тульская 15»

### НЕ входит
- Полная push-маршрутизация на pickup/deliver конкретные экраны (DetailScreen достаточно)
- Bulk approve в CuratorReturns

## Findings во время тестирования

**🐛 Critical bug найден:** колонка `tool_transfers.updated_at` отсутствовала.
В Phase 1 я писал `UPDATE tool_transfers SET ... updated_at = NOW()` — endpoints бы упали. Зафиксено через:
```sql
ALTER TABLE tool_transfers ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW();
```

Проверка через прямой SQL — state machine работает: `accepted → returning_requested → returning → returning_in_transit → returning_delivered → returned` ✅

## Camera integration (`ReturnPhotoCapture.kt`)

### Архитектура

```
ReturnPhotoCaptureViewModel (Hilt)
  └─ state: ReturnPhotoCaptureState(photoUri, photoUrl, isUploading, error)
  └─ onPhotoTaken(uri, file) — multipart upload через ToolsApi.uploadToolPhoto

ReturnPhotoCaptureController (composable scope wrapper)
  └─ context, state, onPhotoTaken, reset

ReturnPhotoCard (composable)
  └─ OutlinedCard with onClick = launchCamera
  └─ AsyncImage preview если фото сделано
  └─ AssistChip status (загрузка / ✓ готово / ошибка)
  └─ «переснять» button
```

### Использование

```kotlin
val photoCapture = rememberReturnPhotoCapture()
val photoState by photoCapture.state.collectAsState()

ReturnPhotoCard(
    capture = photoCapture,
    title = "Фото при заборе",
    description = "Сфотографируй инструмент в руках на объекте",
)

RolePrimaryButton(
    text = when {
        state.isActing -> "Отправка..."
        photoState.isUploading -> "Загрузка фото..."
        photoState.photoUrl == null -> "Сначала фото →"
        else -> "Забрал — еду на завод"
    },
    onClick = { photoState.photoUrl?.let { viewModel.returnPickup(transferId, it, comment) } },
    enabled = !state.isActing && photoState.photoUrl != null,
)
```

### Расширение backend `/tools/photos`

Раньше: только `foreman, installer, curator`. Расширено:
```python
if user.role not in ["foreman", "installer", "curator", "driver", "logistician", "supplier"]:
```

## Driver-picker

### Backend `GET /tools/transfers/drivers`
Только для supplier+curator. Возвращает:
```python
{
  "id": uuid, "name": str, "phone": str,
  "busy": bool  # есть активные передачи (forward или return)
}
```
Сортировка: свободные → занятые.

### Android: `DriverPickerBottomSheet` в `ToolTransferDetailScreen.kt`
- ModalBottomSheet с list водителей
- AssistChip «свободен» (primaryContainer) / «занят» (disabled)
- Click на карточку → `viewModel.returnAssignDriver(transferId, driverId)` → закрытие
- `DriverPickerViewModel` — Hilt

## Push deep-link

В `MainActivity.handlePushIntent`:
```kotlin
extras.getString("kind")?.startsWith("tool_transfer") == true -> {
    val kind = extras.getString("kind") ?: ""
    val tid = extras.getString("transfer_id")
    val isReturn = kind.contains("return")
    when {
        !tid.isNullOrBlank() -> AppRoute.ToolTransferDetail.createRoute(tid)
        isReturn -> AppRoute.CuratorReturns.route
        else -> AppRoute.ToolTransferHub.createRoute("incoming")
    }
}
```

## Curator entry-point

В `CuratorMainScreen.kt` overflow-меню:
```kotlin
DropdownMenuItem(
    text = { Text("⏪ Возвраты инструмента") },
    onClick = { navController.navigate(AppRoute.CuratorReturns.route) },
    leadingIcon = { Icon(Icons.Default.AssignmentReturn, null) },
)
```

## Real-data test (через SQL)

На инструменте «Лазеры × 3» (id `66b8552e-...`):

```
accepted → returning_requested → returning → returning_in_transit
        → returning_delivered → returned ✅

tool.holder_user_id = NULL ✅
tool.site_object_id = NULL ✅
return_accepted_quantity = 3 ✅
```

После теста инструмент возвращён в `accepted` для UI-теста.

## Outcome

- **Файлов изменено:** 8
- **Строк добавлено:** 527
- **APK:** `BELSI_Montaj_2.0.1-driver_build1_debug.apk` (43.5 МБ) установлен на Tecno

## Open issues / TODO (post-2.0.1)

- Алерт-бейдж «N открытых возвратов» в куратор-меню
- Supplier role-routing в MainActivity (если появятся supplier-юзеры в БД)
- Bulk action в CuratorReturns (массовое resolve-rejection)

## Связанные plan-файлы

- `2026-05-13-tool-return-flow-phase1-backend.md` — backend
- `2026-05-13-tool-return-flow-phase2-android.md` — Phase 2

## Commit
`e9d3e92` в `release/2.0.1-internal`
