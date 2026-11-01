# cloud-mindustry #1: проект реализации

Статус: спецификация, runtime/build files пока не изменены. Дата: 2026-10-06.
Baseline — GitHub main `ee2441c1`, версия 0.3.0. Локальный checkout старее;
начинать реализацию следует с актуального baseline.
Решения XCore: [roadmap](../../../XCore-plugin/docs/architecture/permissions-and-cloud-roadmap.md).

## 1. Граница библиотеки

Cloud уже предоставляет command permissions, composite permissions, captions,
help model и execution coordinator. Библиотека адаптирует их к Mindustry.
Роли, grant storage, server scopes, defaults каталога и authentication принадлежат
приложению. `setPermissionChecker(BiPredicate<C,String>)` сохраняет нынешний
контракт и permissive library default. XCore устанавливает свой строгий checker.

Tristate/resolver chain/automatic registry не вводить в этой версии. Boolean hook
позволяет внешнему приложению использовать любой resolver/matcher самостоятельно.
Default ADMIN/CONSOLE нельзя включить автоматически: нынешний default library
разрешает все непустые permissions, смена изменит поведение потребителей.

Не добавлять Fluent/Mongo/Redis/Avaje зависимости в lib.

## 2. Canonical snapshot

Build/test выполняются при push всех branches и pull_request. Canonical snapshot
публикуется только `event_name == push && ref == refs/heads/main` при credentials.
`workflow_dispatch` только build/artifacts, без побочного изменения snapshot.
Tags/release workflow сохраняют отдельную release policy. Feature-branches
получают Actions artifacts; branch Maven versions сейчас не нужны.

Publish condition следует отделить от build/test: отсутствие credentials не
должно пропускать сборку. Resolve snapshot version требуется только publish job.
Test criterion: матрица main push / branch push / PR / manual run / tag показывает
ровно один допустимый canonical publish case. Развёрнутый CI integration test
не нужен для простой condition; проверить YAML и существующий build pipeline.

## 3. Selector affinity

Arc Application.getMainThread()/isOnMainThread() присутствуют и в compile dependency
v154.3, и в XCore runtime v160; это проверено по cached API. `isOnMainThread()`
при неизвестном main thread может возвращать true, поэтому для строгой affinity
нужен non-null getMainThread() и сравнение identity.

```java
public interface SimulationExecutor extends Executor {
    boolean isOnThread();
    void requireOnThread();
    static SimulationExecutor forApplication(Application application);
    static SimulationExecutor bound(Thread thread, Executor dispatcher);
}
```

Это signatures: static factories и requireOnThread получают тела в реализации.
ForApplication привязан к конкретному Application, не глобальному static Thread:
`isOnThread = app.getMainThread()!=null && current==app.getMainThread()`.
Execute запускает inline на этом потоке, иначе вызывает app.post. До готовности
main thread synchronous selector resolve выдаёт explicit readiness error.
Missing Core.app не означает «любой поток игровой»: в тестах bound executor.
Application не принадлежит executor: библиотека его не dispose/close.

Manager хранит executor и передаёт его в один SpatialSelectorEngine. Новый
constructor overload принимает executor. Existing constructor выбирает adapter
для Core.app; library factory использует тот же instance как execution executor:

```java
var simulation = SimulationExecutor.forApplication(Core.app);
var coordinator = ExecutionCoordinator.<MindustrySender>coordinatorFor(simulation);
var manager = new MindustryCommandManager<>(handler, coordinator,
        SenderMapper.identity(), simulation);
```

ExecutionCoordinator.coordinatorFor(Executor) существует в Cloud 2.0.0.
Default pipeline parse/permission/handler и suggestions маршалятся на simulation
executor целиком. Исключения/вывод с доступом к Player также должны сохранять
affinity; проверить actual coordinator completion paths, а не только handler.
Raw sender mapping в Arc wrapper либо выполняется на simulation thread, либо
не читает live player state до hop. XCore custom coordinator оборачивает именно
simulation coordinator своим telemetry decorator.

Custom async coordinator остаётся разрешён для CPU-only работы, но не делает
live Player/Groups thread-safe. Селектор, вызванный worker, обязан явно перейти
через executor; `resolve()` сам не блокирует future.get/join. Это deliberate
behaviour change следующего minor release. Новую отдельную resolveAsync API
в первой версии не вводить: достаточно executor и command coordinator.

SelectorResolutionBridge static state/heuristics/reflection/blocking resolve
убрать из нового execution path. Старый публичный bridge пометить удаляемым API
следующего release; не сохранять скрытый global fallback ради совместимости.
Selectors вызывают engine, который требует свой executor thread.
Rect/IntSet/Seq scratch принадлежат одному resolution call. Per-call state
устраняет nested resolution bug; оно не разрешает concurrent Groups/spatial access.

Shutdown: новые game actions после application/plugin shutdown отклоняются
adapter/lifecycle; callers отменяют собственные pending command futures. Не
обещать manager-wide cancellation всех чужих executor tasks. Без synchronous
wait уже нет заблокированных workers, ожидающих остановленный game loop.

Проверки: отдельный owner thread и controlled queue; два managers с разными
executors; fake null main thread; worker resolve fail; queued command проходит
parse/handler/error на owner; nested selector не портит scratch; late action
после shutdown не мутирует мир. Ни названия потоков, ни reflection в тестах.

## 4. Reversible override

ArcCommandRegistrationHandler хранит owned registration на каждый physical name,
включая aliases и PREFIX names:

```java
record OwnedRegistration(String name, CommandHandler.Command wrapper,
                         CommandHandler.Command displaced, int previousIndex) {}
```

Register OVERRIDE captures прежний объект и его позицию до remove; устанавливает
wrapper в commands map и list. Deletion действует только если текущий map entry
тождественен owned wrapper. Тогда удалить wrapper из map/list, восстановить
displaced object на прежнюю позицию (clamped к размеру list), если он был.
Root/aliases обрабатываются независимо. Root остаётся опубликованным, пока Cloud
удаляет весь root, а не один subcommand variant.

Если другой plugin заменил wrapper — не удалять чужой entry/list object и не
восстанавливать старый поверх него. Metadata owned entry удалить при unregister;
она больше не даёт library право на чужую регистрацию. SKIP/FAIL/PREFIX прежние.
Если register FAIL оборвался на alias после mutations, rollback только собственных
изменений этой попытки; чужие modifications не трогать.

Arbitrary-order unload нескольких перекрывающих managers не гарантируется:
для него потребуется shared owner stack. Сейчас не вводить plugin unload framework.
Обычная пара vanilla -> wrapper -> delete восстанавливается полностью.
Проверить root, alias, no displacement, third-party replacement, повтор deletion,
command list/map agreement и partial registration rollback.

## 5. Player-only

```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PlayerOnly {}

// Builder: .meta(MindustryCommandManager.PLAYER_ONLY, true)
```

Manager поддерживает один CloudKey<Boolean>. Новая explicit
registerMindustryAnnotations(parser) регистрирует и selectors, и PlayerOnly;
existing registerSelectorAnnotations сохраняет только свой старый контракт.
XCore переключает свою init integration на новый helper.

Command postprocessor проверяет `senderMapper.reverse(sender).isPlayer()`.
False -> PlayerRequiredException с caption `mindustry.sender.player_required`;
handler не вызывается. Обратный mapper нужен и при XCoreSender, и при любом
custom C. Command-specific session readiness остаётся делом приложения.
Help visibility использует этот же metadata predicate; annotation не является
permission node и не повторяет роль.

Postprocessor может сработать после argument parsing: malformed input сначала
может дать parse error. Не обещать player-only error раньше всех parsers и не
изобретать второй parser pipeline ради этого. Server-only inverse annotation
сейчас не добавлять без потребителя.

## 6. Help и Arc paramText

Arc Command.paramText final и участвует в parsing. Для wrapper он остаётся
`[args...]`, чтобы Cloud принимал полный input без ограничений Arc specification.
Ни reflection на final, ни Java Unsafe для красивого vanilla help.

```java
public final class MindustryHelp<C> {
    public MindustryHelp(MindustryCommandManager<C> manager, int pageSize);
    public MindustryHelp(MindustryCommandManager<C> manager, int pageSize,
                        BiPredicate<C, Command<C>> visibility);
    public void sendIndex(C sender, int page);
    public void sendQuery(C sender, String query, int page);
}
```

Это небольшой renderer существующих HelpHandler result types. PageSize >0,
page начинается с 1; invalid page сообщает caption. Default example — 8 entries.
Двухаргументный constructor использует дополнительный visibility = true.
Command-specific visibility добавляется к Cloud permission/player-only filtering,
не заменяет его. Formatter Cloud создаёт syntax/argument descriptions; sender
mapper доставляет строки player/console. ConsoleSender уже strips colors в 0.3.0.

Captions: title, entry, usage, page header/footer, empty, no-match, invalid-page.
Args caption содержат text/syntax/page/pages, а не internal exception message.
Provider возвращает null для unknown caption, сохраняя defaults Cloud.
Renderer не создаёт новую библиотеку localization/template DSL.

App явно регистрирует help command через manager builder/annotations. Constructor
helper ничего не override/register. Без opt-in vanilla help продолжит показывать
[args...]; это документированное ограничение решения пункта #2.
Help для PREFIX collision использует actual published physical root names,
не предлагает недоступное canonical имя. SKIP roots не показываются как доступные.
Внутренняя registration metadata может дать renderer эту mapping без публичного
permission registry API.

Legacy Arc entries опциональны через отдельный helper method с CommandHandler
и explicit visibility predicate. У них нет Cloud permissions; не объявлять их
автоматически permission-filtered. XCore сохраняет собственное rich menu presentation
и общий Cloud selection; переписывание UI на chat renderer не требуется.

Проверки: permitted variants одного root; compound allOf/anyOf; PlayerOnly custom
sender; page boundary/empty/unknown query; PREFIX/SKIP; aliases без duplicate entries;
legacy visibility; unknown caption fallback. Fixtures сравнивают messages и доступные
syntax, а не приватные методы renderer.

## 7. Offline PlayerInfo

```java
MindustryParsers.playerInfo(); // ParserDescriptor<C, Administration.PlayerInfo>
```

Parser работает с локальным Administration state на simulation thread, без
файлов/Mongo/Redis. UUID -> exact historical names (raw/stripped) -> console IP;
результат при multiple matches — captioned ambiguous error, без first-match choice.
`#entityId` сначала разрешает только online entity -> getInfo. PlayerInfo не имеет
постоянного offline ID, такой ID не придумывается. IP принимается только console;
IP/name suggestion policy не раскрывает закрытые данные player sender.

Captioned not-found/ambiguous/ip-denied errors. Suggestions минимальны и bounded;
не перечислять всю историю Administration каждому пользователю. Не использовать
Administration.findByName напрямую как «name-only»: он также сравнивает IP/UUID.
Authorization команды происходит средствами Cloud; parser сам не выдаёт moderation.

XCore использует другой local command type PlayerTarget:
`Pid(int)`, `Uuid(String)`, `Username(String)`, `Name(String)`. Формы `#pid`/signed
numeric, `uuid:<uuid>`, `@username`, name; sentinel Integer.MIN_VALUE rejected.
Parser распознаёт форму без I/O. Resolver выполняет нынешний async profile lookup
и возвращает immutable profile ref; ambiguous nickname не выбирается случайно.
Generated wire PlayerRef/PlayerCommandTarget используются лишь при transport,
их локальные копии как DTO не создаются.

Проверки library UUID/historical name/ambiguity/IP policy/online entity ID и
отсутствие game-state access из worker. XCore отдельно проверяет signed PID,
zero PID, sentinel rejection и невозможность смешать PID с online entity ID.

## 8. Release и последовательность

CI и override исправления могут быть отдельными patch PR. Affinity меняет
off-thread resolve contract; PlayerOnly/help/parser добавляют public API —
объединённый следующий minor release, например 0.4.0.
Не публиковать эти несовместимые изменения под прежним 0.3.0 snapshot как незаметный fix.
Java baseline библиотеки остаётся 17; v154.3 compile API проверяется отдельно
от интеграционных v160 scenarios. XCore обновляет dependency после release.

Migration notes: new constructor executor; default coordination on game thread;
worker selector resolve больше не blocking; static bridge replacement;
explicit new annotation registration; opt-in help; library permission defaults
сохраняются. Release примеры показывают player/console и mapped sender без XCore deps.
