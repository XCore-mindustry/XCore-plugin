# ==============================================================================
# General & Help
# ==============================================================================
commands-help-params = [страница]
commands-help-description = Перечисляет все команды
commands-help-start-content = [orange]-- Страница Команд[lightgray] {$page}[gray]/[lightgray]{$totalPages}[orange] --
commands-help-content = [orange] /{$commandName}[white] {$commandParams}[lightgray] - {$commandDescription}

commands-information-params = ${""}
commands-information-description = Показать информацию о сервере
commands-info-title = [orange]XCore сервер — {$xcorServerName}
commands-info-text = [accent]XCore[white] — это [cyan]бесплатный[white] сервер для игры в [accent]Mindustry[white].
    {""}
    {""}Версия XCore — [accent]{$xcoreVersion}[white]

commands-sync-params = {""}
commands-sync-description = Пересинхронизировать состояние мира

commands-discord-params = {""}
commands-discord-description = Перенаправляет вас на сервер discord

welcome = [accent]Добро пожаловать в {$serverName}!
    {""}[lightgray]Введите [accent]/help[lightgray], чтобы увидеть список команд
    {""}[lightgray]Введите [accent]/vote [gray]<y/n>[lightgray], чтобы проголосовать за наказание игрока
    {""}[lightgray]Введите [accent]/votekick [gray]<ID/имя> <причина...>[lightgray], чтобы начать голосование-кик
    {""}[lightgray]Введите [accent]/t [gray]<сообщение...>[lightgray], чтобы отправить сообщение своим союзникам
    {""}[lightgray]Введите [accent]/g [gray]<сообщение...>[lightgray], чтобы отправить сообщение всем серверам
    {""}[lightgray]Введите [accent]/tr [gray]<язык/auto>[lightgray], чтобы включить переводчик
    {""}[lightgray]Введите [accent]/discord[lightgray], чтобы перейти наш сервер discord

# ==============================================================================
# Chat & Social
# ==============================================================================
commands-t-params = <сообщение...>
commands-t-description = Отправить сообщение только своим товарищам по команде
commands-t-chat = [{"#"}{$color}][Команде] [coral]>[accent] {$name}[coral]:[white] {$message}

commands-g-params = <сообщение...>
commands-g-description = Отправить сообщение на все сервера

commands-a-params = <сообщение...>
commands-a-description = Отправить сообщение только администраторам

commands-tr-params = <язык>
commands-tr-description = Установить язык переводчика
commands-tr-success = [accent]Язык переводчика был успешно изменен на [grey]{$translatorLanguage}[]!
commands-tr-off = [accent]Переводчик [scarlet]выключен[]!
commands-tr-not-found = [scarlet]⚠ Нет такого языка

chat-discord-format = [blue][Discord][] {$author}: {$message}
chat-global-format = [royal][[[orange]GLOBAL [lightgray](из [accent]{$server}[])[] {$author}[]]: [white]{$message}

# ==============================================================================
# Authentication & Admin Access
# ==============================================================================
commands-login-params = <пароль>
commands-login-description = Запрос на права админа. Не используйте, если не знаете, что делаете
commands-login-incorrect-password = [scarlet]⚠ Некорректный пароль!
commands-login-success = [green]Права админа получены
commands-login-confirmed = [green]Права админа подтверждены
commands-login-admin-password-created = [green]Пароль админа создан
    {""}[red]Не забудьте свой пароль! Если Вы его забудете, Вам придется обратиться к главному администратору с просьбой сбросить его.
commands-login-request-approval-discord = [accent]Запрос на права админа. [lightgray]Подтвердите его в канале [orange]#admin-bots[] на нашем сервере discord.

commands-logout-params = {""}
commands-logout-description = Лишить себя прав администратора
commands-logout-successful = [green]Вы лишены прав администратора

# ==============================================================================
# Moderation (Ban, Mute, Kick)
# ==============================================================================
commands-ban-params = <id-игрока> <период> [причина...]
commands-ban-description = Забанить игрока. [scarlet]Только для админов
commands-ban-success = {$nickname} [scarlet]забанен

commands-unban-params = <id-игрока>
commands-unban-description = Разбанить игрока. [scarlet]Только для админов
commands-unban-success = {$nickname}[accent] #{$pid} успешно разбанен

commands-mute-params = <id-игрока> <период> [причина...]
commands-mute-description = Замьютить игрока. [scarlet]Только для админов
commands-mute-success = [accent]Игрок {$nickname}[accent] успешно замьючен

commands-unmute-params = <id-игрока>
commands-unmute-description = Размутить игрока. [scarlet]Только для админов
commands-unmute-success = Успешно размучено игрока {$nickname}

ban-content = {$nickname} забанен
  Для снятия бана посетите дискорд(канал [gray]{support-channel}[]):
  {""}[cyan]{$discordUrl}
ban-cancelled = [accent]Бан игрока {$nickname}[accent] был отменен

tempban-content = {$nickname}[accent] забанен.
    Админ: {$adminName}[accent]
    Причина: "[gold]{$reason}[]"
    Вы будете разбанены через: {$days} дней, {$hours} часов и {$minutes} минут
    Для снятия бана посетите дискорд(канал [gray]{support-channel}]):
    {""}[cyan]{$discordUrl}
tempban-player-banned = [scarlet] Админ {$adminName}[scarlet] забанил игрока [gray]'[]{$playerName}[gray]'

you-are-muted-by = [scarlet]Вы были заглушены администратором [accent]{$adminName}[blue] на {$remainMinutes}:{$remainSeconds} минут.
    Причина: {$reason}
you-are-muted = [scarlet]Вы не можете писать в чат. Вы заглушены администратором [accent]{$adminName}[accent] на {$remainMinutes}:{$remainSeconds} минут.
    Причина: {$reason}

kick-pirated-game = [accent]Вход с неофициальных клиентов [scarlet]запрещен[]. Пожалуйста, используйте [lime]официальную[] версию игры (Steam, Google Play, itch.io).
kick-recently-kicked = [accent]Вы были недавно кикнуты с этого сервера. Подождите [cyan]{$remainMinutes}:{$remainSeconds}[accent]
kick-bot-protection = Возможно вы бот. Если нет, попробуйте перезайти.
kick-admintools-outdated = [green]Требуемая версия AdminTools: [grey]{$requiredVersion}[]
    {""}[scarlet]Ваша версия AdminTools: [grey]{$version}[]
    {""}
    {""}[cyan]Пожалуйста, обновите AdminTools для входа на сервер.

support-channel = #reports-appeals

# ==============================================================================
# Voting (VoteKick)
# ==============================================================================
commands-votekick-params = <ID/имя> <причина...>
commands-votekick-description = Голосование за кик игрока.

commands-vote-params = <y/n>
commands-vote-description = Проголосовать за кик текущего игрока
commands-vote-vote-with = [scarlet]⚠ Голосуйте с помощью [orange]/vote <y/n/c>

votekick-vote = {$starter} [grey]#[white]{$starterId}[lightgray] хочет выгнать {$target} [grey]#[white]{$targetId}[lightgray]. Причина: [orange]{$reason}[lightgray]. ([accent]{$votes}[]/[accent]{$required}[])
    {""}[lightgray]Напишите [orange]/vote <y/n>[], чтобы проголосовать.
votekick-left = {$player}[lightgray] покинул игру. Голос аннулирован. ([accent]{$votes}[]/[accent]{$required}[])
votekick-fail = [lightgray]Голосование не состоялось. Недостаточно голосов для изгнания {$target}[lightgray].
votekick-cancelled = [scarlet]Голосование за кик {$target}[scarlet] отменено администратором {$admin}[scarlet].
votekick-success = [orange]Голосование успешно. {$target}[orange] изгнан на [scarlet]{$minutes}[] { $minutes ->
    [one] минуту
    [few] минуты
    *[many] минут
    }.
# ==============================================================================
# Maps & RTV
# ==============================================================================
commands-map-params = [название-карты]
commands-map-description = Статистика конкретной карты
commands-map-title = [orange]XCore сервер — Статистика
commands-map-content = {""}[white]Статистика карты [green]{$name}
    {""}[white]Автор:[green] {$author}[orange] | [white]Размер:[green] {$width}x{$height}[orange]
    {""}[white]Репутация:[green] {$reputation}[orange] | [white]Популярность:[green] {$popularity}[orange] | [white]Интерес:[green] {$interest}[orange]
    {""}[white]Сыграно раз:[green] {$played}[orange] | [white]Сыграно за год:[green] {$playedYear}[orange] | [white]Последняя игра:[green] {$lastPlayed}[orange]
    {""}[white]Мин. время:[green] {$min}[orange] | [white]Ср. время:[green] {$avg}[orange] | [white]Макс. время:[green] {$max}[orange]
    {""}[green]{$desc}[white]

commands-maps-params = [страница]
commands-maps-description = Список всех карт на этом сервере.
commands-maps-title = [orange]XCore сервер — Список карт
commands-maps-content = {""}[white]Страница [green]{$page}[] из [green]{$total}[]
commands-maps-page-must-number = [scarlet]'страница' должна быть числом

commands-maps-text-params = [страница]
commands-maps-text-description = Список всех карт на этом сервере.
commands-maps-text-start-content = [accent]Текущая карта: []{$name}[white]
    {""}[orange][gold]Список карт [lightgray]{$page}[gray]/[lightgray]{$total}
commands-maps-text-content = {""}
    {$index}. [orange] - [white]{$name}[orange] | [green]{$reputation}[orange] | [white]{$width}x{$height}[orange] | [white]{$lastPlayed}[orange] | От: [sky]{$author}

commands-artv-params = [карта...]
commands-artv-description = Изменить карту. [scarlet]Только для админов
commands-artv-map-skipped = {$nickname}[accent] пропустил карту

commands-rtv-params = [карта...]
commands-rtv-description = Голосование за изменение карты

commands-like-params = {""}
commands-like-description = Проголосовать за карту (повышает репутацию)
commands-like-success = [green]Вы лайкнули эту карту!
commands-like-changed = [green]Вы изменили свое мнение на Лайк!

commands-dislike-params = {""}
commands-dislike-description = Проголосовать против карты
commands-dislike-success = [orange]Вы поставили дизлайк этой карте.
commands-dislike-changed = [orange]Вы изменили свое мнение на Дизлайк.

map-vote-title = [orange]XCore сервер — [scarlet]ИГРА ОКОНЧЕНА!
map-vote-content = {""}
    {""}Следующая карта: [accent]{$mapName}[] от [accent]{$author}[white].
    {""}Новая игра начнется через [accent]{$seconds}[white] секунд.
    {""}
    {""}[cyan]Понравилась эта карта?
map-vote-like = [green]👍 Нравится
map-vote-dislike = [red]👎 Не нравится
map-vote-like-selected = [gray]Вам нравится
map-vote-dislike-selected = [gray]Вам не нравится
map-rtv = [orange]Голосование
map-artv = [red]Мгновенная смена
map-maps = Карты

rtv-vote = {$nickname}[lightgray] проголосовал за смену текущей карты на [orange]{$mapName}[lightgray]. ([accent]{$votes}[]/[accent]{$votesRequired}[])
    Напишите [orange]y[] или [orange]n[], чтобы проголосовать.
rtv-left = {$nickname}[lightgray] покинул игру. Голос за смену карты аннулирован. ([accent]{$votes}[]/[accent]{$votesRequired}[])
rtv-fail = [lightgray]Голосование провалилось. Не хватило голосов, чтобы изменить текущую карту на [orange]{$mapName}[].
rtv-success = [orange]Голосование завершено успешно. Карта [accent]{$mapName}[] будет загружена через [accent]{$mapLoadDelay}[] секунд...

# ==============================================================================
# Statistics & Ranks
# ==============================================================================
commands-stats-params = [id-игрока]
commands-stats-description = Статистика игрока
commands-stats-content = Статистика игрока {$nickname} [grey]#{$pid}
    {""}[brown]Время на сервере: [grey]{$totalPlayTime}[] minutes
    Ранг в MiniHexed: [grey]{$hexedRankTag} {$hexedRankName}
    MiniPvP рейтинг: {$pvpRating}

commands-lb-params = {""}
commands-lb-description = Включить/выключить таблицу лидеров
commands-lb-success = { $leaderboardEnabled ->
    [true] [accent]Таблица лидеров [scarlet]включена
    *[other] [accent]Таблица лидеров [scarlet]выключена
    }
leaderboard = [blue]Таблица лидеров

commands-rank-params = [игрок...]
commands-rank-description = Показывает информацию о вашем ранге/ранге игрока
commands-rank-content = {$nickname}
    {$rankTag} [accent]{$rankName}
    {""}[gold]Побед: {$points}/{$requiredPoints}

commands-ranks-params = {""}
commands-ranks-description = Показывает информацию о рангах
commands-ranks-content = {$rankTag} [accent]{$rankName}
    {""}[gold]Требования: [grey]{$requiredPoints} [accent]побед[]
commands-ranks-footer = Количество побед увеличивается только при победе над игроком вашего ранга или выше.

commands-top-description = Топ-игроки
commands-top-hexed-content = [orange]{$index}. {$nickname}[accent]: [blue]{$rankName} [cyan]{$points} []побед
commands-top-pvp-content = [orange]{$index}. {$nickname}[accent]: [cyan]{$rating}

# ==============================================================================
# Game Modes (Hexed, PvP, Spectate, AI)
# ==============================================================================
commands-spectate-params = {""}
commands-spectate-description = Переключить режим наблюдателя. Это очистит вашего юнита.
commands-spectate-success = [green]Теперь вы наблюдаете за игрой

commands-ai-params = <idle/i/attack/a>
commands-ai-description = Контролировать ИИ
commands-ai-usage = [red]attack(i) []или [accent]idle(i)

commands-history-params = [размер] [x] [y]
commands-history-description = Включить/выключить историю блоков
commands-history-success = [accent]История блоков установлена на [scarlet]{0}

hexed-popup = [blue]{$minutes}:{$seconds}[] до конца игры
hexed-eliminated = {$nickname} [gold]уничтожен!
hexed-leaderboard-content = [orange]{$index}. {$nickname}[accent]: [cyan]{$hexes} [accent]хексов
hexed-ranks-newbie = Новичок
hexed-ranks-regular = Базовый
hexed-ranks-advanced = Продвинутый
hexed-ranks-veteran = Ветеран
hexed-ranks-davastator = Разрушитель
hexed-ranks-the_legend = Легенда

hexed-game-over-header = Игра окончена. Победители:
hexed-game-over-winner-row = [orange]{$index}. {$name}[][accent]: [cyan]{$cores} { $cores ->
    [one] гекс
    [few] гекса
    *[many] гексов
    }
hexed-game-over-no-winners = Игра окончена. К сожалению, победители не найдены.
hexed-game-over-restart = Новая игра через 10 секунд...

pvp-team-won = Ваша команда победила. Ваш рейтинг увеличился на {$increased}
pvp-team-lose = Ваша команда проиграла. Ваш рейтинг снижен на {$reduced}
pvp-leaderboard-content = [orange]{$index}. {$nickname}[accent]:[cyan] {$rating} [accent]рейтинг
pvp-you-spectator = [scarlet]Ты проиграл. Подождите следующей игры.

# ==============================================================================
# Events & Notifications
# ==============================================================================
player-joined = {$nickname} [grey]#[white]{$pid}[grey] [accent]присоединился
player-left = {$nickname} [grey]#[white]{$pid}[grey] [accent]вышел

notification-votekick-playtime = [accent]Поздравляем! Вы отыграли [lightgray]{0}[] минут и теперь можете начать голосование за кик игрока.
notification-global-chat-playtime = [accent]Поздравляем! Вы отыграли [lightgray]{0}[] минут и теперь можете писать в глобальный чат
    {""}[lightgray]Введите [accent]/g [gray]<сообщение...>[lightgray], чтобы отправить сообщение.
notification-admin-kick = {$admin}[accent] кикнул {$target}[].
notification-admin-wave-skip = {$admin}[accent] пропустил волну.

notification-server-restart = Перезагрузка через {$seconds}

# ==============================================================================
# Errors
# ==============================================================================
error-access-denied = [scarlet]⚠ Доступ запрещен
error-ip-changed = [scarlet]⚠ Ваш IP адрес изменился. Привилегии администратора были отозваны.
error-not-enough-params = [scarlet]⚠ Недостаточно параметров
error-player-not-found = [scarlet]⚠ Игрок не найден
error-player-not-teammate = [scarlet]⚠ Игрок не в вашей команде
error-player-admin = [scarlet]⚠ Попытка выгнать администратора ⚠
error-already-voted = [scarlet]⚠ Вы уже проголосовали.
error-globalchat-total-playtime = [scarlet]⚠ Для того чтобы отправить сообщение в глобальный чат, вам необходимо отыграть {$globalChatPlayTime} минут.
error-votekick-total-playtime = [scarlet]⚠ Для того чтобы проголосовать, вам необходимо отыграть {$votekickPlayTime} минут.
error-vote-yourself = [scarlet]⚠ Вы не можете голосовать за себя.
error-vote-in-progress = [scarlet]⚠ Голосование уже идет
error-no-voting = [scarlet]⚠ На данный момент голосование не проводится.
error-map-not-found = [scarlet]⚠ Карта не найдена! [accent]Используйте [cyan]/maps[] для просмотра списка всех доступных карт
error-page-between = [scarlet]⚠ 'страница' должна быть числом между[orange] 1[] и [orange]{$totalPages}[]
error-page-number = [scarlet]⚠ 'страница' должна быть числом
error-wrong-number = [scarlet]⚠ Неправильный формат числа
error-wrong-period-format = ⚠ Неправильный формат периода. Пример: 1h 30h, 30 ({hours})
error-invalid-id = [scarlet]⚠ Неккоректное player-id
error-spectator = [scarlet]⚠ Вы наблюдатель
error-admin-password-too-short = [scarlet]⚠ Пароль должен быть длиннее 4 символов
error-wrong-admin-password = [scarlet]⚠ Неправильный пароль
error-internal = [scarlet]Внутренняя ошибка сервера
error-processing-request = [scarlet]Произошла ошибка при обработке запроса.


# ==============================================================================
# Miscellaneous
# ==============================================================================
hours = часы
days = дни

success = [green]Успешно
empty = [accent]Пусто
never = Никогда

close = Закрыть
previous = <- Предыдущая
next = Следующая ->