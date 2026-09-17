# План реализации детерминированного UI-стенда

## Статус

Начат bootstrap отдельного `mindustry-testkit`: очередь и binary UI snapshot с unit-тестами. Этапы ниже ещё не завершены; пункты не отмечены как выполненные. Документ не является отчётом об исправлении product-дефектов.

Изменение плана: общий код этапа 2 размещается в mindustry-testkit/core и mindustry-testkit/ui (обычные java-library artifacts, потребление через testImplementation), а не в xcore-ui/src/testFixtures. В xcore-ui остаётся только runtime adapter. Публикация/интеграция потребителей и actual-client parity пока не проверены. Текущее состояние — mindustry-testkit/README.md относительно workspace.

- [Спецификация и матрица UI-01–UI-09](../architecture/deterministic-ui-client-test-harness.md)
- [ADR и альтернативы](../adr/ADR-deterministic-ui-client-test-harness.md)

Порядок работ: контракт → общий стенд → plugin integration → отдельные подтверждённые исправления → CI. Не менять серверы и не деплоить в рамках реализации тестовой инфраструктуры.

## Этап 1. Зафиксировать реальный клиентский контракт

- [ ] Определить разрешённые Gradle зависимости в xcore-ui и XCore-plugin; записать координаты и SHA-256 фактически загруженных JAR.
- [ ] Сверить Menus, UiTreeBuilder, MenuResult, TypeIO и Arc TaskQueue с этими артефактами, а не только соседним checkout.
- [ ] Описать поддерживаемые flags, widgets и wire shape; Update/Hide без token.
- [ ] Подготовить короткие transcripts: replacement до/после клика, dismiss, server hide, patch существующей/отсутствующей цели, stale descendant ID.
- [ ] Выполнить ограниченный spike настоящего Menus/Scene без draw; зафиксировать результат и зависимости.
- [ ] При неосуществимости выбрать отдельный Xvfb/Mesa oracle вместо бесконечного наращивания заглушек.

Приёмка: понятен способ получения независимых наблюдений настоящего клиента. Если oracle пока не исполним, явно отметить parity непроверенной; нельзя объявлять модель достоверной только по unit-тестам самой модели.

## Этап 2. Общие test fixtures в xcore-ui

Изменения: `xcore-ui/build.gradle.kts` и новые классы в `xcore-ui/src/testFixtures/java/org/xcore/ui/testing/client/`.

- [ ] Подключить java-test-fixtures, проверить публикацию variant и зависимости exposed Mindustry types.
- [ ] Реализовать UiWireMessage и snapshots payload при отправке; проверить настоящий TypeIO codec.
- [ ] Реализовать DeterministicUiLoop: FIFO S→C/C→S, snapshot-drain post, maxSteps.
- [ ] Тестами доказать отсутствие inline доставки, сохранение FIFO и перенос вложенного post на следующий turn.
- [ ] Реализовать HeadlessMenuClient для минимального профиля из спецификации; unknown behavior — fail-fast.
- [ ] Реализовать UiTranscript: шаги, причины, payload, окна, registry, очереди и символические tokens.
- [ ] Добавить UiSessionClientGateway и UI-01 в UiSessionClientIntegrationTest.
- [ ] Сверить поддержанные transcripts с actual oracle; несовпадения не лечить подгонкой product assertions.

Приёмка: настоящий reducer вызывается через обратный клиентский результат; patch меняет целевой слот и сохраняет соседний. Нет real sleep, потоков, внешней БД или сокетов. Fake не фильтрует результаты и не выполняет server cleanup.

## Этап 3. Fixture плагина

Новый тест: `XCore-plugin/src/test/java/org/xcore/plugin/ui/MapUiClientIntegrationTest.java`.

- [ ] Подключить общий test-fixture variant без копирования реализации; для local candidate проверить composite build.
- [ ] Создать настоящие Session/MenuService/MapMenu/MapUiController/UiSession/observer.
- [ ] Включить builder-путь: регистрация menu builder и Player.con; проверить, что тест не попал в legacy fallback.
- [ ] Repository: отдельные незавершённые futures для summaries и каждого details request.
- [ ] Preview: отдельные callbacks и контролируемый cache-hit.
- [ ] Согласованные A/B lookups и детерминированная локализация.
- [ ] Core.app.post направить в queue; не оставлять Core.app=null с inline fallback.
- [ ] Считать живые observer registrations до следующего notify; если нет seam, добавить минимальный test-only доступ, не cleanup в fake.
- [ ] До teardown проверить очереди/подписки; затем восстановить globals и регистрации.

Приёмка: проверяются model, клиентское дерево, gateway trace и ресурсы. Тесты с globals не выполняются параллельно.

## Этап 4. Матрица регрессий

Добавлять в порядке UI-02 → UI-03 → UI-04 → UI-05 → UI-07 → UI-08 → UI-09 → UI-06.

- [ ] UI-02: browser с pending summaries, запрет sync repository calls.
- [ ] UI-03: immediate/delayed details с одинаковым итогом.
- [ ] UI-04: cancel заменённого окна с тем же token.
- [ ] UI-05: поздние details/preview A после открытия B.
- [ ] UI-07: close/replacement до запуска request-post.
- [ ] UI-08: explicit close при pending I/O/RTV, отсутствие resurrection, cleanup и отсутствие дублированного завершения.
- [ ] UI-09: актуальный RTV сохраняется при поздней статистике.
- [ ] UI-06: A₁/A₂ различаются по запросу, устаревший ответ не затирает новый.
- [ ] Characterization Escape до/после клика: отсутствие сигнала не подменяется action:close.
- [ ] Отдельный legacy smoke; не включать его в доказательство reactive coverage.

### Дисциплина RED → GREEN

Каждое падение классифицировать: ошибка fixture, несовпадение client parity или нарушение product requirement. Записать наблюдаемую причину RED. Сценарий, который уже проходит, сохранить как regression coverage без искусственного падения.

Production-изменения вносить отдельно после RED: ранний захват владельца, request generation, merge статистики, lifecycle/dispose, subscriptions. Не переписывать весь runtime и не переходить на MapUiCmd заодно. Fixture не должен скрывать проблему автоматическим discard/cleanup.

Тесты, обнаружившие ещё не исправленный product bug, не объявлять зелёными и не прятать молчаливым skip. Для интеграции в основную ветку нужен отдельный согласованный fix либо явно описанный статус незавершённой работы.

## Этап 5. CI и приёмка

- [ ] Быстрые тесты включить в обычный test.
- [ ] Создать отдельную задачу uiClientParityTest для actual oracle; до создания это только предлагаемое имя.
- [ ] Изолировать actual oracle отдельной JVM/process; исключить параллельность глобального UI-state.
- [ ] Сохранять test reports, transcripts, fingerprints и остатки очередей при падении.
- [ ] Проверить resolved fixture variant в чистом CI без неявных соседних Mindustry/Arc checkout.
- [ ] Проверить отсутствие fixture-классов и добавленных игровых runtime dependencies в production artifact.
- [ ] Обновить статус ADR и спецификации только по результатам реального выполнения.

Предлагаемые команды после реализации, из workspace (выполнять последовательно):

```bash
(cd xcore-ui && ./gradlew test jar sourcesJar)
(cd XCore-plugin && ./gradlew --include-build ../xcore-ui validateCi)
# После создания соответствующей задачи:
(cd xcore-ui && ./gradlew uiClientParityTest)
```

Перед использованием проверить существование задач и fixture variant resolution. Эти команды не запускались при записи плана. Gradle-сборки общего checkout не запускать одновременно.

## Definition of Done

- [ ] UI-01–UI-09 проверяют настоящую серверную цепочку и имеют документированный результат.
- [ ] Поддержанный профиль fake имеет executable parity с зафиксированными артефактами; непроверенные части явно исключены из заявлений.
- [ ] Сценарий «выбор карты → данные → cancel старого окна» воспроизводится без игрока, MongoDB и случайных задержек.
- [ ] Падение объясняется trace до конкретного шага, а не только несовпавшим конечным DSL.
- [ ] Проверяются не только отсутствие пакетов, но и состояние модели/подписок.
- [ ] Нет новых production гарантий, скрыто реализованных внутри fake.
- [ ] Визуальный E2E, таймеры и общий перебор расписаний остаются вне MVP.
