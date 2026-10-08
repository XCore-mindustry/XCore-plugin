# FluBundle: улучшение API и использования в плагинах

Статус: 1.8.0 реализован (см. раздел 0), остальное — предложение. Основано на разборе `FluBundle 1.7.1`, `XCore-plugin`, `HexedCore-plugin`, `xcore-ui`.

## 0. Принятые решения и статус

| Вопрос | Решение | Почему |
|---|---|---|
| Каталог на плагин или общий | Пока **общий** `Bundle.INSTANCE`, но с логированием перекрытых ключей (с указанием мода-источника) и без «freeze» | Отдельные каталоги с `parent` дают мало выгоды при префиксах `hexed_*`, но требуют миграции всех плагинов. Вернуться к ним, если коллизии реально появятся. |
| Где живёт `LocaleResolver` | В `Bundle` (`setLocaleResolver`); XCore устанавливает `SessionLocaleResolver` на общий экземпляр | Плагины без DI и вызовы `bundle.locale(player)` получают правильный язык без изменений кода. |
| Кодогенерация ключей | Отложена; вместо неё `LocaleConsistencyChecker` + `DefaultValueFactory.logMissing` | Проверка ловит реальные ошибки (переменные), лог показывает отсутствующие ключи; codegen требует Gradle-плагина. |
| Переименовать `hexed_*` в kebab-case | Нет | Ломает переводы/Weblate без пользы для игроков. |
| `Text` / `Messenger` / `Audience` | Отложено до 2.0 | Крупная смена API; в 1.8 рассылка уже форматирует текст один раз на локаль. |

Сделано в **FluBundle 1.8.0**: `LocaleResolver`, `Args.of(...)`, `has`/`keys`/`variables`/`formatAttribute`,
перегрузки без аргументов (`format(locale, id)`, `send(player, id)`, `announce(player, id)`), регистрация функций
в любой момент, логирование ошибок рендера и перекрытий ключей, `DefaultValueFactory.logMissing`,
рассылки с форматированием раз на локаль, потокобезопасное хранилище бандлов, `LocaleConsistencyChecker`.

В **XCore-plugin**: `SessionLocaleResolver`, удалён `BundlePlaceholderRegistry` (→ `bundle.has/variables`),
`has()` в мосте к xcore-ui, логирование отсутствующих ключей, тест согласованности переменных.

В **HexedCore-plugin**: циклы `locale + Call.announce` заменены на `bundle.announce`, рассылки — на
`bundle.send(id, args)`, убран дублирующий alias. Код остаётся на API 1.7.1, потому что:

> XCore встраивает FluBundle в свой shadow-jar без relocate, и HexedCore во время работы (и при компиляции,
> через `xcore-plugin` на classpath) использует **ту версию FluBundle, что внутри XCore**. Поэтому
> исправление языка для HexedCore приходит вместе со сборкой XCore на FluBundle 1.8 — без изменений
> HexedCore. Перейти на новый API (`Args`, `LocaleConsistencyChecker`) HexedCore сможет после того, как
> поднимет зависимость `xcore-plugin` до версии, собранной с FluBundle 1.8.

Порядок выпуска: релиз FluBundle 1.8.0 → сборка/релиз XCore → (опционально) HexedCore на новый XCore.

## 1. Как FluBundle используется сейчас

| Плагин | Как получает `Bundle` | Как определяет локаль | Типичный вызов |
|---|---|---|---|
| XCore-plugin | DI-бин из `BundleFactory` (= `Bundle.INSTANCE`) | своя обёртка `Localization` + `session.data.language` | `session.locale().t("key", args(...))` (~290 `t`, ~140 `send`, 282 `args(...)`) |
| HexedCore-plugin | `Bundle.INSTANCE` напрямую (в `RoundLifecycleService`, `HexedHudService`) | `bundle.locale(player)` → `player.locale` | `bundle.format(bundle.locale(p), "hexed_x", Bundle.args(...))` |
| xcore-ui | — | — | `LocalizerResolver`, мост в XCore: `MenuService.resolverFor` |

## 2. Найденные проблемы

### P1. Логика «какая локаль у получателя» размазана и расходится (баг)
Выбор языка игрока хранится в XCore (`Session.data.language`), а FluBundle про него не знает: `Bundle.locale(player)` смотрит только на `player.locale`.
Из-за этого:
- **HexedCore показывает сообщения на языке клиента, а не на языке, выбранном в настройках XCore** (`RoundLifecycleService`: `bundle.locale(p)`, `bundle.send(player, ...)`; `HexedHudService:146`).
- Широковещательные `Bundle.send(id, args)` / `announce(id, args)` и т.п. имеют ту же проблему.
- XCore вынужден повторять один и тот же выбор локали в четырёх местах: `Localization.resolveLocale`, `XCoreSender.locale/format/send`, `CloudCaptionConfigurer`, `MenuService.resolverFor`, а также `BanCheck`/`KickTimeoutCheck` (через `packet.locale`).

### P2. `Bundle` — god-class
Один класс отвечает за загрузку FTL, нормализацию локалей и алиасы, реестр функций, форматирование и **доставку в Mindustry** (8 видов доставки × 2 перегрузки × «игрок/все» ≈ 40 методов). Каждый новый вид доставки или параметр множит перегрузки; `BundleContext` дублирует половину из них.

### P3. Глобальное изменяемое состояние
- `Bundle.INSTANCE` — общий синглтон для всех плагинов, с `public` полями `defaultLocale`, `defaultValueFactory`.
- Lifecycle «freeze»: первый `addSource` замораживает реестр функций. Значит, **второй плагин (HexedCore) уже не может зарегистрировать свою функцию или форматтер** — XCore к этому моменту загрузил свои FTL.
- Тесты XCore мутируют `Bundle.INSTANCE` (`addSource`, `addLocaleAlias`) — состояние течёт между тестами.
- `sources` (`ObjectMap`) меняется в `addSource` без синхронизации, а форматирование идёт и из async-потоков.

### P4. Общее плоское пространство ключей
Все плагины пишут в один `FluentBundle` через `addResourceOverriding` — коллизия ключей молча перетирает чужой текст. Стили ключей разные (XCore: `kebab-case`, HexedCore: `hexed_snake_case`), договорённости о префиксах нет. `addLocaleAlias("uk", "uk_UA")` дублируется в обоих плагинах.

### P5. Аргументы нетипизированы
`Map<String, Object>` + `args(Object...)`: нечётное число аргументов или опечатка в имени ловится только в рантайме (или не ловится вовсе: Fluent подставит `{$name}`). Много шума `Collections.emptyMap()` / `Map.of()`. `numArgs` нигде не используется.

### P6. Не хватает API интроспекции и диагностики
- Нет `has(key)` → XCore написал `BundlePlaceholderRegistry`, который **повторно парсит FTL регулярками**, чтобы узнать, есть ли ключ и какие у него `$`-переменные (для Cloud captions).
- Нет доступа к атрибутам сообщений (`key.title = ...`), хотя `FluentBundle.format(id, attr, args)` есть.
- `FluentBundle.Builder.withLogger(...)` не используется → ошибки рендера (нет переменной, ошибка функции) **глушатся молча**.
- `DefaultValueFactory` по умолчанию возвращает ключ, без логирования — отсутствующие переводы не видны.

### P7. Нет моста в xcore-ui
`MenuService.resolverFor` делает лямбду `(key, args) -> session.locale().format(key, args)`; у неё `has()` по умолчанию `true`, поэтому `LocalizerResolver.orElse(...)` с ней не работает.

### P8. Проверки FTL только синтаксические
`FtlCompiler` проверяет синтаксис, функции и опции, но не проверяет:
- полноту локалей относительно базовой (`en`);
- совпадение набора `$`-переменных одного ключа в разных локалях (`LocalizationPlaceholderConsistencyTest` из плана 1.5 в XCore сейчас отсутствует);
- что ключи, используемые в коде, существуют.

## 3. Целевая модель

Идея: разделить «каталог текстов», «выбор локали» и «доставку»; дать один общий для всех плагинов источник правды о локали игрока; сделать сообщение значением (`Text`), которое рендерится для каждого получателя.

```
               ┌───────────────── shared (XCore публикует, остальные используют) ───┐
               │  LocaleResolver: Player -> Locale   (session-aware в XCore)          │
               └────────────────────────────────────────────────────────────────────┘
 Bundle (immutable, per plugin)          Text (key + Args, deferred)       Audience (кому)
   ├─ locales / aliases / fallback  ◄──   render(Locale)              ──►  player / all / team / filter
   ├─ functions / formatters                                               send / announce / toast / hud / kick ...
   └─ parent: Bundle (fallback)
```

### 3.1 `Bundle` — неизменяемый, собирается builder'ом, по одному на плагин

```java
Bundle hexed = Bundle.builder()
        .defaultLocale(Locale.ENGLISH)
        .alias("uk", "uk_UA")                 // или .aliases(Locales.XCORE_DEFAULTS)
        .function(MY_FN)                      // нет freeze-ловушки: всё до build()
        .formatter(HexSlot.class, (s, scope) -> s.label())
        .source(HexedCorePlugin.class)        // bundles/*.ftl из jar плагина
        .parent(xcoreBundle)                  // fallback на общие ключи XCore (опционально)
        .onMissingKey(MissingKeyPolicy.logOnce())
        .onFormatError(FormatErrorPolicy.log())
        .build();
```

- Каждый плагин владеет своим каталогом → нет коллизий и молчаливого перетирания; функции/форматтеры регистрируются свободно.
- `parent` даёт доступ к общим ключам (например, `exception-*`, названия команд) без копирования.
- Перезагрузка (`/reload-bundles`) — это сборка нового `Bundle` и атомарная подмена ссылки (`BundleHolder`/`AtomicReference`), без гонок.
- `Bundle.INSTANCE` → `@Deprecated`, публичные поля → приватные с геттерами.

Интроспекция (закрывает P6, позволяет удалить `BundlePlaceholderRegistry`):

```java
boolean has(String key);                       // в любой локали цепочки
boolean has(Locale locale, String key);
Set<String> variables(String key);             // из AST, без регулярок
Set<String> keys(Locale locale);
Set<Locale> locales();
String format(Locale locale, String key, Args args);
String format(Locale locale, String key, String attribute, Args args);
```

### 3.2 `LocaleResolver` — одна точка истины о языке игрока (P1)

```java
@FunctionalInterface
public interface LocaleResolver {
    Locale resolve(Player player);           // null-safe: null -> default
    static LocaleResolver clientLocale() { ... }   // текущее поведение: player.locale
}
```

XCore регистрирует session-aware реализацию **один раз** (`Locales.setResolver(...)` или DI-бином, который HexedCore берёт из `scope`):

```java
LocaleResolver resolver = player -> {
    Session s = sessionService.get(player);
    String lang = s == null ? null : s.data.language;
    String code = lang == null || "auto".equals(lang) ? player.locale : lang;
    return Locales.parse(code);   // нормализация en-US / en_us / алиасы
};
```

После этого `Localization.resolveLocale`, `XCoreSender.locale`, ветки в `CloudCaptionConfigurer`/`MenuService` схлопываются в `resolver.resolve(player)`, а HexedCore автоматически начинает уважать выбранный язык.

### 3.3 `Args` — короче и безопаснее `Map<String,Object>` (P5)

```java
Args.of("seconds", 5)
Args.of("target", target, "reason", reason)          // перегрузки на 1–4 пары, без varargs-ловушки
Args.empty()
Args.builder().put("a", 1).putIf(cond, "b", 2).build()
```

`Args` — тонкая неизменяемая обёртка над `Map`; все методы, принимающие `Map<String,Object>`, остаются как перегрузки на переходный период.

### 3.4 `Text` — сообщение как значение

```java
Text text = Text.of("votekick-fail", Args.of("target", target.coloredName()));
String s = text.render(bundle, locale);
```

Зачем:
- можно передавать «что сказать» между слоями (сервис возвращает `Text`, контроллер решает, куда доставить);
- при рассылке рендерится **один раз на локаль**, а не на игрока (9 локалей против N игроков);
- это же значение естественно ложится на `xcore-ui` (`Text#t(key)` там уже есть по смыслу).

### 3.5 `Audience` — доставка отдельно от форматирования (P2)

Отдельный пакет `com.ospx.flubundle.mindustry`, чтобы ядро не зависело от `Call`/`Groups`:

```java
Messenger m = Messenger.of(bundle, localeResolver);

m.to(player).send("hexed_spectator_eliminated");
m.to(player).announce("hexed_round_started");
m.all().send("hexed_game_finished_announcement", Args.of("winner", winner));
m.team(team).toast(Iconc.warning, "core-under-attack", Args.empty());
m.filter(p -> p.admin).send(text);
m.to(player).popup(text, Popup.at(Align.top).duration(5f));   // параметры popup/label — объектом, а не 7 int'ов
```

Вместо ~40 методов в `Bundle` и дубликатов в `BundleContext` — один набор методов доставки в `Audience`. `Bundle.send/announce/...` и `BundleContext` помечаются `@Deprecated` и делегируют сюда.

### 3.6 Политики ошибок

- `MissingKeyPolicy` (замена `DefaultValueFactory`): `returnKey()`, `logOnce()` (лог `missing key 'x' for ru` один раз на пару), `bracketed()` (`⟦x⟧` для dev/тестов), `throwing()` (для тестов).
- `FormatErrorPolicy` подключается в `FluentBundle.Builder.withLogger(...)` — пропущенные `$`-переменные и ошибки функций попадают в лог с ключом и локалью.

### 3.7 Мост в xcore-ui (P7)

В `Localizer` добавить `has(key)`; адаптер (в XCore, чтобы flubundle не зависел от xcore-ui):

```java
static LocalizerResolver resolver(Localizer l) {
    return new LocalizerResolver() {
        public String format(String key, Map<String, Object> args) { return l.format(key, Args.from(args)); }
        public boolean has(String key) { return l.has(key); }
    };
}
```

## 4. Инструменты (P8)

1. **`FtlCompiler` → проверка набора бандлов**, не только файлов:
   - `missing-key` (warning/error по настройке): ключ есть в базовой локали, нет в другой;
   - `variables-mismatch` (error): набор `$`-переменных ключа отличается между локалями;
   - `orphan-key` (warning): ключ есть в переводе, но не в базовой локали;
   - `duplicate-key-across-plugins`: при проверке нескольких каталогов.
   API для тестов: `FtlCompiler.checkBundleSet(Path dir, Locale base)`.
2. **Генерация констант ключей** (Gradle-таск или annotation processor) из базовой FTL:
   ```java
   // generated
   public final class HexedKeys {
       /** args: seconds */
       public static final String COUNTDOWN_TICK = "hexed_countdown_tick";
   }
   ```
   Следующий шаг — типизированные фабрики `HexedText.countdownTick(int seconds)` → `Text`. Это убирает опечатки в ключах и именах аргументов на этапе компиляции.
3. Тест-хелпер `FluBundleTesting.bundle(Path)` — изолированный `Bundle` для тестов вместо мутации `INSTANCE`.

## 5. План внедрения

### FluBundle 1.8 (только добавления, совместимо)
1. `Bundle.builder()`, `parent`, неизменяемый каталог; `INSTANCE` и `public` поля → `@Deprecated`.
2. `LocaleResolver` + `Bundle.locale(player)` через него.
3. `Args`, `Text`, интроспекция (`has`, `variables`, `keys`, атрибуты).
4. Пакет `mindustry`: `Messenger`/`Audience`; старые методы доставки делегируют туда.
5. `MissingKeyPolicy`, `FormatErrorPolicy` (логгер Fluent).
6. `FtlCompiler`: проверки набора бандлов.

### XCore-plugin
1. Зарегистрировать session-aware `LocaleResolver`, отдать его и `Messenger` в DI scope.
2. Схлопнуть `Localization`, `XCoreSender`, `CloudCaptionConfigurer`, `MenuService.resolverFor` на `LocaleResolver`/`Localizer`.
3. Удалить `BundlePlaceholderRegistry` → `bundle.has(key)` / `bundle.variables(key)`.
4. `SessionService.broadcast*` → `messenger.all()/filter()/team()` (рендер раз на локаль).
5. Тесты: изолированные `Bundle` вместо `Bundle.INSTANCE`; включить `checkBundleSet`.

### HexedCore-plugin
1. Собственный `Bundle` с `parent` = бандл XCore, `LocaleResolver`/`Messenger` из `scope`.
2. Заменить `bundle.locale(p)` + `Call.announce(...)` + `Bundle.args(...)` на `messenger.to(p).announce(key, Args.of(...))` → **исправляет язык сообщений HexedCore**.
3. Сгенерированные константы ключей (173 ключа `hexed_*`).

### FluBundle 2.0 (ломающие изменения)
Удалить методы доставки из `Bundle`, `BundleContext`, `Bundle.INSTANCE`, `DefaultValueFactory`, `numArgs`; `Map<String,Object>`-перегрузки оставить только в `format`.

## 6. Открытые вопросы

1. Один общий каталог с namespace-префиксами или каталог на плагин с `parent`? (Предлагается второе.)
2. Где живёт общий `LocaleResolver`: статический `Locales.setResolver` в FluBundle (проще для плагинов без DI) или только DI-бин XCore (чище)?
3. Нужна ли кодогенерация сразу или достаточно `checkBundleSet` + `MissingKeyPolicy.logOnce()` на первом этапе?
4. Единый стиль ключей (`kebab-case` как в Fluent и XCore) — переименовывать ли `hexed_*`?
