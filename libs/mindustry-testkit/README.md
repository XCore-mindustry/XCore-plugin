# mindustry-testkit

Независимый test-only toolkit для детерминированных тестов Mindustry-плагинов.

## Текущее состояние

Начальный срез, **не готовый симулятор клиента**:

- `core`: `DeterministicQueue` — явная FIFO-доставка и snapshot-drain turn;
- `ui`: `UiSnapshot` — неизменяемая копия `NodeBuilder` через штатный binary codec;
- Java 25, Gradle 9.3.1, JUnit 5.

`HeadlessMenuClient`, actual-Menus parity, trace и интеграция с `UiSession` ещё не реализованы. Наличие зелёных unit-тестов не подтверждает fidelity клиента.

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

Это обычные library artifacts, подключаемые **только в test scope**, не Gradle test-fixture variants. Интеграция потребителя пока не проверена. Remote Maven repository и Git remote не настроены.

## Очередь

`post` ничего не выполняет. `runNext` исполняет одну задачу; пустая очередь возвращает false. `runTurn` снимает текущий набор задач, поэтому вложенный `post` остаётся следующему turn. Для каждого направления транспорта и server-post нужна отдельная очередь.

API предназначен для одного потока и внешнего пошагового драйвера, не для рекурсивного вызова drain из callback. При исключении в callback `runTurn` прерывается, а остальные задачи его снимка не возвращаются в очередь pending — в отличие от Arc TaskQueue, который сохраняет pending-задачи; исключение завершает сценарий, поэтому требуется диагностика на месте сбоя.

## Следующие шаги

1. Зафиксировать fingerprints реально загруженных Mindustry/Arc artifacts и проверить actual Menus oracle.
2. Реализовать минимальный HeadlessMenuClient и trace через TDD.
3. В xcore-ui добавить test-only DeliveryGateway adapter и сквозной counter/slot тест.
4. В XCore-plugin добавить сценарии maps; исправлять подтверждённые product RED отдельно.

Общий toolkit не должен зависеть от XCore. Адаптер `UiSession` остаётся в xcore-ui; данные карт, futures repository и subscriptions — в plugin-тестах.

Исходные проектные документы пока находятся в соседнем репозитории `XCore-plugin/docs`: `architecture/deterministic-ui-client-test-harness.md`, `adr/ADR-deterministic-ui-client-test-harness.md`, `implementation/deterministic-ui-client-test-harness-plan.md`. Решение об отдельном репозитории заменяет прежнее размещение общего кода в xcore-ui/src/testFixtures.
