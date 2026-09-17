# mindustry-testkit

Независимый test-only toolkit для детерминированных тестов Mindustry-плагинов.

## Текущее состояние

Начальный срез, **не готовый симулятор клиента**:

- `core`: `DeterministicQueue` — явная FIFO-доставка и snapshot-drain turn;
- `ui`: `UiSnapshot` — неизменяемая копия `NodeBuilder` через штатный binary codec;
- `ui`: `HeadlessMenuClient` — воспроизведение семантики клиента Mindustry (меню, token, outbox, wasHidden-подавление cancel, замена окон);
- `ui`: actual-client oracle тесты (`ActualDialogHideTest`, `ActualMenusOracleTest`) — исполнение настоящего `mindustry.ui.Menus` и `arc.scene.ui.Dialog` под `Mock*` из arc-core в plain JVM без рендера и Xvfb:
  1. Замена окна до клика (`hidePrevious=true`) синхронно шлёт cancel старого окна со старым токеном;
  2. Клик по кнопке устанавливает `wasHidden=true` и передаёт choose-пакет;
  3. Замена окна после клика подавляет cancel старого окна;
  4. Закрытие нового окна шлёт cancel с новым токеном;
  5. SHA-256 fingerprinting реально загруженных JAR артефактов (`Menus.class`: `283c9b56fb...`, `Core.class`: `c2df13f7...`).
- Java 25, Gradle 9.3.1, JUnit 5.

Actual-Menus oracle доказал полную эквивалентность семантики `HeadlessMenuClient` и настоящего `Menus.menuBuilder` в headless окружении.

`HeadlessMenuClient` пока моделирует только текущее окно, outbox выбора и журнал последних patch payloads. `wasHidden` подавляет cancel при Escape, серверном hide и замене после клика; новый show создаёт новое состояние даже при том же token. В xcore-ui есть синтетические интеграционные тесты counter/slot и replacement-cancel через настоящий `UiSession`.

**Ограничения:** нет actual-Menus parity, дерева клиентских элементов, проверки существования targetId/кнопки, нескольких одновременно видимых окон, hideOnClick, полного wire codec и trace. `lastPatchDsl` — журнал доставки, не доказательство применения патча к клиентскому дереву; он пока сохраняется между окнами. Выбор переносит action/token, но не значения формы. Две транспортные очереди и server-post ещё не соединены. Зелёные тесты не подтверждают fidelity клиента или исправность maps.

## Сборка

```bash
./gradlew clean test assemble
```

Зависимости Mindustry/Arc для `ui` — `compileOnly`, для собственных тестов — `testImplementation`. Они не упаковываются в toolkit JAR. `core` не зависит от Mindustry и XCore. `ui` не зависит от xcore-ui или XCore-plugin.

Локальное подключение из потребителя:

```kotlin
testImplementation("org.xcore.testkit:ui:0.1.0-SNAPSHOT")
```

```bash
./gradlew --include-build ../mindustry-testkit test
```

Это обычные library artifacts, подключаемые **только в test scope**, не Gradle test-fixture variants. Подключение xcore-ui проверено через `publishToMavenLocal` и через `--include-build ../mindustry-testkit`; composite использует текущие исходники без повторной публикации snapshot. Remote Maven repository и Git remote не настроены.

## Очередь

`post` ничего не выполняет. `runNext` исполняет одну задачу; пустая очередь возвращает false. `runTurn` снимает текущий набор задач, поэтому вложенный `post` остаётся следующему turn. Для каждого направления транспорта и server-post нужна отдельная очередь.

API предназначен для одного потока и внешнего пошагового драйвера, не для рекурсивного вызова drain из callback. При исключении в callback `runTurn` прерывается, а остальные задачи его снимка не возвращаются в очередь pending — в отличие от Arc TaskQueue, который сохраняет pending-задачи; исключение завершает сценарий, поэтому требуется диагностика на месте сбоя.

## Следующие шаги

1. Зафиксировать fingerprints реально загруженных Mindustry/Arc artifacts (arc-core-v160 SHA-256 получен) и проверить `menuBuilder` path (`MenuDialog`/`BaseDialog`: `Tex.whiteui`, close-кнопка, звуки, `net.active()`).
2. Довести HeadlessMenuClient до дерева элементов и parity; добавить wire/trace и детерминированную доставку.
3. Расширить test-only DeliveryGateway adapter в xcore-ui: две FIFO-очереди, server-post и проверки реально применённых slot-патчей.
4. В XCore-plugin добавить сценарии maps; исправлять подтверждённые product RED отдельно.

Зависимости окружения actual oracle: `Core.gl`/`Core.graphics`/`Core.app` = Arc `Mock*` классы, `Core.scene = new Scene()`, `DialogStyle` с синтетическим `Font` (пустой `FontData` + `Pixmap`-текстура). Глобальные statics Core требуют сброса между тестами; isolation ещё не реализована.

Общий toolkit не должен зависеть от XCore. Адаптер `UiSession` остаётся в xcore-ui; данные карт, futures repository и subscriptions — в plugin-тестах.

Исходные проектные документы пока находятся в соседнем репозитории `XCore-plugin/docs`: `architecture/deterministic-ui-client-test-harness.md`, `adr/ADR-deterministic-ui-client-test-harness.md`, `implementation/deterministic-ui-client-test-harness-plan.md`. Решение об отдельном репозитории заменяет прежнее размещение общего кода в xcore-ui/src/testFixtures.
