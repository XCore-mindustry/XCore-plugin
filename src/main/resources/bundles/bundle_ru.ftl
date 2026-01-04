commands-help-params = [страница]
commands-help-description = Перечисляет все команды
commands-help-start-content = [orange]-- Страница Команд[lightgray] {$page}[gray]/[lightgray]{$totalPages}[orange] --
commands-help-content = [orange] /{$commandName}[white] {$commandParams}[lightgray] - {$commandDescription}
commands-t-params = <сообщение...>
commands-t-description = Отправить сообщение только своим товарищам по команде
commands-g-params = <сообщение...>
commands-g-description = Отправить сообщение на все сервера
commands-t-chat = [{"#"}{$color}][Команде] [coral]>[accent] {$name}[coral]:[white] {$message}
commands-a-params = <сообщение...>
commands-a-description = Отправить сообщение только администраторам
commands-sync-params = {""}
commands-sync-description = Пересинхронизировать состояние мира
commands-discord-params = {""}
commands-discord-description = Перенаправляет вас на сервер discord
commands-js-params = <код...>
commands-js-description = Выполнить javascript- [scarlet]Тольки игрокам с доступом к JS
commands-js-denied = [scarlet]⚠ Доступ запрещен
commands-artv-params = [карта...]
commands-artv-description = Изменить карту. [scarlet]Только для админов
commands-artv-map-skipped = {$nickname}[accent] пропустил карту
commands-rtv-params = [карта...]
commands-rtv-description = Голосование за изменение карты
commands-stats-params = [id-игрока]
commands-stats-description = Статистика игрока
commands-stats-content = Статистика игрока {$nickname} [grey]#{$pid}
    {""}[brown]Время на сервере: [grey]{$totalPlayTime}[] minutes
    Ранг в MiniHexed: [grey]{$hexedRankTag} {$hexedRankName}
    MiniPvP рейтинг: {$pvpRating}
commands-history-params = [размер] [x] [y]
commands-history-description = Включить/выключить историю блоков
commands-history-success = [accent]История блоков установлена на [scarlet]{0}
commands-lb-params = {""}
commands-lb-description = Включить/выключить таблицу лидеров
commands-lb-success = { $leaderboardEnabled ->
[true] [accent]Таблица лидеров [scarlet]включена
*[other] [accent]Таблица лидеров [scarlet]выключена
    }
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
commands-tr-params = <язык>
commands-tr-description = Установить язык переводчика
commands-tr-success = [accent]Язык переводчика был успешно изменен на [grey]{$translatorLanguage}[]!
commands-tr-off = [accent]Переводчик [scarlet]выключен[]!
commands-tr-not-found = [scarlet]⚠ Нет такого языка
commands-maps-params = [страница]
commands-maps-description = Список всех карт на сервере
commands-ban-params = <id-игрока> <период> [причина...]
commands-ban-description = \ Забанить игрока. [scarlet]Только для админов
commands-ban-success = {$nickname} [scarlet]забанен
commands-unban-params = <id-игрока>
commands-unban-description = Разбанить игрока. [scarlet]Только для админов
commands-unban-success = {$nickname}[accent] #{$pid} успешно разбанен
commands-mute-params=<id-игрока> <период> [причина...]
commands-mute-description = Замьютить игрока. [scarlet]Только для админов
commands-mute-success = [accent]Игрок {$nickname}[accent] успешно замьючен
commands-unmute-params = <id-игрока>
commands-unmute-description = Размутить игрока. [scarlet]Только для админов
commands-unmute-success = Успешно размучено игрока {$nickname}
commands-map-stats-content = {""}[white]Статистика карты для [green]{$mapName}
    {""}[white]Автор:[green] {$mapAuthor}[orange] | [white]Размер:[green] {$mapWidth}x{$mapHeight}[orange]
    {""}[white]Репутация:[green] {$mapReputation}[orange] | [white]Популярность:[green] {$mapPopularity}[orange] | [white]Интерес:[green] {$mapInterest}[orange]
    {""}[white]Сыграно раз:[green] {$mapPlayedTimes}[orange] | [white]Сыграно за год:[green] {$mapPlayedTimesYear}[orange] | [white]Последняя игра:[green] {$mapLastPlayed}[orange]
    {""}[white]Мин. время:[green] {$mapMin}[orange] | [white]Сред. время:[green] {$mapAvg}[orange] | [white]Макс. время:[green] {$mapMax}[orange]
    {""}[green]{$mapDescription}[white]
commands-map-stats-params = [название-карты]
commands-map-stats-description = Статистика конкретной карты
commands-maps-page-must-number = [scarlet]'page' должно быть числом
commands-maps-start-content = [accent]Текущая карта: []{$mapName}[white]
    {""}[orange][gold]Список карт [lightgray]{$page}[gray]/[lightgray]{$pageCount}
commands-maps-content = {""}
    {$index}. [orange] - [white]{$mapName}[orange] | [green]{$mapReputation}[orange] | [white]{$mapWidth}x{$mapHeight}[orange] | [white]{$mapLastPlayed}[orange] | От: [sky]{$mapAuthor}
commands-votekick-params = <ID/никнейм> <причина...>
commands-votekick-description = Голосование за кик игрока.
commands-vote-params = <y/n>
commands-vote-description = Проголосовать за кик текущего игрока
commands-vote-vote-with = [scarlet]⚠ Голосуйте с помощью [orange]/vote <y/n/c>
commands-rank-params = [игрок...]
commands-rank-description = Показывает информацию о вашем ранге/ранге игрока
commands-rank-content = {$nickname}
    {$rankTag} [accent]{$rankName}
    {""}[gold]Побед: {$points}/{$reguirePoints}
commands-ranks-params = {""}
commands-ranks-description = Показывает информацию о рангах
commands-ranks-content = {$rankTag} [accent]{$rankName}
    {""}[gold]Требования: [grey]{$requiredPoints} [accent]побед[]
commands-ranks-footer = Количество побед увеличивается только при победе над игроком вашего ранга или выше.
commands-top-description = Топ-игроки
commands-top-hexed-content = [orange]{$index}. {$nickname}[accent]: [blue]{$rankName} [cyan]{$points} []побед
commands-top-pvp-content = [orange]{$index}. {$nickname}[accent]: [cyan]{$rating}
commands-spectate-params = {""}
commands-spectate-description = Переключить режим наблюдателя. Это очистит вашего юнита.
commands-spectate-success = [green]Теперь вы наблюдаете за игрой
commands-spectate-success2 = [green]Вы больше не наблюдаете за игрой
commands-ai-params = <idle/i/attack/a>
commands-ai-description = Контролировать ИИ
commands-ai-usage = [red]attack(i) []или [accent]idle(i)
rtv-vote = {$nickname}[lightgray] проголосовал за смену текущей карты на [orange]{$mapName}[lightgray]. ([accent]{$votes}[]/[accent]{$votesRequired}[])
    Напишите [orange]y[] или [orange]n[], чтобы проголосовать.
rtv-left = {$nickname}[lightgray] вышел с сервера. Его голос за смену карты был отменен. ([accent]{$votes}[]/[accent]{$votesRequired}[])
rtv-fail = [lightgray]Голосование провалилось. Не хватило голосов, чтобы изменить текущую карту на [orange]{$mapName}[].
rtv-success = [orange]Голосование завершено успешно. Карта [accent]{$mapName}[] будет загружена через [accent]{$mapLoadDelay}[] секунд...
votekick-vote = {$nickname} [grey]#[white]{$nicknameId}[lightgray] проголосовал за кик {$targetNickname} [grey]#[white]{$targetNicknameId}[lightgray] по причине [orange]{$reason}[lightgray]. ([accent]{$votes}[]/[accent]{$votesRequired}[])
    {""}[lightgray]Напиши [orange]/vote <y/n>[], чтобы проголосовать.
votekick-left = {$nickname}[lightgray] вышел с сервера. Его голос за кик игрока был отменен. ([accent]{$votes}[]/[accent]{$votesRequired}[])
votekick-cancelled = [scarlet]Голосование за кик {$nickname}[scarlet] было отменено администратором {$admin}[scarlet]-
votekick-fail = [lightgray]Голосование провалилось. Не хватило голосов, чтобы выгнать {$nickname}[lightgray] с сервера.
votekick-success = [orange]Голосование завершено успешно. {$nickname}[orange] выгнан с сервера на [scarlet]{$minutes}[] минут.
player-joined = {$nickname} [grey]#[white]{$pid}[grey] [accent]присоединился
player-left = {$nickname} [grey]#[white]{$pid}[grey] [accent]вышел
notification-votekick-playtime = [accent]Поздравляем! Вы отыграли [lightgray]{0}[] минут и теперь можете начать голосование за кик игрока.
notification-global-chat-playtime = [accent]Поздравляем! Вы отыграли [lightgray]{0}[] минут и теперь можете писать в глобальный чат
    {""}[lightgray]Введите [accent]/g [gray]<сообщение...>[lightgray], чтобы отправить сообщение.

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
error-page-between = [scarlet]⚠ 'страница' должна быть числом между[orange] 1[] и [orange]{$pageCount}[]
error-page-number = [scarlet]⚠ 'страница' должна быть числом
error-wrong-number = [scarlet]⚠ Неправильный формат числа
error-wrong-period-format = ⚠ Неправильный формат периода. Пример: 1h 30h, 30 ({hours})
error-invalid-id = [scarlet]⚠ Неккоректное player-id
error-spectator = [scarlet]⚠ Вы наблюдатель
error-admin-password-too-short = [scarlet]⚠ Пароль должен быть длиннее 4 символов
error-wrong-admin-password = [scarlet]⚠ Неправильный пароль
hours = часы
days = дни
support-channel = #reports-appeals
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
success = [green]Успешно
empty = [accent]Пусто
leaderboard = [blue]Таблица лидеров
pvp-team-won = Ваша команда победила. Ваш рейтинг увеличился на {$increased}
pvp-team-lose = Ваша команда проиграла. Ваш рейтинг снижен на {$reduced}
pvp-leaderboard-content = [orange]{$index}. {$nickname}[accent]:[cyan] {$rating} [accent]рейтинг
hexed-popup = [blue]{$minutes}:{$seconds}[] до конца игры
hexed-eliminated = {$nickname} [gold]уничтожен!
hexed-leaderboard-content = [orange]{$index}. {$nickname}[accent]: [cyan]{$hexes} [accent]хексов
hexed-ranks-newbie = Новичок
hexed-ranks-regular = Базовый
hexed-ranks-advanced = Продвинутый
hexed-ranks-veteran = Ветеран
hexed-ranks-davastator = Разрушитель
hexed-ranks-the_legend = Легенда
pvp-you-spectator = [scarlet]Ты проиграл. Подождите следующей игры.
kick-pirated-game = [accent]Играть на пиратской версии игры запрещено. Установите официальную версию игры из [blue]Play Market[] или [blue]https://anuke-itch-io/mindustry-
kick-recently-kicked = [accent]Вы были недавно кикнуты с этого сервера. Подождите [cyan]{$remainMinutes}:{$remainSeconds}[accent]
welcome = [accent]Добро пожаловать в {$serverName}!
    {""}[lightgray]Введите [accent]/help[lightgray], чтобы увидеть список команд
    {""}[lightgray]Введите [accent]/vote [gray]<y/n>[lightgray], чтобы проголосовать за наказание игрока
    {""}[lightgray]Введите [accent]/votekick [gray]<ID/имя> <причина...>[lightgray], чтобы начать голосование-кик
    {""}[lightgray]Введите [accent]/t [gray]<сообщение...>[lightgray], чтобы отправить сообщение своим союзникам
    {""}[lightgray]Введите [accent]/g [gray]<сообщение...>[lightgray], чтобы отправить сообщение всем серверам
    {""}[lightgray]Введите [accent]/tr [gray]<язык/auto>[lightgray], чтобы включить переводчик
    {""}[lightgray]Введите [accent]/discord[lightgray], чтобы перейти наш сервер discord
commands-like-params = {""}
commands-like-description = Проголосовать за карту (повышает репутацию)
commands-like-success = [green]Вы лайкнули эту карту!
commands-like-changed = [green]Вы изменили свое мнение на Лайк!
commands-dislike-params = {""}
commands-dislike-description = Проголосовать против карты
commands-dislike-success = [orange]Вы поставили дизлайк этой карте.
commands-dislike-changed = [orange]Вы изменили свое мнение на Дизлайк.
never = Никогда
map-vote-title = [scarlet]ИГРА ОКОНЧЕНА!
map-vote-content = {""}
    {""}Следующая карта: [accent]{$mapName}[] от [accent]{$author}[white].
    {""}Новая игра начнется через [accent]{$seconds}[white] секунд.
    {""}
    {""}[cyan]Понравилась эта карта?
map-vote-like = [green]👍 Нравится
map-vote-dislike = [red]👎 Не нравится
map-vote-like-selected = [gray]Вам нравится
map-vote-dislike-selected = [gray]Вам не нравится
map-vote-close = Закрыть