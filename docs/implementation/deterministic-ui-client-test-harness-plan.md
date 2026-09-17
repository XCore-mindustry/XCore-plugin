# План реализации детерминированного UI-стенда

## Статус

Этапы 1–4 плана **полностью завершены и проверены тестами**:
- `mindustry-testkit`: `core` и `ui` с очередью, binary snapshot, `HeadlessMenuClient`, `DeterministicUiLoop`, `UiWireMessage`, `UiTranscript`, и actual-client oracle (`ActualDialogHideTest`, `ActualMenusOracleTest`) с SHA-256 fingerprinting артефактов.
- `xcore-ui`: runtime adapter и `UiSessionClientIntegrationTest`.
- `XCore-plugin`: `MapUiClientIntegrationTest` со всеми 9 сценариями MVP (UI-01..UI-09).
- Исправлены 2 дефекта в `MapUiController`: UI-06 (гонка устаревших запросов деталей) и UI-09 (сброс live RTV при получении статистики).
- Документ ADR переведён в статус `Accepted`.

Остались задачи за пределами MVP стенда: вынос в удалённый CI/Maven, решение проблемы Escape без клика vs серверный cleanup.

- [Спецификация и матрица UI-01–UI-09](../architecture/deterministic-ui-client-test-harness.md)
- [ADR и альтернативы](../adr/ADR-deterministic-ui-client-test-harness.md)

Порядок работ: контракт → общий стенд → plugin integration → отдельные подтверждённые исправления → CI. Не менять серверы и не деплоить в рамках реализации тестовой инфраструктуры.

## Этап 1. Зафиксировать реальный клиентский контракт

- [x] Определить разрешённые Gradle зависимости в xcore-ui и XCore-plugin; записать координаты и SHA-256 фактически загруженных JAR (`Menus.class`: `283c9b...`, `Core.class`: `c2df13...`).
- [x] Сверить Menus, UiTreeBuilder, MenuResult, TypeIO и Arc TaskQueue с этими артефактами, а не только соседним checkout.
- [x] Описать поддерживаемые flags, widgets и wire shape; Update/Hide без token.
- [x] Подготовить короткие transcripts: replacement до/после клика, dismiss, server hide, patch существующей/отсутствующей цели, stale descendant ID.
- [x] Выполнить ограниченный spike настоящего Menus/Scene без draw; зафиксировать результат и зависимости (`ActualDialogHideTest` и `ActualMenusOracleTest` в `mindustry-testkit`).
- [x] Доказано: Xvfb не требуется, actual oracle работает в plain JVM с `Mock*` классами из `arc-core`.

Приёмка: понятен способ получения независимых наблюдений настоящего клиента. Если oracle пока не исполним, явно отметить parity непроверенной; нельзя объявлять модель достоверной только по unit-тестам самой модели.

## Этап 2. Общие test fixtures в отдельном mindustry-testkit

Изменения: создание репозитория `mindustry-testkit` (`core` и `ui`), подключение в xcore-ui через `testImplementation`.

- [x] Создать `mindustry-testkit`, проверить локальное подключение через `mavenLocal` и `--include-build`.
- [x] Реализовать UiWireMessage и snapshots payload при отправке; проверить настоящий TypeIO codec (`UiSnapshot`).
- [x] Реализовать DeterministicUiLoop: FIFO S→C/C→S, snapshot-drain post.
- [x] Тестами доказать отсутствие inline доставки, сохранение FIFO и перенос вложенного post на следующий turn (`DeterministicQueueTest`, `DeterministicUiLoopTest`).
- [x] Реализовать HeadlessMenuClient для минимального профиля из спецификации; unknown behavior — fail-fast.
- [x] Реализовать UiTranscript: шаги, причины, payload, окна, registry, очереди.
- [x] Добавить UiSessionClientGateway и UI-01 в UiSessionClientIntegrationTest.
- [x] Сверить поддержанные transcripts с actual oracle; семантика HeadlessMenuClient полностью совпадает с ActualMenusOracleTest.

Приёмка: настоящий reducer вызывается через обратный клиентский результат; patch меняет целевой слот и сохраняет соседний. Нет real sleep, потоков, внешней БД или сокетов. Fake не фильтрует результаты и не выполняет server cleanup.

## Этап 3. Fixture плагина

Новый тест: `XCore-plugin/src/test/java/org/xcore/plugin/ui/MapUiClientIntegrationTest.java`.

- [x] Подключить testkit без копирования реализации; проверены `publishToMavenLocal` и composite build.
- [x] Создать настоящие Session/MenuService/MapMenu/MapUiController/UiSession/observer.
- [x] Включить builder-путь: регистрация menu builder и Player.con; проверить, что тест не попал в legacy fallback.
- [x] Repository: отдельные незавершённые futures для summaries и каждого details request.
- [x] Preview: отдельные callbacks и контролируемый cache-hit.
- [x] Согласованные A/B lookups и детерминированная локализация.
- [x] Core.app.post направить в queue; устранена утечка static Core.app между тестами.
- [x] Считать живые observer registrations до следующего notify; observerService подключён.
- [x] До teardown проверить очереди/подписки; затем восстановить globals и регистрации.

Приёмка: проверяются model, клиентское дерево, gateway trace и ресурсы. Тесты с globals не выполняются параллельно.

## Этап 4. Матрица регрессий

Добавлять в порядке UI-02 → UI-03 → UI-04 → UI-05 → UI-07 → UI-08 → UI-09 → UI-06.

- [x] UI-02: browser с pending summaries, запрет sync repository calls.
- [x] UI-03: immediate/delayed details с одинаковым итогом.
- [x] UI-04: cancel заменённого окна с тем же token.
- [x] UI-05: поздние details/preview A после открытия B.
- [x] UI-07: close/replacement до запуска request-post.
- [x] UI-08: explicit close при pending I/O/RTV, отсутствие resurrection, cleanup и отсутствие дублированного завершения.
- [x] UI-09: актуальный RTV сохраняется при поздней статистике (**исправлен баг в коде**).
- [x] UI-06: A₁/A₂ различаются по запросу, устаревший ответ не затирает новый (**исправлен баг в коде**).
- [ ] Characterization Escape до/после клика: отсутствие сигнала не подменяется action:close (клиентский cancel игнорируется сервером для защиты от d16772b, серверный cleanup по Escape требует отдельного решения).
- [x] Отдельный legacy smoke; не включать его в доказательство reactive coverage.

### Дисциплина RED → GREEN

Каждое падение классифицировать: ошибка fixture, несовпадение client parity или нарушение product requirement. Записать наблюдаемую причину RED. Сценарий, который уже проходит, сохранить как regression coverage без искусственного падения.

Production-изменения вносить отдельно после RED: ранний захват владельца, request generation, merge статистики, lifecycle/dispose, subscriptions. Не переписывать весь runtime и не переходить на MapUiCmd заодно. Fixture не должен скрывать проблему автоматическим discard/cleanup.

Тесты, обнаружившие ещё не исправленный product bug, не объявлять зелёными и не прятать молчаливым skip. Для интеграции в основную ветку нужен отдельный согласованный fix либо явно описанный статус незавершённой работы.

## Этап 5. CI и приёмка (бэклог)

- [x] Быстрые тесты включить в обычный test (включены в `gradle test`).
- [ ] Создать отдельную задачу uiClientParityTest для actual oracle (сейчас выполняется в рамках test).
- [x] Изолировать actual oracle отдельной JVM/process; исключить параллельность глобального UI-state.
- [x] Сохранять test reports, transcripts, fingerprints и остатки очередей при падении.
- [ ] Проверить resolved fixture variant в чистом CI без неявных соседних Mindustry/Arc checkout.
- [x] Проверить отсутствие fixture-классов и добавленных игровых runtime dependencies в production artifact (`mindustry-testkit/ui` JAR чист от Mindustry/Arc классов).
- [x] Обновить статус ADR и спецификации только по результатам реального выполнения.

Предлагаемые команды после реализации, из workspace (выполнять последовательно):

```bash
(cd mindustry-testkit && ./gradlew test)
(cd xcore-ui && ./gradlew test)
(cd XCore-plugin && ./gradlew test --include-build ../mindustry-testkit --tests '*MapUiClientIntegrationTest')
```

Перед использованием проверить существование задач и fixture variant resolution. Эти команды не запускались при записи плана. Gradle-сборки общего checkout не запускать одновременно.

## Definition of Done

- [x] UI-01–UI-09 проверяют настоящую серверную цепочку и имеют документированный результат.
- [x] Поддержанный профиль fake имеет executable parity с зафиксированными артефактами; непроверенные части явно исключены из заявлений.
- [x] Сценарий «выбор карты → данные → cancel старого окна» воспроизводится без игрока, MongoDB и случайных задержек.
- [x] Падение объясняется trace до конкретного шага, а не только несовпавшим конечным DSL.
- [x] Проверяются не только отсутствие пакетов, но и состояние модели/подписок.
- [x] Нет новых production гарантий, скрыто реализованных внутри fake.
- [x] Визуальный E2E, таймеры и общий перебор расписаний остаются вне MVP.
