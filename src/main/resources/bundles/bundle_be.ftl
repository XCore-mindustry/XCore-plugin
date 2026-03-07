# ==============================================================================
# Events (Server)
# ==============================================================================

-xcore = XCore сервер
menu-main = Галоўнае меню
commands-main-description = Адкрыццё інтэрактыўнага галоўнага меню
menu-main-title = { "[" }orange]{ -xcore } — Галоўнае меню
menu-main-content = Галоўнае меню сервера
help-menu = Меню дапамогі
commands-help-description = Адкрыццё інтэрактыўнага меню дапамогі
help-menu-title = { "[" }orange]{ -xcore } — Каманды
help-menu-content =
    { "[" }gray]Старонка[white]{ $page }[gray]/[white]{ $total }
    { "" }[lightgray]Выбраць каманду для дэталёвай інформацыі
help-menu-button = { "[" }accent]/{ $command } [gray]» [white]{ $description }
help-command-with-overload-count = { $name } ({ $count })
help-command-title = { "[" }orange]» [white]/{ $name }
help-command-header =
    { "[" }orange]» [accent]Сінтаксіс: [white]{ $syntax }
    { "" }[orange]» [accent]Апіс: [lightgray]{ $description }
player-menu-settings-badges = Бэйджы
badge-menu-title = { "[" }orange]{ -xcore } — Бэйджы
badge-menu-content =
    { "" }[white]Сістэмны бэйдж: [green]{ $systemBadge }[]
    { "" }[white]Актыўны бэйдж: [green]{ $activeBadge }[]
badge-menu-empty = { "[" }lightgray]У вас пакуль няма адкрытых бэйджаў.
badge-menu-row = { "[" }white]{ $badge }[] [gray]-[] { $description }
badge-clear-button = Зняць актыўны бэйдж
badge-set-success = { "[" }accent]Актыўны бэйдж усталяваны: [green]{ $badge }[].
badge-clear-success = { "[" }accent]Актыўны бэйдж зняты.
badge-grant-success = { "[" }accent]Бэйдж [green]{ $badge }[] выдадзены гульцу [green]{ $nickname }[][gray]#{ $pid }[].
badge-revoke-success = { "[" }accent]Бэйдж [green]{ $badge }[] зняты ў гульца [green]{ $nickname }[][gray]#{ $pid }[].
badge-already-unlocked = { "[" }scarlet]⚠ Бэйдж [accent]{ $badge }[scarlet] ужо адкрыты.
badge-not-owned = { "[" }scarlet]⚠ У гульца няма бэйджа [accent]{ $badge }[scarlet].
error-badge-not-found = { "[" }scarlet]⚠ Бэйдж [accent]{ $badge }[scarlet] не знойдзены.
error-badge-not-unlocked = { "[" }scarlet]⚠ Бэйдж [accent]{ $badge }[scarlet] не адкрыты.
error-badge-not-selectable = { "[" }scarlet]⚠ Бэйдж [accent]{ $badge }[scarlet] нельга выбраць уручную.
badge-admin-name = Адмін
badge-admin-description = Аўтаматычны бэйдж для адміністратараў.
badge-developer-name = Распрацоўшчык
badge-developer-description = Выдаецца распрацоўшчыкам XCore.
badge-translator-name = Перакладчык
badge-translator-description = Выдаецца тым, хто дапамагае з перакладам XCore.
badge-map-maker-name = Мапмэйкер
badge-map-maker-description = Выдаецца аўтарам мап, што выкарыстоўваюцца на серверы.
badge-contributor-name = Кантрыб'ютар
badge-contributor-description = Выдаецца за значны ўклад у праект або супольнасць.
badge-event-winner-name = Пераможца івэнту
badge-event-winner-description = Выдаецца пераможцам спецыяльных падзей сервера.
badge-veteran-name = Ветэран
badge-veteran-description = Выдаецца паважаным даўнім гульцам.
help-aliases = { "[" }orange]» [accent]Псеўданімы: [white]{ $aliases }
help-args-title = { "[" }orange]» [accent]Аргументы:
help-usages-title = { "[" }orange]» [accent]Выкарыстанне:
help-usage-entry = { "[" }gray]• [white]{ $syntax }
help-usage-args-title = { "[" }orange]» [accent]Для [white]{ $syntax }[accent]:
help-arg-entry = { "[" }gray]• [white]{ $arg } [lightgray]- { $description }
help-no-arguments = { "[" }gray]Дадатковыя аргументы не патрабуюцца.
help-no-arg-description = Не мае апісання.
help-no-description = Апісанне гэтай каманды адсутнічае.
help-legacy-command-content =
    { "[" }orange]» [accent]Каманда: [white]/{ $name }
    { "" }[orange]» [accent]Параметры: [white]{ $params }
    { "" }[orange]» [accent]Апіс: [lightgray]{ $description }
    { "" }
    { "" }[gray](Гэта састарэлая каманда з абмежаванай інфармацыяй)
help-legacy-command-content-no-params =
    { "[" }orange]» [accent]Каманда: [white]/{ $name }
    { "" }[orange]» [accent]Апіс: [lightgray]{ $description }
    { "" }
    { "" }[gray](Гэта састарэлая каманда з абмежаванай інфармацыяй)
help-back = { "[" }lightgray]« Назад
commands-help-page-description = Нумар старонкі для адлюстравання
commands-login-password-description = Ваш пароль адміністратара
commands-ban-id-description = ID гульца для бана
commands-ban-period-description = Працягласць бана (напрыклад: 1d, 2h, 30m)
commands-ban-reason-description = Прычына бана
commands-unban-id-description = ID гульца для разбана
commands-mute-id-description = ID гульца для мута
commands-mute-period-description = Працягласць мута (напрыклад: 1h, 30m)
commands-mute-reason-description = Прычына мута
commands-unmute-id-description = ID гульца для зняцця мута
commands-votekick-target-description = Гулец для кіка (ID ці імя)
commands-votekick-reason-description = Прычына кіка
commands-vote-choice-description = Ваш голас: y (так), n (не) або c (адмена, толькі адмін)
commands-t-message-description = Паведамленне для адпраўкі саюзнікам
commands-g-message-description = Паведамленне для адпраўкі на ўсе серверы
commands-tr-language-description = Код мовы, 'uk_UA', 'en', '...', 'auto' або 'off'
commands-stats-id-description = ID гульца для прагляду статыстыкі
commands-rank-player-description = Гулец для прагляду рэйтынгу
commands-map-map-description = Назва ці нумар карты
commands-maps-page-description = Нумар старонкі
commands-maps-text-page-description = Нумар старонкі
commands-rtv-map-description = Карта для галасавання (неабавязкова)
commands-artv-map-description = Карта для прымусовай змены
commands-ai-state-description = Стан AI: атака (a) або бяздзейнасць (i)
commands-events-page-description = Нумар старонкі
commands-information-description = Паказаць інфармацыю аб серверы
commands-info = Інфармацыя
commands-info-title = { "[" }orange]{ -xcore } — { $server-name }
commands-info-text =
    { "[" }accent]XCore[white] гэта [cyan]бязмежны[white] сервер для гульні ў [accent]Mindustry[white].
    { "" }
    { "" } Версія XCore — [accent]{ $version }[white]
commands-sync-description = Сінхранізуйце гульню з серверам. Выканайце гэта для выпраўлення памылак (напрыклад прывідныя юніты).
none = Няма
error-nickname-badge-glyph = { "[" }scarlet]⚠ Карыстальніцкі нік не можа ўтрымліваць зарэзерваваныя іконкі бэйджаў.
