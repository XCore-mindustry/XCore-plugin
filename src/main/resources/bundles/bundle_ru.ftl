# ==============================================================================
# Terms
# ==============================================================================
-xcore = XCore сервер
# ==============================================================================
# General & Help
# ==============================================================================
menu-main = Главное меню
commands-main-description = Открыть интерактивное главное меню.
menu-main-title = { "[" }orange]{ -xcore } — Главное меню
menu-main-content = Главное меню сервера
help-menu = Меню помощи
commands-help-description = Открыть интерактивное меню помощи.
help-menu-title = { "[" }orange]{ -xcore } — Команды
help-menu-content =
    { "[" }gray]Страница [white]{ $page }[gray]/[white]{ $total }
    { "" }[lightgray]Выберите команду для подробной информации:
help-menu-button = { "[" }accent]/{ $command } [gray]» [white]{ $description }
help-command-with-overload-count = { $name } ({ $count })
help-command-title = { "[" }orange]» [white]/{ $name }
help-command-header =
    { "[" }orange]» [accent]Синтаксис: [white]{ $syntax }
    { "" }[orange]» [accent]Описание: [lightgray]{ $description }
help-aliases = { "[" }orange]» [accent]Псевдонимы: [white]{ $aliases }
help-args-title = { "[" }orange]» [accent]Аргументы:
help-usages-title = { "[" }orange]» [accent]Использование:
help-usage-entry = { "[" }gray]• [white]{ $syntax }
help-usage-args-title = { "[" }orange]» [accent]Для [white]{ $syntax }[accent]:
help-arg-entry = { "[" }gray]• [white]{ $arg } [lightgray]- { $description }
help-no-arguments = { "[" }gray]Дополнительные аргументы не требуются.
help-no-arg-description = Нет описания.
help-no-description = Описание для этой команды не предоставлено.
help-legacy-command-content =
    { "[" }orange]» [accent]Команда: [white]/{ $name }
    { "" }[orange]» [accent]Параметры: [white]{ $params }
    { "" }[orange]» [accent]Описание: [lightgray]{ $description }
    { "" }
    { "" }[gray](Это устаревшая команда с ограниченной информацией)
help-legacy-command-content-no-params =
    { "[" }orange]» [accent]Команда: [white]/{ $name }
    { "" }[orange]» [accent]Описание: [lightgray]{ $description }
    { "" }
    { "" }[gray](Это устаревшая команда с ограниченной информацией)
help-back = { "[" }lightgray]« Назад
# ==============================================================================
# Command Argument Descriptions
# ==============================================================================
# help
commands-help-page-description = Номер страницы для отображения
# login
commands-login-password-description = Ваш пароль администратора
# ban
commands-ban-id-description = ID игрока для бана
commands-ban-period-description = Длительность бана (например: 1d, 2h, 30m)
commands-ban-reason-description = Причина бана
# unban
commands-unban-id-description = ID игрока для разбана
# mute
commands-mute-id-description = ID игрока для мута
commands-mute-period-description = Длительность мута (например: 1h, 30m)
commands-mute-reason-description = Причина мута
# unmute
commands-unmute-id-description = ID игрока для снятия мута
# votekick
commands-votekick-target-description = Игрок для кика (ID или имя)
commands-votekick-reason-description = Причина кика
# vote
commands-vote-choice-description = Ваш голос: y (да), n (нет), c (отмена, только админ)
# t (team chat)
commands-t-message-description = Сообщение для союзников
# g (global chat)
commands-g-message-description = Сообщение для всех серверов
# tr (translator)
commands-tr-language-description = Код языка, 'ru', 'en', ..., 'auto' или 'off'
# stats
commands-stats-id-description = ID игрока для просмотра статистики
# rank
commands-rank-player-description = Игрок для просмотра ранга
# map
commands-map-map-description = Название или номер карты
# maps / maps-text
commands-maps-page-description = Номер страницы
commands-maps-text-page-description = Номер страницы
# rtv / artv
commands-rtv-map-description = Карта для голосования (опционально)
commands-artv-map-description = Карта для принудительной смены
# ai
commands-ai-state-description = Состояние ИИ: attack (a) или idle (i)
# event / events
commands-events-page-description = Номер страницы
# ==============================================================================
# General & Help (continued)
# ==============================================================================
commands-information-description = Показать информацию о сервере
commands-info = Информация
commands-info-title = { "[" }orange]{ -xcore } — Название сервера: [orange]{ $server-name }
commands-info-text =
    { "[" }accent]XCore[white] — это [cyan]бесплатный[white] сервер для игры в [accent]Mindustry[white].
    { "" }
    { "" }Версия XCore — [accent]{ $version }[white]
commands-sync-description = Синхронизировать игру с сервером. Запустите это для исправления ошибок, таких как фантомные юниты.
commands-discord-description = Перенаправляет вас на сервер discord.
discord-menu-title = { "[" }orange]{ -xcore } — Discord
discord-menu-content =
    { "" }[white]Управляйте привязкой Discord здесь.
    { "" }
    { "" }[white]Статус: { $status }
    { "" }[white]Сервер: [accent]{ $discordUrl }[]
discord-menu-open = Открыть Discord
discord-menu-link = Привязать аккаунт
discord-menu-status = Обновить статус
discord-menu-unlink = Отвязать аккаунт
discord-menu-status-not-linked = [lightgray]не привязан[]
discord-menu-status-linked = [green]{ $discordUsername }[] [gray]({ $discordId })[]
discord-link-menu-title = { "[" }orange]{ -xcore } — Привязка Discord аккаунта
discord-link-menu-content =
    { "" }[white]Отправьте этот код Discord-боту:
    { "" }
    { "" }[accent]{ $code }[]
    { "" }
    { "" }[white]Истекает через: [accent]{ $expireMinutes }[] мин
    { "" }[white]Discord: [accent]{ $discordUrl }[]
discord-link-menu-refresh = Обновить код
discord-link-menu-regenerate = Сгенерировать новый код
discord-link-menu-status = Назад в меню Discord
welcome =
    { "[" }accent]Добро пожаловать в { $serverName }!
    { "" }[lightgray]Введите [accent]/help[lightgray], чтобы увидеть список команд
    { "" }[lightgray]Введите [accent]/vote [gray]<y/n>[lightgray], чтобы проголосовать за наказание игрока
    { "" }[lightgray]Введите [accent]/votekick [gray]<ID/имя> <причина...>[lightgray], чтобы начать голосование-кик
    { "" }[lightgray]Введите [accent]/t [gray]<сообщение...>[lightgray], чтобы отправить сообщение своим союзникам
    { "" }[lightgray]Введите [accent]/g [gray]<сообщение...>[lightgray], чтобы отправить сообщение всем серверам
    { "" }[lightgray]Введите [accent]/tr [gray]<язык/auto>[lightgray], чтобы включить переводчик
    { "" }[lightgray]Введите [accent]/discord[lightgray], чтобы открыть меню Discord и привязать аккаунт
# ==============================================================================
# Chat & Social
# ==============================================================================
commands-t-description = Отправить сообщение только своим товарищам по команде
commands-t-chat = { "[" }{ "#" }{ $color }][Команде] [coral]> { $badge }[accent]{ $name }[lightgray]: [white]{ $message }
commands-g-description = Отправить сообщение на все сервера
commands-a-description = Отправить сообщение только администраторам
commands-msg-description = Отправить игроку личное сообщение
commands-msg-id-description = ID игрока
commands-msg-message-description = Текст личного сообщения
commands-reply-description = Ответить последнему собеседнику в личных сообщениях
commands-reply-message-description = Текст ответа
commands-inbox-description = Открыть меню личных сообщений
commands-inbox-id-description = ID игрока
commands-tr-description = Установить язык переводчика
commands-tr-success = { "[" }accent]Язык переводчика был успешно изменен на [grey]{ $translatorLanguage }[]!
commands-tr-off = { "[" }accent]Переводчик [scarlet]выключен[]!
commands-tr-not-found = { "[" }scarlet]⚠ Нет такого языка
discord-chat-format = { "[" }#5865F2][DISCORD][] [lightgray]| [accent]{ $author }[lightgray] >> [white]{ $message }
global-chat-format = { "[" }royal][[[orange]GLOBAL [lightgray](из [accent]{ $server }[])[] { $author }[]]: [white]{ $message }
private-message-received = { "[" }sky][ЛС][] [lightgray]от [accent]{ $author } [gray]#{ $pid }[lightgray]: [white]{ $message }
private-message-sent = { "[" }sky][ЛС][] [lightgray]для [accent]{ $target } [gray]#{ $pid }[lightgray]: [white]{ $message }
private-message-unread-count = { "[" }accent]У вас [white]{ $count }[accent] непрочитанных личных сообщений.
private-message-join-notification = { "[" }accent]У вас [white]{ $count }[accent] непрочитанных личных сообщений. Используйте [white]/inbox[accent], чтобы открыть их.
private-message-block-success = { "[" }accent]Личные сообщения от [white]{ $target } [gray]#{ $pid }[accent] теперь заблокированы.
private-message-block-already = { "[" }lightgray]Личные сообщения от [white]{ $target } [gray]#{ $pid }[lightgray] уже заблокированы.
private-message-unblock-success = { "[" }accent]Личные сообщения от [white]{ $target } [gray]#{ $pid }[accent] снова разрешены.
private-message-unblock-missing = { "[" }lightgray]Игрок [white]{ $target } [gray]#{ $pid }[lightgray] не находится в блок-листе.
private-message-menu-title = { "[" }orange]{ -xcore } — Входящие
private-message-menu-content =
    { "" }[white]Страница [green]{ $page }[] из [green]{ $total }[]
    { "" }[white]Непрочитано: [accent]{ $unread }[]
private-message-menu-empty = { "" }[lightgray]У вас пока нет личных сообщений.
private-message-menu-entry-unread = { "[" }accent]Непрочитано[] [white]{ $author } [gray]#{ $pid }[] [lightgray]({ $time })[]: [white]{ $message }
private-message-menu-entry-read = { "[" }gray]Прочитано[] [white]{ $author } [gray]#{ $pid }[] [lightgray]({ $time })[]: [white]{ $message }
private-message-details-title = { "[" }orange]{ -xcore } — Сообщение
private-message-details-content =
    { "" }[white]От: [accent]{ $author } [gray]#{ $pid }[]
    { "" }[white]Время: [accent]{ $time }[]
    { "" }[white]Статус: [accent]{ $status }[]
    { "" }
    { "" }[white]{ $message }
private-message-status-unread = непрочитано
private-message-status-read = прочитано
private-message-blocked-title = { "[" }orange]{ -xcore } — Заблокированные игроки
private-message-blocked-content =
    { "" }[white]Страница [green]{ $page }[] из [green]{ $total }[]
    { "" }[white]Заблокировано: [accent]{ $count }[]
private-message-blocked-empty = { "" }[lightgray]У вас нет заблокированных игроков.
private-message-blocked-entry = { "[" }white]{ $target } [gray]#{ $pid }[]
private-message-compose = Написать
private-message-blocked = Заблокированные
private-message-block = Блокировать отправителя
private-message-unblock = Разблокировать отправителя
private-message-reply-title = Ответ
private-message-reply-message = Введите сообщение для [accent]#{ $pid }[]
private-message-compose-target-title = Новое сообщение
private-message-compose-target-message = Введите ID игрока в формате [accent]#123[]
private-message-compose-body-title = Текст сообщения
private-message-compose-body-message = Введите личное сообщение для [accent]{ $pid }[]
# ==============================================================================
# Authentication & Admin Access
# ==============================================================================
commands-login-description = Запрос на права админа. Не используйте, если не знаете, что делаете
commands-login-incorrect-password = { "[" }scarlet]⚠ Некорректный пароль!
commands-login-success = { "[" }green]Права админа получены
commands-login-confirmed = { "[" }green]Права админа подтверждены
commands-login-admin-password-created =
    { "[" }green]Пароль админа создан
    { "" }[red]Не забудьте свой пароль! Если Вы его забудете, Вам придется обратиться к главному администратору с просьбой сбросить его.
commands-login-request-approval-discord = { "[" }accent]Вам необходимо подтвердить запрос на права администратора в Discord канале [orange]#admin-bots[].
commands-discord-link-created =
    { "[" }green]Код привязки Discord создан: [accent]{ $code }[]
    { "" }[lightgray]Отправьте этот код Discord-боту в течение [accent]{ $expireMinutes }[] мин.
    { "" }[cyan]{ $discordUrl }
commands-discord-link-confirmed = { "[" }green]Discord аккаунт привязан: [accent]{ $discordUsername }[]
commands-discord-link-already-linked = { "[" }lightgray]Этот аккаунт Mindustry уже привязан. Используйте [accent]/discord status[] или [accent]/discord unlink[].
commands-discord-link-error = { "[" }scarlet]Не удалось создать код привязки Discord. Попробуйте позже.
commands-discord-status-not-linked = { "[" }lightgray]Ваш аккаунт не привязан к Discord.
commands-discord-status-linked = { "[" }green]Привязанный Discord: [accent]{ $discordUsername }[] [gray]({ $discordId })[]
commands-discord-unlink-not-linked = { "[" }lightgray]Ваш аккаунт не привязан к Discord.
commands-discord-unlink-success = { "[" }green]Привязка Discord удалена.
commands-logout-description = Выйти. Это [scarlet]отзовет ваши права администратора.
commands-logout-successful = { "[" }green]Вы лишены прав администратора
# ==============================================================================
# Moderation (Ban, Mute, Kick)
# ==============================================================================
commands-ban-description = Забанить игрока. [scarlet]Только для админов
commands-ban-success = { $nickname } [scarlet]забанен
commands-unban-description = Разбанить игрока. [scarlet]Только для админов
commands-unban-success = { $nickname }[accent] #{ $pid } успешно разбанен
commands-mute-description = Замьютить игрока. [scarlet]Только для админов
commands-mute-success = { "[" }accent]Игрок { $nickname }[accent] успешно замьючен
commands-unmute-description = Размутить игрока. [scarlet]Только для админов
commands-unmute-success = Успешно размучено игрока { $nickname }
ban-content =
    { $nickname } забанен
    Для снятия бана посетите дискорд(канал [gray]{ support-channel }[]):
    { "" }[cyan]{ $discordUrl }
ban-cancelled = { "[" }accent]Бан игрока { $nickname }[accent] был отменен
tempban-content =
    { $nickname }[accent] забанен.
    Админ: { $adminName }[accent]
    Причина: "[gold]{ $reason }[]"
    Вы будете разбанены через: { $days } дней, { $hours } часов и { $minutes } минут
    Для снятия бана посетите дискорд(канал [gray]{ support-channel }[]):
    { "" }[cyan]{ $discordUrl }
tempban-player-banned = { "[" }scarlet] Админ { $adminName }[scarlet] забанил игрока [gray]'[]{ $playerName }[gray]'
you-are-muted-by =
    { "[" }scarlet]Вы были заглушены администратором [accent]{ $adminName }[blue] на { $remainMinutes }:{ $remainSeconds } минут.
    Причина: { $reason }
you-are-muted =
    { "[" }scarlet]Вы не можете писать в чат. Вы заглушены администратором [accent]{ $adminName }[accent] на { $remainMinutes }:{ $remainSeconds } минут.
    Причина: { $reason }
kick-pirated-game = { "[" }accent]Обнаружен неавторизованный клиент. [scarlet]Доступ запрещен[]. Пожалуйста, используйте [lime]официальную[] версию из [blue]Steam[], [blue]Google Play[] или [blue]itch.io[].
kick-recently-kicked =
    { "[" }accent]Вы недавно были кикнуты с этого сервера.
    Подождите [cyan]{ $remainMinutes }:{ $remainSeconds }[accent] перед повторным входом.
kick-bot-protection = Возможно вы бот. Если нет, попробуйте перезайти.
kick-admintools-outdated =
    { "[" }green]Требуемая версия AdminTools: [grey]{ $requiredVersion }[]
    { "" }[scarlet]Ваша версия AdminTools: [grey]{ $version }[]
    { "" }
    { "" }[cyan]Пожалуйста, обновите AdminTools для входа на сервер.
support-channel = #reports-appeals
# ==============================================================================
# Voting (VoteKick)
# ==============================================================================
commands-votekick-description = Голосование за кик игрока.
commands-vote-description = Проголосовать за кик текущего игрока
commands-vote-vote-with = { "[" }scarlet]⚠ Голосуйте с помощью [orange]/vote <y/n/c>
votekick-vote =
    { $starter } [grey]#[white]{ $starterId }[lightgray] хочет выгнать { $target } [grey]#[white]{ $targetId }[lightgray]. Причина: [orange]{ $reason }[lightgray]. ([accent]{ $votes }[]/[accent]{ $required }[])
    { "" }[lightgray]Напишите [orange]/vote <y/n>[], чтобы проголосовать.
votekick-left = { $player }[lightgray] покинул игру. Голос аннулирован. ([accent]{ $votes }[]/[accent]{ $required }[])
votekick-fail = { "[" }lightgray]Голосование не состоялось. Недостаточно голосов для изгнания { $target }[lightgray].
votekick-cancelled = { "[" }scarlet]Голосование за кик { $target }[scarlet] отменено администратором { $admin }[scarlet].
votekick-success =
    { "[" }orange]Голосование успешно. { $target }[orange] изгнан на [scarlet]{ $minutes }[] { $minutes ->
        [one] минуту
        [few] минуты
       *[other] минут
    }.
# ==============================================================================
# Maps & RTV
# ==============================================================================
commands-map-description = Статистика конкретной карты
commands-map-title = { "[" }orange]{ -xcore } — Статистика
commands-map-content =
    { "" }[white]Статистика карты [green]{ $name }
    { "" }[white]Автор:[green] { $author }[orange] | [white]Размер:[green] { $width }x{ $height }[orange]
    { "" }[white]Репутация:[green] { $reputation }[orange] | [white]Популярность:[green] { $popularity }[orange] | [white]Интерес:[green] { $interest }[orange]
    { "" }[white]Сыграно раз:[green] { $played }[orange] | [white]Сыграно за год:[green] { $playedYear }[orange] | [white]Последняя игра:[green] { $lastPlayed }[orange]
    { "" }[white]Лайк:[green] { $like }[orange] | [white]Дизлайк:[green] { $dislike }[orange]
    { "" }[white]Мин. время:[green] { $min }[orange] | [white]Ср. время:[green] { $avg }[orange] | [white]Макс. время:[green] { $max }[orange]
    { "" }[green]{ $description }[white]
commands-maps-description = Список всех карт на этом сервере.
commands-maps-title = { "[" }orange]{ -xcore } — Список карт
commands-maps-content = { "" }[white]Страница [green]{ $page }[] из [green]{ $total }[]
commands-maps-text-description = Список всех карт на этом сервере.
commands-maps-text-start-content =
    { "[" }accent]Текущая карта: []{ $name }[white]
    { "" }[orange][gold]Список карт [lightgray]{ $page }[gray]/[lightgray]{ $total }
commands-maps-text-content =
    { "" }
    { $index }. [orange] - [white]{ $name }[orange] | [green]{ $reputation }[orange] | [white]{ $width }x{ $height }[orange] | [white]{ $lastPlayed }[orange] | От: [sky]{ $author }
commands-artv-description = Изменить карту. [scarlet]Только для админов
commands-artv-map-skipped = { $nickname }[accent] пропустил карту. Следующая карта: { $name }.
commands-rtv-description = Голосование за изменение карты
commands-like-description = Проголосовать за карту (повышает репутацию)
commands-dislike-description = Проголосовать против карты
map-vote-title = { "[" }orange]{ -xcore } — [scarlet]ИГРА ОКОНЧЕНА!
map-vote-content =
    { "" }
    { "" }Следующая карта: [accent]{ $mapName }[] от [accent]{ $author }[white].
    { "" }Новая игра начнется через [accent]{ $seconds }[white] секунд.
    { "" }
    { "" }[cyan]Понравилась эта карта?
map-vote-like = { "[" }green]👍 Нравится
map-vote-dislike = { "[" }red]👎 Не нравится
map-vote-like-selected = { "[" }gray]Вам нравится
map-vote-dislike-selected = { "[" }gray]Вам не нравится
map-rtv = { "[" }orange]Голосование
map-artv = { "[" }red]Мгновенная смена
map-maps = Карты
current-map = Текущая карта
next-map = Следующая карта
rtv-vote =
    { $nickname }[lightgray] проголосовал за смену текущей карты на [orange]{ $mapName }[lightgray]. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Напишите [orange]y[] или [orange]n[], чтобы проголосовать.
rtv-left = { $nickname }[lightgray] покинул игру. Голос за смену карты аннулирован. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
rtv-fail = { "[" }lightgray]Голосование провалилось. Не хватило голосов, чтобы изменить текущую карту на [orange]{ $mapName }[].
rtv-success = { "[" }orange]Голосование завершено успешно. Карта [accent]{ $mapName }[] будет загружена через [accent]{ $mapLoadDelay }[] секунд…
rtv-cancelled = { "[" }lightgray]Голосование за смену карты на [orange]{ $mapName }[lightgray] было отменено администратором { $admin }.
# ==============================================================================
# Statistics & Ranks & Players
# ==============================================================================
commands-player-description = Статистика игрока
player-menu-player = Игрок
player-menu-player-title = { "[" }orange]{ -xcore } — Статистика игрока
player-menu-player-content =
    { "" }[gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }[white]{ $customNickname }[] [gray]#{ $pid }[]
    { "" }[lightgray]{ $description }[]
    { "" }[gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    { "" }[accent]■ Профиль[]
    { "" }[gray]Имя: [white]{ $nickname } [darkgray]|[gray] Админ: [lime]{ $admin }[]
    { "" }[gray]Бейдж: [white]{ $activeBadge } [darkgray]|[gray] Системный: [coral]{ $systemBadge }[]
    { "" }[gray]Регистрация: [white]{ $accountCreated }[]
    { "" }
    { "" }[accent]■ Игровые рейтинги[]
    { "" }[gray]Время в игре: [white]{ $totalPlayTime }[]
    { "" }[gray]MiniPvP: [sky]{ $pvpRating } [darkgray]|[gray] Hexed: [sky]{ $hexedRankName } [gray]({ $hexedPoints } очк.)[]
    { "" }[lightgray]{ $hexedProgress }[]
    { "" }
    { "" }[accent]■ Матчи: [white]{ $gamesPlayed } [gray]игр [darkgray]|[lime] { $gamesWon } [gray]побед [darkgray]|[sky] { $winRate }% [gray]винрейт[]
    { "" }[gray]• [white]PvP: { $pvpSummary }[]
    { "" }[gray]• [white]Surv: { $survivalSummary }[]
    { "" }[gray]• [white]Hexed: { $hexedSummary }[]
    { "" }
    { "" }[accent]■ Боевая эффективность[]
    { "" }[gray]Блоки (Стр/Разб/Уничт): [lime]{ $blocksBuilt } [darkgray]/ [orange]{ $blocksDeconstructed } [darkgray]/ [scarlet]{ $blocksDestroyed }[]
    { "" }[gray]Юниты (Созд/Убито): [lime]{ $unitsProduced } [darkgray]/ [scarlet]{ $unitsDestroyed }[]
player-menu-players = Список игроков
player-menu-players-title = { "[" }orange]{ -xcore } — Список игроков
player-menu-players-content = { "" }[white]Страница [green]{ $page }[] из [green]{ $total }[]
player-menu-players-empty = Игроки не найдены
player-menu-settings = Настройки
player-menu-settings-title = { "[" }orange]{ -xcore } — Настройки игрока
player-menu-settings-content =
    { "" }[white]Установленное имя: [green]{ $customNickname }[]
    { "" }[white]Системный бейдж: [green]{ $systemBadge }[] | Активный бейдж: [green]{ $activeBadge }[]
    { "" }[white]Таблица лидеров: [green]{ $leaderboard }[]
    { "" }[white]Язык: [green]{ $language }[] | Язык переводчика: [green]{ $translatorLanguage }[]
    { "" }[green]{ $description }[white]
player-menu-settings-translator-title = { "[" }orange]{ -xcore } — Выбор языка переводчика
player-menu-settings-language-title = { "[" }orange]{ -xcore } — Выбор языка
player-menu-settings-badges = Бейджи
player-menu-player-max-rank = Максимальный ранг достигнут
player-menu-player-hexed-progress = { "[" }gray]До [white]{ $nextRankName } [gray]осталось побед: [accent]{ $requiredPoints }[]
player-menu-player-no-mode-stats = { "[" }gray]нет данных[]
player-menu-player-pvp-summary = { "[" }gray]игр [white]{ $gamesPlayed }[], побед [lime]{ $gamesWon }[], [sky]{ $winRate }%[]
player-menu-player-survival-summary = { "[" }gray]волны: макс [lime]{ $bestWave }[], ср [white]{ $averageWave }[] [gray](забегов: { $gamesPlayed })[]
player-menu-player-hexed-summary = { "[" }gray]матчей [white]{ $gamesPlayed }[], топ-1 [lime]{ $gamesWon }[], лучшее место [accent]#{ $bestPlacement }[]
player-menu-time-days = { $value }д
player-menu-time-hours = { $value }ч
player-menu-time-minutes = { $value }м
settings-language-label = Перевод: [green]{ $lang }[]
settings-translator-label = Язык переводчика: [green]{ $lang }[]
badge-menu-title = { "[" }orange]{ -xcore } — Бейджи
badge-menu-content =
    { "" }[white]Системный бейдж: [green]{ $systemBadge }[]
    { "" }[white]Активный бейдж: [green]{ $activeBadge }[]
badge-menu-empty = { "[" }lightgray]У вас пока нет открытых бейджей.
badge-menu-row = { "[" }white]{ $badge }[] [gray]-[] { $description }
badge-menu-view-all = Все бейджи
badge-menu-all-title = { "[" }orange]{ -xcore } — Все бейджи
badge-menu-all-content = { "[" }lightgray]Просмотр всех бейджей, их статуса и описания.
badge-menu-all-row = { "[" }white]{ $badge }[] [gray]-[] [accent]{ $state }[] [gray]-[] { $description }
badge-clear-button = Снять активный бейдж
badge-state-system = Системный
badge-state-system-active = Системный активен
badge-state-active = Активен
badge-state-unlocked = Открыт
badge-state-locked = Закрыт
badge-set-success = { "[" }accent]Активный бейдж установлен: [green]{ $badge }[].
badge-clear-success = { "[" }accent]Активный бейдж снят.
badge-grant-success = { "[" }accent]Бейдж [green]{ $badge }[] выдан игроку [green]{ $nickname }[][gray]#{ $pid }[].
badge-revoke-success = { "[" }accent]Бейдж [green]{ $badge }[] снят у игрока [green]{ $nickname }[][gray]#{ $pid }[].
badge-already-unlocked = { "[" }scarlet]⚠ Бейдж [accent]{ $badge }[scarlet] уже открыт.
badge-not-owned = { "[" }scarlet]⚠ У игрока нет бейджа [accent]{ $badge }[scarlet].
error-badge-not-found = { "[" }scarlet]⚠ Бейдж [accent]{ $badge }[scarlet] не найден.
error-badge-not-unlocked = { "[" }scarlet]⚠ Бейдж [accent]{ $badge }[scarlet] не открыт.
error-badge-not-selectable = { "[" }scarlet]⚠ Бейдж [accent]{ $badge }[scarlet] нельзя выбрать вручную.
badge-admin-name = Админ
badge-admin-description = Автоматический бейдж для администраторов.
badge-developer-name = Разработчик
badge-developer-description = Выдаётся разработчикам XCore.
badge-translator-name = Переводчик
badge-translator-description = Выдаётся тем, кто помогает с переводом XCore.
badge-map-maker-name = Мапмейкер
badge-map-maker-description = Выдаётся авторам карт, используемых на сервере.
badge-contributor-name = Контрибьютор
badge-contributor-description = Выдаётся за заметный вклад в проект или сообщество.
badge-event-winner-name = Победитель ивента
badge-event-winner-description = Выдаётся победителям специальных серверных событий.
badge-veteran-name = Ветеран
badge-veteran-description = Выдаётся уважаемым старым игрокам.
commands-lb-description = Включить/выключить таблицу лидеров
commands-lb-success =
    { $leaderboardEnabled ->
        [true] { "[" }accent]Таблица лидеров [scarlet]включена
       *[other] { "[" }accent]Таблица лидеров [scarlet]выключена
    }
leaderboard = { "[" }blue]Таблица лидеров
commands-rank-description = Показывает информацию о вашем ранге или ранге игрока.
commands-rank-content =
    { $nickname }
    { $rankTag } [accent]{ $rankName }
    { "" }[gold]Побед: { $points }/{ $requiredPoints }
commands-ranks-description = Показывает информацию о рангах
commands-ranks-content =
    { $rankTag } [accent]{ $rankName }
    { "" }[gold]Требования: [grey]{ $requiredPoints } [accent]побед[]
commands-ranks-footer = Количество побед увеличивается только при победе над игроком вашего ранга или выше.
commands-top-description = Топ-игроки
commands-top-hexed-content = { "[" }orange]{ $index }. { $nickname }[accent]: [blue]{ $rankName } [cyan]{ $points } []побед
commands-top-pvp-content = { "[" }orange]{ $index }. { $nickname }[accent]: [cyan]{ $rating }
# ==============================================================================
# Game Modes (Hexed, PvP, Spectate, AI)
# ==============================================================================
commands-spectate-description = Переключить режим наблюдателя. Это очистит вашего юнита.
commands-spectate-success = { "[" }green]Теперь вы наблюдаете за игрой
commands-ai-description = Контролировать ИИ
commands-ai-usage = { "[" }red]attack(i) []или [accent]idle(i)
hexed-popup = { "[" }blue]{ $minutes }:{ $seconds }[] до конца игры
hexed-eliminated = { $nickname } [gold]уничтожен!
hexed-leaderboard-content = { "[" }orange]{ $index }. { $nickname }[accent]: [cyan]{ $hexes } [accent]хексов
hexed-ranks-newbie = Новичок
hexed-ranks-regular = Базовый
hexed-ranks-advanced = Продвинутый
hexed-ranks-veteran = Ветеран
hexed-ranks-davastator = Разрушитель
hexed-ranks-the_legend = Легенда
hexed-game-over-header = Игра окончена. Победители:
hexed-game-over-winner-row =
    { "[" }orange]{ $index }. { $name }[][accent]: [cyan]{ $cores } { $cores ->
        [one] гекс
        [few] гекса
       *[other] гексов
    }
hexed-game-over-no-winners = Игра окончена. К сожалению, победители не найдены.
hexed-game-over-restart = Новая игра через 10 секунд…
pvp-team-won = Ваша команда победила. Ваш рейтинг увеличился на { $increased }
pvp-team-lose = Ваша команда проиграла. Ваш рейтинг снижен на { $reduced }
pvp-leaderboard-content = { "[" }orange]{ $index }. { $nickname }[accent]:[cyan] { $rating } [accent]рейтинг
pvp-you-spectator = { "[" }scarlet]Вы выбыли. Пожалуйста, дождитесь следующей игры.
# ==============================================================================
# Events & Notifications
# ==============================================================================
player-joined = { $nickname } [grey]#[white]{ $pid }[grey] [accent]присоединился
player-left = { $nickname } [grey]#[white]{ $pid }[grey] [accent]вышел
notification-votekick-playtime = { "[" }accent]Поздравляем! Вы отыграли [lightgray]{ $votekickPlayTime }[] минут и теперь можете начать голосование за кик игрока.
notification-global-chat-playtime =
    { "[" }accent]Поздравляем! Вы отыграли [lightgray]{ $globalChatPlayTime }[] минут и теперь можете писать в глобальный чат.
    { "" }[lightgray]Введите [accent]/g [gray]<сообщение...>[lightgray], чтобы отправить сообщение.
notification-admin-kick = { $admin }[accent] кикнул { $target }[].
notification-admin-wave-skip = { $admin }[accent] пропустил волну.
server-restart-countdown = Перезагрузка через { $seconds }
like-map-success = { "[" }green]Вам понравилась эта карта!
like-map-changed = { "[" }green]Вы изменили мнение на лайк!
dislike-map-success = { "[" }orange]Вам не понравилась эта карта.
dislike-map-changed = { "[" }orange]Вы изменили мнение на дизлайк.
like-event-success = { "[" }green]Вам понравилось это событие!
like-event-changed = { "[" }green]Вы изменили мнение на лайк!
dislike-event-success = { "[" }orange]Вам не понравилось это событие.
dislike-event-changed = { "[" }orange]Вы изменили мнение на дизлайк.

# ==============================================================================
# Events (Server)
# ==============================================================================

commands-event-description = Меню управления событиями.
commands-events-description = Список всех событий на серверах.
event-events = События
event-menu-main = Основные события
event-menu-main-title = { "[" }orange]{ -xcore } — События
event-menu-main-content = Главная страница событий
event-menu-event = Событие
event-menu-event-title = { "[" }orange]{ -xcore } — Событие
event-menu-event-content =
    { "" }[white]Статистика события [green]{ $name }
    { "" }[white]Автор:[green] { $author }[orange] | [white]Карта:[green] { $mapName }[orange]
    { "" }[white]Крупное?:[green] { $isMajor }[orange] | [white]Проведено?:[green] { $isConducted }[orange]
    { "" }[white]Активно?:[green] { $isActive }[orange] | [white]Временное?:[green] { $isTemporary }[orange]
    { "" }[white]Лайки:[green] { $like }[orange] | [white]Дизлайки:[green] { $dislike }[orange]
    { "" }[green]{ $description }[white]
event-menu-event-map = Посмотреть карту
event-menu-events = Список событий
event-menu-events-title = { "[" }orange]{ -xcore } — Список событий
event-menu-events-content = { "" }[white]Страница [green]{ $page }[] из [green]{ $total }[]
event-menu-events-empty = События не найдены
event-menu-events-selected = { "[" }green]●[] { $name }
event-menu-create-start = Создать
event-menu-create-start-title = { "[" }orange]{ -xcore } — Создание события
event-menu-create-start-message = Введите название будущего события
event-menu-create-start-default = Событие игрока { $playerName }
event-menu-create-start-map = Создать событие для этой карты
event-menu-edit = Редактировать
event-menu-edit-title = { "[" }orange]{ -xcore } — Редактирование события
event-menu-edit-content =
    { "" }[white]Статистика события [green]{ $name }
    { "" }[white]Автор:[green] { $author }[orange] | [white]Карта:[green] { $mapName }[orange]
    { "" }[white]Крупное?:[green] { $isMajor }[orange] | [white]Временное?:[green] { $isTemporary }[orange]
    { "" }[green]{ $description }[white]
event-menu-edit-name = Название
event-menu-edit-name-title = { "[" }orange]{ -xcore } — Редактирование события
event-menu-edit-name-message = Измените название:
event-menu-edit-description = Описание
event-menu-edit-description-title = { "[" }orange]{ -xcore } — Редактирование события
event-menu-edit-description-message = Измените описание:
event-menu-edit-map = Изменить карту
event-menu-edit-temporary-active = { "[" }green]Временное
event-menu-edit-temporary-inactive = { "[" }gray]Временное
event-menu-edit-major-active = { "[" }green]Крупное
event-menu-edit-major-inactive = { "[" }gray]Крупное
event-menu-edit-planned-start = Начало события
event-menu-edit-planned-start-title = { "[" }orange]{ -xcore } — Редактирование события
event-menu-edit-planned-start-message = Введите время начала в мс или через m/h/d:
event-menu-edit-planned-end = Конец события
event-menu-edit-planned-end-title = { "[" }orange]{ -xcore } — Редактирование события
event-menu-edit-planned-end-message = Введите время окончания в мс или через m/h/d:
event-menu-maps = Карты
event-menu-maps-title = { "[" }orange]{ -xcore } — Выбор карты
event-menu-maps-content = { "" }[white]Страница [green]{ $page }[] из [green]{ $total }[]
vote-event-vote =
    { $nickname }[lightgray] проголосовал за смену текущего события на [orange]{ $name }[lightgray]. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Напишите [orange]y[] или [orange]n[], чтобы проголосовать.
vote-event-left = { $nickname }[lightgray] вышел. Его голос за смену события отменен. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
vote-event-fail = { "[" }lightgray]Голосование не прошло. Недостаточно голосов для смены события на [orange]{ $name }[].
vote-event-success = { "[" }orange]Голосование прошло. Событие [accent]{ $name }[] будет загружено при следующей смене карты.
vote-event-cancelled = { "[" }lightgray]Голосование за смену события на [orange]{ $name }[lightgray] было отменено администратором { $admin }.
event-vote = { "[" }orange]Голосование
event-avote = { "[" }red]Мгновенная смена
event-menu-vote-stop = Остановить голосование
event-menu-stop = Остановить событие
event-menu-this-event = { "[" }orange]Текущее событие
event-end = Событие [green]{ $name }[] завершилось!
# ==============================================================================
# Errors
# ==============================================================================
error-access-denied = { "[" }scarlet]⚠ Доступ запрещен
error-ip-changed = { "[" }scarlet]⚠ Ваш IP адрес изменился. Привилегии администратора были отозваны.
error-not-enough-params = { "[" }scarlet]⚠ Недостаточно параметров
error-player-not-found = { "[" }scarlet]Игрок не найден.
error-player-not-teammate = { "[" }scarlet]⚠ Игрок не в вашей команде
error-player-admin = { "[" }scarlet]⚠ Попытка выгнать администратора ⚠
error-already-voted = { "[" }scarlet]⚠ Вы уже проголосовали.
error-playtime-requirement = { "[" }scarlet]⚠ Вам нужно отыграть минимум { $time } минут для использования этой функции.
error-globalchat-total-playtime = { "[" }scarlet]⚠ Для того чтобы отправить сообщение в глобальный чат, вам необходимо отыграть { $globalChatPlayTime } минут.
error-votekick-total-playtime = { "[" }scarlet]⚠ Для того чтобы проголосовать, вам необходимо отыграть { $votekickPlayTime } минут.
error-vote-yourself = { "[" }scarlet]⚠ Вы не можете голосовать за себя.
error-vote-in-progress = { "[" }scarlet]⚠ Голосование уже идет
error-no-voting = { "[" }scarlet]⚠ На данный момент голосование не проводится.
error-no-map = { "[" }scarlet]⚠ Карта не выбрана.
error-map-not-event = { "[" }scarlet]⚠ Карта не относится к текущему событию.
error-map-not-found = { "[" }scarlet]⚠ Карта не найдена! [accent]Используйте [cyan]/maps[] для просмотра списка всех доступных карт
error-maps-empty = { "[" }scarlet]⚠ Список карт пуст.
error-event-not-found = { "[" }scarlet]⚠ Событие не найдено! [accent]Используйте [cyan]/events[] для просмотра списка доступных событий.
error-page-between = { "[" }scarlet]⚠ 'страница' должна быть числом между[orange] 1[] и [orange]{ $totalPages }[]
error-page-number = { "[" }scarlet]'страница' должна быть числом.
error-wrong-number = { "[" }scarlet]⚠ Неправильный формат числа
error-wrong-period-format = { "[" }scarlet]⚠ Неправильный формат периода. Пример: 1h 30m, 30 ({ hours })
error-invalid-id = { "[" }scarlet]⚠ Некорректный ID игрока.
error-spectator = { "[" }scarlet]⚠ Вы наблюдатель
error-admin-password-too-short = { "[" }scarlet]⚠ Пароль должен быть длиннее 4 символов
error-wrong-admin-password = { "[" }scarlet]⚠ Неправильный пароль
error-internal = { "[" }scarlet]Внутренняя ошибка сервера
error-processing-request = { "[" }scarlet]Произошла ошибка при обработке запроса.
error-team-not-found = { "[" }scarlet]⚠ Команда не найдена.
error-no-access = { "[" }scarlet]⚠ Нет доступа.
error-nickname-too-long = { "[" }scarlet]⚠ Никнейм слишком длинный. Максимум { $max } видимых символов.
error-private-message-invalid-pid = { "[" }scarlet]⚠ Неверный ID игрока. Используйте формат [lightgray]#123[].
error-private-message-self = { "[" }scarlet]⚠ Нельзя отправить личное сообщение самому себе.
error-private-message-empty = { "[" }scarlet]⚠ Сообщение не может быть пустым.
error-private-message-too-long = { "[" }scarlet]⚠ Сообщение слишком длинное. Максимум { $max } символов.
error-private-message-cooldown = { "[" }scarlet]⚠ Подождите { $seconds }с перед отправкой следующего личного сообщения.
error-private-message-target-unavailable = { "[" }scarlet]⚠ Этот игрок сейчас недоступен для личных сообщений.
error-private-message-no-reply-target = { "[" }scarlet]⚠ Некому ответить в личных сообщениях.
error-private-message-not-found = { "[" }scarlet]⚠ Сообщение не найдено.
error-private-message-block-self = { "[" }scarlet]⚠ Нельзя заблокировать самого себя.
error-private-message-block-limit = { "[" }scarlet]⚠ Достигнут лимит блок-листа ({ $limit }).
ban-menu-duration-title = { "[" }orange]{ -xcore } - Длительность бана
ban-menu-duration-message = Введите длительность бана для { $nickname }. Например: 1d, 12h, 30m
ban-menu-reason-title = { "[" }orange]{ -xcore } - Причина бана
ban-menu-reason-message = Введите причину бана для { $nickname }. Оставьте пустым для причины по умолчанию.
ban-menu-confirm-title = { "[" }orange]{ -xcore } - Подтверждение бана
ban-menu-confirm-content =
    { "" }[white]Игрок: { $nickname }[]
    { "" }[white]Длительность: [accent]{ $duration }[]
    { "" }[white]Причина: [accent]{ $reason }[]
ban-menu-confirm-action = { "[" }scarlet]Забанить игрока
error-invalid-syntax = { "[" }scarlet]⚠ Неверный синтаксис команды. Использование: [lightgray]/{ $syntax }
error-invalid-sender = { "[" }scarlet]⚠ Неверный отправитель команды. Требуется: [lightgray]{ $type }
error-argument-parse-generic = { "[" }scarlet]⚠ Ошибка аргумента: { $error }
argument-parse-failure-boolean = { "[" }scarlet]⚠ Не удалось распознать '{ $input }' как логическое значение (true/false).
argument-parse-failure-number = { "[" }scarlet]⚠ Число '{ $input }' вне допустимого диапазона [{ $min }, { $max }].
argument-parse-failure-char = { "[" }scarlet]⚠ '{ $input }' не является допустимым символом.
argument-parse-failure-enum = { "[" }scarlet]⚠ '{ $input }' недопустимая опция. Доступно: [lightgray]{ $acceptableValues }
argument-parse-failure-string = { "[" }scarlet]⚠ Неверный формат строки: '{ $input }'.
argument-parse-failure-uuid = { "[" }scarlet]⚠ Неверный формат UUID: '{ $input }'.
argument-parse-failure-regex = { "[" }scarlet]⚠ Ввод '{ $input }' не соответствует шаблону '{ $pattern }'.
argument-parse-failure-flag-unknown = { "[" }scarlet]⚠ Неизвестный флаг: '{ $flag }'.
argument-parse-failure-flag-duplicate = { "[" }scarlet]⚠ Повторяющийся флаг: '{ $flag }'.
argument-parse-failure-flag-missing-argument = { "[" }scarlet]⚠ Отсутствует аргумент для флага: '{ $flag }'.
argument-parse-failure-flag-no-permission = { "[" }scarlet]⚠ У вас нет прав на использование флага '{ $flag }'.
# ==============================================================================
# Button Status
# ==============================================================================
finished = завершено
finished-neutral = { "[" }orange]Завершено
finished-active = { "[" }green]Завершено
finished-inactive = { "[" }red]Завершено
major = Крупное
major-neutral = { "[" }orange]Крупное
major-active = { "[" }green]Крупное
major-inactive = { "[" }red]Крупное
active = Активно
active-neutral = { "[" }orange]Активно
active-active = { "[" }green]Активно
active-inactive = { "[" }red]Активно
admin = Админ
admin-neutral = { "[" }orange]Админ
admin-active = { "[" }green]Админ
admin-inactive = { "[" }red]Админ
player-leaderboard-active = { "[" }green]Таблица лидеров
player-leaderboard-inactive = { "[" }red]Таблица лидеров
# ==============================================================================
# Miscellaneous
# ==============================================================================
hours = часы
days = дни
success = { "[" }green]Успешно
empty = { "[" }accent]Пусто
never = Никогда
save = Сохранить
close = { "[" }scarlet]Закрыть
previous = { "[" }accent]« Предыдущая
next = { "[" }accent]Следующая »
cancel = Отмена
back = Назад
yes = Да
no = Нет
test = Тест
no-description = Без описания
discord = Discord
github = GitHub
donatello = Donatello
weblate = Weblate
discord-red-vs-blue = RedVSBlue
auto = Авто
on = Включено
off = Выключено
error-command-disabled = { "[" }scarlet]⚠ Команда [accent]/{ $command }[scarlet] отключена на этом сервере.
error-feature-disabled = { "[" }scarlet]⚠ Эта функция отключена на этом сервере.
none = Нет
error-nickname-badge-glyph = { "[" }scarlet]⚠ Пользовательский ник не может содержать зарезервированные иконки бейджей.
