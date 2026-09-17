# Детерминированный клиентский стенд для UI-тестов

## Статус и назначение

**Изменение размещения:** по последующему решению общий код переносится в самостоятельный `mindustry-testkit` (`core`/`ui`, обычные library artifacts для `testImplementation`). В `xcore-ui` остаётся только test-only адаптер `UiSession`, в plugin — maps-сценарии. Указанные ниже пути общего кода в `xcore-ui/src/testFixtures` — исходный вариант, заменённый этим решением. Начат bootstrap нового репозитория: FIFO/snapshot queue и binary UI snapshot; клиент и parity ещё не реализованы. Текущее состояние — `mindustry-testkit/README.md` относительно workspace.

Проект по результатам трёх независимых исследований и последующего отдельного синтеза. Начат инфраструктурный срез; feasibility настоящего headless-клиента не проверена. Выводы по исходникам не равнозначны воспроизведённым дефектам.

Связанные документы:

- [ADR](../adr/ADR-deterministic-ui-client-test-harness.md)
- [План реализации](../implementation/deterministic-ui-client-test-harness-plan.md)

Исходная документация межрепозиторного стенда хранится здесь; общий toolkit реализуется в `mindustry-testkit`, адаптер runtime — в `xcore-ui`, интеграционные сценарии — в `XCore-plugin`. Пути ниже относительно корня workspace, если начинаются с имени репозитория.

## 1. Цель и границы

Воспроизводить ошибки на границе серверного UI и клиента: отмена при замене окна, запоздалые данные, закрытие при pending I/O, расхождение серверной модели и клиентского окна.

Каждый сценарий проверяет четыре наблюдения:

1. Серверную модель и активную сессию.
2. Логическое дерево открытого клиентского окна.
3. Команды show/update/hide и результаты выбора.
4. Живые серверные регистрации/подписки.

Не входит в MVP: MongoDB, Redis, сокеты, игровой мир, отрисовка, геометрия, focus/scroll, доставка текстур, таймеры, общий model checking, случайные расписания, shrinking и перестройка production-кода на MapUiCmd. Это не полноценный визуальный E2E.

## 2. Архитектура

```text
MapMenu -> MapUiController.update -> UiSession -> MenuService
                                                   |
                                      MindustryMenuGateway adapter
                                                   |
                                         FIFO server -> client
                                                   |
                                          HeadlessMenuClient
                                                   |
                                         FIFO client -> server
                                                   |
                                  MenuService.onMenuBuilderResult
```

Исполняются настоящие `Session`, `MenuService`, `MapMenu`, `MapUiController`, `UiSession`, observer. `evaluateCommands` и новый интерпретатор `MapUiCmd` не заменяют рабочий `update`.

Подменяются только внешние границы:

- repository: отдельный controlled CompletableFuture на каждый запрос;
- preview: захваченный callback каждого запроса;
- каталог: фиксированные карты A/B;
- локализация: детерминированные строки;
- сеть: очередь gateway;
- Core.app.post: управляемая очередь.

Повторные запросы одной карты A₁/A₂ имеют разные request ID и futures. Fixture обязан включать builder-путь (регистрация меню, Player.con); legacy MapFlows проверяется отдельно и не считается reactive coverage.

## 3. Компоненты и размещение

Общие test-only классы:

```text
xcore-ui/src/testFixtures/java/org/xcore/ui/testing/client/
    DeterministicUiLoop.java
    HeadlessMenuClient.java
    UiWireMessage.java
    UiTranscript.java
    UiSessionClientGateway.java
```

| Компонент | Ответственность |
| --- | --- |
| DeterministicUiLoop | FIFO каждого направления, snapshot-drain post, пошаговое исполнение |
| HeadlessMenuClient | Экземпляры окон, registry, дерево поддержанного профиля, callbacks |
| UiWireMessage | Снимки реальных wire-сообщений |
| UiTranscript | Причины, шаги, payload и наблюдения после каждого шага |
| UiSessionClientGateway | Подключение DeliveryGateway к общему клиенту |

Интеграционные тесты:

```text
xcore-ui/src/test/java/org/xcore/ui/runtime/UiSessionClientIntegrationTest.java
XCore-plugin/src/test/java/org/xcore/plugin/ui/MapUiClientIntegrationTest.java
```

Пакет plugin fixture — именно `org.xcore.plugin.ui`, не подпакет `integration`: нужен package-private вход `onMenuBuilderResult`. Production API ради доступа не расширять.

Общий код публикуется через `java-test-fixtures`. Plugin использует fixture variant; локальный composite build допустим после проверки variant resolution. Не копировать fake между репозиториями. Mindustry/Arc не добавлять в production implementation ради тестов; exposed fixture types требуют соответствующих test-fixture dependencies.

## 4. Детерминированное исполнение

Минимальный предлагаемый API (ещё не реализован):

```java
deliverNextToClient();
deliverNextToServer();
runServerTurn();
client.click(menuId, action);
client.dismiss(menuId);
// futures завершаются тестом явно:
detailsRequestA.complete(mapDataA);
```

### Транспорт

Две независимые FIFO на соединение. Доставляется только голова выбранного направления. Запрещены произвольная перестановка, потеря и дублирование TCP-сообщений. Для reconnect использовать отдельную connection epoch; старые пакеты не переносить в новое соединение.

Допустимая гонка: клиент отправил результат A; независимый серверный callback открыл B; затем результат A дошёл до сервера. FIFO каждого направления при этом сохранён.

Gateway не вызывает сервер рекурсивно внутри show. Hidden callback только ставит Choose в C→S. Futures могут исполнять continuation немедленно, как настоящий CompletionStage, но последующий post остаётся отложенным.

### Snapshot-drain post

`runServerTurn` забирает снимок текущей очереди и исполняет его FIFO. Новые post во время исполнения остаются следующему turn — как в Arc TaskQueue.run. Inline post и рекурсивное опустошение очереди маскируют реальные границы исполнения.

MVP использует рукописные расписания, без Thread.sleep и фоновых потоков. Вспомогательный drain имеет maxSteps и явно падает при превышении. Он не завершает futures автоматически. Client-post queue добавляется, если её требует поддержанный dismiss/parity сценарий, с той же snapshot-семантикой.

Виртуальные часы вводить только с первым timed-сценарием. Они не подменяют автоматически System.currentTimeMillis; потребуется узкий Clock/LongSupplier seam.

## 5. Клиентский контракт

```text
Show(menuId, token, flags, body)
Update(menuId, targetId, body)
Hide(menuId)
Choose(menuId, token, result, values)
```

У Update и Hide нет token на настоящем wire. Нельзя добавлять token guard в fake на основании удобного параметра DeliveryGateway.

Наблюдения по исследованным исходникам, требующие проверки относительно resolved artifact:

- Каждый Show создаёт новый экземпляр окна.
- Replacement при hidePrevious=true скрывает старый экземпляр и может отправить отмену с его token.
- Callback зависит от wasHidden; нажатие устанавливает этот флаг даже при hideOnClick=false.
- Поэтому dismiss/Escape после первого клика может не отправить никакого результата.
- Серверный Hide тоже может породить hidden callback.
- UiSession повторно использует token при full rerender: один token-check не решает отмену внутри той же сессии.
- Update адресуется по menuId/element ID; отсутствующая или неподходящая цель даёт no-op.
- Клиент пополняет ID-индекс через putAll, без полной очистки старых ID.

Симулятор различает видимые экземпляры и текущую запись registry. Поддерживаемые flags должны быть явно описаны; неподдержанные комбинации отклонять.

Профиль MVP: tables, scroll pane, labels, buttons и необходимые структурные контейнеры/декоративные листья maps UI. Геометрия и image delivery не моделируются. Неизвестное поведенческое свойство — явная ошибка fixture, а не молчаливое приближение. Fields и build-local контексты values расширяются отдельными parity cases.

Payload снимать при отправке, предпочтительно настоящим TypeIO round-trip после проверки доступности codec. Mutable NodeBuilder по ссылке не сохранять.

Fake не фильтрует stale events, не переводит cancel в action:close и не освобождает серверные подписки вместо production.

## 6. Trace и воспроизведение

Записывать: sequence, причину, направление, player/connection epoch, menuId, экземпляр окна, token, flags, target, снимок payload и наблюдения после шага. Хранить состояние очередей при падении.

Runtime tokens можно представлять символически (`session-1`), сохраняя отношения равенства. Production UUID generator ради тестов не менять. Replay воспроизводит точную последовательность выбранных шагов, не только seed.

Для MVP достаточно стабильного текстового dump/Java records. JSONL, ограниченный перебор разрешённых голов очередей и shrinking — последующие расширения. Лимиты будущего перебора выводить явно; исчерпание бюджета не означает полноту проверки.

## 7. Достоверность: parity и product correctness

Два независимых набора:

1. Parity: fake и настоящий Menus дают одинаковые наблюдения на одном transcript.
2. Product correctness: настоящий сервер обеспечивает требуемый пользовательский результат.

Совпадение с ошибкой/ограничением клиента не доказывает корректность продукта.

### Fingerprint

Фиксировать resolved coordinates, SHA-256 фактически загруженных Mindustry/Arc JAR, SHA использованных исходников и версию fixture profile. Исследователь обнаружил разные cached core-v160.jar под одной координатой: строка v160 и соседний checkout недостаточны.

### Actual oracle

Ограниченный feasibility spike: настоящий Menus + Arc Scene, управляемый Application, минимальные ресурсы, без draw. Работоспособность не проверена.

Если это требует чрезмерных графических заглушек — отдельный actual-client job под Xvfb/Mesa. Проверка только UiTreeBuilder подтверждает лишь widget contracts, но не Menus lifecycle. Если actual execution недоступен, маркировать parity как непроверенную; исторические golden traces не доказывают соответствие текущему artifact.

Минимальные transcripts: replacement до/после клика, dismiss, server hide, patch существующей/отсутствующей цели, старый descendant ID после parent patch. Сравнивать видимые окна отдельно от registry, порядок результатов и эффект patch после каждого шага.

Actual oracle запускать отдельным процессом из-за Core/Vars/Events/Menus globals. Не делать соседние исходники Mindustry/Arc неявной зависимостью CI.

## 8. Матрица MVP

| ID | Сценарий | Проверка |
| --- | --- | --- |
| UI-01 | Show → click → reducer → patch | Обновлён нужный слот, соседний неизменен |
| UI-02 | /maps с pending summaries | Browser показан немедленно, нет sync repository calls |
| UI-03 | A: immediate и delayed details | Одинаковые данные модели и клиентского дерева |
| UI-04 | Full rerender → cancel старого окна с тем же token | Обновлённая карточка остаётся открытой |
| UI-05 | A → browser → B; затем details/preview A | B не меняется |
| UI-06 | A₁ → B → A₂; ответы A₂, затем A₁ | Устаревшая загрузка не затирает новую |
| UI-07 | Close до request-post → другой диалог → post/completion | Map events не попадают в чужую сессию |
| UI-08 | Explicit close при pending details/preview/RTV | Нет воскресшего show/update, корректное число close/hide, cleanup до notify |
| UI-09 | RTV update → delayed DetailsReady | Статистика не сбрасывает live-прогресс |

UI-04–09 — кандидаты для RED, не результаты запуска. Часть сценариев может уже проходить; это не повод создавать искусственное падение.

Отдельная characterization: Escape до/после клика. Нельзя требовать мгновенного серверного cleanup при отсутствующем клиентском сигнале. Legacy smoke не подменяет reactive coverage.

## 9. Последующие production-решения

Только после воспроизведения рассматривать ранний захват владельца async-запроса, request-generation, merge статистики без сброса RTV, единый идемпотентный close/dispose, владение подписками, идентичность каждого показанного окна и политику для неуведомлённых закрытий.

Закрытие UI должно запрещать доставку старому окну, но не обязательно отменять общую cache/hash загрузку. Исправления продукта отделять от инфраструктуры; стенд не реализует эти гарантии за продукт.

## 10. Исходные точки проверки

- `Mindustry/core/src/mindustry/ui/Menus.java` — show/update/hide и callbacks.
- `Mindustry/core/src/mindustry/ui/builder/UiTreeBuilder.java` — ID-индекс и контексты values.
- `Mindustry/core/src/mindustry/ui/builder/MenuResult.java` — token/result/values.
- `Mindustry/core/src/mindustry/io/TypeIO.java` — codec snapshots.
- `Arc/arc-core/src/arc/util/TaskQueue.java` — snapshot-drain.
- `xcore-ui/src/main/java/org/xcore/ui/runtime/UiSession.java` — runtime.
- `XCore-plugin/src/main/java/org/xcore/plugin/ui/MenuService.java` — routing/gateway.
- `XCore-plugin/src/main/java/org/xcore/plugin/ui/menu/map/MapUiController.java` — actual reducer/async delivery.
- `XCore-plugin/src/main/java/org/xcore/plugin/service/map/MapVoteObserverService.java` — subscriptions.

Пути являются ориентирами для реализации, не доказательством совпадения локальных исходников с используемыми бинарными зависимостями.
