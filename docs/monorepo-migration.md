# План перехода XCore на монорепо

Согласовано 2026-11-01. Основа: два раунда council (Claude Opus 5.5, GPT-6.1 Sol, Gemini 3.8 Flash High), один по монорепо, второй по dev-инструменту, плюс решения владельца.

Согласованный план сохранён в публичном монорепо; локальные данные и секреты в него не входят.

## 0. Статус реализации (2026-11-02)

- P0–P4 выполнены, PR #57–#59 смёржены; первые релизы библиотек опубликованы.
- P5: код перенесён, PR #60 смёржен. Прод-сервисы пока работают из прежних checkout через uv/tmux; переключение ещё не выполнялось.
- P6: выполняется перенос web и dev-инструмента. Уточнение: команда `./dev`, каталог `devtools/` (файл и каталог с одним именем невозможны).
- P7–P8 остаются после проверки последней волны и необходимых эксплуатационных шагов.

## 1. Итоговые решения

| Вопрос | Решение |
|---|---|
| Где монорепо | Существующий `XCore-mindustry/XCore-plugin`, переименованный в `xcore`. История плагина не переписывается |
| Суперрепо (`XCore-mindustry/xcore`, приватное) | **Удалить** после последней волны. В начале переименовать в `xcore-legacy`, чтобы освободить имя `xcore`. Никогда не делать публичным и не пушить через `--mirror` |
| Что входит | XCore-plugin, flubundle, cloud-mindustry, xcore-ui, mindustry-testkit, xcore-protocol, TileLogger, XCore-discord-bot, xcore-metrics-gateway, xcore-web, новый dev-инструмент |
| Что остаётся отдельно | Приватные: xcore-sentinel, aethercore-plugin (HexedCore), TheSiege. Внешние: rvsb-plugin, Tower-defence-plugin. Отдельный форк: AdminTools. Внешняя зависимость: fluent |
| Сборка JVM | Одна Gradle multi-project сборка, один wrapper, один `libs.versions.toml`, convention-плагины в `build-logic/` |
| Сборка Python | uv workspace: protocol (Python), discord-bot, metrics-gateway, один `uv.lock` |
| Web | Отдельный bun-проект в `web/`, без JS workspace |
| Версии | Независимые у каждого компонента. Maven-координаты и имена пакетов не меняются |
| Dev-инструмент | Новый `./dev` (раздел 10). dev.sh, dev_stack.py и accounts-стек не переносятся |

## 2. Лицензии

| Компонент | Лицензия |
|---|---|
| plugins/xcore, services/discord-bot, services/metrics-gateway, web | AGPL-3.0 + исключение для плагинов |
| libs/flubundle, libs/cloud-mindustry, libs/xcore-ui, libs/mindustry-testkit, protocol | MPL-2.0 |
| plugins/tilelogger | GPL-3.0, как было |
| devtools/, build-logic/, корень | MPL-2.0 |

- **Исключение для плагинов.** Это дополнительное разрешение по разделу 7 AGPL: отдельные плагины, которые используют API XCore, под AGPL не попадают. Без него AGPL может зацепить закрытый sentinel и внешние rvsb/Tower-defence. Текст брать готовый, по образцу GPL linking exception, не сочинять. Перед публикацией дать вычитать.
- **Старые лицензии.** Код, полученный под MIT, остаётся с MIT-уведомлением: старый `LICENSE` сохраняется в каталоге компонента, например как `LICENSE.MIT`. Выпущенные версии остаются под MIT. Согласие контрибьюторов юридически не нужно, но активных (BRamil0, gorynich2048 и других) стоит предупредить, что новые PR идут под новой лицензией.
- **Без лицензии сейчас:** flubundle, xcore-protocol, mindustry-testkit, metrics-gateway, AdminTools. Первые четыре получают лицензию при переезде. AdminTools — отдельное решение, лицензию оригинального мода не проверяли.
- В каждом каталоге компонента лежит свой `LICENSE`. В `README.md` корня — таблица лицензий.

## 3. Структура

```
xcore/
  settings.gradle.kts  build.gradle.kts  gradlew  gradle/libs.versions.toml
  build-logic/                       # convention-плагины
  libs/flubundle/
  libs/cloud-mindustry/              # включая example-plugin
  libs/xcore-ui/
  libs/mindustry-testkit/{core,ui,fixtures}/
  protocol/                          # репо xcore-protocol целиком: spec, generators, java, python, typescript, fixtures, compat
  plugins/xcore/                     # бывший XCore-plugin (git mv, история как есть)
  plugins/tilelogger/                # включая native/
  services/discord-bot/
  services/metrics-gateway/
  web/
  devtools/                               # новый dev-инструмент (раздел 10)
  pyproject.toml  uv.lock            # корень uv workspace
  .github/  .coderabbit.yaml  renovate.json  README.md  docs/
  dev                                # shim: python3 -m devtools
```

## 4. Сборка

### JVM
- Внутренние зависимости превращаются в project-зависимости: `project(":libs:flubundle")` и т.д. `implementation` и `compileOnly` остаются как были. XCore-plugin продолжает шейдить flubundle, cloud, ui и protocol, но теперь собирает их из HEAD.
- TileLogger подключает `compileOnly(project(":plugins:xcore"))`.
- `build-logic`:
  - `xcore.java25`;
  - `xcore.java17-lib`: JDK 25, `options.release = 17`. Нужен для protocol-java и cloud-mindustry, потому что AdminTools на Java 17 и Android dex;
  - `xcore.toxopid`;
  - `xcore.publish`: Reposilite `maven.x-core.org`, креды из env.
- Check-задача падает, если class-файлы protocol-java или cloud-mindustry выше major version 61.
- Maven artifactId не должны измениться из-за переименования каталогов или проектов. Каждый задаётся явно.
- Убрать `mavenLocal`, шаг `publishToMavenLocal` для xcore-ui и `local-protocol.init.gradle`.
- fluent остаётся внешней Maven-зависимостью.

### Python
- `xcore-protocol = { workspace = true }` в боте и gateway, без PyPI-пина и git-тега.
- Dockerfile бота и gateway собираются с контекстом от корня репо (или через `uv export`).
- PyPI-публикацию `xcore-protocol` оставить.

### Генерация протокола
- Сгенерированный код остаётся в репо.
- CI запускает генератор, затем `git diff --exit-code`.
- Gradle Python не вызывает.

## 5. Перенос истории

Делается только в локальном одноразовом клоне скриптом, который можно перезапускать. Ничего не пушит, пока человек не проверит результат.

1. XCore-plugin: история не переписывается. Один коммит `git mv` всего в `plugins/xcore/`. Теги `v5.x` остаются как есть.
2. Каждый импортируемый репо, в отдельном свежем клоне:
   ```
   git filter-repo --to-subdirectory-filter <dir> \
     --tag-rename 'v:<component>/v' \
     --message-callback '<переписать голые #N в XCore-mindustry/<старый-репо>#N>'
   ```
   Затем в монорепо выполнить `git merge --allow-unrelated-histories`, по одному merge на репо.
3. Переносится только `main`. Устаревшие ветки удалить заранее, открытых PR и issues в этих репо нет (проверено).
4. Старый репо архивируется (не удаляется) с указателем в README, но только после первого успешного релиза компонента из монорепо. Release assets, сборки JitPack и uv git-теги в архиве продолжают работать.
5. Недостающие теги flubundle и xcore-ui восстанавливать, только если опубликованный jar совпадает с коммитом. Если не совпадает, тег не создавать.
6. Перед любым push прогнать gitleaks по каждой импортированной истории.

## 6. Версии и публикация

- Версия каждого компонента хранится в его собственных build-файлах или `gradle.properties`. Protocol Java и Python делят одну версию, как сейчас.
- Теги имеют вид `<component>/vX.Y.Z`: `flubundle/v2.1.3`, `cloud-mindustry/v0.4.1`, `xcore-ui/v…`, `testkit/v…`, `protocol/v0.18.0`, `xcore/v5.4.0`, `tilelogger/v…`.
- `release.yml` срабатывает на `*/v*`. Он разбирает компонент и проверяет, что тег совпадает с версией в сборке. Публикует только этот подпроект, а релиз TileLogger переиспользует матрицу natives. Работает в защищённом environment `release` с `XCORE_USERNAME`/`XCORE_PASSWORD`.
- Координаты остаются прежними: `com.ospx:flubundle`, `org.xcore:cloud-mindustry`, `org.xcore:xcore-ui`, `org.xcore.testkit:*` (проверить фактические), `org.xcore:xcore-protocol-java`, `org.xcore.plugin:XCore-plugin`.
- SNAPSHOT библиотек больше не публикуются. cloud-mindustry публикуется только в Reposilite, без JitPack.
- Бот, gateway и web не публикуются, они деплоятся из `main` (механизм деплоя — открытый вопрос, раздел 13).
- Против «исправили, но не выпустили» работают две вещи:
  - CI-проверка требует указать в PR, нужен ли релиз затронутого публикуемого компонента;
  - еженедельный job выводит компоненты с коммитами после последнего тега: `git log <tag>..HEAD -- <dir>`.

## 7. CI

- Один `ci.yml`. Job `changes` (dorny/paths-filter) решает, какие jobs запускать, с учётом зависимостей: изменение библиотеки тестирует её потребителей.
  - `gradle`: любой JVM-каталог, `gradle/` или `build-logic/`. Запускает `./gradlew build` с кешем setup-gradle (на PR только чтение) и configuration cache.
  - `menu-render`: plugins/xcore, xcore-ui, testkit.
  - `tilelogger-native`: матрица linux x64/arm, windows и sanitizers, только при изменениях в `plugins/tilelogger/native/**` и на релизах.
  - `python`: ruff, mypy baseline, pytest с Mongo и Redis.
  - `protocol-codegen`: проверка расхождения сгенерированного кода.
  - `web`: bun lint, test, build.
  - `dev`: тесты `devtools/tests/`.
- Единственный обязательный check — агрегатор `ci-ok` (`if: always()`). Он падает, если упал любой из jobs, от которых он зависит. Пропущенные по путям jobs merge не блокируют.
- В `.coderabbit.yaml` исключить сгенерированный код, lock-файлы и fixtures. Draft-PR не ревьюить.
- Убрать дублирующие publish-воркфлоу протокола и рутинный `--refresh-dependencies`.

## 8. Сателлиты

- Сателлиты продолжают брать релизные артефакты из `maven.x-core.org`, в их сборке ничего не меняется.
- Renovate с кастомным registry `maven.x-core.org/releases`. Одна сгруппированная PR «xcore core», патчи автоматически мёржатся при зелёном CI. Для приватных репо боту нужен доступ.
- В каждом сателлите CI-проверка падает на `-SNAPSHOT` в version catalog.
- AdminTools продолжает получать protocol-java под Java 17, это гарантирует проверка class-версий.
- Перед cutover проверить, что sentinel, HexedCore и AdminTools собираются против артефактов из монорепо.

## 9. Фазы

| # | Шаг | Условие перехода | Откат |
|---|---|---|---|
| P0 | Заморозка, удаление устаревших веток, релиз всего невыпущенного. Суперрепо переименовать в `xcore-legacy`. Тексты лицензий | Все core-репо зелёные, SNAPSHOT у потребителей нет | Ничего необратимого |
| P1 | Локальный скрипт импорта в одноразовом клоне, можно перезапускать | Выборочный `git log --follow` в порядке, теги на месте, gitleaks чистый | Удалить клон |
| P2 | **Волна 1:** flubundle, cloud-mindustry, xcore-ui, testkit, protocol (подключена только Java-часть) в репо XCore-plugin, ветка `monorepo`, общая сборка, PR | `./gradlew build` зелёный. `jar tf` и POM библиотек, shadow-jar плагина совпадают с v5.3.0, кроме намеренных изменений | Закрыть PR |
| P3 | Merge, переименование XCore-plugin → `xcore`, `ci-ok` обязательный, патч-релизы библиотек из монорепо | Артефакты резолвятся из Reposilite, сателлиты собираются | Старые репо ещё живые, переименование обратимо |
| P4 | **Волна 2:** TileLogger | Нативная матрица зелёная, jar совпадает | Закрыть PR |
| P5 | **Волна 3:** discord-bot, metrics-gateway, uv workspace, проверка codegen | Тесты, Docker-образы собираются, деплой работает | Прежние образы |
| P6 | **Волна 4:** web, новый `./dev` | Раздел 10, «Тесты и проверки», smoke с чистого клона | Закрыть PR |
| P7 | Архивация старых репо после первого релиза каждого компонента. Удаление суперрепо после чеклиста (раздел 12) | Чеклист пройден | Архивацию можно отменить. Удалённый репо организации восстанавливается около 90 дней |
| P8 | Renovate и проверка SNAPSHOT в сателлитах | Первая сгруппированная PR смёржена | Выключить приложение |

Каждая волна оформляется отдельным PR.

## 10. Dev-инструмент `./dev`

### Принципы
- **Без пресетов и режимов.** Разработчик называет то, над чем работает, а инструмент поднимает всё нужное для этого.
- **Ровно один реестр целей.** dev.sh сломался потому, что имена плагинов жили в четырёх местах (`case` в dev.sh, `ensure_plugins_built`, алиасы в entrypoint, volume-монтирования в compose) и разъехались. Compose и entrypoint не знают ни одного имени плагина.
- Python 3.11+, только stdlib (`tomllib`, `zipfile`, `hashlib`, `subprocess`). Это тонкая обёртка над `docker compose` и Gradle.

### Использование
```
./dev up tilelogger       # game с TileLogger + xcore, mongo, redis
./dev up hexed            # game с сателлитом из local.toml и его зависимостями
./dev up web              # web, mongo, redis, демо-сид; OAuth из local.toml
./dev up xcore bot        # несколько целей
./dev reload              # пересобрать текущий набор, перезапустить при изменениях
./dev down                # остановить, данные сохранить
./dev logs [svc]  ./dev console  ./dev status
./dev reset               # удалить volumes проекта, с явным подтверждением
./dev check               # бывший test-env: готовность mongo rs, транзакции, redis
./dev config diff|apply|restore   # перенесено из dev_stack.py вместе с тестами
```

### Файлы
```
devtools/
  __main__.py          # argparse, вызовы compose/gradle (цель — не больше ~300 строк)
  targets.py           # единственный реестр
  configcmd.py         # config diff/apply/restore из dev_stack.py
  compose.yaml         # один файл, без container_name, порты на 127.0.0.1, без profiles
  mindustry/           # Dockerfile, entrypoint.sh, tools/config-file.sh, config/*.toml
  mongo/init-rs.sh
  seed/demo.js  seed/audit.js
  tools/publish_mock_servers.py    # самостоятельный скрипт, dev его не вызывает
  local.example.toml   # в git
  local.toml           # в .gitignore
  .run/                # в .gitignore: mods/, targets.json, env
  tests/
```

### Реестр
```python
TARGETS = {
    "xcore":      Plugin(mod="xcore-plugin", task=":plugins:xcore:shadowJar",        jar="plugins/xcore/build/libs/<точное имя>.jar"),
    "tilelogger": Plugin(mod="TileLogger",   task=":plugins:tilelogger:jarRelease",  jar="plugins/tilelogger/build/libs/<точное имя>.jar"),
    "web":     Services("web", "web-seed"),
    "bot":     Services("discord-bot"),
    "metrics": Services("metrics-gateway", "prometheus", "grafana"),
}
```
- Любая цель-плагин добавляет сервис `game`.
- Путь к jar указывается точно: без глобов и без выбора «самого свежего».
- `dev` передаёт имена сервисов напрямую (`docker compose up -d game web`). mongo, mongo-init и redis подтягивает `depends_on`.

### Разрешение зависимостей плагинов
1. Собрать названные цели: один вызов Gradle для целей монорепо, свой Gradle у каждого сателлита.
2. Прочитать `plugin.json` из каждого jar через `zipfile`.
3. Для каждой жёсткой зависимости найти, кто даёт этот mod: сначала `TARGETS` по `mod`, потом сателлиты из `local.toml` по ключу или по `mod =`. Имена сравнивать с той же нормализацией, что в Mindustry.
4. Собрать найденных поставщиков, повторять, пока ничего не пропущено.
5. Мягкие зависимости в сборку не попадают, но если такой плагин тоже запрошен, порядок загрузки учитывает зависимость.
6. Ошибки, которые останавливают `up` до любых изменений в рантайме:
   - нет поставщика (в сообщении имя mod и jar, который его требует);
   - два поставщика одного mod;
   - цикл;
   - jar без `plugin.json`.

### Staging
- Готовый набор jar копируется в `devtools/.run/mods.new/`, затем атомарно заменяет `devtools/.run/mods/`.
- Compose монтирует только этот каталог (read-only). Entrypoint очищает `config/mods` и копирует туда всё из монтирования.
- Переменные `PLUGINS`, список алиасов и монтирования `mods-dev/*` исчезают.
- Перед стартом game проверить, что жёсткие зависимости каждого jar тоже лежат в staging: Mindustry молча пропускает плагин, только пишет строку в лог.

### Семантика `up` и `reload`
- `up A B` заменяет предыдущий набор целей. Сервисы, не нужные новому набору, останавливаются, volumes остаются. Удачный набор записывается в `.run/targets.json`.
- `reload`:
  1. читает `.run/targets.json`, без активного набора завершается с ошибкой;
  2. пересобирает полное замыкание (Gradle сам решает, что пересобирать, проверок по mtime нет);
  3. заново читает jar сателлитов и их зависимости;
  4. сравнивает хэши staged jar. Без изменений печатает «nothing changed» и не перезапускает. Иначе выполняет `docker compose restart game`;
  5. для целей web или bot выполняет `up -d --build <svc>`.
  Если сборка или валидация упали, работающий сервер не трогается. Базы, сейвы и живые конфиги сохраняются.

### Env и секреты
- `dev` читает `local.toml` и никогда в него не пишет.
- Секреты передаются в `docker compose` через env подпроцесса. **Набор env одинаковый на каждом вызове**, иначе `./dev up tilelogger` пересоздаст запущенный web без OAuth-секретов.
- `status`, `check` и сообщения об ошибках не печатают значения секретов.

### `devtools/local.toml`
```toml
[game]                       # всё необязательно
server_name = "dev"          # также идёт в XCORE_ACCOUNT_SERVERS у web
gamemode = "survival"
auto_start = true
jvm_opts = "-Xmx2G"

[web]
origin = "http://localhost:3100"
discord_client_id = ""
discord_client_secret = ""

[bot]
discord_bot_token = ""
guild_id = 0

[satellite.sentinel]         # ключ — имя, которое пишут в ./dev up
repo = "~/src/xcore-sentinel"
task = "shadowJar"
jar = "build/libs/xcore-sentinel.jar"   # относительно repo

[satellite.hexed]
jar = "~/jars/hexedcore-plugin.jar"     # готовый jar, без repo
mod = "hexedcore-plugin"                # только если ключ не совпадает с name в plugin.json
game = { auto_start = false }
```
- Относительные пути считаются от `devtools/local.toml`, `jar` при заданном `repo` — от `repo`.
- Сателлит задаётся готовым `jar` либо исходным `repo` с обязательными `task` и точным путём `jar`. Сборка сателлита выполняется так: `<repo>/gradlew -p <repo> <task>` с аргументами массивом. Инструмент ничего не клонирует, не сканирует соседние каталоги и не собирает сателлиты, которые не запрошены.
- `game = {...}` у сателлита заменяет бывшие режимы: auto_start у hexed, имя сервера и т.п. Если у выбранных целей значения расходятся, это ошибка.
- Значения `game` уходят в entrypoint через env: `MINDUSTRY_GAMEMODE`, `MINDUSTRY_SERVER_NAME`, `XCORE_AUTO_START`. `sed` по конфигам плагинов в entrypoint больше нет.
- Неизвестные ключи вызывают ошибку.

### Сайт и бывший accounts-стек
- Отдельного стека нет: `./dev up web` использует общие mongo и redis.
- `seed/demo.js` (бывший `docker/accounts/seed-demo.js`) нужно адаптировать:
  - работать без карт, которые регистрирует game;
  - быть идемпотентным;
  - не трогать существующие записи;
  - вместо имени `xcore_accounts_local` проверять, что база — dev-база.
- `permissions.toml` из accounts вливается в единый config seed игры.
- `seed/audit.js`: убрать удаление всей коллекции и личные идентификаторы.

### Что выкидываем
- dev.sh и `dev-stack/tests/test-dev-args.sh`;
- `docker-compose.yml` (побайтовый дубль), `docker/accounts.compose.yaml`, флаг `--accounts`;
- `.env`, `.env.example`, `docker/accounts/oauth.env.example` (их заменяет `local.toml`);
- хостовую копию `scripts/init-mongo-rs.sh`;
- туннель: `scripts/dev_tunnel.py`, `scripts/start_tunnel.sh`, сервис tunnel;
- команды `build` и `restart` (их заменяет `reload`);
- режимы, `combo` и алиасы;
- проверки по mtime и глобы jar;
- фиксированные `container_name`;
- приватные монтирования и сателлиты по умолчанию.

### Что оставляем
- Dockerfile с закреплённой версией Mindustry;
- интерактивную консоль;
- persistence;
- один `init-rs.sh`;
- предупреждение entrypoint о расхождении config seed;
- `config-file.sh`;
- `config diff/apply/restore` с бэкапами, скрытием значений и тестами (`test_dev_stack.py`);
- `test-env` как `./dev check`;
- сиды.

### Тесты и проверки
- **Разрешение зависимостей.** Замыкание, нормализация имён, мягкие зависимости, нет поставщика, два поставщика, цикл, jar без `plugin.json`, поставщик-сателлит, алиас `mod =`.
- **Валидация `local.toml`.** Неизвестные ключи, готовый `jar`, полный набор `repo` + `task` + `jar`, неполный набор, конфликт `game`.
- **Реестр против compose.** Каждый сервис из `TARGETS` есть в `docker compose -f devtools/compose.yaml config --services`. Именно этот рассинхрон сломал dev.sh.
- **`up`.** Замена набора убирает лишние плагины из staging.
- **`reload`.** Без изменений нет перезапуска. При упавшей сборке сервер остаётся как был.
- **Секреты.** Значения не попадают в вывод.
- **Сиды.** Демо-сид отказывается работать не на dev-базе и не портит существующие записи.
- **`down` и `reset`.** `down` сохраняет volumes, `reset` требует подтверждения.
- **Smoke с чистого клона.** `./dev up xcore`, затем `./dev up web`.

### Принятый риск
Сателлиты собираются против релизного XCore, а в dev-сервере работают рядом с XCore из HEAD монорепо. Сейчас всё устроено так же. `includeBuild`-подмену добавлять, только если что-то реально сломается.

## 11. Чего не делаем в рамках миграции

- редизайн API и протокола;
- апгрейд зависимостей, включая Mindustry v160.7;
- миграции данных в базах;
- импорт сателлитов;
- массовое удаление репо;
- CI или деплой-изменения сверх описанных;
- новые фичи dev-инструмента, которых не было в старом (в том числе `doctor` с проверкой портов).

## 12. Проверки перед завершением миграции

- Сохранить локальные незакоммиченные данные и конфиги отдельно, не публикуя их.
- Никогда не публиковать приватную историю суперрепозитория, refs/t3 или mirror refs.
- Проверить новый dev-инструмент на чистом клоне.
- Проверить эксплуатационные пути и переключение развёрнутых сервисов.
- Архивировать старый компонент только после успешного релиза/проверки замены.
- Суперрепозиторий сначала архивировать; удаление — не раньше двух недель без обращений к нему и после проверки резервных копий.

## 13. Уточнения, полученные при реализации

- Бот и gateway запущены через uv/tmux, с Python 3.14 и 3.13 соответственно. Перенос runtime должен сохранять конфиги и версии Python.
- Maven-координаты testkit: org.xcore.testkit:core, ui, fixtures.
- Лицензионные согласия подтверждены владельцем; исключение включено в компоненты.
- PyPI trusted publisher для xcore/release.yml/pypi настроен; протокол 0.17.1 опубликован.
- Каталог devtools/ сохраняет согласованную короткую команду ./dev.
