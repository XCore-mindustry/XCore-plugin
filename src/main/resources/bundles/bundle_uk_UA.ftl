# ==============================================================================
# General & Help
# ==============================================================================
commands-help-params = [сторінка]
commands-help-description = Показує список усіх команд.
commands-help-start-content = [orange]-- Сторінка Команд[lightgray] {$page}[gray]/[lightgray]{$totalPages}[orange] --
commands-help-content = [orange] /{$commandName}[white] {$commandParams}[lightgray] - {$commandDescription}

commands-information-params = ${""}
commands-information-description = Показати інформацію про сервер
commands-info-title = [orange]XCore сервер — {$xcorServerName}
commands-info-text = [accent]XCore[white] це [cyan]безкоштовний[white] сервер для гри у [accent]Mindustry[white].
    {""}
    {""}Версія XCore — [accent]{$xcoreVersion}[white]

commands-sync-params = {""}
commands-sync-description = Синхронізувати гру з сервером. Використовуйте це для виправлення помилок (наприклад, фантомних одиниць).

commands-discord-params = {""}
commands-discord-description = Перенаправляє вас на наш Discord сервер.

welcome = [accent]Ласкаво просимо на {$serverName}!
    {""}[lightgray]Напишіть [accent]/help[lightgray], щоб побачити список команд
    {""}[lightgray]Напишіть [accent]/vote [gray]<y/n>[lightgray], щоб проголосувати за вигнання гравця
    {""}[lightgray]Напишіть [accent]/votekick [gray]<ID/ім'я> <причина...>[lightgray], щоб почати голосування за вигнання
    {""}[lightgray]Напишіть [accent]/t [gray]<повідомлення...>[lightgray], щоб надіслати повідомлення союзникам
    {""}[lightgray]Напишіть [accent]/g [gray]<повідомлення...>[lightgray], щоб надіслати повідомлення на всі сервери
    {""}[lightgray]Напишіть [accent]/tr [gray]<мова/auto>[lightgray], щоб увімкнути перекладач
    {""}[lightgray]Напишіть [accent]/discord[lightgray], щоб перейти на наш Discord сервер

# ==============================================================================
# Chat & Social
# ==============================================================================
commands-t-params = <повідомлення...>
commands-t-description = Надіслати повідомлення тільки своїм союзникам по команді.
commands-t-chat = [{"#"}{$color}][Команді] [coral]>[accent] {$name}[coral]:[white] {$message}

commands-g-params = <повідомлення...>
commands-g-description = Надіслати повідомлення на всі сервери.

commands-a-params = <повідомлення...>
commands-a-description = Надіслати повідомлення тільки адміністраторам.

commands-tr-params = <мова>
commands-tr-description = Встановити мову перекладача.
commands-tr-success = [accent]Мову перекладача успішно змінено на [grey]{$translatorLanguage}[]!
commands-tr-off = [accent]Перекладач [scarlet]вимкнено[]!
commands-tr-not-found = [scarlet]⚠ Такої мови не існує.

chat-discord-format = [blue][Discord][] {$author}: {$message}
chat-global-format = [royal][[[orange]GLOBAL [lightgray](з [accent]{$server}[])[] {$author}[]]: [white]{$message}

# ==============================================================================
# Authentication & Admin Access
# ==============================================================================
commands-login-params = <пароль>
commands-login-description = Запит на права адміністратора. Не використовуйте, якщо не знаєте, що робите.
commands-login-incorrect-password = [scarlet]⚠ Невірний пароль!
commands-login-success = [green]Права адміністратора отримано.
commands-login-confirmed = [green]Права адміністратора підтверджено.
commands-login-admin-password-created = [green]Пароль адміністратора створено.
    {""}[red]Не забудьте свій пароль! Якщо ви його забудете, вам доведеться просити головного адміністратора скинути його.
commands-login-request-approval-discord = [accent]Вам потрібно підтвердити запит на права адміністратора в каналі [orange]#admin-bots[] на нашому Discord сервері.

commands-logout-params = {""}
commands-logout-description = Вийти з адмін-панелі. Це [scarlet]відкличе ваші права адміністратора.
commands-logout-successful = [green]Права адміністратора відкликано.

# ==============================================================================
# Moderation (Ban, Mute, Kick)
# ==============================================================================
commands-ban-params = <id-гравця> <період> [причина...]
commands-ban-description = Заблокувати гравця. [scarlet]Тільки для адміністраторів.
commands-ban-success = {$nickname} [scarlet]заблокований

commands-unban-params = <id-гравця>
commands-unban-description = Розблокувати гравця. [scarlet]Тільки для адміністраторів.
commands-unban-success = {$nickname}[accent] #{$pid} [green]успішно розблокований.

commands-mute-params = <id-гравця> <період> [причина...]
commands-mute-description = Заглушити гравця. [scarlet]Тільки для адміністраторів.
commands-mute-success = [accent]Успішно заглушено гравця {$nickname}

commands-unmute-params = <id-гравця>
commands-unmute-description = Зняти заглушення з гравця. [scarlet]Тільки для адміністраторів.
commands-unmute-success = [green]Успішно знято заглушення з гравця []{$nickname}

ban-content = {$nickname} [accent]був [scarlet]заблокований[].
    Щоб оскаржити блокування, відвідайте Discord (канал [gray]{support-channel}[]):
    {""}[cyan]{$discordUrl}
ban-cancelled = [accent]Блокування гравця [scarlet]{$nickname}[accent] було скасовано

tempban-content = {$nickname}[accent] був заблокований.
    Адміністратор: {$adminName}[accent]
    Причина: "[gold]{$reason}[]"
    Ви будете розблоковані через: {$days} днів, {$hours} годин та {$minutes} хвилин
    Щоб оскаржити блокування, відвідайте Discord (канал [gray]{support-channel}[]):
    {""}[cyan]{$discordUrl}
tempban-player-banned = [scarlet] Адміністратор {$adminName}[scarlet] заблокував гравця [gray]'[]{$playerName}[gray]'

you-are-muted-by = [scarlet]Ви були заглушені адміністратором [accent]{$adminName}[blue] на {$remainMinutes}:{$remainSeconds} хвилин,
    причина: {$reason}
you-are-muted = [scarlet]Ви не можете писати в чат. [accent]Ви були заглушені адміністратором {$adminName}[blue] на {$remainMinutes}:{$remainSeconds} хвилин,
    причина: {$reason}

kick-pirated-game = [accent]Вхід з неофіційних клієнтів [scarlet]заборонено[]. Будь ласка, використовуйте [lime]офіційну[] версію гри (Steam, Google Play, itch.io).
kick-recently-kicked = [accent]Ви були нещодавно вигнані з цього сервера.
    Зачекайте [cyan]{$remainMinutes}:{$remainSeconds}[accent] перед повторним входом.
kick-bot-protection = Можливо ви бот. Якщо ні, спробуйте перезайти.
kick-admintools-outdated = [green]Необхідна версія AdminTools: [grey]1.3[]
    {""}[scarlet]Ваша версія AdminTools: [grey]{$version}[]
    {""}
    {""}[cyan]Будь ласка, оновіть AdminTools для входу на сервер.

support-channel = #reports-appeals

# ==============================================================================
# Voting (VoteKick)
# ==============================================================================
commands-votekick-params = <ID/ім'я> <причина...>
commands-votekick-description = Голосування за вигнання гравця з сервера.

commands-vote-params = <y/n>
commands-vote-description = Проголосувати у поточному голосуванні.
commands-vote-vote-with = [scarlet]⚠ Голосуйте за допомогою [orange]/vote <y/n/c>

votekick-vote = {$starter} [grey]#[white]{$starterId}[lightgray] хоче вигнати {$target} [grey]#[white]{$targetId}[lightgray]. Причина: [orange]{$reason}[lightgray]. ([accent]{$votes}[]/[accent]{$required}[])
    {""}[lightgray]Напишіть [orange]/vote <y/n>[], щоб проголосувати.
votekick-left = {$player}[lightgray] покинув гру. Голос анульовано. ([accent]{$votes}[]/[accent]{$required}[])
votekick-fail = [lightgray]Голосування не відбулося. Недостатньо голосів для вигнання {$target}[lightgray].
votekick-cancelled = [scarlet]Голосування за вигнання {$target}[scarlet] скасовано адміністратором {$admin}.
votekick-success = [orange]Голосування успішне. {$target}[orange] вигнаний на [scarlet]{$minutes}[] { $minutes ->
    [one] хвилину
    [few] хвилини
    *[many] хвилин
    }.

# ==============================================================================
# Maps & RTV
# ==============================================================================
commands-map-params = [назва-мапи]
commands-map-description = Статистика конкретної мапи
commands-map-title = [orange]XCore сервер — Статистика
commands-map-content = {""}[white]Статистика мапи [green]{$name}
    {""}[white]Автор:[green] {$author}[orange] | [white]Розмір:[green] {$width}x{$height}[orange]
    {""}[white]Репутація:[green] {$reputation}[orange] | [white]Популярність:[green] {$popularity}[orange] | [white]Інтерес:[green] {$interest}[orange]
    {""}[white]Зіграно разів:[green] {$played}[orange] | [white]Зіграно за рік:[green] {$playedYear}[orange] | [white]Остання гра:[green] {$lastPlayed}[orange]
    {""}[white]Мін. час:[green] {$min}[orange] | [white]Сер. час:[green] {$avg}[orange] | [white]Макс. час:[green] {$max}[orange]
    {""}[green]{$desc}[white]

commands-maps-params = [сторінка]
commands-maps-description = Список усіх мап на цьому сервері.
commands-maps-title = [orange]XCore сервер — Список мап
commands-maps-content = {""}[white]Сторінка [green]{$page}[] з [green]{$total}[]
commands-maps-page-must-number = [scarlet]'сторінка' має бути числом

commands-maps-text-params = [сторінка]
commands-maps-text-description = Список усіх мап на цьому сервері.
commands-maps-text-start-content = [accent]Поточна мапа: []{$name}[white]
    {""}[orange][gold]Список мап [lightgray]{$page}[gray]/[lightgray]{$total}
commands-maps-text-content = {""}
    {$index}. [orange] - [white]{$name}[orange] | [green]{$reputation}[orange] | [white]{$width}x{$height}[orange] | [white]{$lastPlayed}[orange] | Від: [sky]{$author}

commands-artv-params = [мапа...]
commands-artv-description = Примусово змінити мапу. [scarlet]Тільки для адміністраторів.
commands-artv-map-skipped = {$nickname}[accent] пропустив мапу.

commands-rtv-params = [мапа...]
commands-rtv-description = Голосування за зміну мапи (Rock the vote).

commands-like-params = {""}
commands-like-description = Проголосувати за мапу (підвищує репутацію)
commands-like-success = [green]Ви вподобали цю мапу!
commands-like-changed = [green]Ви змінили свою думку на Вподобайку!

commands-dislike-params = {""}
commands-dislike-description = Проголосувати проти мапи
commands-dislike-success = [orange]Ви поставили "Не подобається" цій мапі.
commands-dislike-changed = [orange]Ви змінили свою думку на "Не подобається".

map-vote-title = [orange]XCore сервер — [scarlet]ГРУ ЗАКІНЧЕНО!
map-vote-content = {""}
    {""}Наступна мапа: [accent]{$mapName}[] від [accent]{$author}[white].
    {""}Нова гра почнеться через [accent]{$seconds}[white] секунд.
    {""}
    {""}[cyan]Чи сподобалась ця мапа?
map-vote-like = [green]👍 Подобається
map-vote-dislike = [red]👎 Не подобається
map-vote-like-selected = [gray]Вам вже подобається
map-vote-dislike-selected = [gray]Вам вже не подобається
map-rtv = [orange]Голосування
map-artv = [red]Миттєва зміна
map-maps = Мапи

rtv-vote = {$nickname}[lightgray] проголосував за зміну поточної мапи на [orange]{$mapName}[lightgray]. ([accent]{$votes}[]/[accent]{$votesRequired}[])
    Напишіть [orange]y[] або [orange]n[], щоб проголосувати.
rtv-left = {$nickname}[lightgray] вийшов. Його голос за зміну мапи скасовано. ([accent]{$votes}[]/[accent]{$votesRequired}[])
rtv-fail = [lightgray]Голосування не пройшло. Недостатньо голосів для зміни мапи на [orange]{$mapName}[].
rtv-success = [orange]Голосування пройшло. Мапа [accent]{$mapName}[] буде завантажена через [accent]{$mapLoadDelay}[] секунд...

# ==============================================================================
# Statistics & Ranks
# ==============================================================================
commands-stats-params = [id-гравця]
commands-stats-description = Переглянути статистику гравця.
commands-stats-content = Статистика гравця {$nickname} [grey]#{$pid}
    {""}[brown]Час у грі: [grey]{$totalPlayTime}[] хвилин
    Ранг у Hexed: [grey]{$hexedRankTag} {$hexedRankName}
    Рейтинг MiniPvP: {$pvpRating}

commands-lb-params = {""}
commands-lb-description = Увімкнути/вимкнути таблицю лідерів.
commands-lb-success = { $leaderboardEnabled ->
    [true] [accent]Таблиця лідерів [green]увімкнена
    *[other] [accent]Таблиця лідерів [scarlet]вимкнена
    }
leaderboard = [blue]Таблиця лідерів

commands-rank-params = [гравець...]
commands-rank-description = Показує інформацію про ваш ранг або ранг іншого гравця.
commands-rank-content = {$nickname}
    {$rankTag} [accent]{$rankName}
    {""}[gold]Перемог: {$points}/{$requiredPoints}

commands-ranks-params = {""}
commands-ranks-description = Показує інформацію про систему рангів.
commands-ranks-content = {$rankTag} [accent]{$rankName}
    {""}[gold]Вимоги: [grey]{$requiredPoints} [accent]перемог[]
commands-ranks-footer = Кількість перемог збільшується лише при перемозі над гравцем вашого рангу або вище.

commands-top-params = {""}
commands-top-description = Топ гравців.
commands-top-hexed-content = [orange]{$index}. {$nickname}[accent]: [blue]{$rankName} [cyan]{$points} []перемог
commands-top-pvp-content = [orange]{$index}. {$nickname}[accent]: [cyan]{$rating}

# ==============================================================================
# Game Modes (Hexed, PvP, Spectate, AI)
# ==============================================================================
commands-spectate-params = {""}
commands-spectate-description = Перейти в режим спостерігача. Це видалить вашу одиницю.
commands-spectate-success = [green]Тепер ви спостерігаєте за грою

commands-ai-params = <idle/i/attack/a>
commands-ai-description = Керування ШІ (AI)
commands-ai-usage = [red]attack(i) []або [accent]idle(i)

commands-history-params = [розмір] [x] [y]
commands-history-description = Увімкнути/вимкнути історію блоків.
commands-history-success = [accent]Історію блоків встановлено на [scarlet]{0}

hexed-popup = [blue]{$minutes}:{$seconds}[] до кінця гри.
hexed-eliminated = {$nickname} [gold]був [scarlet]знищений[]!
hexed-leaderboard-content = [orange]{$index}. {$nickname}[accent]: [cyan]{$hexes} [accent]гексів
hexed-ranks-newbie = Новачок
hexed-ranks-regular = Звичайний
hexed-ranks-advanced = Досвідчений
hexed-ranks-veteran = Ветеран
hexed-ranks-davastator = Руйнівник
hexed-ranks-the_legend = Легенда

hexed-game-over-header = Гру закінчено. Переможці:
hexed-game-over-winner-row = [orange]{$index}. {$name}[][accent]: [cyan]{$cores} { $cores ->
    [one] гекс
    [few] гекси
    *[many] гексів
    }
hexed-game-over-no-winners = Гру закінчено. На жаль, переможців не знайдено.
hexed-game-over-restart = Нова гра через 10 секунд...

pvp-team-won = Ваша команда перемогла. Ваш рейтинг зріс на {$increased}
pvp-team-lose = Ваша команда програла. Ваш рейтинг знизився на {$reduced}
pvp-leaderboard-content = [orange]{$index}. {$nickname}[accent]:[cyan] {$rating} [accent]рейтинг
pvp-you-spectator = [scarlet]Ви програли. Будь ласка, зачекайте наступної гри.

# ==============================================================================
# Events & Notifications
# ==============================================================================
player-joined = {$nickname} [grey]#[white]{$pid}[grey] [accent]приєднався.
player-left = {$nickname} [grey]#[white]{$pid}[grey] [accent]вийшов.

notification-votekick-playtime = [accent]Вітаємо! Ви відіграли [lightgray]{0}[] хвилин і тепер можете почати голосування за вигнання.
notification-global-chat-playtime = [accent]Вітаємо! Ви відіграли [lightgray]{0}[] хвилин і тепер можете писати в глобальний чат.
    {""}[lightgray]Напишіть [accent]/g [gray]<повідомлення...>[lightgray], щоб надіслати повідомлення.
notification-admin-kick = {$admin}[accent] вигнав(ла) {$target}[].
notification-admin-wave-skip = {$admin}[accent] пропустив(ла) хвилю.

notification-server-restart = Перезавантаження через {$seconds}

# ==============================================================================
# Errors
# ==============================================================================
error-access-denied = [scarlet]⚠ Доступ заборонено
error-ip-changed = [scarlet]⚠ Ваша IP-адреса змінилася. Права адміністратора було відкликано.
error-not-enough-params = [scarlet]⚠ Недостатньо параметрів
error-player-not-found = [scarlet]⚠ Гравця не знайдено
error-player-not-teammate = [scarlet]⚠ Цей гравець не у вашій команді
error-player-admin = [scarlet]⚠ Не намагайтеся вигнати адміністратора ⚠
error-already-voted = [scarlet]⚠ Ви вже проголосували. Заспокойтесь.
error-globalchat-total-playtime = [scarlet]⚠ Щоб писати в глобальний чат, вам потрібно відіграти {$globalChatPlayTime} хвилин.
error-votekick-total-playtime = [scarlet]⚠ Щоб почати голосування за вигнання, вам потрібно відіграти {$votekickPlayTime} хвилин.
error-vote-yourself = [scarlet]⚠ Ви не можете голосувати у власному голосуванні.
error-vote-in-progress = [scarlet]⚠ Голосування вже триває.
error-no-voting = [scarlet]⚠ На даний момент голосування не проводиться.
error-map-not-found = [scarlet]⚠ Мапу не знайдено! [accent]Використовуйте [cyan]/maps[], щоб побачити список доступних мап.
error-page-between = [scarlet]⚠ 'сторінка' має бути числом від[orange] 1[] до [orange]{$totalPages}[]
error-page-number = [scarlet]'сторінка' має бути числом
error-wrong-number = [scarlet]⚠ Неправильний формат числа
error-wrong-period-format = [scarlet]⚠ Неправильний формат періоду. Приклад: 1h 30m, 30 ({hours})
error-invalid-id = [scarlet]⚠ Невірний ID гравця
error-spectator = [scarlet]⚠ Ви спостерігач. Напишіть /spectate, щоб повернутися.
error-admin-password-too-short = [scarlet]⚠ Пароль адміністратора має бути не коротшим за 4 символи
error-wrong-admin-password = [scarlet]⚠ Невірний пароль адміністратора
error-internal = [scarlet]Внутрішня помилка сервера
error-processing-request = [scarlet]Виникла помилка під час обробки запиту.

# ==============================================================================
# Miscellaneous
# ==============================================================================
hours = годин
days = днів

success = [green]Успішно
empty = [accent]Порожньо
never = Ніколи

close = Закрити
previous = <- Попередня
next = Наступна ->