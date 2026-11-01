# Решения по permissions и cloud-mindustry

Статус: проект готов для реализации; изменения ограничены документацией.
Дата: 2026-10-06.

Исходные предложения:
[cloud-mindustry #1](https://github.com/XCore-mindustry/cloud-mindustry/issues/1),
[XCore-plugin #17](https://github.com/XCore-mindustry/XCore-plugin/issues/17).
Оба принять с ограниченным scope. Они описывают цели, а детали реализации
определяют следующие документы:

- [Спецификация XCore permissions](permissions-design-spec.md).
- [Спецификация cloud-mindustry](../../../cloud-mindustry/docs/design/roadmap-implementation.md).
- [План PR, миграции и проверки](../implementation/permissions-implementation-plan.md).

Спецификации заменяют раннее предложение в этом файле. В частности, credentials
переносятся вместе с grants в отдельный security document; требование login
принадлежит permission node, а не role definition.

## 1. Что принять

| Предложение | Решение первой версии |
| --- | --- |
| Единые проверки chat/UI/packets/services | PermissionService и проверка общей action boundary |
| Роли и наследование | Config definitions; без role weights |
| Wildcard и deny | Exact/последний .*/*; direct overrides старше roles |
| Scope и expiry | Global или конкретный server; UTC expiry |
| Audit и синхронизация | Общий Mongo commit; revision invalidation + periodic reload |
| Player.admin | Производная от mindustry.admin после activation |
| Login | Общий password/token flow и явный enrollment нового staff |
| Permission management | Local console; отдельные info/check для диагностики |
| Discord | Configured staff role IDs -> XCore roles; независимые source grants |
| Cloud permissions API | Оставить boolean hook; roles/matcher/catalog в XCore |
| Arc usage/help | Opt-in Cloud help; parser specification остаётся [args...] |
| Player-only и offline parser | Library API без XCore/Mongo dependencies |
| Selector affinity | Executor на manager; Arc main-thread API; per-call scratch |
| Command override | Восстановление вытесненных root/aliases при owned unregister |
| Canonical snapshots | Publish только main push; остальные ветки build/artifacts |

Native offline PlayerInfo и XCore PlayerTarget решают разные задачи. Первый
использует локальный Administration, второй — PID/profile lookup без I/O в parser.
Discord permissions expressions, bot/web grant editor, gamemode contexts,
owner-player wildcard, hot role-definition distribution и backend SPI отложены.
Они не нужны для moderator/event-organizer и ограничений отдельного admin.

## 2. Модель использования

Определения ролей находятся в общем permissions.toml; назначения, срок,
source и credentials — в permission_subjects. Обычный PlayerData save не может
перезаписать этот документ. Роль даёт eligibility; вход текущего подключения
активирует STAFF nodes. Moderator может mute/kick и читать staff history без
native admin. Event organizer получает только события/force-rtv.
Управление правами доступно local console независимо от wildcard игрока.

Нет двойного authentication switch на роли/узле, числового приоритета ролей
и resolver chain. Explain показывает winning rule, source/scope/expiry,
activation/freshness и revision. Возврат DENY должен быть понятен оператору.

По уточнению пользователя Discord mapping входит в первую версию:
Moderator -> moderator, Admin -> admin; третий уровень при необходимости —
Senior Moderator -> senior-moderator. Общая Staff роль может быть декоративной.
Назначения синхронизируются автоматически, authentication остаётся отдельной.

## 3. Почему нельзя ограничиться заменой admin checks

На baseline XCore обнаружены конкретные ограничения:

| Место | Следствие для проекта |
| --- | --- |
| CloudPermissionPolicy разрешает незнакомые non-admin strings | Checker менять до установки granular annotations |
| PlayerData.admin хранит Discord eligibility | Не импортировать boolean как активную Session |
| Administration.adminPlayer вызывает save | Нынешний login оставляет registry state без provenance |
| DataRepository.save заменяет whole profile | Security state нельзя хранить в обычном PlayerData |
| AdminModIntegration делает synchronous auth | Chat и packet login переводить на один async path |
| Bot пишет password_hash/is_admin напрямую | Credentials cutover требует согласованных bot/plugin adapters |
| Session lookup по UUID | Проверять actual Player/connection/session, защищаться от reconnect |
| UI и ModerationActor имеют отдельные gates/fallback | Проверять действия и origin, не только annotations |
| Account merge копирует auth/admin state | Security merge должен быть явным и отзываться по обоим UUID |

Проверено по XCore-plugin `ae3c5a27`. Это baseline проектирования, не утверждение
о будущих commits. Inventory и критерии перехода находятся в плане реализации.

## 4. Library baseline и совместимость

GitHub cloud-mindustry main на момент проверки: `ee2441c1`, 0.3.0.
Локальный checkout `8c89f6f2` старее: перед реализацией обновить отдельную ветку,
не повторять уже сделанные fixes caption/exception handling, selector restrictions,
Team/content parsers и console colors.

Arc Application.getMainThread существует в compile API v154.3 и runtime v160.
Не нужен reflection/thread-name guessing или повышение minimum Mindustry только
ради affinity. Default game-command coordination использует один executor manager.
Custom worker coordinator обязан соблюдать thread affinity live game state.

[args...] остаётся Arc parse contract; красивый help — явный helper приложения.
Library не получает Fluent, Mongo, Redis, Avaje или XCore role model.
Library permissive checker default сохраняется; XCore устанавливает строгий checker.
Изменение worker selector semantics документировать в следующем minor release.

## 5. Последовательность и готовность

Library CI/override исправления независимы от новой role model.
XCore сначала вводит каталог и COMPAT checks, затем engine/storage/auth,
после чего закрывает обходы в UI/services/packets и делает SHADOW report.
ROLES включается после coordinated credentials migration и проверки cutover.

Критерии готовности: полный grant -> enroll -> login -> action -> revoke сценарий;
безопасный reconnect/logout во время async работы; deny/expiry/scopes;
атомарный audit; устойчивость к stale/out-of-order invalidations; native compatibility;
проверенный план rollback. Одних unit tests matcher недостаточно.

После ROLES rollback — отдельная migration с повторной authentication,
а не переключатель, превращающий moderator в legacy full admin.
Runtime implementation, protocol generation, PR creation и GitHub comments
не выполнялись в рамках этого проектирования.
