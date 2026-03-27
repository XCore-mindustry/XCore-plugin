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
discord-menu-title = { "[" }orange]{ -xcore } — Discord
discord-menu-content =
    { "" }[white]Керуйте привʼязкою Discord тут.
    { "" }
    { "" }[white]Статус: { $status }
    { "" }[white]Сервер: [accent]{ $discordUrl }[]
discord-menu-open = Відкрити Discord
discord-menu-link = Привʼязати акаунт
discord-menu-status = Оновити статус
discord-menu-unlink = Відвʼязати акаунт
discord-menu-status-not-linked = [lightgray]не привʼязаний[]
discord-menu-status-linked = [green]{ $discordUsername }[] [gray]({ $discordId })[]
discord-link-menu-title = { "[" }orange]{ -xcore } — Привʼязка Discord акаунта
discord-link-menu-content =
    { "" }[white]На нашому Discord сервері викличте slash-команду бота:
    { "" }
    { "" }[accent]/link { $code }[]
    { "" }
    { "" }[white]Спливає через: [accent]{ $expireMinutes }[] хв
    { "" }[white]Discord: [accent]{ $discordUrl }[]
discord-link-menu-refresh = Оновити код
discord-link-menu-copy = Скопіювати код
discord-link-menu-regenerate = Згенерувати новий код
discord-link-menu-status = Назад до меню Discord
welcome =
    { "[" }accent]Ласкаво просимо на { $serverName }!
    { "" }[lightgray]Напишіть [accent]/help[lightgray], щоб побачити список команд
    { "" }[lightgray]Напишіть [accent]/vote [gray]<y/n>[lightgray], щоб проголосувати за вигнання гравця
    { "" }[lightgray]Напишіть [accent]/votekick [gray]<ID/ім'я> <причина...>[lightgray], щоб почати голосування за вигнання
    { "" }[lightgray]Напишіть [accent]/t [gray]<повідомлення...>[lightgray], щоб надіслати повідомлення союзникам
    { "" }[lightgray]Напишіть [accent]/g [gray]<повідомлення...>[lightgray], щоб надіслати повідомлення на всі сервери
    { "" }[lightgray]Напишіть [accent]/tr [gray]<мова/auto>[lightgray], щоб увімкнути перекладач
    { "" }[lightgray]Напишіть [accent]/discord[lightgray], щоб відкрити меню Discord і привʼязати акаунт
# ==============================================================================
# Command Argument Descriptions
# ==============================================================================
commands-help-page-description = Номер сторінки для відображення.
commands-login-password-description = Ваш пароль адміністратора.
commands-ban-id-description = ID гравця для блокування.
commands-ban-period-description = Тривалість блокування (наприклад: 1d, 2h, 30m).
commands-ban-reason-description = Причина блокування.
commands-unban-id-description = ID гравця для розблокування.
commands-mute-id-description = ID гравця для вимкнення можливості писати.
commands-mute-period-description = Тривалість вимкнення чату (наприклад: 1h, 30m).
commands-mute-reason-description = Причина вимкнення чату.
commands-unmute-id-description = ID гравця для зняття вимкнення чату.
commands-votekick-target-description = Гравець для вигнання (ID або ім'я).
commands-votekick-reason-description = Причина вигнання.
commands-vote-choice-description = Ваш голос: y (так), n (ні), c (скасувати, тільки адмін).
commands-t-message-description = Повідомлення для союзників.
commands-g-message-description = Повідомлення для всіх серверів.
commands-tr-language-description = Код мови, 'uk_UA', 'en', '...', 'auto' або 'off'.
commands-stats-id-description = ID гравця для перегляду статистики
commands-rank-player-description = Гравець для перегляду рангу
commands-map-map-description = Назва або номер мапи.
commands-maps-page-description = Номер сторінки.
commands-maps-text-page-description = Номер сторінки.
commands-rtv-map-description = Мапа для голосування (опціонально).
commands-artv-map-description = Мапа для примусової зміни.
commands-ai-state-description = Стан ШІ: attack (a) або idle (i).
commands-events-page-description = Номер сторінки.
# ==============================================================================
# Chat & Social
# ==============================================================================
commands-t-description = Надіслати повідомлення тільки своїм союзникам по команді.
commands-t-chat = { "[" }{ "#" }{ $color }][Команді] [coral]> { $badge }[accent]{ $name }[lightgray]: [white]{ $message }
commands-g-description = Надіслати повідомлення на всі сервери.
commands-a-description = Надіслати повідомлення тільки адміністраторам.
commands-msg-description = Надіслати гравцю приватне повідомлення.
commands-msg-id-description = ID гравця.
commands-msg-message-description = Текст приватного повідомлення.
commands-reply-description = Відповісти останньому співрозмовнику в приватних повідомленнях.
commands-reply-message-description = Текст відповіді.
commands-inbox-description = Відкрити меню приватних повідомлень.
commands-inbox-id-description = ID гравця.
commands-tr-description = Встановити мову перекладача.
commands-tr-success = { "[" }accent]Мову перекладача успішно змінено на [grey]{ $translatorLanguage }[]!
commands-tr-off = { "[" }accent]Перекладач [scarlet]вимкнено[]!
commands-tr-not-found = { "[" }scarlet]⚠ Такої мови не існує.
discord-chat-format = { "[" }#5865F2][DISCORD][] [lightgray]| [accent]{ $author }[lightgray] >> [white]{ $message }
global-chat-format = { "[" }royal][[[orange]GLOBAL [lightgray](з [accent]{ $server }[])[] { $author }[]]: [white]{ $message }
private-message-received = { "[" }sky][ПП][] [lightgray]від [accent]{ $author } [gray]#{ $pid }[lightgray]: [white]{ $message }
private-message-sent = { "[" }sky][ПП][] [lightgray]для [accent]{ $target } [gray]#{ $pid }[lightgray]: [white]{ $message }
private-message-unread-count = { "[" }accent]У вас [white]{ $count }[accent] непрочитаних приватних повідомлень.
private-message-join-notification = { "[" }accent]У вас [white]{ $count }[accent] непрочитаних приватних повідомлень. Використайте [white]/inbox[accent], щоб відкрити їх.
private-message-block-success = { "[" }accent]Приватні повідомлення від [white]{ $target } [gray]#{ $pid }[accent] тепер заблоковані.
private-message-block-already = { "[" }lightgray]Приватні повідомлення від [white]{ $target } [gray]#{ $pid }[lightgray] вже заблоковані.
private-message-unblock-success = { "[" }accent]Приватні повідомлення від [white]{ $target } [gray]#{ $pid }[accent] знову дозволені.
private-message-unblock-missing = { "[" }lightgray]Гравця [white]{ $target } [gray]#{ $pid }[lightgray] немає у списку заблокованих.
private-message-menu-title = { "[" }orange]{ -xcore } — Приватні повідомлення
private-message-menu-content =
    { "" }[white]Сторінка [green]{ $page }[] з [green]{ $total }[]
    { "" }[white]Непрочитано: [accent]{ $unread }[]
private-message-menu-empty = { "" }[lightgray]У вас поки немає приватних повідомлень.
private-message-menu-entry-unread = { "[" }accent]Непрочитано[] [white]{ $author } [gray]#{ $pid }[] [lightgray]({ $time })[]: [white]{ $message }
private-message-menu-entry-read = { "[" }gray]Прочитано[] [white]{ $author } [gray]#{ $pid }[] [lightgray]({ $time })[]: [white]{ $message }
private-message-details-title = { "[" }orange]{ -xcore } — Повідомлення
private-message-details-content =
    { "" }[white]Від: [accent]{ $author } [gray]#{ $pid }[]
    { "" }[white]Час: [accent]{ $time }[]
    { "" }[white]Статус: [accent]{ $status }[]
    { "" }
    { "" }[white]{ $message }
private-message-status-unread = непрочитано
private-message-status-read = прочитано
private-message-blocked-title = { "[" }orange]{ -xcore } — Заблоковані гравці
private-message-blocked-content =
    { "" }[white]Сторінка [green]{ $page }[] з [green]{ $total }[]
    { "" }[white]Заблоковано: [accent]{ $count }[]
private-message-blocked-empty = { "" }[lightgray]У вас немає заблокованих гравців.
private-message-blocked-entry = { "[" }white]{ $target } [gray]#{ $pid }[]
private-message-compose = Написати
private-message-blocked = Заблоковані
private-message-block = Заблокувати відправника
private-message-unblock = Розблокувати відправника
private-message-reply-title = Відповідь
private-message-reply-message = Введіть повідомлення для [accent]#{ $pid }[]
private-message-compose-target-title = Нове повідомлення
private-message-compose-target-message = Введіть ID гравця у форматі [accent]#123[]
private-message-compose-body-title = Текст повідомлення
private-message-compose-body-message = Введіть приватне повідомлення для [accent]{ $pid }[]
# ==============================================================================
# Authentication & Admin Access
# ==============================================================================
commands-login-description = Активувати права адміністратора, якщо ваш прив’язаний Discord акаунт вже має доступ.
commands-login-incorrect-password = { "[" }scarlet]⚠ Невірний пароль!
commands-login-success = { "[" }green]Права адміністратора отримано.
commands-login-confirmed = { "[" }green]Discord-доступ адміністратора підтверджено.
commands-login-admin-password-created =
    { "[" }green]Пароль адміністратора створено.
    { "" }[red]Не забудьте свій пароль! Якщо ви його забудете, вам доведеться просити головного адміністратора скинути його.
commands-login-request-approval-discord = { "[" }accent]Ваш акаунт не має Discord-доступу адміністратора. Отримайте admin role у Discord і спробуйте знову.
commands-discord-link-created =
    { "[" }green]Код привʼязки Discord створено: [accent]{ $code }[]
    { "" }[lightgray]На нашому Discord сервері викличте slash-команду бота [accent]/link { $code }[] протягом [accent]{ $expireMinutes }[] хв.
    { "" }[cyan]{ $discordUrl }
commands-discord-link-confirmed = { "[" }green]Discord акаунт привʼязано: [accent]{ $discordUsername }[]
commands-discord-link-already-linked = { "[" }lightgray]Цей акаунт Mindustry уже привʼязаний. Використайте [accent]/discord status[] або [accent]/discord unlink[].
commands-discord-link-error = { "[" }scarlet]Не вдалося створити код привʼязки Discord. Спробуйте пізніше.
commands-discord-status-not-linked = { "[" }lightgray]Ваш акаунт не привʼязаний до Discord.
commands-discord-status-linked = { "[" }green]Привʼязаний Discord: [accent]{ $discordUsername }[] [gray]({ $discordId })[]
commands-discord-unlink-not-linked = { "[" }lightgray]Ваш акаунт не привʼязаний до Discord.
commands-discord-unlink-success = { "[" }green]Привʼязку Discord видалено.
commands-logout-description = Вийти з адмін-панелі. Це [scarlet]відкличе ваші активні права адміністратора.
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
commands-map-description = Статистика конкретної мапи та швидкі дії.
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
commands-dislike-description = Проголосувати проти мапи.
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
    { "" }[gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }[white]{ $customNickname }[] [gray]#{ $pid }[]
    { "" }[lightgray]{ $description }[]
    { "" }[gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    { "" }[accent]■ Профіль[]
    { "" }[gray]Ім'я: [white]{ $nickname } [darkgray]|[gray] Адмін: [lime]{ $admin }[]
    { "" }[gray]Відзнака: [white]{ $activeBadge } [darkgray]|[gray] Системна: [coral]{ $systemBadge }[]
    { "" }[gray]Реєстрація: [white]{ $accountCreated }[]
    { "" }
    { "" }[accent]■ Ігрові рейтинги[]
    { "" }[gray]Час у грі: [white]{ $totalPlayTime }[]
    { "" }[gray]MiniPvP: [sky]{ $pvpRating } [darkgray]|[gray] Hexed: [sky]{ $hexedRankName } [gray]({ $hexedPoints } очк.)[]
    { "" }[lightgray]{ $hexedProgress }[]
    { "" }
    { "" }[accent]■ Матчі: [white]{ $gamesPlayed } [gray]ігор [darkgray]|[lime] { $gamesWon } [gray]перемог [darkgray]|[sky] { $winRate }% [gray]вінрейт[]
    { "" }[gray]• [white]PvP: { $pvpSummary }[]
    { "" }[gray]• [white]Surv: { $survivalSummary }[]
    { "" }[gray]• [white]Hexed: { $hexedSummary }[]
    { "" }
    { "" }[accent]■ Бойова ефективність[]
    { "" }[gray]Блоки (Буд/Роз/Знищ): [lime]{ $blocksBuilt } [darkgray]/ [orange]{ $blocksDeconstructed } [darkgray]/ [scarlet]{ $blocksDestroyed }[]
    { "" }[gray]Юніти (Створ/Вбито): [lime]{ $unitsProduced } [darkgray]/ [scarlet]{ $unitsDestroyed }[]
player-menu-player-max-rank = Досягнуто максимального рангу
player-menu-player-hexed-progress = { "[" }gray]До [white]{ $nextRankName } [gray]залишилось перемог: [accent]{ $requiredPoints }[]
player-menu-player-no-mode-stats = { "[" }gray]немає даних[]
player-menu-player-pvp-summary = { "[" }gray]ігор [white]{ $gamesPlayed }[], перемог [lime]{ $gamesWon }[], [sky]{ $winRate }%[]
player-menu-player-survival-summary = { "[" }gray]хвилі: макс [lime]{ $bestWave }[], сер [white]{ $averageWave }[] [gray](забігів: { $gamesPlayed })[]
player-menu-player-hexed-summary = { "[" }gray]матчів [white]{ $gamesPlayed }[], топ-1 [lime]{ $gamesWon }[], краще місце [accent]#{ $bestPlacement }[]
player-menu-time-days = { $value }д
player-menu-time-hours = { $value }г
player-menu-time-minutes = { $value }хв
settings-translator-label = Мова перекладача: [green]{ $lang }[]
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
commands-ai-description = Керування ШІ (AI).
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

commands-event-description = Меню керуванням подій.
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
error-access-denied = { "[" }scarlet]⚠ Доступ заборонено.
error-ip-changed = { "[" }scarlet]⚠ Ваша IP-адреса змінилася. Права адміністратора було відкликано.
error-not-enough-params = { "[" }scarlet]⚠ Недостатньо параметрів.
error-player-not-found = { "[" }scarlet]⚠ Гравця не знайдено.
error-player-not-teammate = { "[" }scarlet]⚠ Цей гравець не у вашій команді.
error-player-admin = { "[" }scarlet]⚠ Не намагайтеся вигнати адміністратора. ⚠
error-already-voted = { "[" }scarlet]⚠ Ви вже проголосували. Заспокойтесь.
error-globalchat-total-playtime = { "[" }scarlet]⚠ Щоб писати в глобальний чат, вам потрібно відіграти { $globalChatPlayTime } хвилин.
error-votekick-total-playtime = { "[" }scarlet]⚠ Щоб почати голосування за вигнання, вам потрібно відіграти { $votekickPlayTime } хвилин.
error-vote-yourself = { "[" }scarlet]⚠ Ви не можете голосувати у власному голосуванні.
error-vote-in-progress = { "[" }scarlet]⚠ Голосування вже триває.
error-no-voting = { "[" }scarlet]⚠ На даний момент голосування не проводиться.
error-no-map = { "[" }scarlet]⚠ Мапу не задано.
error-map-not-event = { "[" }scarlet]⚠ Мапа не входить у теперішню подію.
error-map-not-found = { "[" }scarlet]⚠ Мапу не знайдено! [accent]Використовуйте [cyan]/maps[], щоб побачити список доступних мап.
error-maps-empty = { "[" }scarlet]⚠ Список мап порожній.
error-event-not-found = { "[" }scarlet]⚠ Подію не знайдено! [accent]Використовуйте [cyan]/events[], щоб побачити список доступних подій.
error-page-between = { "[" }scarlet]⚠ 'сторінка' має бути числом від[orange] 1[] до [orange]{ $totalPages }[].
error-page-number = { "[" }scarlet]'сторінка' має бути числом.
error-wrong-number = { "[" }scarlet]⚠ Неправильний формат числа.
error-wrong-period-format = { "[" }scarlet]⚠ Неправильний формат періоду. Приклад: 1h 30m, 30 ({ hours })
error-invalid-id = { "[" }scarlet]⚠ Невірний ID гравця.
error-spectator = { "[" }scarlet]⚠ Ви спостерігач. Напишіть /spectate, щоб повернутися.
error-admin-password-too-short = { "[" }scarlet]⚠ Пароль адміністратора має бути не коротшим за 4 символи.
error-wrong-admin-password = { "[" }scarlet]⚠ Невірний пароль адміністратора.
error-internal = { "[" }scarlet]Внутрішня помилка сервера.
error-processing-request = { "[" }scarlet]Виникла помилка під час обробки запиту.
error-playtime-requirement = { "[" }scarlet]⚠ Вам потрібно відіграти мінімум { $time } хвилин для використання цієї функції.
error-invalid-syntax = { "[" }scarlet]⚠ Невірний синтаксис команди. Використання: [lightgray]/'{ $syntax }'.
error-invalid-sender = { "[" }scarlet]⚠ Невірний відправник команди. Потрібно: '[lightgray]{ $type }[]'.
error-argument-parse-generic = { "[" }scarlet]⚠ Помилка аргументу: '{ $error }'.
exception-unexpected = { "[" }scarlet]⚠ Внутрішня помилка під час виконання команди.
exception-invalid-argument = { "[" }scarlet]⚠ Невірний аргумент команди: '{ $cause }'.
exception-no-such-command = { "[" }scarlet]⚠ Невідома команда.
exception-no-permission = { "[" }scarlet]⚠ Доступ заборонено.
exception-invalid-sender = { "[" }scarlet]⚠ '{ $actual }' не може виконати цю команду. Потрібно: [lightgray]{ $expected }[].
exception-invalid-sender-list = { "[" }scarlet]⚠ '{ $actual }' не може виконати цю команду. Дозволені відправники: [lightgray]{ $expected }[].
exception-invalid-syntax = { "[" }scarlet]⚠ Невірний синтаксис команди. Використання: [lightgray]/'{ $syntax }'.
argument-parse-failure-boolean = { "[" }scarlet]⚠ Не вдалося розпізнати '{ $input }' як логічне значення (true/false).
argument-parse-failure-number = { "[" }scarlet]⚠ Число '{ $input }' поза допустимим діапазоном [{ $min }, { $max }].
argument-parse-failure-char = { "[" }scarlet]⚠ '{ $input }' не є допустимим символом.
argument-parse-failure-enum = { "[" }scarlet]⚠ '{ $input }' недопустима опція. Доступно: [lightgray]{ $acceptableValues }
argument-parse-failure-string = { "[" }scarlet]⚠ Невірний формат рядка: '{ $input }'.
argument-parse-failure-uuid = { "[" }scarlet]⚠ Невірний формат UUID: '{ $input }'.
argument-parse-failure-regex = { "[" }scarlet]⚠ Введення '{ $input }' не відповідає шаблону '{ $pattern }'.
argument-parse-failure-color = { "[" }scarlet]⚠ '{ $input }' не є допустимим кольором.
argument-parse-failure-duration = { "[" }scarlet]⚠ '{ $input }' не є допустимим форматом тривалості.
argument-parse-failure-aggregate-missing = { "[" }scarlet]⚠ Відсутній компонент '{ $component }'.
argument-parse-failure-aggregate-failure = { "[" }scarlet]⚠ Некоректний компонент '{ $component }': '{ $failure }'.
argument-parse-failure-either = { "[" }scarlet]⚠ Не вдалося визначити { $primary } або { $fallback } з '{ $input }'.
argument-parse-failure-flag-unknown = { "[" }scarlet]⚠ Невідомий прапорець: '{ $flag }'.
argument-parse-failure-flag-duplicate = { "[" }scarlet]⚠ Прапорець, що повторюється: '{ $flag }'.
argument-parse-failure-flag-duplicate-flag = { "[" }scarlet]⚠ Прапорець, що повторюється: '{ $flag }'.
argument-parse-failure-flag-no-flag-started = { "[" }scarlet]⚠ Прапорець не було розпочато. Незрозуміло, що робити з '{ $input }'.
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
player-menu-players = Онлайн гравці
player-menu-players-title = { "[" }orange]{ -xcore } — Онлайн гравці
player-menu-players-content = { "" }[white]Сторінка [green]{ $page }[] з [green]{ $total }[]
player-menu-players-empty = Онлайн гравців не знайдено
player-menu-players-row = [white]{ $nickname } [gray](PID: { $pid })[]
player-menu-settings = Налаштування
player-menu-settings-title = { "[" }orange]{ -xcore } — Налаштування гравця
player-menu-settings-content =
    { "" }[white]Встановлене ім'я: [green]{ $customNickname }[]
    { "" }[white]Системний відзнака: [green]{ $systemBadge }[] | Активний бейдж: [green]{ $activeBadge }[]
    { "" }[white]Таблиця лідерів: [green]{ $leaderboard }[]
    { "" }[white]Мова: [green]{ $language }[] | Мова перекладача: [green]{ $translatorLanguage }[]
    { "" }[green]{ $description }[white]
player-menu-settings-translator-title = { "[" }orange]{ -xcore } — Вибір мови перекладача
player-menu-settings-language-title = { "[" }orange]{ -xcore } — Вибір мови
player-menu-settings-customNickname = Редагувати ім'я
player-menu-settings-customNickname-message = { "[" }lightgray]Залиште пустим для скидання
player-menu-settings-description = Редагувати опис
settings-language-label = Переклад: [green]{ $lang }[]
event-menu-create-start-map = Створити подію для цієї карти
event-end = Подія [green]{ $name }[] завершилася!
error-team-not-found = { "[" }scarlet]⚠ Команду не знайдено.
error-no-access = { "[" }scarlet]⚠ Немає доступу.
error-nickname-too-long = { "[" }scarlet]⚠ Ім'я користувача надто довге. Максимум { $max } видимих символів.
error-private-message-invalid-pid = { "[" }scarlet]⚠ Невірний ID гравця. Використовуйте формат [lightgray]#123[].
error-private-message-self = { "[" }scarlet]⚠ Не можна надсилати приватне повідомлення самому собі.
error-private-message-empty = { "[" }scarlet]⚠ Повідомлення не може бути порожнім.
error-private-message-too-long = { "[" }scarlet]⚠ Повідомлення надто довге. Максимум { $max } символів.
error-private-message-cooldown = { "[" }scarlet]⚠ Зачекайте { $seconds }с перед надсиланням наступного приватного повідомлення.
error-private-message-target-unavailable = { "[" }scarlet]⚠ Цей гравець зараз недоступний для приватних повідомлень.
error-private-message-no-reply-target = { "[" }scarlet]⚠ Немає кому відповісти в приватних повідомленнях.
error-private-message-not-found = { "[" }scarlet]⚠ Повідомлення не знайдено.
error-private-message-block-self = { "[" }scarlet]⚠ Не можна заблокувати самого себе.
error-private-message-block-limit = { "[" }scarlet]⚠ Досягнуто ліміту списку блокувань ({ $limit }).
ban-menu-duration-title = { "[" }orange]{ -xcore } — Тривалість блокування
ban-menu-duration-message = Введіть тривалість блокування для { $nickname }. Наприклад: 1d, 12h, 30m
ban-menu-reason-title = { "[" }orange]{ -xcore } — Причина блокування
ban-menu-reason-message = Введіть причину блокування для { $nickname }. Залиште порожнім для причини за замовчуванням.
ban-menu-confirm-title = { "[" }orange]{ -xcore } — Підтвердження блокування
ban-menu-confirm-content =
    { "" }[white]Гравець: { $nickname }[]
    { "" }[white]Тривалість: [accent]{ $duration }[]
    { "" }[white]Причина: [accent]{ $reason }[]
ban-menu-confirm-action = { "[" }scarlet]Заблокувати гравця
finished = завершено
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
error-command-disabled = { "[" }scarlet]⚠ Команда [accent]/{ $command }[scarlet] вимкнена на цьому сервері.
error-feature-disabled = { "[" }scarlet]⚠ Цю функцію вимкнено на цьому сервері.
error-nickname-badge-glyph = { "[" }scarlet]⚠ Користувацький нік не може містити зарезервовані іконки відзнак.
player-leaderboard-active = { "[" }green]Список лідерів
player-leaderboard-inactive = { "[" }red]Список лідерів
player-menu-settings-badges = Відзнаки
badge-menu-title = { "[" }orange]{ -xcore } — Відзнаки
badge-menu-content =
    { "" }[white]Системна відзнака: [green]{ $systemBadge }[]
    { "" }[white]Активна відзнака: [green]{ $activeBadge }[]
badge-menu-empty = { "[" }lightgray]У вас поки що немає відкритих відзнак.
badge-menu-row = { "[" }white]{ $badge }[] [gray]-[] { $description }
badge-menu-view-all = Усі відзнаки
badge-menu-all-title = { "[" }orange]{ -xcore } — Усі відзнаки
badge-menu-all-content = { "[" }lightgray]Перегляд усіх відзнак, їхнього статусу та опису.
badge-menu-all-row = { "[" }white]{ $badge }[] [gray]-[] [accent]{ $state }[] [gray]-[] { $description }
badge-clear-button = Прибрати активну відзнаку
badge-state-system = Системна
badge-state-system-active = Системна активна
badge-state-active = Активна
badge-state-unlocked = Відкрита
badge-state-locked = Закрита
badge-set-success = { "[" }accent]Активну відзнаку встановлено: [green]{ $badge }[].
badge-clear-success = { "[" }accent]Активну відзнаку прибрано.
badge-grant-success = { "[" }accent]Відзнаку [green]{ $badge }[] видано гравцеві [green]{ $nickname }[][gray]#{ $pid }[].
badge-revoke-success = { "[" }accent]Відзнаку [green]{ $badge }[] знято з гравця [green]{ $nickname }[][gray]#{ $pid }[].
badge-already-unlocked = { "[" }scarlet]⚠ Відзнаку [accent]{ $badge }[scarlet] уже відкрито.
badge-not-owned = { "[" }scarlet]⚠ Гравець не має відзнаки [accent]{ $badge }[scarlet].
error-badge-not-found = { "[" }scarlet]⚠ Відзнаку [accent]{ $badge }[scarlet] не знайдено.
error-badge-not-unlocked = { "[" }scarlet]⚠ Відзнаку [accent]{ $badge }[scarlet] не відкрито.
error-badge-not-selectable = { "[" }scarlet]⚠ Відзнаку [accent]{ $badge }[scarlet] не можна вибрати вручну.
badge-admin-name = Адміністратор
badge-admin-description = Автоматична відзнака для адміністраторів.
badge-developer-name = Розробник
badge-developer-description = Надається розробникам XCore.
badge-translator-name = Перекладач
badge-translator-description = Надається тим, хто допомагає з перекладом XCore.
badge-map-maker-name = Творець мап
badge-map-maker-description = Надається авторам мап, що використовуються на сервері.
badge-contributor-name = Контриб'ютор
badge-contributor-description = Надається за вагомий внесок у проєкт або спільноту.
badge-bug-finder-name = Шукач багів
badge-bug-finder-description = Надається гравцям, які регулярно знаходять реальні баги й оформлюють репорти так, щоб їх можна було відтворити та виправити.
badge-event-winner-name = Переможець події
badge-event-winner-description = Надається переможцям особливих серверних подій.
badge-veteran-name = Ветеран
badge-veteran-description = Надається шанованим досвідченим гравцям.
none = Немає
