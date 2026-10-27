# ==============================================================================
# Terms
# ==============================================================================
-xcore = XCore сервер
# ==============================================================================
# General & Help
# ==============================================================================
menu-main = Галоўнае меню
commands-main-description = Адкрыццё інтэрактыўнага галоўнага меню
menu-main-title = [orange]{ -xcore } — Галоўнае меню
menu-main-content = Галоўнае меню сервера
help-menu = Меню дапамогі
commands-help-description = Адкрыццё інтэрактыўнага меню дапамогі
help-menu-title = [orange]{ -xcore } — Каманды
help-menu-content =
    [gray]Старонка [white]{ $page }[gray]/[white]{ $total }
    [lightgray]Выбраць каманду для дэталёвай інфармацыі
help-menu-button = [accent]/{ $command } [gray]» [white]{ $description }
help-command-with-overload-count = { $name } ({ $count })
help-command-title = [orange]» [white]/{ $name }
help-command-header =
    [orange]» [accent]Сінтаксіс: [white]{ $syntax }
    [orange]» [accent]Апіс: [lightgray]{ $description }
help-aliases = [orange]» [accent]Псеўданімы: [white]{ $aliases }
help-args-title = [orange]» [accent]Аргументы:
help-usages-title = [orange]» [accent]Выкарыстанне:
help-usage-entry = [gray]• [white]{ $syntax }
help-usage-args-title = [orange]» [accent]Для [white]{ $syntax }[accent]:
help-arg-entry = [gray]• [white]{ $arg } [lightgray]- { $description }
help-no-arguments = [gray]Дадатковыя аргументы не патрабуюцца.
help-no-arg-description = Не мае апісання.
help-no-description = Апісанне гэтай каманды адсутнічае.
help-legacy-command-content =
    [orange]» [accent]Каманда: [white]/{ $name }
    [orange]» [accent]Параметры: [white]{ $params }
    [orange]» [accent]Апіс: [lightgray]{ $description }
    { "" }
    [gray](Гэта састарэлая каманда з абмежаванай інфармацыяй)
help-legacy-command-content-no-params =
    [orange]» [accent]Каманда: [white]/{ $name }
    [orange]» [accent]Апіс: [lightgray]{ $description }
    { "" }
    [gray](Гэта састарэлая каманда з абмежаванай інфармацыяй)
help-back = [lightgray]« Назад

# ==============================================================================
# Modern Reactive Help & Commands Guide (xcore-ui)
# ==============================================================================
help-ui-title = ДАВЕДНІК КАМАНД
help-ui-summary = [gray]Даступна каманд: { $count }
help-ui-empty-category = [lightgray]У гэтай катэгорыі няма каманд.[]
help-ui-overloads = (варыянтаў: { $count })
help-ui-aliases = [lightgray]Псеўданімы:[] { $aliases }
help-ui-syntax-title = Сінтаксіс
help-ui-args-title = Параметры
help-ui-arg-required = [scarlet]Абавязковы
help-ui-arg-optional = [sky]Неабавязковы
help-ui-btn-run = Выканаць
help-ui-btn-copy = У чат
help-ui-btn-back = Назад
help-ui-copied = [accent]Каманда: [white]/{ $syntax }
help-ui-executed = [accent]Выконваецца каманда: [white]/{ $syntax }

# Categories
help-cat-all = Усе
help-cat-general = Агульнае
help-cat-game = Гульня
help-cat-social = Чат
help-cat-votes = Галасаванні
help-cat-admin = Адмін
# ==============================================================================
# Command Argument Descriptions
# ==============================================================================
# help
commands-help-page-description = Нумар старонкі для адлюстравання
# login
commands-login-password-description = Ваш пароль адміністратара
# ban
commands-ban-id-description = ID гульца для бана
commands-ban-period-description = Працягласць бана (напрыклад: 1d, 2h, 30m)
commands-ban-reason-description = Прычына бана
# unban
commands-unban-id-description = ID гульца для разбана
# mute
commands-mute-id-description = ID гульца для мута
commands-mute-period-description = Працягласць мута (напрыклад: 1h, 30m)
commands-mute-reason-description = Прычына мута
# unmute
commands-unmute-id-description = ID гульца для зняцця мута
# votekick
commands-votekick-target-description = Гулец для кіка (ID ці імя)
commands-votekick-reason-description = Прычына кіка
# vote
commands-vote-choice-description = Ваш голас: y (так), n (не) або c (адмена, толькі адмін)
# t (team chat)
commands-t-message-description = Паведамленне для адпраўкі саюзнікам
# g (global chat)
commands-g-message-description = Паведамленне для адпраўкі на ўсе серверы
# tr (translator)
commands-tr-language-description = Код мовы, 'uk_UA', 'en', '...', 'auto' або 'off'
# stats
commands-stats-id-description = ID гульца для прагляду статыстыкі
# rank
commands-rank-player-description = Гулец для прагляду рэйтынгу
# map
commands-map-map-description = Назва ці нумар карты
# maps / maps-text
commands-maps-page-description = Нумар старонкі
commands-maps-text-page-description = Нумар старонкі
# rtv / artv
commands-rtv-map-description = Карта для галасавання (неабавязкова)
commands-artv-map-description = Карта для прымусовай змены
# ai
commands-ai-state-description = Стан AI: атака (a) або бяздзейнасць (i)
# event / events
commands-events-page-description = Нумар старонкі
# ==============================================================================
# General & Help (continued)
# ==============================================================================
commands-information-description = Паказаць інфармацыю аб серверы
commands-info = Інфармацыя
commands-info-title = [orange]{ -xcore } — { $server-name }
commands-info-text =
    [accent]XCore[white] гэта [cyan]бязмежны[white] сервер для гульні ў [accent]Mindustry[white].
    { "" }
    Версія XCore — [accent]{ $version }[white]
commands-sync-description = Сінхранізуйце гульню з серверам. Выканайце гэта для выпраўлення памылак (напрыклад прывідныя юніты).
commands-discord-description = Адкрывае меню Discord.
discord-menu-title = [orange]{ -xcore } — Discord
discord-menu-content =
    [white]Кіруйце прывязкай Discord тут.
    { "" }
    [white]Статус: { $status }
    [white]Сервер: [accent]{ $discordUrl }[]
discord-menu-open = Адкрыць Discord
discord-menu-link = Прывязаць акаўнт
discord-menu-status = Абнавіць статус
discord-menu-unlink = Адвязаць акаўнт
discord-menu-status-not-linked = [lightgray]не прывязаны[]
discord-menu-status-linked = [green]{ $discordUsername }[] [gray]({ $discordId })[]
discord-link-menu-title = [orange]{ -xcore } — Прывязка Discord акаўнта
discord-link-menu-content =
    [white]На нашым Discord серверы выклічце slash-каманду бота:
    { "" }
    [accent]/link { $code }[]
    { "" }
    [white]Скончыцца праз: [accent]{ $expireMinutes }[] хв
    [white]Discord: [accent]{ $discordUrl }[]
discord-link-menu-refresh = Абнавіць код
discord-link-menu-copy = Скапіяваць код
discord-link-menu-regenerate = Згенераваць новы код
discord-link-menu-status = Назад у меню Discord
welcome =
    [accent]Сардэчна запрашаем на { $serverName }!
    [lightgray]Увядзіце [accent]/help[lightgray], каб убачыць спіс каманд
    [lightgray]Увядзіце [accent]/vote [gray]<y/n>[lightgray], каб прагаласаваць за выключэнне гульца
    [lightgray]Увядзіце [accent]/votekick [gray]<ID/імя> <прычына…>[lightgray], каб пачаць галасаванне за выключэнне
    [lightgray]Увядзіце [accent]/t [gray]<паведамленне…>[lightgray], каб напісаць сваёй камандзе
    [lightgray]Увядзіце [accent]/g [gray]<паведамленне…>[lightgray], каб напісаць на ўсе серверы
    [lightgray]Увядзіце [accent]/tr [gray]<мова/auto>[lightgray], каб уключыць перакладчык
    [lightgray]Увядзіце [accent]/discord[lightgray], каб адкрыць меню Discord і прывязаць акаўнт
# ==============================================================================
# Chat & Social
# ==============================================================================
commands-t-description = Адправіць паведамленне толькі сваёй камандзе.
commands-t-chat = [{ "#" }{ $color }][Каманда] [coral]> { $badge }[accent]{ $name }[lightgray]: [white]{ $message }
commands-g-description = Адправіць паведамленне на ўсе серверы.
commands-a-description = Адправіць паведамленне толькі адмінам.
commands-msg-description = Адправіць асабістае паведамленне гульцу.
commands-msg-id-description = ID гульца.
commands-msg-message-description = Тэкст асабістага паведамлення.
commands-reply-description = Адказаць апошняму гульцу ў асабістых паведамленнях.
commands-reply-message-description = Тэкст адказу.
commands-inbox-description = Адкрыць меню асабістых паведамленняў.
commands-inbox-id-description = ID гульца.
commands-tr-description = Задаць мову перакладчыка.
commands-badge-description = Адкрыць меню бэйджаў і кіраваць актыўным бэйджам.
commands-tr-success = [accent]Мова перакладчыка зменена на [grey]{ $translatorLanguage }[]!
commands-tr-off = [accent]Перакладчык [scarlet]выключаны[]!
commands-tr-not-found = [scarlet]⚠ Такой мовы няма.
discord-chat-format = [#5865F2][DISCORD][] [lightgray]| [accent]{ $author }[lightgray] >> [white]{ $message }
global-chat-format = [royal][[[orange]ГЛАБАЛЬНЫ [lightgray](з [accent]{ $server }[])[] { $author }[]]: [white]{ $message }
private-message-received = [sky][АП][] [lightgray]ад [accent]{ $author } [gray]#{ $pid }[lightgray]: [white]{ $message }
private-message-sent = [sky][АП][] [lightgray]для [accent]{ $target } [gray]#{ $pid }[lightgray]: [white]{ $message }
private-message-unread-count =
    [accent]У вас [white]{ $count }[accent] { $count ->
        [one] непрачытанае асабістае паведамленне
        [few] непрачытаныя асабістыя паведамленні
        [many] непрачытаных асабістых паведамленняў
       *[other] непрачытанага асабістага паведамлення
    }.
private-message-join-notification =
    [accent]У вас [white]{ $count }[accent] { $count ->
        [one] непрачытанае асабістае паведамленне
        [few] непрачытаныя асабістыя паведамленні
        [many] непрачытаных асабістых паведамленняў
       *[other] непрачытанага асабістага паведамлення
    }. Каб { $count ->
        [one] прачытаць яго
       *[other] прачытаць іх
    }, увядзіце [white]/inbox[accent].
private-message-block-success = [accent]Асабістыя паведамленні ад [white]{ $target } [gray]#{ $pid }[accent] цяпер заблакаваны.
private-message-block-already = [lightgray]Асабістыя паведамленні ад [white]{ $target } [gray]#{ $pid }[lightgray] ужо заблакаваны.
private-message-unblock-success = [accent]Асабістыя паведамленні ад [white]{ $target } [gray]#{ $pid }[accent] больш не заблакаваны.
private-message-unblock-missing = [lightgray][white]{ $target } [gray]#{ $pid }[lightgray] не заблакаваны.
private-message-menu-title = [orange]{ -xcore } — Асабістыя паведамленні
private-message-menu-content =
    [white]Старонка [green]{ $page }[] з [green]{ $total }[]
    [white]Непрачытаных: [accent]{ $unread }[]
private-message-menu-empty = [lightgray]Вашая скрынка пустая.
private-message-menu-entry-unread = [accent]Непрачытанае[] [white]{ $author } [gray]#{ $pid }[] [lightgray]({ $time })[]: [white]{ $message }
private-message-menu-entry-read = [gray]Прачытанае[] [white]{ $author } [gray]#{ $pid }[] [lightgray]({ $time })[]: [white]{ $message }
private-message-details-title = [orange]{ -xcore } — Паведамленне
private-message-details-content =
    [white]Ад: [accent]{ $author } [gray]#{ $pid }[]
    [white]Час: [accent]{ $time }[]
    [white]Статус: [accent]{ $status }[]
    { "" }
    [white]{ $message }
private-message-status-unread = непрачытанае
private-message-status-read = прачытанае
private-message-blocked-title = [orange]{ -xcore } — Заблакаваныя гульцы
private-message-blocked-content =
    [white]Старонка [green]{ $page }[] з [green]{ $total }[]
    [white]Заблакавана: [accent]{ $count }[]
private-message-blocked-empty = [lightgray]У вас няма заблакаваных гульцоў.
private-message-blocked-entry = [white]{ $target } [gray]#{ $pid }[]
private-message-compose = Новае паведамленне
private-message-blocked = Заблакаваныя
private-message-block = Заблакаваць адпраўніка
private-message-unblock = Разблакаваць адпраўніка
private-message-reply-title = Адказаць
private-message-reply-message = Увядзіце паведамленне для [accent]#{ $pid }[]
private-message-compose-target-title = Новае паведамленне
private-message-compose-target-message = Увядзіце ID гульца ў фармаце [accent]#123[]
private-message-compose-body-title = Тэкст паведамлення
private-message-compose-body-message = Увядзіце асабістае паведамленне для [accent]{ $pid }[]
# ==============================================================================
# Authentication & Admin Access
# ==============================================================================
commands-login-description = Актываваць правы адміна, калі ваш прывязаны акаўнт Discord ужо мае доступ.
commands-login-incorrect-password = [scarlet]⚠ Няправільны пароль!
commands-login-success = [green]Правы адміна выдадзены.
commands-login-confirmed = [green]Доступ адміна праз Discord пацверджаны.
commands-login-admin-password-created =
    [green]Пароль адміна створаны.
    [red]Не забудзьце пароль! Калі забудзеце, давядзецца прасіць галоўнага адміністратара скінуць яго.
commands-login-request-approval-discord = [accent]Ваш акаунт не мае Discord-доступу адміністратара. Атрымайце роль адміністратара ў Discord і паспрабуйце зноў.
commands-login-verifying = [lightgray]Праверка пароля адміністратара...
commands-login-already-processing = [scarlet]⚠ Запыт на ўваход ужо апрацоўваецца. Пачакайце.
commands-login-rate-limited = [scarlet]⚠ Занадта шмат няўдалых спроб уваходу. Пачакайце перад наступнай спробай.
commands-discord-link-created =
    [green]Код прывязкі Discord створаны: [accent]{ $code }[]
    [lightgray]На нашым Discord серверы выклічце slash-каманту бота [accent]/link { $code }[] на працягу [accent]{ $expireMinutes }[] хв.
    [cyan]{ $discordUrl }
commands-discord-link-confirmed = [green]Discord акаўнт прывязаны: [accent]{ $discordUsername }[]
commands-discord-link-already-linked = [lightgray]Гэты акаўнт Mindustry ужо прывязаны. Скарыстайце [accent]/discord status[] або [accent]/discord unlink[].
commands-discord-link-error = [scarlet]Не ўдалося стварыць код прывязкі Discord. Паспрабуйце пазней.
commands-discord-status-not-linked = [lightgray]Ваш акаўнт не прывязаны да Discord.
commands-discord-status-linked = [green]Прывязаны Discord: [accent]{ $discordUsername }[] [gray]({ $discordId })[]
commands-discord-unlink-not-linked = [lightgray]Ваш акаўнт не прывязаны да Discord.
commands-discord-unlink-success = [green]Прывязка Discord выдалена.
commands-logout-description = Выйсці. Гэта [scarlet]адбярэ ў вас правы адміна.
commands-logout-successful = [green]Правы адміна адабраныя.
# ==============================================================================
# Moderation (Ban, Mute, Kick)
# ==============================================================================
commands-ban-description = Заблакаваць гульца.
commands-ban-success = { $nickname } [scarlet]заблакаваны
commands-unban-description = Разблакаваць гульца.
commands-unban-success = { $nickname }[accent] #{ $pid } [green]разблакаваны.
commands-mute-description = Выдаць гульцу мут.
commands-mute-success = [accent]{ $nickname } атрымаў мут
commands-unmute-description = Зняць мут з гульца.
commands-unmute-success = [green]Мут зняты з []{ $nickname }
commands-alert-description = Паказвае выбраным або ўсім гульцам прыкметны банер з аб'явай.
commands-toast-description = Паказвае выбраным гульцам усплывальнае папярэджанне.
commands-announcement-description = Адпраўляе перыядычную аб'яву па ключы або наступную па чарзе.
commands-audit-description = Паказвае гісторыю і дзеянні адміністрацыі і мадэрацыі.
ban-content = [scarlet]⚠ Заблакаваны[]
    [accent]{ $nickname }[white] — вы назаўсёды заблакаваныя на гэтым серверы.
    [orange]» [accent]ID гульца: [white]{ $pid }
    [lightgray]Каб абскардзіць, зайдзіце ў канал Discord [gray]{ support-channel }[]:
    [cyan]{ $discordUrl }
ban-cancelled = [accent]Блакіроўка гульца [scarlet]{ $nickname }[accent] адменена
tempban-content = [scarlet]⚠ Заблакаваны[]
    { $permanent ->
        [true] [accent]{ $nickname }[white] — вы назаўсёды заблакаваныя на гэтым серверы.
       *[false] [accent]{ $nickname }[white] — вы часова заблакаваныя на гэтым серверы.
    }
    [orange]» [accent]Блакіроўка: [white]{ $banId }
    [orange]» [accent]ID гульца: [white]{ $pid }
    { "" }
    [orange]» [accent]Адмін: [white]{ $adminName }
    [orange]» [accent]Прычына: [gold]{ $reason }
    { $permanent ->
        [true] [orange]» [accent]{ audit-menu-duration-permanent }[]
       *[false] [orange]» [accent]Засталося: { DURATION($duration, style: "full", colored: "true", maxUnits: 2) }
            { "" }[orange]» [accent]Сканчаецца: [white]{ DATETIME($expireDate, dateStyle: "medium", timeStyle: "short") }
    }
    { "" }
    [lightgray]Каб абскардзіць, зайдзіце ў канал Discord [gray]{ support-channel }[]:
    [lightgray]Пазначце { $banId } у звароце.
    [cyan]{ $discordUrl }
tempban-player-banned = [scarlet] Адмін { $adminName }[scarlet] заблакаваў гульца [gray]'[]{ $playerName }[gray]'
you-are-muted-by =
    [orange]⚠ Чат абмежаваны[]
    [lightgray]Адміністратар [accent]{ $adminName }[lightgray] выдаў вам мут.
    [orange]» [accent]Прычына: [gold]{ $reason }
    [orange]» [accent]Засталося: { DURATION($duration, style: "full", colored: "true", maxUnits: 2) }
you-are-muted =
    [orange]⚠ Чат абмежаваны[]
    [lightgray]Пакуль дзейнічае мут, вы не можаце адпраўляць паведамленні.
    [orange]» [accent]Адмін: [white]{ $adminName }
    [orange]» [accent]Прычына: [gold]{ $reason }
    [orange]» [accent]Засталося: { DURATION($duration, style: "full", colored: "true", maxUnits: 2) }
kick-pirated-game = [accent]Выяўлены неаўтарызаваны кліент. [scarlet]Доступ забаронены[]. Калі ласка, гуляйце ў [lime]афіцыйную[] версію са [blue]Steam[], [blue]Google Play[] або [blue]itch.io[].
kick-recently-kicked =
    [accent]Вас нядаўна выключылі з гэтага сервера.
    Пачакайце [cyan]{ DURATION($remaining, style: "timer") }[accent], перш чым зноў заходзіць.
kick-admintools-outdated =
    [green]Патрэбная версія AdminTools: [grey]{ $requiredVersion }[]
    [scarlet]Ваша версія AdminTools: [grey]{ $version }[]
    { "" }
    [cyan]Абнавіце AdminTools, каб зайсці на гэты сервер.
support-channel = #tickets

# ==============================================================================
# Cases: player reports, in-game notices and the /report menu
# ==============================================================================
report-menu-title = [orange]{ -xcore } — Скарга на гульца
report-menu-pick-player = Выберыце гульца
report-menu-left-recently = Нядаўна выйшлі
report-menu-left-ago = { $minutes } хв таму
report-menu-empty = Няма гульцоў для скаргі
report-menu-staff-hint = [lightgray]Скаргі на адміністрацыю падавайце ў Discord.
report-menu-pick-reason = Выберыце прычыну
report-menu-comment = Каментар (неабавязкова)
report-menu-comment-required = Для прычыны «Іншае» патрэбен каментар.
report-menu-confirm-title = Пацвердзіце скаргу
report-menu-confirm-note = [lightgray]Дадасца апошні чат і гісторыя будаўніцтва. Несапраўдныя скаргі караюцца.
report-menu-next = Далей
report-menu-back = Назад
report-menu-send = Адправіць
report-menu-close = Закрыць
report-result-sent = [green]Скарга адпраўлена. Дзякуй!
report-result-cooldown = [scarlet]Пачакайце [accent]{ $seconds } с[scarlet] перад наступнай скаргай.
report-result-same-target = [scarlet]Вы нядаўна ўжо скардзіліся на гэтага гульца.
report-result-daily-limit = [scarlet]Дзённы ліміт скаргаў вычарпаны. Паспрабуйце заўтра.
report-result-invalid-target = [scarlet]На гэтага гульца нельга паскардзіцца.
report-result-playtime = [scarlet]Адгуляйце яшчэ [accent]{ $minutes } хв[scarlet], каб адпраўляць скаргі.
report-result-disabled = [scarlet]Скаргі выключаны на гэтым серверы.
report-result-failed = [scarlet]Не ўдалося адправіць скаргу. Паспрабуйце яшчэ раз.
report-reason-griefing = Грыферства
report-reason-toxicity = Таксічнасць
report-reason-cheating = Чытэрства
report-reason-nsfw = NSFW
report-reason-evasion = Абыход блакіроўкі
report-reason-other = Іншае
case-notice-report-received = [lightgray]Скарга зарэгістравана пад нумарам [accent]#{ $number }[lightgray]. Дзякуй!
case-notice-report-resolved = [lightgray]Скарга [accent]#{ $number }[lightgray] разгледжана: [green]прыняты захады[lightgray]. Дзякуй.
case-notice-report-rejected =
    { $reason ->
        [no_violation] [lightgray]Скарга [accent]#{ $number }[lightgray] разгледжана: парушэнняў не выяўлена. Дзякуй.
        [no_evidence] [lightgray]Скарга [accent]#{ $number }[lightgray] разгледжана: недастаткова доказаў. Дзякуй.
        [already_handled] [lightgray]Скарга [accent]#{ $number }[lightgray] закрыта: выпадак ужо вырашаны. Дзякуй.
        [duplicate] [lightgray]Скарга [accent]#{ $number }[lightgray] закрыта: пра гэта ўжо паведамілі раней. Дзякуй.
        [abuse] [lightgray]Скарга [accent]#{ $number }[lightgray] адхілена: [scarlet]яна сама парушае правілы сервера.
       *[other] [lightgray]Скарга [accent]#{ $number }[lightgray] разгледжана: захадаў не прынята. Дзякуй.
    }
case-notice-report-not-accepted = [scarlet]Ваша скарга не прынята.
case-notice-appeal-accepted = [lightgray]Апеляцыя [accent]#{ $number }[lightgray] прынята: блакіроўка знята.
case-notice-appeal-shortened = [lightgray]Апеляцыя [accent]#{ $number }[lightgray] прынята: тэрмін блакіроўкі скарочаны.
case-notice-badge-granted = [lightgray]Запыт на бэйдж [accent]#{ $number }[lightgray] ухвалены: бэйдж [green]{ $badge }[lightgray] выдадзены.
case-notice-badge-rejected = [lightgray]Запыт на бэйдж [accent]#{ $number }[lightgray] адхілены.
case-notice-map-accepted = [lightgray]Праверка карты [accent]#{ $number }[lightgray]: карта [green]{ $map }[lightgray] прынята.
case-notice-map-rejected = [lightgray]Праверка карты [accent]#{ $number }[lightgray]: карта [scarlet]{ $map }[lightgray] адхілена.
case-notice-complaint-closed = [lightgray]Зварот [accent]#{ $number }[lightgray] закрыты. Дзякуй.

# ==============================================================================
# Voting (VoteKick)
# ==============================================================================
commands-votekick-description = Галасаванне за выключэнне гульца з сервера.
commands-vote-description = Прагаласаваць у бягучым галасаванні.
commands-vote-vote-with = [scarlet]⚠ Галасуйце праз [orange]/vote <y/n/c>
votekick-vote =
    { $starter } [grey]#[white]{ $starterId }[lightgray] прагаласаваў за выключэнне { $target } [grey]#[white]{ $targetId }[lightgray] з прычыны [orange]{ $reason }[lightgray]. ([accent]{ $votes }[]/[accent]{ $required }[])
    [lightgray]Увядзіце [orange]/vote <y/n>[], каб прагаласаваць.
votekick-left = { $player }[lightgray] выйшаў. Яго голас адменены. ([accent]{ $votes }[]/[accent]{ $required }[])
votekick-fail = [lightgray]Галасаванне не прайшло. Недастаткова галасоў, каб выключыць { $target }[lightgray].
votekick-cancelled = [scarlet]Галасаванне за выключэнне { $target }[scarlet] адмяніў { $admin }.
votekick-success =
    [orange]Галасаванне прайшло. { $target }[orange] выключаны на [scarlet]{ $minutes }[] { $minutes ->
        [one] хвіліну
        [few] хвіліны
        [many] хвілін
       *[other] хвіліны
    }.
# ==============================================================================
# Maps & RTV
# ==============================================================================
commands-map-description = Статыстыка канкрэтнай карты.
commands-map-title = [orange]{ -xcore } — Карта
commands-map-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $name }[] [sky]{ $authorLabel }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Агляд[]
    [gray]Памер: [white]{ $width }x{ $height } [darkgray]|[gray] Галасы: [lime]+{ $like } [darkgray]/[scarlet] -{ $dislike }[]
    { "" }
    [accent]■ Актыўнасць[]
    [gray]Усяго гульняў: [white]{ $played } [darkgray]|[gray] У гэтым годзе: [white]{ $playedYear }[]
    [gray]Апошняя гульня: [white]{ $lastPlayed }[]
    [gray]Папулярнасць: [white]{ $popularity } [darkgray]|[gray] Цікавасць: [white]{ $interest } [darkgray]|[gray] Рэпутацыя: [white]{ $reputation }[]
    { "" }
    [accent]■ Працягласць матчу[]
    [gray]Мін.: [white]{ $min } [darkgray]|[gray] Сяр.: [white]{ $avg } [darkgray]|[gray] Макс.: [white]{ $max }[]
commands-maps-description = Спіс усіх карт на гэтым серверы.
commands-maps-title = [orange]{ -xcore } — Карты
commands-maps-content =
    [gray]Бягучая карта: [accent]{ $current }[]
    [white]Старонка [green]{ $page }[] з [green]{ $total }[]
commands-maps-current-row = { $name } ★
commands-maps-text-description = Спіс усіх карт на гэтым серверы.
commands-maps-text-start-content =
    [accent]Бягучая карта: []{ $name }[white]
    [orange][gold]Спіс карт [lightgray]{ $page }[gray]/[lightgray]{ $total }
commands-maps-text-content =
    { "" }
    { $index }. [orange] - [white]{ $name }[orange] | [green]{ $reputation }[orange] | [white]{ $width }x{ $height }[orange] | [white]{ $lastPlayed }[orange] | Аўтар: [sky]{ $author }
commands-artv-description = Адразу змяніць карту.
commands-artv-map-skipped = { $nickname }[accent] прапусціў карту. Наступная карта: { $name }.
commands-artv-event-skipped = { $nickname }[accent] прапусціў падзею. Наступная падзея: { $name }.
commands-rtv-description = Галасаванне за змену карты.
commands-vnw-description = Галасаванне за датэрміновы пачатак наступнай хвалі.
commands-avnw-description = Адразу пачаць наступную хвалю.
commands-like-description = Прагаласаваць за бягучую карту (павышае яе рэпутацыю).
commands-dislike-description = Прагаласаваць супраць бягучай карты.
map-vote-title = [orange]{ -xcore } — [scarlet]ГУЛЬНЯ СКОНЧАНА!
map-vote-content =
    { "" }
    Наступная карта: [accent]{ $mapName }[], аўтар [accent]{ $author }[white].
    Новая гульня пачнецца праз [accent]{ $seconds }[white] { $seconds ->
        [one] секунду
        [few] секунды
        [many] секунд
       *[other] секунды
    }.
    { "" }
    [cyan]Вам спадабалася гэтая карта?
map-vote-like = [green]👍 Падабаецца
map-vote-dislike = [red]👎 Не падабаецца
map-vote-like-selected = [gray]Вам спадабалася
map-vote-dislike-selected = [gray]Вам не спадабалася
map-rtv = [orange]Галасаванне
map-artv = [red]Імгненная змена
map-maps = Карты
map-maps-back = Да спісу карт
current-map = Бягучая карта
next-map = Наступная карта

# Map UI Modernized
map-ui-search-hint = Пошук карты ці аўтара...
map-ui-total = [lightgray]Усяго карт: [white]{ $count }[]
map-ui-about-title = Пра карту
map-ui-rtv-title = Змена карты
map-ui-no-maps-found = [lightgray]Карты па вашым запыце не знойдзены.[]
map-ui-by = ад [lightgray]{ $author }[]
map-ui-mode = Рэжым: [white]{ $mode }[]
map-ui-loading = [gray]Загрузка...[]
map-ui-no-preview = [gray]Няма прэв'ю[]
map-ui-dimensions = [gray]Памеры: [white]{ $width } x { $height }[]
map-ui-total-plays = [gray]Усяго згуляна: [white]{ $played } [lightgray]({ $playedYear } за год)[]
map-ui-last-played = [gray]Апошняя гульня: [white]{ $lastPlayed }[]
map-ui-description = [gray]Апісанне: [lightgray]{ $description }[]
map-ui-no-description = [gray]Апісанне: [lightgray]Апісанне адсутнічае.[]

map-ui-col-duration = Працягласць
map-ui-duration-min = [gray]Мін: [white]{ $value }[]
map-ui-duration-avg = [gray]Сяр: [white]{ $value }[]
map-ui-duration-max = [gray]Макс: [white]{ $value }[]

map-ui-col-popularity = Папулярнасць
map-ui-popularity-score = [gray]Рэйтынг: [white]{ $value }[]
map-ui-popularity-pop = [gray]Папулярнасць: [white]{ $value }[]
map-ui-popularity-interest = [gray]Цікавасць: [white]{ $value }[]

map-ui-col-community = Супольнасць
map-ui-community-approval = [gray]Ухваленне: [green]{ $rate }%[]

map-ui-btn-like = Лайк ({ $count })
map-ui-btn-dislike = Дызлайк ({ $count })

map-ui-rtv-active-status = [accent]● Ідзе галасаванне: [white]{ $votes }/{ $required }[] [gray](засталося [white]{ $seconds } с[gray])[]
map-ui-rtv-vote-yes = Галасаваць за карту
map-ui-rtv-start = Галасаваць за гэтую карту
map-ui-admin-rtv = Змяніць карту адразу
map-ui-admin-rtv-confirm = Націсніце яшчэ раз

gamemode-survival = Выжыванне
gamemode-attack = Атака
gamemode-pvp = PvP
gamemode-sandbox = Пясочніца
gamemode-editor = Рэдактар
rtv-vote =
    { $nickname }[lightgray] прагаласаваў за змену карты на [orange]{ $mapName }[lightgray]. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Увядзіце [orange]y[] або [orange]n[], каб прагаласаваць.
rtv-left = { $nickname }[lightgray] выйшаў. Яго голас за змену карты адменены. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
rtv-fail = [lightgray]Галасаванне не прайшло. Недастаткова галасоў, каб змяніць карту на [orange]{ $mapName }[].
rtv-success = [orange]Галасаванне прайшло. Карта [accent]{ $mapName }[] загрузіцца праз [accent]{ $mapLoadDelay }[] { $mapLoadDelay ->
    [one] секунду
    [few] секунды
    [many] секунд
   *[other] секунды
}…
rtv-cancelled = [lightgray]Галасаванне за змену карты на [orange]{ $mapName }[lightgray] адмяніў { $admin }.
vnw-vote =
    { $nickname }[lightgray] прагаласаваў за датэрміновы пачатак хвалі [orange]{ $wave }[lightgray]. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Увядзіце [orange]y[] або [orange]n[], каб прагаласаваць.
vnw-left = { $nickname }[lightgray] выйшаў. Яго голас за датэрміновы пачатак хвалі [orange]{ $wave }[lightgray] адменены. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
vnw-fail = [lightgray]Галасаванне не прайшло. Недастаткова галасоў, каб датэрмінова пачаць хвалю [orange]{ $wave }[].
vnw-success = [orange]Галасаванне прайшло. Хваля [accent]{ $wave }[] пачынаецца зараз.
vnw-cancelled = [lightgray]Галасаванне за датэрміновы пачатак хвалі [orange]{ $wave }[lightgray] адмяніў { $admin }.
vnw-obsolete = [lightgray]Хваля [orange]{ $wave }[lightgray] ужо пачалася, вынік галасавання больш не патрэбны.
# ==============================================================================
# Statistics & Ranks & Players
# ==============================================================================
commands-player-description = Паглядзець статыстыку гульца.
commands-settings-description = Адкрыць налады гульца.
player-menu-player = Гулец
player-menu-player-title = [orange]{ -xcore } — Статыстыка гульца
player-menu-player-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $customNickname }[] [gray]#{ $pid }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Профіль[]
    [gray]Імя: [white]{ $nickname } [darkgray]|[gray] Адмін: [lime]{ $admin }[]
    [gray]Бэйдж: [white]{ $activeBadge } [darkgray]|[gray] Сістэмны: [coral]{ $systemBadge }[]
    [gray]Далучыўся: [white]{ $accountCreated }[]
    { "" }
    [accent]■ Рэйтынгі[]
    [gray]Час у гульні: [white]{ $totalPlayTime }[]
    [gray]MiniPvP: [sky]{ $pvpRating } [darkgray]|[gray] Стары Hexed: [sky]{ $hexedRankName } [gray]({ $hexedPoints } ачк.) [darkgray]|[gray] Топ: [accent]{ $hexedTopRank }[]
    { "" }
    [accent]■ Матчы: [white]{ $gamesPlayed } [gray]гульняў [darkgray]|[lime] { $gamesWon } [gray]перамог [darkgray]|[sky] { $winRate }% [gray]перамог[]
    [gray]• [white]PvP: { $pvpSummary }[]
    [gray]• [white]Выжыв.: { $survivalSummary }[]
    [gray]• [white]Стары Hexed: { $hexedSummary }[]
    { "" }
    [accent]■ Баявая эфектыўнасць[]
    [gray]Блокі (Пабудавана/Разабрана/Знішчана): [lime]{ $blocksBuilt } [darkgray]/ [orange]{ $blocksDeconstructed } [darkgray]/ [scarlet]{ $blocksDestroyed }[]

# Modern Player Stats UI
player-stats-title = [orange]{ -xcore } — Профіль гульца
player-stats-tab-overview = Агляд
player-stats-tab-stats = Статыстыка
player-stats-tab-matches = Матчы
player-stats-tab-blocks = Блокі
player-stats-tab-players = Анлайн ({ $count })

player-stats-status-online = [lime]● Анлайн[]
player-stats-status-offline = [gray]○ Не ў сетцы[]
player-stats-no-bio = [gray]Апісання пакуль няма.[]
player-stats-loading = [lightgray]Загрузка даных...[]
player-stats-no-stats = [gray]Даных пра матчы пакуль няма.[]
player-stats-filter-all = Фільтр: Усе
player-stats-filter-admins = Фільтр: Толькі адміны
player-stats-filter-non-admins = Фільтр: Без адмінаў
player-stats-refresh = Абнавіць
player-stats-combat-efficiency = Эфектыўнасць бою і будаўніцтва
player-stats-waves-summary = [gray]хвалі: макс. [lime]{ $best }[], сяр. [white]{ $avg }[]
player-stats-hexed-top-placement = [gray]лепшае [accent]#{ $best }[], топ-3: [sky]{ $top3 }[]
player-stats-max-rank = [gold]★ ДАСЯГНУТЫ МАКСІМАЛЬНЫ РАНГ ★[]
player-stats-max-league = [gold]★ ДАСЯГНУТА МАКСІМАЛЬНАЯ ЛІГА ★[]
player-stats-hexed-wins-left = [lightgray]Перамог да [white]{ $rank }[]: { $wins }
player-stats-league-elo-left = [lightgray]ELO да [white]{ $league }[]: { $elo }
player-stats-ratio-legend = [lightgray]Суадносіны блокаў[]

player-stats-account-created = [gray]Далучыўся:[]
player-stats-play-time = [gray]Час у гульні:[]
player-stats-legacy-pvp-rating = [gray]Стары PvP:[]
player-stats-hexed-rank = [gray]Стары Hexed:[]
player-stats-hexed-leaderboard = [gray]Топ Hexed:[]

player-stats-total-games = [gray]Усяго гульняў:[]
player-stats-victories = [gray]Перамогі:[]
player-stats-survival-summary = [gray]Выжыванне:[]
player-stats-hexed-summary = [gray]Стары Hexed:[]

player-stats-blocks-built = [gray]Пабудавана блокаў:[]
player-stats-blocks-deconstructed = [gray]Разабрана:[]
player-stats-blocks-destroyed = [gray]Знішчана:[]
player-stats-units-produced = [gray]Выраблена юнітаў:[]
player-stats-units-lost = [gray]Страчана юнітаў:[]

player-stats-games-played-value = [gray]Гульняў:[] [white]{ $count }[]
player-stats-victories-value = [gray]Перамог:[] [lime]{ $wins }[]  [darkgray]|[]  [sky]{ $winRate }%[] [gray]перамог[]
player-stats-hexed-points = [gray]({ $points } ачк.)[]

player-stats-btn-settings = Налады
player-stats-btn-matches = Матчы
player-stats-btn-audit = Гісторыя
player-stats-btn-players = Анлайн
player-stats-btn-close = Закрыць
player-stats-admin-tag = [coral]<Адмін>[]
player-menu-players = Гульцы анлайн
player-menu-players-title = [orange]{ -xcore } — Гульцы анлайн
player-menu-players-content = [white]Старонка [green]{ $page }[] з [green]{ $total }[]
player-menu-players-empty = Няма гульцоў анлайн
player-menu-players-row = [white]{ $nickname } [gray](PID: { $pid })[]
player-menu-settings = Налады
player-menu-settings-title = [orange]{ -xcore } — Налады гульца
player-menu-settings-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $displayNickname }[] [gray]#{ $pid }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Профіль[]
    [gray]Імя: [white]{ $nickname } [darkgray]|[gray] Адлюстроўваецца: [lime]{ $customNickname }[]
    [gray]Бэйдж: [white]{ $activeBadge } [darkgray]|[gray] Сістэмны: [coral]{ $systemBadge }[]
    { "" }
    [accent]■ Бачнасць[]
    [gray]Рэйтынг: [white]{ $leaderboard }[]
    { "" }
    [accent]■ Чат[]
    [gray]Глабальны: [white]{ $globalChat } [darkgray]|[gray] Discord: [white]{ $discordRelay }[]
    [gray]Перакладчык: [white]{ $translatorLanguage }[]
    { "" }
    [accent]■ Мова[]
    [gray]Мова: [white]{ $language }[]
player-menu-settings-chat = Налады чата
player-menu-settings-chat-title = [orange]{ -xcore } — Налады чата

# Modern Player Settings Form
player-settings-tab-profile = Профіль
player-settings-tab-chat = Чат
player-settings-tab-badges = Бэйджы
player-settings-chat-preview = Прагляд чата
player-settings-chat-preview-sample = Прыклад
player-settings-chat-preview-message = Прывітанне, свет!
player-settings-symbol-color-mode = Колер бэйджа
player-settings-badges-my = Мае бэйджы
player-settings-badges-all = Усе бэйджы
player-settings-badge-equip = Надзець
player-settings-badge-unequip = Зняць
player-settings-badge-preview = Прагляд
player-settings-badge-previewing = У праглядзе
player-settings-translator-lang = Перакладчык чата
player-settings-translator-off = Выключаны
player-settings-badges-empty = Вы яшчэ не адкрылі ніводнага бэйджа.
player-settings-manage-badges = Кіраваць бэйджамі
player-settings-global-chat = Глабальны чат
player-settings-discord-relay = Перадача з Discord
player-settings-leaderboard = Паказваць рэйтынг
player-settings-language = Мова
player-settings-saved = [lime]Налады захаваны![]
player-settings-reset-feedback = [lightgray]Уласнае імя скінута.[]
player-settings-edit-badges = [accent]Змяніць[]
player-settings-tab-language = Мова
player-settings-identity = Імя і апісанне
player-settings-interface = Экран
player-settings-leaderboard-hint = Спіс лепшых гульцоў над гульнёй. Працуе ў Mini-PvP.
player-settings-global-chat-hint = Паведамленні гульцоў з іншых сервераў XCore.
player-settings-discord-relay-hint = Паведамленні з Discord-канала сервера ў гульнявым чаце.
player-settings-translator-hint = Паведамленні іншых гульцоў перакладаюцца на выбраную мову.
player-settings-language-hint = Мова меню і паведамленняў сервера. Auto ідзе за мовай вашай гульні.
player-menu-settings-chat-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [accent]■ Бачнасць чата[]
    [gray]Глабальны чат: [white]{ $globalChat }[]
    [gray]Перадача з Discord: [white]{ $discordRelay }[]
    { "" }
    [accent]■ Пераклад[]
    [gray]Мова перакладчыка: [white]{ $translatorLanguage }[]
player-menu-settings-translator-title = [orange]{ -xcore } — Выбар мовы перакладчыка
player-menu-settings-language-title = [orange]{ -xcore } — Выбар мовы
player-menu-settings-customNickname = Змяніць імя
player-menu-settings-customNickname-title = [orange]{ -xcore } — Змяніць імя
player-menu-settings-customNickname-message = [lightgray]Пакіньце пустым, каб скінуць
player-menu-settings-customNickname-reset = [scarlet]Скінуць імя
player-menu-settings-description = Змяніць апісанне
player-menu-settings-description-title = [orange]{ -xcore } — Змяніць апісанне
player-menu-settings-badges = Бэйджы
player-menu-settings-global-chat-on = [green]Глабальны чат
player-menu-settings-global-chat-off = [red]Глабальны чат
player-menu-settings-discord-relay-on = [green]Перадача з Discord
player-menu-settings-discord-relay-off = [red]Перадача з Discord
audit-menu-open = Гісторыя
audit-menu-actions-open = Дзеянні
audit-menu-history-title = [orange]{ -xcore } — Гісторыя
audit-menu-history-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $player }[] [gray]#{ $pid }[]
    [gray]Паказана запісаў: [accent]{ $entriesShown }[]
    [gray]{ $pageState } [darkgray]|[] { $nextState }
    [lightgray]{ $hint }[]
audit-menu-history-page-first = Найноўшыя запісы
audit-menu-history-page-older = Старэйшыя запісы
audit-menu-tab-sanctions = Атрыманыя пакаранні
audit-menu-tab-actions = Выкананыя дзеянні
audit-menu-filter-all = Усе
audit-menu-filter-bans = Блакіроўкі
audit-menu-filter-mutes = Муты
audit-menu-filter-warns = Папярэджанні
audit-menu-filter-other = Іншае
audit-menu-btn-back = Назад да гісторыі
audit-menu-btn-copy-id = Скапіяваць ID
audit-menu-copy-id-success = ID запісу адпраўлены ў чат
audit-menu-field-id = ID запісу
audit-menu-page = Старонка { $page }
audit-menu-details-unavailable = Запіс недаступны.
audit-menu-field-target = Гулец
audit-menu-field-actor = Выканаў
audit-menu-field-server = Сервер
audit-menu-field-reason = Прычына
audit-menu-section-time = Час
audit-menu-field-occurred = Калі
audit-menu-field-duration = Працягласць
audit-menu-field-expires = Сканчаецца
audit-menu-btn-revoke = Адмяніць пакаранне
audit-menu-revoke-success = Пакаранне адменена.
audit-menu-status-active = АКТЫЎНАЕ
audit-menu-status-expired = СКОНЧЫЛАСЯ
audit-menu-status-permanent = НАЗАЎСЁДЫ
audit-menu-history-more = Ёсць яшчэ запісы
audit-menu-history-end = Канец гісторыі
audit-menu-history-empty = Для гэтага гульца пакуль няма запісаў.
audit-menu-history-hint = Выберыце запіс ніжэй, каб убачыць падрабязнасці.
audit-menu-actions-title = [orange]{ -xcore } — Дзеянні
audit-menu-actions-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $player }[] [gray]#{ $pid }[]
    [gray]Паказана запісаў: [accent]{ $entriesShown }[]
    [gray]{ $pageState } [darkgray]|[] { $nextState }
    [lightgray]{ $hint }[]
audit-menu-actions-empty = У гэтага гульца пакуль няма дзеянняў.
audit-menu-actions-hint = Выберыце запіс ніжэй, каб убачыць, што зрабіў гэты гулец.
audit-menu-summary-row = [accent]{ $action }[] [darkgray]•[] [white]{ $actor }[] [gray]— { $reason }
audit-menu-action-summary-row = [accent]{ $action }[] [darkgray]•[] [white]{ $target }[] [gray]— { $reason }
audit-menu-details-title = [orange]{ -xcore } — Падрабязнасці запісу
audit-menu-details-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $player }[] [gray]#{ $pid }[]
    { "" }
    [accent]■ Падзея[]
    [gray]Дзеянне: [white]{ $action }[]
    [gray]Выканаў: [white]{ $actor }[]
    [gray]Прычына: [white]{ $reason }[]
    { "" }
    [accent]■ Час[]
    [gray]Калі: [white]{ $occurredAt }[]
    [gray]Працягласць: [white]{ $duration }[]
    [gray]Сканчаецца: [white]{ $expiresAt }[]
    { "" }
    [accent]■ Метаданыя[]
    [gray]ID запісу: [white]{ $auditId }[]
audit-menu-unknown-actor = Невядома
audit-menu-unknown-target = Невядома
audit-menu-reason-unspecified = Не ўказана
audit-menu-duration-permanent = Назаўсёды
audit-menu-action-ban = Блакіроўка
audit-menu-action-unban = Разблакіроўка
audit-menu-action-mute = Мут
audit-menu-action-unmute = Зняцце мута
audit-menu-action-warn = Папярэджанне
audit-menu-action-kick = Выключэнне
audit-menu-action-note = Нататка
audit-menu-action-quarantine = Каранцін
audit-menu-action-unquarantine = Зняцце каранціну
player-menu-player-max-rank = Дасягнуты максімальны ранг
player-menu-player-hexed-progress = [gray]Патрэбна перамог для [white]{ $nextRankName }[gray]: [accent]{ $requiredPoints }[]
player-menu-player-no-mode-stats = [gray]няма даных[]
player-menu-player-pvp-summary = [gray]гульні [white]{ $gamesPlayed }[], перамогі [lime]{ $gamesWon }[], [sky]{ $winRate }%[]
player-menu-player-survival-summary = [gray]хвалі: макс. [lime]{ $bestWave }[], сяр. [white]{ $averageWave }[] [gray](гульняў: { $gamesPlayed })[]
player-menu-player-hexed-summary = [gray]матчы [white]{ $gamesPlayed }[], 1-е месца [lime]{ $gamesWon }[], лепшае месца [accent]#{ $bestPlacement }[]
player-menu-time-days = { $value } д
player-menu-time-hours = { $value } г
player-menu-time-minutes = { $value } хв
settings-language-label = Мова: [green]{ $lang }[]
settings-translator-label = Перакладчык: [green]{ $lang }[]
badge-menu-title = [orange]{ -xcore } — Бэйджы
badge-menu-content =
    [white]Сістэмны бэйдж: [green]{ $systemBadge }[]
    [white]Актыўны бэйдж: [green]{ $activeBadge }[]
    [white]Колер сімвала: [green]{ $symbolColorMode }[]
badge-menu-empty = [lightgray]У вас пакуль няма адкрытых бэйджаў.
badge-menu-row = [white]{ $badge }[] [gray]-[] { $description }
badge-menu-symbol-color-button = Колер сімвала: [green]{ $mode }[]
badge-menu-symbol-color-title = [orange]{ -xcore } — Колер сімвала бэйджа
badge-menu-symbol-color-content =
    [white]Бягучы рэжым: [green]{ $mode }[]
    [lightgray]Выберыце, як павінен афарбоўвацца сімвал бэйджа.
badge-menu-symbol-color-default = Стандартны колер бэйджа
badge-menu-symbol-color-player-color = Сімвал у колеры гульца
badge-menu-symbol-color-player-color-full = Увесь бэйдж у колеры гульца
badge-menu-view-all = Усе бэйджы
badge-menu-all-title = [orange]{ -xcore } — Усе бэйджы
badge-menu-all-content = [lightgray]Прагляд усіх бэйджаў, іх статусу і апісання.
badge-menu-all-row = [white]{ $badge }[] [gray]-[] [accent]{ $state }[] [gray]-[] { $description }
badge-clear-button = Зняць актыўны бэйдж
badge-state-system = Сістэмны
badge-state-system-active = Сістэмны актыўны
badge-state-active = Актыўны
badge-state-unlocked = Адкрыты
badge-state-locked = Закрыты
badge-set-success = [accent]Актыўны бэйдж усталяваны: [green]{ $badge }[].
badge-clear-success = [accent]Актыўны бэйдж зняты.
badge-grant-success = [accent]Бэйдж [green]{ $badge }[] выдадзены гульцу [green]{ $nickname }[][gray]#{ $pid }[].
badge-revoke-success = [accent]Бэйдж [green]{ $badge }[] зняты ў гульца [green]{ $nickname }[][gray]#{ $pid }[].
badge-already-unlocked = [scarlet]⚠ Бэйдж [accent]{ $badge }[scarlet] ужо адкрыты.
badge-not-owned = [scarlet]⚠ У гульца няма бэйджа [accent]{ $badge }[scarlet].
error-badge-not-found = [scarlet]⚠ Бэйдж [accent]{ $badge }[scarlet] не знойдзены.
error-badge-not-unlocked = [scarlet]⚠ Бэйдж [accent]{ $badge }[scarlet] не адкрыты.
error-badge-not-selectable = [scarlet]⚠ Бэйдж [accent]{ $badge }[scarlet] нельга выбраць уручную.
badge-admin-name = Адмін
badge-admin-description = Аўтаматычны бэйдж для адміністратараў.
badge-developer-name = Распрацоўшчык
badge-developer-description = Выдаецца распрацоўшчыкам XCore.
badge-translator-name = Перакладчык
badge-translator-description = Выдаецца тым, хто дапамагае з перакладам XCore.
badge-map-maker-name = Мапмэйкер
badge-map-maker-description = Выдаецца аўтарам мап, што выкарыстоўваюцца на серверы.
badge-contributor-name = Кантрыб'ютар
badge-contributor-description = Выдаецца за ўклад у XCore або супольнасць.
badge-bug-finder-name = Паляўнічы на багі
badge-bug-finder-description = Выдаецца за рэгулярныя якасныя баг-рэпарты.
badge-event-winner-name = Пераможца івэнту
badge-event-winner-description = Выдаецца пераможцам спецыяльных падзей сервера.
badge-veteran-name = Ветэран
badge-veteran-description = Выдаецца паважаным даўнім гульцам.
badge-season-champion-name = Чэмпіён сезона
badge-season-champion-description = Выдаецца за месца на п'едэстале рэйтынгавага сезона.
commands-lb-description = Уключыць/выключыць рэйтынг.
commands-lb-success =
    { $leaderboardEnabled ->
        [true] [accent]Рэйтынг [green]уключаны.
       *[other] [accent]Рэйтынг [scarlet]выключаны.
    }
leaderboard = [blue]Рэйтынг
commands-observer-description = Перайсці ў рэжым назіральніка. Ваш бягучы юніт знікне, і вы перойдзеце ў каманду гледачоў.
commands-rank-description = Паказаць ваш ранг або ранг іншага гульца.
commands-rank-content =
    { $nickname }
    { $rankTag } [accent]{ $rankName }
    [gold]Перамогі: { $points }/{ $requiredPoints }
commands-ranks-description = Паказвае інфармацыю пра рангі.
commands-ranks-content =
    { $rankTag } [accent]{ $rankName }
    [gold]Патрабаванне: [grey]{ $requiredPoints } [accent]перамог[]
commands-ranks-footer = Перамогі залічваюцца, толькі калі вы перамагаеце гульца свайго рангу або вышэй.
commands-top-description = Лепшыя гульцы.
commands-season-description = Рэйтынгавы сезон: колькі засталося, ваша месца і пераможцы мінулага сезона.
commands-matches-description = Вашы рэйтынгавыя матчы: вынікі, змены рэйтынгу і з кім вы гулялі.
commands-pvp-description = Гайд па MiniPvP: раўнд, правілы сервера і як лічыцца рэйтынг.
commands-top-hexed-content = [orange]{ $index }. { $nickname }[accent]: [blue]{ $rankName } [cyan]{ $points } []перамог
commands-top-pvp-content = [orange]{ $index }. { $nickname }[accent]: [cyan]{ $rating }
top-menu-title = [orange]{ -xcore } — Лепшыя гульцы: [accent]{ $category }
top-menu-content =
    [lightgray]Выберыце гульца, каб адкрыць яго профіль.[]
    [lightgray]Старонка [green]{ $page }[]/[green]{ $totalPages }[] [gold]•[] [lightgray]Гульцоў: [green]{ $totalEntries }[]
    { $selfRankLine }
top-menu-empty =
    [accent]Катэгорыя: [green]{ $category }[]
    [gray]Гульцоў пакуль няма.
top-menu-categories-title = [orange]{ -xcore } — Катэгорыя рэйтынгу
top-menu-categories-content =
    [lightgray]Выберыце, які рэйтынг паказваць.[]
    [lightgray]Бягучы: [green]{ $category }[]
top-menu-category-button = [accent]Катэгорыя: [green]{ $category }[]
top-menu-category-mini-pvp = MiniPvP
top-menu-category-playtime = Час у гульні
top-menu-category-hexed = Hexed
top-menu-self-rank-known = [lightgray]Ваша месца: [accent]#{ $rank }[]
top-menu-self-rank-unknown = [lightgray]Ваша месца: [gray]не знойдзена[]
top-menu-entry-mini-pvp = { $rankLabel } { $leagueIcon } [accent]{ $nickname }[] [gray]—[] [sky]{ $value }[]
top-menu-entry-playtime = { $rankLabel } [accent]{ $nickname }[] [gray]—[] [green]{ $value }[]
top-menu-entry-hexed = { $rankLabel } [accent]{ $nickname }[] [gray]—[] [violet]{ $rankName }[] [gold]•[] [cyan]{ $value }[]
top-menu-total-count = Гульцоў: { $count }
top-menu-btn-find-self = Знайсці сябе
top-menu-on-this-page = на гэтай старонцы
top-menu-unranked = У гэтай катэгорыі ў вас пакуль няма месца
top-menu-self-rank-line = Ваша месца: { $rank }
top-menu-score-points = { $points } ачк.
top-menu-score-minutes = { $time }
# ==============================================================================
# Game Modes (Hexed, PvP, Surrender, AI)
# ==============================================================================
commands-surrender-description = Здацца ў Hexed. Ваша бягучая каманда будзе знішчана, юніт знікне, і вы перойдзеце ў каманду гледачоў.
commands-surrender-success = [green]Вы здаліся і цяпер назіраеце
commands-observer-success = [green]Цяпер вы назіраеце
commands-observer-exit-success = [green]Вы больш не назіраеце
commands-ai-description = Кіраваць ШІ.
commands-ai-usage = [red]attack(a) []або [accent]idle(i)
hexed-popup = [blue]{ DURATION($remaining, style: "timer") }[] да канца гульні.
hexed-eliminated = { $nickname } [gold][scarlet]выбыў[] з гульні!
hexed-leaderboard-content = [orange]{ $index }. { $nickname }[accent]: [cyan]{ $hexes } [accent]гексаў
hexed-ranks-newbie = Навічок
hexed-ranks-regular = Звычайны
hexed-ranks-advanced = Дасведчаны
hexed-ranks-veteran = Ветэран
hexed-ranks-davastator = Разбуральнік
hexed-ranks-the_legend = Легенда
hexed-game-over-header = Гульня скончана. Пераможцы:
hexed-game-over-winner-row =
    [orange]{ $index }. { $name }[][accent]: [cyan]{ $cores } { $cores ->
        [one] гекс
        [few] гексы
        [many] гексаў
       *[other] гекса
    }
hexed-game-over-no-winners = Гульня скончана. На жаль, не ўдалося знайсці пераможцаў.
hexed-game-over-restart = Новая гульня праз 10 секунд…
rating_league_scrap = Лом
rating_league_copper = Медзь
rating_league_lead = Свінец
rating_league_graphite = Графіт
rating_league_silicon = Крэмній
rating_league_titanium = Тытан
rating_league_thorium = Торый
rating_league_plastanium = Пластан
rating_league_phase_fabric = Фазавая тканіна
rating_league_surge_alloy = Кінетычны сплаў
pvp-team-won = Ваша каманда перамагла. Ваш рэйтынг вырас на { $increased }
pvp-team-lose = Ваша каманда прайграла. Ваш рэйтынг знізіўся на { $reduced }
pvp-match-settlement-win = [accent]■ Вынік матчу: [green]Перамога![] Ваш рэйтынг: [stat]{ $oldRating }[] -> [stat]{ $newRating }[] [green](+{ $delta })[] { $leagueIcon } { $leagueName }
pvp-match-settlement-loss = [accent]■ Вынік матчу: [scarlet]Паражэнне![] Ваш рэйтынг: [stat]{ $oldRating }[] -> [stat]{ $newRating }[] [scarlet]({ $delta })[] { $leagueIcon } { $leagueName }
pvp-match-settlement-draw = [accent]■ Вынік матчу: [yellow]Нічыя![] Ваш рэйтынг: [stat]{ $oldRating }[] -> [stat]{ $newRating }[] [yellow]({ $delta })[] { $leagueIcon } { $leagueName }
pvp-match-settlement-exempt = [accent]■ Вынік матчу: [lightgray]Рэйтынг не змяніўся (замала актыўнай гульні).[]
pvp-match-settlement-details = [lightgray]Падрабязней пра матч: [white]/matches[][]
season-ending-soon = [accent]■ [white]{ $ladder }[]: сезон { $number } сканчаецца праз [stat]{ $remaining }[]. Выкарыстайце апошнія матчы!
season-started = [accent]■ [white]{ $ladder }[]: сезон { $previous } скончыўся. Пачаўся сезон { $number }!
season-reset-soft = [lightgray]Рэйтынгі наблізілі да пачатковага значэння, лічыльнікі матчаў пачынаюцца з нуля.[]
season-reset-hard = [lightgray]Усе пачынаюць зноў з пачатковага рэйтынгу.[]
season-reset-none = [lightgray]Рэйтынгі захоўваюцца, лічыльнікі матчаў пачынаюцца з нуля.[]
season-title = Сезон { $number }
top-menu-scope-current = { $season } [lightgray]· сканчаецца праз { $remaining }[]
top-menu-scope-past = { $season } [gray]· { $from } – { $to }[]
ladder-profile-standing = { $leagueIcon } [white]{ $league }[] [accent]{ $rating } ELO[]
ladder-profile-headline = [accent]{ $icon }[] [lightgray]{ $ladder }:[] { $standing }
ladder-profile-unplaced = [gray]у гэтым сезоне няма рэйтынгавых матчаў[]
ladder-profile-matches = [lightgray]Матчы:[] [white]{ $matches }[]
ladder-profile-wins = [lightgray]Перамогі:[] [white]{ $wins }[] [gray]({ $rate }%)[]
ladder-profile-rank = [lightgray]Месца:[] [accent]#{ $rank }[]
ladder-profile-peak = [lightgray]Пік:[] [white]{ $rating }[]
ladder-profile-season = [lightgray]{ $season } · сканчаецца праз [white]{ $remaining }[][]
ladder-profile-season-closing = [lightgray]{ $season } · вынікі падлічваюцца[]
ladder-profile-history = [gray]{ $season } — { $rank }, { $league }, { $rating } ELO[]
season-menu-title = Рэйтынгавыя сезоны
season-menu-card-title = { $ladder } — { $season }
season-menu-ends = [lightgray]Сканчаецца праз [white]{ $remaining }[] ({ $date })[]
season-menu-closing = [lightgray]Сезон скончыўся, вынікі падлічваюцца.[]
season-menu-participants = [lightgray]Гульцоў у гэтым сезоне: [white]{ $count }[][]
season-menu-you = [lightgray]Вы:[] { $standing }
season-menu-previous = [lightgray]{ $season } — пераможцы:[]
season-menu-podium-entry = [gold]{ $place }.[] [white]{ $name }[] [gray]—[] { $league } [accent]{ $rating }[]
season-menu-open-top = Рэйтынг
season-menu-prizes = [lightgray]Прызы гэтага сезона:[]
season-menu-prize-entry = [gold]{ $places }.[] [white]{ $prize }[]
season-menu-podium-prizes = [gray] · [gold]{ $prizes }[]
prize-grant-line = [lightgray]{ $season } · прыз:[] [gold]{ $prize }[] [gray]({ $status })[]
prize-status-pending = чакае ўручэння
prize-status-granted = атрыманы
prize-status-delivered = дастаўлены
prize-status-failed = адмін з гэтым разбярэцца
season-menu-empty = Рэйтынгавых сезонаў пакуль няма.
pvp-hud-status = [accent]MiniPvP[] | [stat]Жывыя:[] { $teams } | [gray]{ $time }[]
pvp-leaderboard-content = [orange]{ $index }. { $nickname }[accent]:[cyan] { $rating } [accent]рэйтынгу
pvp-you-spectator = [scarlet]Вы выбылі. Пачакайце наступнай гульні.

# MiniPvP guide (/pvp info)
pvp-guide-title = Як тут гуляць
pvp-guide-subtitle = Звычайны PvP Mindustry: каманды будуюць базы і знішчаюць ядры адна адной. Тут сабрана, чым гэты сервер адрозніваецца і як лічыцца рэйтынг.
pvp-guide-hint = Адкрыць зноў: [accent]/pvp info[]
pvp-guide-join-hint = [lightgray]Правілы MiniPvP і рэйтынг:[] [accent]/pvp info[]
pvp-guide-menu-button = Як гуляць у MiniPvP
pvp-guide-tab-round = Раўнд
pvp-guide-tab-rules = Правілы
pvp-guide-tab-rating = Рэйтынг
pvp-guide-stat-minutes = хвілін ідзе раўнд
pvp-guide-stat-teams = каманд у гульні
pvp-guide-stat-players = гульцоў у камандах
pvp-guide-stat-zone = тайлаў вакол ядра
pvp-guide-stat-unitcap = ліміт юнітаў
pvp-guide-stat-banned = блокаў пад забаронай
pvp-guide-stat-start = рэйтынг на старце
pvp-guide-stat-top = лепшыя гульцы
pvp-guide-stat-matches = гісторыя матчаў

pvp-guide-now-title = Зараз
pvp-guide-now-map = Мапа: [accent]{ $map }[]
pvp-guide-now-players = { $count } { $count ->
        [one] гулец
        [few] гульцы
        [many] гульцоў
       *[other] гульца
    }
pvp-guide-win-title = Як перамагчы
pvp-guide-win-text = Знішч усе ядры супернікаў. Перамагае апошняя каманда, у якой засталіся ядры і гульцы. Каманда, у якой нікога не засталося, выбывае.
pvp-guide-out-title = Выбыў: глядзіш да канца
pvp-guide-out-text =
    Твая каманда страціла апошняе ядро: ты назіраеш да наступнага раўнда.
    Выйшаў сярод раўнда і вярнуўся: трапіш у тую ж каманду.
pvp-guide-commands-title = Каманды
pvp-guide-cmd-info = гэта акно
pvp-guide-cmd-top = табліца лідараў
pvp-guide-cmd-season = сезон і прызы
pvp-guide-cmd-matches = твае матчы і рэйтынг за іх
pvp-guide-cmd-rtv = галасаваць за змену мапы
pvp-guide-cmd-observer = глядзець збоку

pvp-guide-zone-title = Не будуй каля чужога ядра
pvp-guide-zone-text = Бліжэй за [accent]{ $tiles }[] тайлаў да варожага ядра будаваць нельга. Каб прасунуцца, спачатку знішч гэта ядро.
pvp-guide-zone-polygon-title = Зямля падзелена паміж ядрамі
pvp-guide-zone-polygon-text = Будаваць можна толькі там, дзе найбліжэйшае ядро тваё. Каб заняць чужую зямлю, знішч ядро, да якога яна належыць.
pvp-guide-unitcap-title = Ліміт юнітаў
pvp-guide-unitcap-text = Каманда пачынае з лімітам [accent]{ $cap }[], і кожнае ядро павялічвае яго.
pvp-guide-unitcap-fixed-title = Ліміт юнітаў
pvp-guide-unitcap-fixed-text = У каманды не больш за [accent]{ $cap }[] юнітаў, колькі б ядраў у яе ні было.
pvp-guide-multipliers-title = Іншы баланс
pvp-guide-multipliers-text = На гэтай мапе гэтыя лічбы адрозніваюцца ад звычайных:
pvp-guide-rule-unit-damage = Шкода юнітаў
pvp-guide-rule-unit-health = Трываласць юнітаў
pvp-guide-rule-unit-cost = Кошт юнітаў
pvp-guide-rule-unit-build-speed = Хуткасць вытворчасці юнітаў
pvp-guide-rule-block-damage = Шкода пабудоў
pvp-guide-rule-block-health = Трываласць пабудоў
pvp-guide-rule-build-cost = Кошт пабудоў
pvp-guide-rule-build-speed = Хуткасць будаўніцтва
pvp-guide-banned-blocks-title = Забароненыя пабудовы
pvp-guide-banned-units-title = Забароненыя юніты
pvp-guide-other-title = Яшчэ
pvp-guide-other-fog = Туман вайны: бачна толькі тое, што бачаць твае пабудовы і юніты.
pvp-guide-other-capture = Знішчанае ядро пераходзіць да таго, хто яго знішчыў.
pvp-guide-other-reactors = Рэактары не выбухаюць.
pvp-guide-vanilla-title = Звычайныя правілы
pvp-guide-vanilla-text = На гэтай мапе правілы як у звычайным PvP Mindustry.

pvp-guide-elo-title = Рэйтынг ELO
pvp-guide-elo-text = Усе пачынаюць з [accent]{ $start }[]. Перамога падымае рэйтынг, параза апускае. Наколькі, залежыць ад рэйтынгу тваёй каманды і супернікаў: перамога над мацнейшымі дае больш.
pvp-guide-leave-title = Выхад не ратуе ад паразы
pvp-guide-leave-text = Калі твая каманда прайграла, ты губляеш рэйтынг цалкам, нават калі выйшаў або пайшоў у назіральнікі раней. Без страт, толькі калі ты прабыў у гульні менш за [accent]{ $seconds }[] с.
pvp-guide-unrated-title = Калі рэйтынг не мяняецца
pvp-guide-unrated-text =
    Раўнд карацейшы за [accent]{ $seconds }[] с або з менш чым [accent]{ $players }[] гульцамі з рэйтынгам не залічваецца.
    Пераможцу трэба адгуляць хаця б [accent]{ $winner }%[] раўнда, інакш рэйтынг не расце.
    Зайшоў позна ў каманду, якая прайграла, і адгуляў менш за [accent]{ $late }%[] раўнда: рэйтынг не падае.
pvp-guide-seasons-title = Лігі і сезоны
pvp-guide-seasons-text = Рэйтынг вызначае лігу. Рэйтынг ідзе па сезонах: у канцы сезона лепшыя атрымліваюць прызы. Вынікі кожнага матчу: [accent]/matches[].
# ==============================================================================
# Events & Notifications
# ==============================================================================
player-joined-pid = { $nickname } [grey]#[white]{ $pid }[grey] [accent]далучыўся.
player-joined-username = { $nickname } [grey]@[white]{ $username }[grey] [accent]далучыўся.
player-joined-both = { $nickname } [accent]@{ $username } [grey]#[white]{ $pid }[grey] [accent]далучыўся.
player-joined-none = { $nickname } [accent]далучыўся.

player-left-pid = { $nickname } [grey]#[white]{ $pid }[grey] [accent]выйшаў.
player-left-username = { $nickname } [grey]@[white]{ $username }[grey] [accent]выйшаў.
player-left-both = { $nickname } [accent]@{ $username } [grey]#[white]{ $pid }[grey] [accent]выйшаў.
player-left-none = { $nickname } [accent]выйшаў.

player-settings-identity-mode = [accent]Ідэнтыфікатар у аб'явах:[]
player-settings-identity-mode-pid = Толькі ID (#12)
player-settings-identity-mode-username = Толькі імя карыстальніка (@Steve)
player-settings-identity-mode-both = Абодва (@Steve #12)
player-settings-identity-mode-none = Схаваны

notification-votekick-playtime =
    [accent]Віншуем! Вы адгулялі [lightgray]{ $votekickPlayTime }[] { $votekickPlayTime ->
        [one] хвіліну
        [few] хвіліны
        [many] хвілін
       *[other] хвіліны
    } і цяпер можаце пачынаць галасаванні за выключэнне.
notification-global-chat-playtime =
    [accent]Віншуем! Вы адгулялі [lightgray]{ $globalChatPlayTime }[] { $globalChatPlayTime ->
        [one] хвіліну
        [few] хвіліны
        [many] хвілін
       *[other] хвіліны
    } і цяпер можаце пісаць у глабальны чат.
    [lightgray]Увядзіце [accent]/g [gray]<паведамленне…>[lightgray], каб адправіць паведамленне.
notification-admin-kick = { $admin }[accent] выключыў { $target }[].
notification-admin-wave-skip = { $admin }[accent] прапусціў хвалю.
server-restart-countdown =
    Перазапуск праз { $seconds ->
        [one] { $seconds } секунду
        [few] { $seconds } секунды
        [many] { $seconds } секунд
       *[other] { $seconds } секунды
    }
like-map-success = [green]Вам спадабалася гэтая карта!
like-map-changed = [green]Вы перадумалі: цяпер падабаецца!
dislike-map-success = [orange]Вам не спадабалася гэтая карта.
dislike-map-changed = [orange]Вы перадумалі: цяпер не падабаецца.
like-event-success = [green]Вам спадабалася гэтая падзея!
like-event-changed = [green]Вы перадумалі: цяпер падабаецца!
dislike-event-success = [orange]Вам не спадабалася гэтая падзея.
dislike-event-changed = [orange]Вы перадумалі: цяпер не падабаецца.

# ==============================================================================
# Events (Server)
# ==============================================================================

commands-event-description = Меню кіравання падзеямі.
commands-events-description = Спіс усіх падзей на серверах.
event-events = Падзеі
event-menu-main = Галоўныя падзеі
event-menu-event = Падзея
event-menu-create-start-default = Падзея { $playerName }
event-menu-create-start-map = Стварыць падзею для гэтай карты
event-ui-title = Падзеі
event-ui-server-time = Час сервера: { $zone }
event-ui-tab-upcoming = Хутка
event-ui-tab-major = Буйныя
event-ui-tab-past = Мінулыя
event-ui-tab-mine = Мае
event-ui-now-running = Ідзе зараз
event-ui-now-vote = Галасаванне
event-ui-empty-upcoming = Падзей пакуль не запланавана. Стварыце першую!
event-ui-empty-major = Буйных падзей пакуль не запланавана.
event-ui-empty-past = Яшчэ ніводная падзея не завяршылася.
event-ui-empty-mine = Вы пакуль не стварылі ніводнай падзеі.
event-ui-create = Стварыць падзею
event-ui-load-failed = Не ўдалося загрузіць падзеі. Паспрабуйце яшчэ раз.
event-ui-state-active = Ідзе зараз
event-ui-state-active-until = Ідзе да { $time }
event-ui-state-finished = Завершана
event-ui-state-finished-at = Завершана { $date }
event-ui-state-unscheduled = Без даты
event-ui-major = Буйная
event-ui-major-hint = Пазначана зоркай і сабрана на асобнай укладцы
event-ui-temporary = Часовая
event-ui-temporary-hint = Скончыцца сама ў час канца
event-ui-about = Пра падзею
event-ui-no-description = Апісання няма.
event-ui-author = Аўтар: { $author }
event-ui-created = Створана: { $date }
event-ui-map-schedule = Карта і расклад
event-ui-map-none = Карта не ўказана
event-ui-map-pick = Выберыце карту
event-ui-planned-start = Пачатак: { $time }
event-ui-planned-end = Канец: { $time }
event-ui-started = Пачалася: { $time }
event-ui-ended = Завяршылася: { $time }
event-ui-not-set = не зададзена
event-ui-open-map = Адкрыць карту
event-ui-rating = Ацэнка
event-ui-launch = Запуск
event-ui-launch-hint = Падзею запускаюць самі гульцы — галасаваннем. Яна пачнецца з наступнай зменай карты.
event-ui-launch-again = Гэтая падзея ўжо праводзілася. Яе можна запусціць зноў.
event-ui-launch-active = Падзея ідзе проста зараз.
event-ui-launch-voting = Ідзе галасаванне: { $votes } з { $required }. Напішыце y або n у чат.
event-ui-launch-busy = Зараз ідзе іншае галасаванне. Дачакайцеся яго канца.
event-ui-vote = Пачаць галасаванне
event-ui-vote-busy = Галасаванне ўжо ідзе.
event-ui-vote-stopped = Галасаванне спынена.
event-ui-force = Запусціць адразу
event-ui-force-confirm = Дакладна запусціць?
event-ui-stop = Спыніць падзею
event-ui-stop-confirm = Дакладна спыніць?
event-ui-stop-vote = Спыніць галасаванне
event-ui-stopped = Падзея спынена.
event-ui-to-list = Да спіса
event-ui-edit = Змяніць
event-ui-editor-new = Новая падзея
event-ui-editor-edit = Змена падзеі
event-ui-texts = Назва і апісанне
event-ui-name = Назва
event-ui-description = Апісанне
event-ui-description-hint = Пра што гэтая падзея
event-ui-map = Карта
event-ui-schedule = Расклад і тып
event-ui-start = Пачатак
event-ui-end = Канец
event-ui-save = Захаваць
event-ui-saved = Падзея захавана.
event-ui-draft-restored = Ваша недапісаная падзея на месцы.
event-ui-maps-title = Выбар карты
event-ui-to-editor = Да рэдактара
event-ui-time-start = Пачатак падзеі
event-ui-time-end = Канец падзеі
event-ui-time-unset = Не зададзена
event-ui-time-unset-hint = Задайце стрэлкамі або кнопкай ніжэй
event-ui-time-hours = { $value } г
event-ui-time-minutes = { $value } хв
event-ui-time-now = Зараз
event-ui-time-in-hour = Праз гадзіну
event-ui-time-today-evening = Сёння 20:00
event-ui-time-tomorrow-noon = Заўтра 12:00
event-ui-time-plus-hour = +1 г
event-ui-time-plus-three-hours = +3 г
event-ui-time-plus-day = +1 дзень
event-ui-time-apply = Гатова
event-ui-time-clear = Скінуць
event-ui-error-no-map = Спачатку выберыце карту.
event-ui-error-no-end = Часовай падзеі патрэбен час канца.
event-ui-error-end-before-start = Канец павінен быць пазней за пачатак.
event-ui-error-not-allowed = Вам нельга змяняць гэтую падзею.
event-ui-error-gone = Гэтай падзеі больш няма.
event-ui-error-save-failed = Не ўдалося захаваць падзею. Паспрабуйце яшчэ раз.
vote-event-vote =
    { $nickname }[lightgray] прагаласаваў за змену падзеі на [orange]{ $name }[lightgray]. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Увядзіце [orange]y[] або [orange]n[], каб прагаласаваць.
vote-event-left = { $nickname }[lightgray] выйшаў. Яго голас за змену падзеі адменены. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
vote-event-fail = [lightgray]Галасаванне не прайшло. Недастаткова галасоў, каб змяніць падзею на [orange]{ $name }[].
vote-event-success = [orange]Галасаванне прайшло. Падзея [accent]{ $name }[] загрузіцца пры наступнай змене карты.
vote-event-cancelled = [lightgray]Галасаванне за змену падзеі на [orange]{ $name }[lightgray] адмяніў адміністратар { $admin }.
event-end = Падзея [green]{ $name }[] скончылася!
# ==============================================================================
# Errors
# ==============================================================================
error-access-denied = [scarlet]⚠ Доступ забаронены.
error-ip-changed = [scarlet]⚠ Ваш IP-адрас змяніўся. Правы адміна адабраныя.
error-not-enough-params = [scarlet]⚠ Недастаткова пазіцыйных параметраў.
error-player-not-found = [scarlet]Гулец не знойдзены.
error-player-not-teammate = [scarlet]⚠ Гэты гулец не ў вашай камандзе.
error-player-admin = [scarlet]⚠ Не спрабуйце выключыць адміна. ⚠
error-already-voted = [scarlet]⚠ Вы ўжо прагаласавалі. Супакойцеся.
error-playtime-requirement =
    [scarlet]⚠ Каб карыстацца гэтай функцыяй, трэба адгуляць не менш за { $time } { $time ->
        [one] хвіліну
        [few] хвіліны
        [many] хвілін
       *[other] хвіліны
    }.
error-globalchat-total-playtime =
    [scarlet]⚠ Каб пісаць у глабальны чат, трэба адгуляць { $globalChatPlayTime } { $globalChatPlayTime ->
        [one] хвіліну
        [few] хвіліны
        [many] хвілін
       *[other] хвіліны
    }.
error-votekick-total-playtime =
    [scarlet]⚠ Каб пачаць галасаванне за выключэнне, трэба адгуляць { $votekickPlayTime } { $votekickPlayTime ->
        [one] хвіліну
        [few] хвіліны
        [many] хвілін
       *[other] хвіліны
    }.
error-vote-yourself = [scarlet]⚠ Нельга галасаваць у сваім жа галасаванні.
error-vote-in-progress = [scarlet]⚠ Галасаванне ўжо ідзе.
error-no-voting = [scarlet]⚠ Зараз няма галасавання.
error-wave-vote-unavailable = [scarlet]⚠ Датэрміновы пачатак хвалі даступны толькі ў рэжымах з хвалямі.
error-no-map = [scarlet]⚠ Карта не зададзена.
error-map-not-event = [scarlet]⚠ Карта не ўваходзіць у бягучую падзею.
error-map-not-found = [scarlet]⚠ Карта не знойдзена! [accent]Выкарыстайце [cyan]/maps[], каб убачыць усе даступныя карты.
error-maps-empty = [scarlet]⚠ Спіс карт пусты.
error-event-not-found = [scarlet]⚠ Падзея не знойдзена! [accent]Выкарыстайце [cyan]/events[], каб убачыць даступныя падзеі.
error-page-between = [scarlet]⚠ 'page' павінна быць лікам ад[orange] 1[] да [orange]{ $totalPages }[].
error-page-number = [scarlet]'page' павінна быць лікам.
error-wrong-number = [scarlet]⚠ Няправільны фармат ліку.
error-wrong-period-format = [scarlet]⚠ Няправільны фармат перыяду. Прыклад: 1h 30m, 30 ({ hours })
error-invalid-id = [scarlet]⚠ Няправільны ID гульца.
error-spectator = [scarlet]⚠ Вы глядач і не можаце выкарыстоўваць гэтую каманду.
error-admin-password-too-short = [scarlet]⚠ пароль адміністратара павінен быць не карацейшы за 8 сімвалаў.
error-wrong-admin-password = [scarlet]⚠ Няправільны пароль адміна.
error-internal = [scarlet]Унутраная памылка.
error-processing-request = [scarlet]Адбылася памылка пры апрацоўцы запыту.
error-no-access = [scarlet]⚠ Няма доступу.
error-nickname-too-long = [scarlet]⚠ Імя занадта доўгае. Максімум { $max } бачных сімвалаў.
error-private-message-invalid-pid = [scarlet]⚠ Няправільны PID асабістага паведамлення. Фармат: [lightgray]#123[].
error-private-message-self = [scarlet]⚠ Нельга адправіць асабістае паведамленне самому сабе.
error-private-message-empty = [scarlet]⚠ Паведамленне не можа быць пустым.
error-private-message-too-long = [scarlet]⚠ Паведамленне занадта доўгае. Максімум { $max } сімвалаў.
error-private-message-cooldown = [scarlet]⚠ Пачакайце { DURATION($seconds) }, перш чым адправіць наступнае асабістае паведамленне.
error-private-message-target-unavailable = [scarlet]⚠ Гэты гулец зараз не можа атрымліваць асабістыя паведамленні.
error-private-message-no-reply-target = [scarlet]⚠ Няма нядаўняга кантакту, каму можна адказаць.
error-private-message-not-found = [scarlet]⚠ Паведамленне не знойдзена.
error-private-message-block-self = [scarlet]⚠ Нельга заблакаваць самога сябе.
error-private-message-block-limit = [scarlet]⚠ Дасягнуты ліміт спісу заблакаваных ({ $limit }).
ban-menu-duration-title = [orange]{ -xcore } - Тэрмін блакіроўкі
ban-menu-duration-message = Увядзіце тэрмін блакіроўкі для { $nickname }. Прыклад: 1d, 12h, 30m
ban-menu-reason-title = [orange]{ -xcore } - Прычына блакіроўкі
ban-menu-reason-message = Увядзіце прычыну блакіроўкі для { $nickname }. Пакіньце пустым для прычыны па змаўчанні.
ban-menu-confirm-title = [orange]{ -xcore } - Пацвердзіць блакіроўку
ban-menu-confirm-content =
    [white]Гулец: { $nickname }[]
    [white]Тэрмін: [accent]{ $duration }[]
    [white]Прычына: [accent]{ $reason }[]
ban-menu-confirm-action = [scarlet]Заблакаваць гульца
error-invalid-syntax = [scarlet]⚠ Няправільны сінтаксіс каманды. Выкарыстанне: [lightgray]/'{ $syntax }'.
error-invalid-sender = [scarlet]⚠ Няправільны адпраўнік каманды. Каманда патрабуе: '[lightgray]{ $type }[]'.
error-argument-parse-generic = [scarlet]⚠ Няправільны аргумент: '{ $error }'.
exception-unexpected = [scarlet]⚠ Пры выкананні каманды адбылася ўнутраная памылка.
exception-invalid-argument = [scarlet]⚠ Няправільны аргумент каманды: '{ $cause }'.
exception-no-such-command = [scarlet]⚠ Невядомая каманда.
exception-no-permission = [scarlet]⚠ Доступ забаронены.
exception-invalid-sender = [scarlet]⚠ '{ $actual }' не можа выканаць гэтую каманду. Патрэбны адпраўнік: [lightgray]{ $expected }[].
exception-invalid-sender-list = [scarlet]⚠ '{ $actual }' не можа выканаць гэтую каманду. Дазволеныя адпраўнікі: [lightgray]{ $expected }[].
exception-invalid-syntax = [scarlet]⚠ Няправільны сінтаксіс каманды. Выкарыстанне: [lightgray]/'{ $syntax }'.
argument-parse-failure-boolean = [scarlet]⚠ Не ўдалося прачытаць лагічнае значэнне з '{ $input }'.
argument-parse-failure-number = [scarlet]⚠ '{ $input }' — не правільны лік у дыяпазоне [{ $min }, { $max }].
argument-parse-failure-char = [scarlet]⚠ '{ $input }' — не правільны сімвал.
argument-parse-failure-enum = [scarlet]⚠ '{ $input }' — не правільны варыянт. Дазволена: [lightgray]{ $acceptableValues }
argument-parse-failure-string = [scarlet]⚠ Няправільны фармат тэксту для '{ $input }'.
argument-parse-failure-uuid = [scarlet]⚠ Няправільны фармат UUID: '{ $input }'.
argument-parse-failure-regex = [scarlet]⚠ Увод '{ $input }' не адпавядае шаблону '{ $pattern }'.
argument-parse-failure-color = [scarlet]⚠ '{ $input }' — не правільны колер.
argument-parse-failure-duration = [scarlet]⚠ '{ $input }' — не правільны фармат працягласці.
argument-parse-failure-aggregate-missing = [scarlet]⚠ Не хапае кампанента '{ $component }'.
argument-parse-failure-aggregate-failure = [scarlet]⚠ Няправільны кампанент '{ $component }': '{ $failure }'.
argument-parse-failure-either = [scarlet]⚠ Не ўдалося атрымаць { $primary } або { $fallback } з '{ $input }'.
argument-parse-failure-flag-unknown = [scarlet]⚠ Невядомы сцяг: '{ $flag }'.
argument-parse-failure-flag-duplicate = [scarlet]⚠ Паўторны сцяг: '{ $flag }'.
argument-parse-failure-flag-duplicate-flag = [scarlet]⚠ Паўторны сцяг: '{ $flag }'.
argument-parse-failure-flag-no-flag-started = [scarlet]⚠ Сцяг не пачаты. Незразумела, што рабіць з '{ $input }'.
argument-parse-failure-flag-missing-argument = [scarlet]⚠ Не хапае аргумента для сцяга: '{ $flag }'.
argument-parse-failure-flag-no-permission = [scarlet]⚠ У вас няма права выкарыстоўваць сцяг '{ $flag }'.
argument-parse-failure-selector-syntax = [scarlet]⚠ Няправільны селектар '{ $input }': [lightgray]{ $reason }
argument-parse-failure-selector-no-such-target = [scarlet]⚠ Нічога не адпавядае '{ $input }'.
argument-parse-failure-selector-too-many-targets = [scarlet]⚠ '{ $input }' адпавядае некалькім мэтам, а каманда патрабуе адну.
argument-parse-failure-selector-denied = [scarlet]⚠ Селектары тут забароненыя: [lightgray]{ $reason }
argument-parse-failure-selector-kind-not-allowed = [scarlet]⚠ Селектар '{ $kind }' тут забаронены.
argument-parse-failure-selector-sender-required = [scarlet]⚠ Селектар '{ $kind }' можна выкарыстоўваць толькі ў гульні.
argument-parse-failure-selector-limit-exceeded = [scarlet]⚠ Селектар адпавядае { $count } мэтам, ліміт — { $limit }.
argument-parse-failure-team = [scarlet]⚠ Каманда '{ $input }' не знойдзена.
argument-parse-failure-content = [scarlet]⚠ '{ $input }' — не правільнае значэнне тыпу { $type }.
# ==============================================================================
# Button Status
# ==============================================================================
finished = скончаныя
finished-neutral = [orange]Скончаныя
finished-active = [green]Скончаныя
finished-inactive = [red]Скончаныя
major = Вялікія
major-neutral = [orange]Вялікія
major-active = [green]Вялікія
major-inactive = [red]Вялікія
active = Актыўныя
active-neutral = [orange]Актыўныя
active-active = [green]Актыўныя
active-inactive = [red]Актыўныя
admin = Адмін
admin-neutral = [orange]Адмін
admin-active = [green]Адмін
admin-inactive = [red]Адмін
player-leaderboard-active = [green]Рэйтынг: уключаны[]
player-leaderboard-inactive = [red]Рэйтынг: выключаны[]
# ==============================================================================
# Miscellaneous
# ==============================================================================
hours = гадзіны
days = дні
success = [green]Паспяхова
empty = [accent]Пуста
never = Ніколі
save = Захаваць
close = [scarlet]Закрыць
previous = [accent]« Папярэдняя
next = [accent]Наступная »
cancel = Скасаваць
back = Назад
yes = Так
no = Не
test = Тэст
no-description = Няма апісання
site = Сайт
discord = Discord
github = Github
donatello = Donatello
weblate = Weblate
discord-red-vs-blue = RedVSBlue
auto = Аўта
on = Укл.
off = Выкл.
error-command-disabled = [scarlet]⚠ Каманда [accent]/{ $command }[scarlet] выключана на гэтым серверы.
error-feature-disabled = [scarlet]⚠ Гэтая функцыя выключана на гэтым серверы.
none = Няма
unknown = Невядома
error-nickname-badge-glyph = [scarlet]⚠ Карыстальніцкі нік не можа ўтрымліваць зарэзерваваныя іконкі бэйджаў.

# Server browser (/servers)
player-servers-title = СЕТКА СЕРВЕРАЎ XCORE
player-servers-cat-all = Усе
player-servers-cat-attack = Напад
player-servers-cat-pvp = PvP
player-servers-cat-survival = Выжыванне
player-servers-cat-special = Асаблівыя
player-servers-hint = Націсніце на картку сервера, каб падключыцца
player-servers-refresh = Абнавіць
player-servers-already-connected = [gold]● Вы ўжо падключаны да гэтага сервера!
player-servers-transferring = [accent]Пераход на сервер [white]{ $server }[]...
player-servers-full = [scarlet]Сервер { $server } запоўнены! Пачакайце вольнага месца.
player-servers-offline = [scarlet]Сервер { $server } зараз не працуе.
player-servers-not-found = [scarlet]Сервер '{ $server }' не знойдзены.
player-servers-empty-category = [lightgray]У гэтай катэгорыі няма даступных сервераў.[]
player-servers-online-summary = [green]● { $players } [gray]гуляюць[] [darkgray]|[] [sky]{ $servers } [gray]анлайн[]
player-servers-badge-current = [gold]● ВЫ ТУТ[]
player-servers-offline-badge = [darkgray]● НЕ ПРАЦУЕ[]
player-servers-capacity-full = [scarlet]● { $players }/{ $max } ЗАПОЎНЕНЫ[]
player-servers-capacity-normal = [green]● { $players }/{ $max } { $bar }
player-servers-card-current = [lightgray]Вы падключаны да гэтага сервера[]
player-servers-card-wave = [darkgray]|[] [accent]Хваля { $wave }[]
player-servers-card-empty = [sky]Будзьце першым! Пачніце гульню[]
player-servers-card-map = [gray]Карта:[] [white]{ $map }[]
player-servers-card-mode = [gray]Рэжым:[] [white]{ $mode }[]

announcement-hub =
    [gold]★ [accent]Серверы XCore [lightgray]» [white]Надакучыў гэты матч?
    [lightgray]Зазірніце на іншыя серверы ў любы час праз [accent]/hub[lightgray]!
announcement-discord =
    [gold]★ [accent]Супольнасць XCore [lightgray]» [white]Шукаеце саюзнікаў і навіны?
    [lightgray]Далучайцеся да нашага Discord-сервера праз [accent]/discord[lightgray]!
announcement-help =
    [gold]★ [accent]Дапамога XCore [lightgray]» [white]Патрэбна дапамога або спіс каманд?
    [lightgray]Увядзіце [accent]/help[lightgray], каб убачыць усе даступныя каманды!
announcement-report =
    [gold]★ [accent]Скаргі XCore [lightgray]» [white]Нехта грыферыць ці парушае правілы?
    [lightgray]Паскардзіцеся камандай [accent]/report[lightgray] — адміністрацыя разбярэцца!


error-only-players = [scarlet]⚠ Гэтая каманда даступная толькі гульцам.

player-settings-username-editable = [gold]★ Імя карыстальніка (узнагарода за падзею):[]
player-settings-username-hint = Увядзіце ўнікальнае імя карыстальніка (4–32 сімвалы)...
player-settings-username-locked = [gray]Імя карыстальніка:[] [accent]@{ $username }[] [darkgray](Заблакавана)[]
player-settings-username-none = [gray]Імя карыстальніка: [darkgray]Не зададзена (перамажыце ў падзеі, каб адкрыць)[]

error-username-empty = Імя карыстальніка не можа быць пустым!
error-username-length = Імя карыстальніка павінна мець ад 4 да 32 сімвалаў!
error-username-invalid-chars = Імя карыстальніка можа ўтрымліваць толькі лацінскія літары, лічбы і падкрэсліванні!
error-username-taken = Гэта імя карыстальніка ўжо заняў іншы гулец!

# ==============================================================================
# Permission nodes
# ==============================================================================
permission-mindustry-admin = Карыстацца ўбудаваным меню адміна гульні і прапускаць хвалі
permission-xcore-moderation-mute = Выдаваць мут
permission-xcore-moderation-unmute = Здымаць мут
permission-xcore-moderation-kick = Выключаць гульцоў
permission-xcore-moderation-ban = Блакаваць гульцоў
permission-xcore-moderation-unban = Разблакоўваць гульцоў
permission-xcore-moderation-audit-others = Праглядаць гісторыю мадэрацыі іншых гульцоў
permission-xcore-moderation-votekick-immune = Не можа быць выключаны галасаваннем
permission-xcore-admin-tp = Тэлепартаваць гульцоў
permission-xcore-admin-broadcast = Адпраўляць аб'явы ўсім
permission-xcore-admin-kill = Знішчаць юнітаў і гульцоў
permission-xcore-admin-heal = Лячыць юнітаў і гульцоў
permission-xcore-admin-set-team = Змяняць каманду гульца
permission-xcore-maps-force-rtv = Змяняць карту без галасавання
permission-xcore-maps-force-vnw = Прапускаць хвалю без галасавання
permission-xcore-votes-cancel = Скасоўваць бягучае галасаванне
permission-xcore-events-create-major = Ствараць вялікія падзеі
permission-xcore-events-edit-others = Змяняць падзеі іншых гульцоў
permission-xcore-events-force-vote = Запускаць падзею без галасавання
permission-xcore-events-stop = Спыняць бягучую падзею
permission-xcore-players-settings-others = Змяняць налады іншых гульцоў
permission-xcore-players-private-info = Бачыць прыватныя даныя гульцоў, напрыклад іх IP-адрасы
permission-xcore-bypass-playtime = Абыходзіць патрабаванні каманд да гульнявога часу
permission-xcore-permissions-inspect = Бачыць, у каго якія дазволы
permission-xcore-permissions-manage = Змяняць ролі і дазволы

error-target-outranks = [scarlet]⚠ Нельга зрабіць гэта з гульцом, чыя роля не ніжэйшая за вашу.
perm-me-header = [accent]Вашы ролі тут (вага [white]{ $weight }[accent]):
perm-me-none = [lightgray] - няма
perm-me-logged-in = [green]Вы ўвайшлі як член адміністрацыі.
perm-me-not-logged-in = [yellow]Вы не ўвайшлі як член адміністрацыі. Выкарыстайце [white]/login <пароль>[yellow].
perm-me-stale = [scarlet]Вашы правы адміністрацыі прыпынены: сервер не змог іх абнавіць. Яны вернуцца самі.

help-ui-search-hint = Пошук па камандах, псеўданімах і апісаннях
help-ui-search-empty = [lightgray]Па вашым запыце каманд не знойдзена.[]

match-history-title = Гісторыя матчаў
match-history-rank = #{ $rank } у сезоне
match-history-form = Апошнія { $count }:
match-history-empty = Тут з'явяцца вашы рэйтынгавыя матчы.
match-history-since = Гісторыя вядзецца з { $date }.
match-history-load-failed = Не ўдалося загрузіць гісторыю матчаў. Паспрабуйце крыху пазней.
match-history-back-to-list = Да спісу
match-history-your-result = Ваш вынік
match-history-places = Месцы
match-history-team-title = { $place } месца · { $team } · сярэдні { $average }
match-history-time-now = толькі што
match-history-time-minutes = { $count } хв таму
match-history-time-hours = { $count } гадз таму
match-history-time-yesterday = учора
match-history-outcome-win = [lime]Перамога[]
match-history-outcome-loss = [scarlet]Паражэнне[]
match-history-outcome-unrated = [lightgray]Без рэйтынгу[]
match-history-outcome-uncounted = [lightgray]Не залічаны[]
match-history-lineup = { $own } на { $other }
match-history-team-place = { $place } месца з { $teams } каманд
match-history-place = #{ $place } з { $players }
match-history-counted = Залічаны: { $reason }
match-history-not-counted = Не залічаны: { $reason }
match-history-counted-share = Залічана { $percent }% матчу
match-history-reason-winner = перамога
match-history-reason-defeated = паражэнне
match-history-reason-short-play = згуляў менш за палову матчу
match-history-reason-late-join = зайшоў напрыканцы
match-history-reason-left-at-once = амаль адразу выйшаў
match-history-reason-match-unrated = матч без рэйтынгу
match-history-reason-unknown = прычына не запісана
match-history-skip-not-enough-players = замала гульцоў
match-history-skip-admin-stop = спынены адміністратарам
match-history-skip-technical-error = тэхнічная памылка
match-history-skip-unknown = без рэйтынгу
match-history-finish-surrender = здачай
match-history-finish-timeout = па таймеры
match-history-finish-admin-stop = спынены адміністратарам
match-history-finish-technical-error = тэхнічная памылка

# Discord submission attribution; the declared creator is supplied by the uploader.
map-upload-file-author = Аўтар з файла: { $author }
map-upload-creator = Указаны аўтар: { $person }
map-upload-submitter = Дадаў праз Discord: { $person }
map-upload-date = Загружана: [lightgray]{ $date }[] (UTC)

map-upload-current-creator = [accent]{ $map }[] — { $creator }
