# VERDION OF: 04.02.2026

# ==============================================================================
# Terms
# ==============================================================================
-xcore = XCore сервер
# ==============================================================================
# General & Help
# ==============================================================================
commands-help-description = Відкрийте інтерактивне меню довідки.
help-menu-title = { "[" }orange]{ -xcore } — Команди
help-menu-content =
    { "[" }gray]Сторінка [white]{ $page }[gray]/[white]{ $total }
    { "" }[lightgray]Оберіть команду для детальної інформації:
help-menu-button = { "[" }accent]/{ $command } [gray]» Опис: [white]{ $description }
help-command-with-overload-count = { $name } В ({ $count })
help-command-title = { "[" }orange]» Назва: [white]/{ $name }
help-command-header =
    { "[" }orange]» [accent]Синтаксис: [white]{ $syntax }
    { "" }[orange]» [accent]Опис: [lightgray]{ $description }
help-aliases = { "[" }orange]» [accent]Псевдоніми: [white]{ $aliases }
help-args-title = { "[" }orange]» [accent]Аргументи:
help-usages-title = { "[" }orange]» [accent]Використання:
help-usage-entry = { "[" }gray]• [white]{ $syntax }
help-usage-args-title = { "[" }orange]» [accent]Для [white]{ $syntax }[accent]:
help-arg-entry = { "[" }gray]• [white]{ $arg } [lightgray]- { $description }
help-no-arguments = { "[" }gray]Додаткові аргументи не потрібні.
help-no-arg-description = Немає опису.
help-no-description = Опис для цієї команди не надано.
help-legacy-command-content =
    { "[" }orange]» [accent]Команда: [white]/{ $name }
    { "" }[orange]» [accent]Параметри: [white]{ $params }
    { "" }[orange]» [accent]Опис: [lightgray]{ $description }
    { "" }
    { "" }[gray](Це застаріла команда з обмеженою інформацією)
help-legacy-command-content-no-params =
    { "[" }orange]» [accent]Команда: [white]/{ $name }
    { "" }[orange]» [accent]Опис: [lightgray]{ $description }
    { "" }
    { "" }[gray](Це застаріла команда з обмеженою інформацією)
help-back = { "[" }lightgray]« Назад
commands-information-description = Показати інформацію про сервер.
commands-info-title = { "[" }orange]{ -xcore } — Назва сервера: [orange]{ $server-name }
commands-info-text =
    { "[" }accent]XCore[white] це [cyan]безкоштовний[white] сервер для гри у [accent]Mindustry[white].
    { "" }
    { "" }Версія XCore — [accent]{ $version }[white]
commands-sync-description = Синхронізувати гру з сервером. Використовуйте це для виправлення помилок (наприклад, фантомних одиниць).
commands-discord-description = Перенаправляє вас на наш Discord сервер.
welcome =
    { "[" }accent]Ласкаво просимо на { $serverName }!
    { "" }[lightgray]Напишіть [accent]/help[lightgray], щоб побачити список команд
    { "" }[lightgray]Напишіть [accent]/vote [gray]<y/n>[lightgray], щоб проголосувати за вигнання гравця
    { "" }[lightgray]Напишіть [accent]/votekick [gray]<ID/ім'я> <причина...>[lightgray], щоб почати голосування за вигнання
    { "" }[lightgray]Напишіть [accent]/t [gray]<повідомлення...>[lightgray], щоб надіслати повідомлення союзникам
    { "" }[lightgray]Напишіть [accent]/g [gray]<повідомлення...>[lightgray], щоб надіслати повідомлення на всі сервери
    { "" }[lightgray]Напишіть [accent]/tr [gray]<мова/auto>[lightgray], щоб увімкнути перекладач
    { "" }[lightgray]Напишіть [accent]/discord[lightgray], щоб перейти на наш Discord сервер
# ==============================================================================
# Command Argument Descriptions
# ==============================================================================
commands-help-page-description = Номер сторінки для відображення
commands-login-password-description = Ваш пароль адміністратора.
commands-ban-id-description = ID гравця для бану
commands-ban-period-description = Тривалість бану (наприклад: 1d, 2h, 30m)
commands-ban-reason-description = Причина бану
commands-unban-id-description = ID гравця для розбану
commands-mute-id-description = ID гравця для вимкнення можливості писати.
commands-mute-period-description = Тривалість мута (наприклад: 1h, 30m)
commands-mute-reason-description = Причина мута
commands-unmute-id-description = ID гравця для зняття мута
commands-votekick-target-description = Гравець для кіку (ID або ім'я)
commands-votekick-reason-description = Причина кіку
commands-vote-choice-description = Ваш голос: y (так), n (ні), c (скасувати, тільки адмін)
commands-t-message-description = Повідомлення для союзників
commands-g-message-description = Повідомлення для всіх серверів
commands-tr-language-description = Код мови, 'uk_UA', 'en', '...', 'auto' або 'off'
commands-stats-id-description = ID гравця для перегляду статистики
commands-rank-player-description = Гравець для перегляду рангу
commands-map-map-description = Назва або номер мапи.
commands-maps-page-description = Номер сторінки.
commands-maps-text-page-description = Номер сторінки.
commands-rtv-map-description = Мапа для голосування (опціонально)
commands-artv-map-description = Мапа для примусової зміни
commands-ai-state-description = Стан ШІ: attack (a) або idle (i)
commands-events-page-description = Номер сторінки
# ==============================================================================
# Chat & Social
# ==============================================================================
commands-t-description = Надіслати повідомлення тільки своїм союзникам по команді.
commands-t-chat = { "[" }{ "#" }{ $color }][Команді] [coral]>[accent] { $name }[coral]:[white] { $message }
commands-g-description = Надіслати повідомлення на всі сервери.
commands-a-description = Надіслати повідомлення тільки адміністраторам.
commands-tr-description = Встановити мову перекладача.
commands-tr-success = { "[" }accent]Мову перекладача успішно змінено на [grey]{ $translatorLanguage }[]!
commands-tr-off = { "[" }accent]Перекладач [scarlet]вимкнено[]!
commands-tr-not-found = { "[" }scarlet]⚠ Такої мови не існує.
discord-message-format = { "[" }blue][Discord][] { $author }: { $message }
global-chat-format = { "[" }royal][[[orange]GLOBAL [lightgray](з [accent]{ $server }[])[] { $author }[]]: [white]{ $message }
# ==============================================================================
# Authentication & Admin Access
# ==============================================================================
commands-login-description = Запит на права адміністратора. Не використовуйте, якщо не знаєте, що робите.
commands-login-incorrect-password = { "[" }scarlet]⚠ Невірний пароль!
commands-login-success = { "[" }green]Права адміністратора отримано.
commands-login-confirmed = { "[" }green]Права адміністратора підтверджено.
commands-login-admin-password-created =
    { "[" }green]Пароль адміністратора створено.
    { "" }[red]Не забудьте свій пароль! Якщо ви його забудете, вам доведеться просити головного адміністратора скинути його.
commands-login-request-approval-discord = { "[" }accent]Вам потрібно підтвердити запит на права адміністратора в каналі [orange]#admin-bots[] на нашому Discord сервері.
commands-logout-description = Вийти з адмін-панелі. Це [scarlet]відкличе ваші права адміністратора.
commands-logout-successful = { "[" }green]Права адміністратора відкликано.
# ==============================================================================
# Moderation (Ban, Mute, Kick)
# ==============================================================================
commands-ban-description = Заблокувати гравця. [scarlet]Тільки для адміністраторів.
commands-ban-success = { $nickname } [scarlet]заблокований
commands-unban-description = Розблокувати гравця. [scarlet]Тільки для адміністраторів.
commands-unban-success = { $nickname }[accent] #{ $pid } [green]успішно розблокований.
commands-mute-description = Заглушити гравця. [scarlet]Тільки для адміністраторів.
commands-mute-success = { "[" }accent]Успішно заглушено гравця { $nickname }
commands-unmute-description = Зняти заглушення з гравця. [scarlet]Тільки для адміністраторів.
commands-unmute-success = { "[" }green]Успішно знято заглушення з гравця []{ $nickname }
ban-content =
    { $nickname } [accent]був [scarlet]заблокований[].
    Щоб оскаржити блокування, відвідайте Discord (канал [gray]{ support-channel }[]):
    { "" }[cyan]{ $discordUrl }
ban-cancelled = { "[" }accent]Блокування гравця [scarlet]{ $nickname }[accent] було скасовано
tempban-content =
    { $nickname }[accent] був заблокований.
    Адміністратор: { $adminName }[accent]
    Причина: "[gold]{ $reason }[]"
    Ви будете розблоковані через: { $days } днів, { $hours } годин та { $minutes } хвилин
    Щоб оскаржити блокування, відвідайте Discord (канал [gray]{ support-channel }[]):
    { "" }[cyan]{ $discordUrl }
tempban-player-banned = { "[" }scarlet] Адміністратор { $adminName }[scarlet] заблокував гравця [gray]'[]{ $playerName }[gray]'
you-are-muted-by =
    { "[" }scarlet]Ви були заглушені адміністратором [accent]{ $adminName }[blue] на { $remainMinutes }:{ $remainSeconds } хвилин,
    причина: { $reason }
you-are-muted =
    { "[" }scarlet]Ви не можете писати в чат. [accent]Ви були заглушені адміністратором { $adminName }[blue] на { $remainMinutes }:{ $remainSeconds } хвилин,
    причина: { $reason }
kick-pirated-game = { "[" }accent]Вхід з неофіційних клієнтів [scarlet]заборонено[]. Будь ласка, використовуйте [lime]офіційну[] версію гри (Steam, Google Play, itch.io).
kick-recently-kicked =
    { "[" }accent]Ви були нещодавно вигнані з цього сервера.
    Зачекайте [cyan]{ $remainMinutes }:{ $remainSeconds }[accent] перед повторним входом.
kick-bot-protection = Можливо ви бот. Якщо ні, спробуйте перезайти.
kick-admintools-outdated =
    { "[" }green]Необхідна версія AdminTools: [grey]{ $requiredVersion }[]
    { "" }[scarlet]Ваша версія AdminTools: [grey]{ $version }[]
    { "" }
    { "" }[cyan]Будь ласка, оновіть AdminTools для входу на сервер.
support-channel = #reports-appeals
# ==============================================================================
# Voting (VoteKick)
# ==============================================================================
commands-votekick-description = Голосування за вигнання гравця з сервера.
commands-vote-description = Проголосувати у поточному голосуванні.
commands-vote-vote-with = { "[" }scarlet]⚠ Голосуйте за допомогою [orange]/vote <y/n/c>
votekick-vote =
    { $starter } [grey]#[white]{ $starterId }[lightgray] хоче вигнати { $target } [grey]#[white]{ $targetId }[lightgray]. Причина: [orange]{ $reason }[lightgray]. ([accent]{ $votes }[]/[accent]{ $required }[])
    { "" }[lightgray]Напишіть [orange]/vote <y/n>[], щоб проголосувати.
votekick-left = { $player }[lightgray] покинув гру. Голос анульовано. ([accent]{ $votes }[]/[accent]{ $required }[])
votekick-fail = { "[" }lightgray]Голосування не відбулося. Недостатньо голосів для вигнання { $target }[lightgray].
votekick-cancelled = { "[" }scarlet]Голосування за вигнання { $target }[scarlet] скасовано адміністратором { $admin }.
votekick-success =
    { "[" }orange]Голосування успішне. { $target }[orange] вигнаний на [scarlet]{ $minutes }[] { $minutes ->
        [one] хвилину
        [few] хвилини
       *[many] хвилин
    }.
# ==============================================================================
# Maps & RTV
# ==============================================================================
commands-map-description = Статистика конкретної мапи та швидкі дії
commands-map-title = { "[" }orange]{ -xcore } — Мапа
commands-map-content =
    { "" }[white]Статистика мапи [green]{ $name }
    { "" }[white]Автор:[green] { $author }[orange] | [white]Розмір:[green] { $width }x{ $height }[orange]
    { "" }[white]Репутація:[green] { $reputation }[orange] | [white]Популярність:[green] { $popularity }[orange] | [white]Інтерес:[green] { $interest }[orange]
    { "" }[white]Зіграно разів:[green] { $played }[orange] | [white]Зіграно за рік:[green] { $playedYear }[orange] | [white]Остання гра:[green] { $lastPlayed }[orange]
    { "" }[white]Подобається:[green] { $like }[orange] | [white]Не подобається:[green] { $dislike }[orange]
    { "" }[white]Мін. час:[green] { $min }[orange] | [white]Сер. час:[green] { $avg }[orange] | [white]Макс. час:[green] { $max }[orange]
    { "" }[green]{ $description }[white]
commands-maps-description = Список усіх мап на цьому сервері.
commands-maps-title = { "[" }orange]{ -xcore } — Список мап
commands-maps-content = { "" }[white]Сторінка [green]{ $page }[] з [green]{ $total }[]
commands-maps-text-description = Список усіх мап на цьому сервері.
commands-maps-text-start-content =
    { "[" }accent]Поточна мапа: []{ $name }[white]
    { "" }[orange][gold]Список мап [lightgray]{ $page }[gray]/[lightgray]{ $total }
commands-maps-text-content =
    { "" }
    { $index }. [orange] - [white]{ $name }[orange] | [green]{ $reputation }[orange] | [white]{ $width }x{ $height }[orange] | [white]{ $lastPlayed }[orange] | Від: [sky]{ $author }
commands-artv-description = Примусово змінити мапу. [scarlet]Тільки для адміністраторів.
commands-artv-map-skipped = { $nickname }[accent] пропустив мапу. Наступна мапа: { $name }.
commands-artv-event-skipped = { $nickname }[accent] пропустив подію. Наступна подія: { $name }
commands-rtv-description = Голосування за зміну мапи (Rock the vote).
commands-like-description = Проголосувати за мапу (підвищує репутацію).
commands-dislike-description = Проголосувати проти мапи
map-vote-title = { "[" }orange]{ -xcore } — [scarlet]ГРУ ЗАКІНЧЕНО!
map-vote-content =
    { "" }
    { "" }Наступна мапа: [accent]{ $mapName }[] від [accent]{ $author }[white].
    { "" }Нова гра почнеться через [accent]{ $seconds }[white] секунд.
    { "" }
    { "" }[cyan]Чи сподобалась ця мапа?
map-vote-like = { "[" }green]👍 Подобається
map-vote-dislike = { "[" }red]👎 Не подобається
map-vote-like-selected = { "[" }gray]Вам вже подобається
map-vote-dislike-selected = { "[" }gray]Вам вже не подобається
map-rtv = { "[" }orange]Голосування
map-artv = { "[" }red]Миттєва зміна
map-maps = Мапи
rtv-vote =
    { $nickname }[lightgray] проголосував за зміну поточної мапи на [orange]{ $mapName }[lightgray]. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Напишіть [orange]y[] або [orange]n[], щоб проголосувати.
rtv-left = { $nickname }[lightgray] вийшов. Його голос за зміну мапи скасовано. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
rtv-fail = { "[" }lightgray]Голосування не пройшло. Недостатньо голосів для зміни мапи на [orange]{ $mapName }[].
rtv-success = { "[" }orange]Голосування пройшло. Мапа [accent]{ $mapName }[] буде завантажена через [accent]{ $mapLoadDelay }[] секунд…
rtv-cancelled = { "[" }lightgray]Голосування за зміну мапи на [orange]{ $mapName }[lightgray] було скасовано адміністратором { $admin }.
# ==============================================================================
# Statistics & Ranks
# ==============================================================================
commands-player-description = Переглянути статистику гравця.
player-menu-player-content =
    Статистика гравця { $nickname } [grey]#{ $pid }
    { "" }[brown]Час у грі: [grey]{ $totalPlayTime }[] хвилин
    Ранг у Hexed: [grey]{ $hexedRankTag } { $hexedRankName }
    Рейтинг MiniPvP: { $pvpRating }
commands-lb-description = Увімкнути/вимкнути таблицю лідерів.
commands-lb-success =
    { $leaderboardEnabled ->
        [true] { "[" }accent]Таблиця лідерів [green]увімкнена.
       *[other] { "[" }accent]Таблиця лідерів [scarlet]вимкнена.
    }
leaderboard = { "[" }blue]Таблиця лідерів
commands-rank-description = Показує інформацію про ваш ранг або ранг іншого гравця.
commands-rank-content =
    { $nickname }
    { $rankTag } [accent]{ $rankName }
    { "" }[gold]Перемог: { $points }/{ $requiredPoints }
commands-ranks-description = Показує інформацію про систему рангів.
commands-ranks-content =
    { $rankTag } [accent]{ $rankName }
    { "" }[gold]Вимоги: [grey]{ $requiredPoints } [accent]перемог[]
commands-ranks-footer = Кількість перемог збільшується лише при перемозі над гравцем вашого рангу або вище.
commands-top-description = Топ гравців.
commands-top-hexed-content = { "[" }orange]{ $index }. { $nickname }[accent]: [blue]{ $rankName } [cyan]{ $points } []перемог
commands-top-pvp-content = { "[" }orange]{ $index }. { $nickname }[accent]: [cyan]{ $rating }
# ==============================================================================
# Game Modes (Hexed, PvP, Spectate, AI)
# ==============================================================================
commands-spectate-description = Перейти в режим спостерігача. Це видалить вашу одиницю.
commands-spectate-success = { "[" }green]Тепер ви спостерігаєте за грою
commands-ai-description = Керування ШІ (AI)
commands-ai-usage = { "[" }red]attack(i) []або [accent]idle(i)
commands-history-description = Увімкнути/вимкнути історію блоків.
commands-history-success = { "[" }accent]Історію блоків встановлено на [scarlet]{ 0 }
hexed-popup = { "[" }blue]{ $minutes }:{ $seconds }[] до кінця гри.
hexed-eliminated = { $nickname } [gold]був [scarlet]знищений[]!
hexed-leaderboard-content = { "[" }orange]{ $index }. { $nickname }[accent]: [cyan]{ $hexes } [accent]гексів
hexed-ranks-newbie = Новачок
hexed-ranks-regular = Звичайний
hexed-ranks-advanced = Досвідчений
hexed-ranks-veteran = Ветеран
hexed-ranks-davastator = Руйнівник
hexed-ranks-the_legend = Легенда
hexed-game-over-header = Гру закінчено. Переможці:
hexed-game-over-winner-row =
    { "[" }orange]{ $index }. { $name }[][accent]: [cyan]{ $cores } { $cores ->
        [one] гекс
        [few] гекси
       *[many] гексів
    }
hexed-game-over-no-winners = Гру закінчено. На жаль, переможців не знайдено.
hexed-game-over-restart = Нова гра через 10 секунд…
pvp-team-won = Ваша команда перемогла. Ваш рейтинг зріс на { $increased }
pvp-team-lose = Ваша команда програла. Ваш рейтинг знизився на { $reduced }
pvp-leaderboard-content = { "[" }orange]{ $index }. { $nickname }[accent]:[cyan] { $rating } [accent]рейтинг
pvp-you-spectator = { "[" }scarlet]Ви програли. Будь ласка, зачекайте наступної гри.
# ==============================================================================
# Events & Notifications
# ==============================================================================
player-joined = { $nickname } [grey]#[white]{ $pid }[grey] [accent]приєднався.
player-left = { $nickname } [grey]#[white]{ $pid }[grey] [accent]вийшов.
notification-votekick-playtime = { "[" }accent]Вітаємо! Ви відіграли [lightgray]{ $votekickPlayTime }[] хвилин і тепер можете почати голосування за вигнання.
notification-global-chat-playtime =
    { "[" }accent]Вітаємо! Ви відіграли [lightgray]{ $globalChatPlayTime }[] хвилин і тепер можете писати в глобальний чат.
    { "" }[lightgray]Напишіть [accent]/g [gray]<повідомлення…>[lightgray], щоб надіслати повідомлення.
notification-admin-kick = { $admin }[accent] вигнав(ла) { $target }[].
notification-admin-wave-skip = { $admin }[accent] пропустив(ла) хвилю.
server-restart-countdown = Перезавантаження через { $seconds }
like-map-success = { "[" }green]Ви вподобали цю мапу!
like-map-changed = { "[" }green]Ви змінили свою думку на Вподобайку!
dislike-map-success = { "[" }orange]Ви поставили "Не подобається" цій мапі.
dislike-map-changed = { "[" }orange]Ви змінили свою думку на "Не подобається".
like-event-success = { "[" }green]Ви вподобали цю подію!
like-event-changed = { "[" }green]Ви змінили свою думку на Вподобайку!
dislike-event-success = { "[" }orange]Ви поставили "Не подобається" цій події.
dislike-event-changed = { "[" }orange]Ви змінили свою думку на "Не подобається".

# ==============================================================================
# Events (Server)
# ==============================================================================

commands-event-description = Меню керуванням подій
commands-events-description = Список усіх подій на серверах.
event-menu-main = Головних подій
event-menu-main-title = { "[" }orange]{ -xcore } — Події
event-menu-main-content = Головна сторінка подій
event-menu-event = Подія
event-menu-event-title = { "[" }orange]{ -xcore } — Подія
event-menu-event-content =
    { "" }[white]Статистика події [green]{ $name }
    { "" }[white]Автор:[green] { $author }[orange] | [white]Мапа:[green] { $mapName }[orange]
    { "" }[white]Чи масштабна?:[green] { $isMajor }[orange] | [white]Чи проходила?:[green] { $isConducted }[orange]
    { "" }[white]Чи активна?:[green] { $isActive }[orange] | [white]Чи тимчасова?:[green] { $isTemporary }[orange]
    { "" }[white]Подобається:[green] { $like }[orange] | [white]Не подобається:[green] { $dislike }[orange]
    { "" }[green]{ $description }[white]
event-menu-event-map = Подивитися мапу
event-menu-events = Список подій
event-menu-events-title = { "[" }orange]{ -xcore } — Список Подій
event-menu-events-content = { "" }[white]Сторінка [green]{ $page }[] з [green]{ $total }[]
event-menu-events-empty = Немає подій
event-menu-events-selected = { "[" }green]●[] { $name }
event-menu-create-start = Створити
event-menu-create-start-title = { "[" }orange]{ -xcore } — Створення подій
event-menu-create-start-message = Ведіть назву майбутьної події
event-menu-create-start-default = { $playerName } Подія
event-menu-edit = Редагувати
event-menu-edit-title = { "[" }orange]{ -xcore } — Редагування подій
event-menu-edit-content =
    { "" }[white]Статистика події [green]{ $name }
    { "" }[white]Автор:[green] { $author }[orange] | [white]Мапа:[green] { $mapName }[orange]
    { "" }[white]Чи масштабна?:[green] { $isMajor }[orange] | [white]Чи тимчасова?:[green] { $isTemporary }[orange]
    { "" }[green]{ $description }[white]
event-menu-edit-name = Назва
event-menu-edit-name-title = { "[" }orange]{ -xcore } — Редагування події
event-menu-edit-name-message = Редагування назви:
event-menu-edit-description = Опис
event-menu-edit-description-title = { "[" }orange]{ -xcore } — Редагування події
event-menu-edit-description-message = Редагування опису:
event-menu-edit-map = Редагувати мапу
event-menu-edit-temporary-active = { "[" }green]Тимчасова
event-menu-edit-temporary-inactive = { "[" }gray]Тимчасова
event-menu-edit-major-active = { "[" }green]Масштабна
event-menu-edit-major-inactive = { "[" }gray]Масштабна
event-menu-edit-planned-start = Початок події
event-menu-edit-planned-start-title = { "[" }orange]{ -xcore } — Редагування події
event-menu-edit-planned-start-message = Напишіть початковий час у мс або з використанням m/h/d:
event-menu-edit-planned-end = Кінець події
event-menu-edit-planned-end-title = { "[" }orange]{ -xcore } — Редагування події
event-menu-edit-planned-end-message = Напишіть кінцевий час у мс або з використанням m/h/d:
event-menu-maps = Мапи
event-menu-maps-title = { "[" }orange]{ -xcore } — Вибір мапи
event-menu-maps-content = { "" }[white]Сторінка [green]{ $page }[] з [green]{ $total }[]
vote-event-vote =
    { $nickname }[lightgray] проголосував за зміну поточної події на [orange]{ $name }[lightgray]. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Напишіть [orange]y[] або [orange]n[], щоб проголосувати.
vote-event-left = { $nickname }[lightgray] вийшов. Його голос за зміну події скасовано. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
vote-event-fail = { "[" }lightgray]Голосування не пройшло. Недостатньо голосів для зміни події на [orange]{ $name }[].
vote-event-success = { "[" }orange]Голосування пройшло. Подія [accent]{ $name }[] буде завантажена при наступній зміни мапи.
vote-event-cancelled = { "[" }lightgray]Голосування за зміну події на [orange]{ $name }[lightgray] було скасовано адміністратором { $admin }.
event-vote = { "[" }orange]Голосувати
event-avote = { "[" }red]Миттєва зміна
event-menu-vote-stop = Зупинити голосування
event-menu-stop = Зупинити подію
event-menu-this-event = { "[" }orange]Теперішна подія
# ==============================================================================
# Errors
# ==============================================================================
error-access-denied = { "[" }scarlet]⚠ Доступ заборонено
error-ip-changed = { "[" }scarlet]⚠ Ваша IP-адреса змінилася. Права адміністратора було відкликано.
error-not-enough-params = { "[" }scarlet]⚠ Недостатньо параметрів
error-player-not-found = { "[" }scarlet]⚠ Гравця не знайдено
error-player-not-teammate = { "[" }scarlet]⚠ Цей гравець не у вашій команді.
error-player-admin = { "[" }scarlet]⚠ Не намагайтеся вигнати адміністратора ⚠
error-already-voted = { "[" }scarlet]⚠ Ви вже проголосували. Заспокойтесь.
error-globalchat-total-playtime = { "[" }scarlet]⚠ Щоб писати в глобальний чат, вам потрібно відіграти { $globalChatPlayTime } хвилин.
error-votekick-total-playtime = { "[" }scarlet]⚠ Щоб почати голосування за вигнання, вам потрібно відіграти { $votekickPlayTime } хвилин.
error-vote-yourself = { "[" }scarlet]⚠ Ви не можете голосувати у власному голосуванні.
error-vote-in-progress = { "[" }scarlet]⚠ Голосування вже триває.
error-no-voting = { "[" }scarlet]⚠ На даний момент голосування не проводиться.
error-no-map = { "[" }scarlet]⚠ Мапу не задано
error-map-not-event = { "[" }scarlet]⚠ Мапа не входить у теперішню подію.
error-map-not-found = { "[" }scarlet]⚠ Мапу не знайдено! [accent]Використовуйте [cyan]/maps[], щоб побачити список доступних мап.
error-maps-empty = { "[" }scarlet]⚠ Список мап порожний
error-event-not-found = { "[" }scarlet]⚠ Подію не знайдено! [accent]Використовуйте [cyan]/events[], щоб побачити список доступних подій.
error-page-between = { "[" }scarlet]⚠ 'сторінка' має бути числом від[orange] 1[] до [orange]{ $totalPages }[]
error-page-number = { "[" }scarlet]'сторінка' має бути числом
error-wrong-number = { "[" }scarlet]⚠ Неправильний формат числа
error-wrong-period-format = { "[" }scarlet]⚠ Неправильний формат періоду. Приклад: 1h 30m, 30 ({ hours })
error-invalid-id = { "[" }scarlet]⚠ Невірний ID гравця
error-spectator = { "[" }scarlet]⚠ Ви спостерігач. Напишіть /spectate, щоб повернутися.
error-admin-password-too-short = { "[" }scarlet]⚠ Пароль адміністратора має бути не коротшим за 4 символи
error-wrong-admin-password = { "[" }scarlet]⚠ Невірний пароль адміністратора
error-internal = { "[" }scarlet]Внутрішня помилка сервера
error-processing-request = { "[" }scarlet]Виникла помилка під час обробки запиту.
error-playtime-requirement = { "[" }scarlet]⚠ Вам потрібно відіграти мінімум { $time } хвилин для використання цієї функції.
error-invalid-syntax = { "[" }scarlet]⚠ Невірний синтаксис команди. Використання: [lightgray]/{ $syntax }
error-invalid-sender = { "[" }scarlet]⚠ Невірний відправник команди. Потрібно: [lightgray]{ $type }
error-argument-parse-generic = { "[" }scarlet]⚠ Помилка аргументу: { $error }
argument-parse-failure-boolean = { "[" }scarlet]⚠ Не вдалося розпізнати '{ $input }' як логічне значення (true/false).
argument-parse-failure-number = { "[" }scarlet]⚠ Число '{ $input }' поза допустимим діапазоном [{ $min }, { $max }].
argument-parse-failure-char = { "[" }scarlet]⚠ '{ $input }' не є допустимим символом.
argument-parse-failure-enum = { "[" }scarlet]⚠ '{ $input }' недопустима опція. Доступно: [lightgray]{ $acceptableValues }
argument-parse-failure-string = { "[" }scarlet]⚠ Невірний формат рядка: '{ $input }'.
argument-parse-failure-uuid = { "[" }scarlet]⚠ Невірний формат UUID: '{ $input }'.
argument-parse-failure-regex = { "[" }scarlet]⚠ Введення '{ $input }' не відповідає шаблону '{ $pattern }'.
argument-parse-failure-flag-unknown = { "[" }scarlet]⚠ Невідомий прапорець: '{ $flag }'.
argument-parse-failure-flag-duplicate = { "[" }scarlet]⚠ Прапорець, що повторюється: '{ $flag }'.
argument-parse-failure-flag-missing-argument = { "[" }scarlet]⚠ Відсутній аргумент для прапорця: '{ $flag }'.
argument-parse-failure-flag-no-permission = { "[" }scarlet]⚠ У вас немає прав на використання прапорця '{ $flag }'.
# ==============================================================================
# Miscellaneous
# ==============================================================================
hours = годин
days = днів
success = { "[" }green]Успішно[]
empty = { "[" }accent]Порожньо[]
never = Ніколи
save = Зберегти
close = { "[" }scarlet]Закрити
previous = { "[" }accent]« Попередня
next = { "[" }accent]Наступна »
yes = Так
no = Ні
event-events = Події
test = Тест
menu-main = Головне меню
commands-main-description = Відкрийте інтерактивне головне меню.
menu-main-title = { "[" }orange]{ -xcore } — Головне меню
menu-main-content = Головне меню сервера
help-menu = Меню довідки
commands-info = Інформація
current-map = Поточна мапа
next-map = Наступна мапа
player-menu-player = Гравець
player-menu-player-title = { "[" }orange]{ -xcore } — Статистика гравця
player-menu-players = Список гравців
player-menu-players-title = { "[" }orange]{ -xcore } — Список гравців
player-menu-players-content = { "" }[white]Сторінка [green]{ $page }[] з [green]{ $total }[]
player-menu-players-empty = Гравців не знайдено
player-menu-settings = Налаштування
player-menu-settings-title = { "[" }orange]{ -xcore } — Налаштування гравця
player-menu-settings-content = Показувати таблицю лідерів: [green]{ $leaderboard }[] | Переклад: [green]{ $language }[] | Мова для автоматичного перекладу: [green]{ $translatorLanguage }[]
player-menu-settings-translator-title = { "[" }orange]{ -xcore } — Вибір мови перекладача
player-menu-settings-language-title = { "[" }orange]{ -xcore } — Вибір мови
settings-language-label = Переклад: [green]{ $lang }[]
event-menu-create-start-map = Створити подію для цієї карти
event-end = Подія [green]{ $name }[] завершилася!
error-team-not-found = { "[" }scarlet]⚠ Команду не знайдено
error-no-access = { "[" }червоний]⚠ Немає доступу
finished = масштабна
finished-neutral = { "[" }orange]Завершено
finished-active = { "[" }green]Завершено
finished-inactive = { "[" }red]Завершено
major = Масштабна
major-neutral = { "[" }orange]Масштабна
major-active = { "[" }green]Масштабна
major-inactive = { "[" }red]Масштабна
active = Активна
active-neutral = { "[" }orange]Активна
active-active = { "[" }green]Активна
active-inactive = { "[" }red]Активна
admin = Адмін
admin-neutral = { "[" }orange]Адмін
admin-active = { "[" }green]Адмін
admin-inactive = { "[" }red]Адмін
cancel = Скасувати
back = Назад
no-description = Без опису
discord = Discord
github = GitHub
donatello = Donatello
weblate = Weblate
discord-red-vs-blue = RedVSBlue
auto = Авто
on = Увімкнено
off = Вимкнено
