# BELSI — Plan files

Per-task plan-документы. Каждый plan = одна большая фича или фаза рефакторинга.

**Формат имени:** `YYYY-MM-DD-<feature-name>[-phaseN-area].md`

**Зачем нужно:**
- Каждая большая фича документируется отдельно — не «всё в одной куче».
- AI-ассистент может загружать только нужный план в контекст, не пытаясь переварить всю историю.
- Один plan-файл = один документ требований → исполнение → результат.

**Шаблон plan-файла:**

```markdown
# YYYY-MM-DD — <Title> [Phase N]

## Контекст / Зачем
Что было плохо, почему делаем эту фичу.

## Скоуп
Что входит. Что НЕ входит (явно).

## Backend changes
- Миграция: ...
- Endpoints: ...
- Push: ...

## Android changes
- DTO: ...
- API: ...
- Repository: ...
- ViewModel: ...
- Screens: ...

## State machine / Data model
ASCII-диаграммы переходов или SQL.

## Тестирование
Что проверяли вручную / через SQL. Что не проверено.

## Outcome
Файлы изменены, строки, время реализации.

## Open issues / TODO
Известные ограничения. Что нужно доделать.

## Связанные plan-файлы
Ссылки на смежные фазы.
```

## Список планов

См. файлы по дате — самые новые сверху.

### 2026-05
- `2026-05-14-tool-return-flow-phase3-camera-push.md` — реальная камера + push deep-link + driver-picker
- `2026-05-13-tool-return-flow-phase2-android.md` — Android UI для возврата
- `2026-05-13-tool-return-flow-phase1-backend.md` — backend + state machine

### Backlog (на будущее)
- AI alerts unified feed (Phase 2 — UI улучшения, batch-actions)
- Production smoke-test (создание тестовой фабрики + chief + senior + workers)
- AudioWaveform integration (port из Telegram-iOS)
