# ==============================================================================
# Terms
# ==============================================================================
-xcore = XCore сервер
# ==============================================================================
# General & Help
# ==============================================================================
menu-main = Главное меню
commands-main-description = Открыть интерактивное главное меню.
menu-main-title = [orange]{ -xcore } — Главное меню
menu-main-content = Главное меню сервера
help-menu = Меню помощи
commands-help-description = Открыть интерактивное меню помощи.
help-menu-title = [orange]{ -xcore } — Команды
help-menu-content =
    [gray]Страница [white]{ $page }[gray]/[white]{ $total }
    [lightgray]Выберите команду для подробной информации:
help-menu-button = [accent]/{ $command } [gray]» Описание: [white]{ $description }
help-command-with-overload-count = { $name } ({ $count })
help-command-title = [orange]» Название: [white]/{ $name }
help-command-header =
    [orange]» [accent]Синтаксис: [white]{ $syntax }
    [orange]» [accent]Описание: [lightgray]{ $description }
help-aliases = [orange]» [accent]Псевдонимы: [white]{ $aliases }
help-args-title = [orange]» [accent]Аргументы:
help-usages-title = [orange]» [accent]Использование:
help-usage-entry = [gray]• [white]{ $syntax }
help-usage-args-title = [orange]» [accent]Для [white]{ $syntax }[accent]:
help-arg-entry = [gray]• [white]{ $arg } [lightgray]- { $description }
help-no-arguments = [gray]Дополнительные аргументы не требуются.
help-no-arg-description = Нет описания.
help-no-description = Описание для этой команды не предоставлено.
help-legacy-command-content =
    [orange]» [accent]Команда: [white]/{ $name }
    [orange]» [accent]Параметры: [white]{ $params }
    [orange]» [accent]Описание: [lightgray]{ $description }
    { "" }
    [gray](Это устаревшая команда с ограниченной информацией)
help-legacy-command-content-no-params =
    [orange]» [accent]Команда: [white]/{ $name }
    [orange]» [accent]Описание: [lightgray]{ $description }
    { "" }
    [gray](Это устаревшая команда с ограниченной информацией)
help-back = [lightgray]« Назад

# ==============================================================================
# Modern Reactive Help & Commands Guide (xcore-ui)
# ==============================================================================
help-ui-title = СПРАВОЧНИК КОМАНД
help-ui-summary = [gray]{ $count } команд доступно
help-ui-empty-category = [lightgray]В этой категории нет доступных команд.[]
help-ui-overloads = ({ $count } вар.)
help-ui-aliases = [lightgray]Псевдонимы:[] { $aliases }
help-ui-syntax-title = Синтаксис
help-ui-args-title = Параметры
help-ui-arg-required = [scarlet]Обязательный
help-ui-arg-optional = [sky]Опциональный
help-ui-btn-run = Выполнить
help-ui-btn-copy = В чат
help-ui-btn-back = Назад
help-ui-copied = [accent]Команда: [white]/{ $syntax }
help-ui-executed = [accent]Выполняется команда: [white]/{ $syntax }

# Categories
help-cat-all = Все
help-cat-general = Общие
help-cat-game = Игра
help-cat-social = Чат
help-cat-votes = Голоса
help-cat-admin = Админ
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
commands-info-title = [orange]{ -xcore } — Название сервера: [orange]{ $server-name }
commands-info-text =
    [accent]XCore[white] — это [cyan]бесплатный[white] сервер для игры в [accent]Mindustry[white].
    { "" }
    Версия XCore — [accent]{ $version }[white]
commands-sync-description = Синхронизировать игру с сервером. Запустите это для исправления ошибок, таких как фантомные юниты.
commands-discord-description = Перенаправляет вас на сервер discord.
discord-menu-title = [orange]{ -xcore } — Discord
discord-menu-content =
    [white]Управляйте привязкой Discord здесь.
    { "" }
    [white]Статус: { $status }
    [white]Сервер: [accent]{ $discordUrl }[]
discord-menu-open = Открыть Discord
discord-menu-link = Привязать аккаунт
discord-menu-status = Обновить статус
discord-menu-unlink = Отвязать аккаунт
discord-menu-status-not-linked = [lightgray]не привязан[]
discord-menu-status-linked = [green]{ $discordUsername }[] [gray]({ $discordId })[]
discord-link-menu-title = [orange]{ -xcore } — Привязка Discord аккаунта
discord-link-menu-content =
    [white]На нашем Discord сервере вызовите slash-команду бота:
    { "" }
    [accent]/link { $code }[]
    { "" }
    [white]Истекает через: [accent]{ $expireMinutes }[] мин
    [white]Discord: [accent]{ $discordUrl }[]
discord-link-menu-refresh = Обновить код
discord-link-menu-copy = Скопировать код
discord-link-menu-regenerate = Сгенерировать новый код
discord-link-menu-status = Назад в меню Discord
welcome =
    [accent]Добро пожаловать в { $serverName }!
    [lightgray]Введите [accent]/help[lightgray], чтобы увидеть список команд
    [lightgray]Введите [accent]/vote [gray]<y/n>[lightgray], чтобы проголосовать за наказание игрока
    [lightgray]Введите [accent]/votekick [gray]<ID/имя> <причина...>[lightgray], чтобы начать голосование-кик
    [lightgray]Введите [accent]/t [gray]<сообщение...>[lightgray], чтобы отправить сообщение своим союзникам
    [lightgray]Введите [accent]/g [gray]<сообщение...>[lightgray], чтобы отправить сообщение всем серверам
    [lightgray]Введите [accent]/tr [gray]<язык/auto>[lightgray], чтобы включить переводчик
    [lightgray]Введите [accent]/discord[lightgray], чтобы открыть меню Discord и привязать аккаунт
# ==============================================================================
# Chat & Social
# ==============================================================================
commands-t-description = Отправить сообщение только своим товарищам по команде
commands-t-chat = [{ "#" }{ $color }][Команде] [coral]> { $badge }[accent]{ $name }[lightgray]: [white]{ $message }
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
commands-badge-description = Открыть меню бейджей и управлять активным бейджем
commands-tr-success = [accent]Язык переводчика был успешно изменен на [grey]{ $translatorLanguage }[]!
commands-tr-off = [accent]Переводчик [scarlet]выключен[]!
commands-tr-not-found = [scarlet]⚠ Нет такого языка
discord-chat-format = [#5865F2][DISCORD][] [lightgray]| [accent]{ $author }[lightgray] >> [white]{ $message }
global-chat-format = [royal][[[orange]GLOBAL [lightgray](из [accent]{ $server }[])[] { $author }[]]: [white]{ $message }
private-message-received = [sky][ЛС][] [lightgray]от [accent]{ $author } [gray]#{ $pid }[lightgray]: [white]{ $message }
private-message-sent = [sky][ЛС][] [lightgray]для [accent]{ $target } [gray]#{ $pid }[lightgray]: [white]{ $message }
private-message-unread-count =
    [accent]У вас [white]{ $count }[accent] { $count ->
        [one] непрочитанное личное сообщение
        [few] непрочитанных личных сообщения
        [many] непрочитанных личных сообщений
       *[other] непрочитанных личных сообщений
    }.
private-message-join-notification =
    [accent]У вас [white]{ $count }[accent] { $count ->
        [one] непрочитанное личное сообщение
        [few] непрочитанных личных сообщения
        [many] непрочитанных личных сообщений
       *[other] непрочитанных личных сообщений
    }. Используйте [white]/inbox[accent], чтобы открыть { $count ->
        [one] его
       *[other] их
    }.
private-message-block-success = [accent]Личные сообщения от [white]{ $target } [gray]#{ $pid }[accent] теперь заблокированы.
private-message-block-already = [lightgray]Личные сообщения от [white]{ $target } [gray]#{ $pid }[lightgray] уже заблокированы.
private-message-unblock-success = [accent]Личные сообщения от [white]{ $target } [gray]#{ $pid }[accent] снова разрешены.
private-message-unblock-missing = [lightgray]Игрок [white]{ $target } [gray]#{ $pid }[lightgray] не находится в блок-листе.
private-message-menu-title = [orange]{ -xcore } — Входящие
private-message-menu-content =
    [white]Страница [green]{ $page }[] из [green]{ $total }[]
    [white]Непрочитано: [accent]{ $unread }[]
private-message-menu-empty = [lightgray]У вас пока нет личных сообщений.
private-message-menu-entry-unread = [accent]Непрочитано[] [white]{ $author } [gray]#{ $pid }[] [lightgray]({ $time })[]: [white]{ $message }
private-message-menu-entry-read = [gray]Прочитано[] [white]{ $author } [gray]#{ $pid }[] [lightgray]({ $time })[]: [white]{ $message }
private-message-details-title = [orange]{ -xcore } — Сообщение
private-message-details-content =
    [white]От: [accent]{ $author } [gray]#{ $pid }[]
    [white]Время: [accent]{ $time }[]
    [white]Статус: [accent]{ $status }[]
    { "" }
    [white]{ $message }
private-message-status-unread = непрочитано
private-message-status-read = прочитано
private-message-blocked-title = [orange]{ -xcore } — Заблокированные игроки
private-message-blocked-content =
    [white]Страница [green]{ $page }[] из [green]{ $total }[]
    [white]Заблокировано: [accent]{ $count }[]
private-message-blocked-empty = [lightgray]У вас нет заблокированных игроков.
private-message-blocked-entry = [white]{ $target } [gray]#{ $pid }[]
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
commands-login-description = Активировать права админа, если одна из ваших ролей на этом сервере даёт вход как стафф.
commands-login-incorrect-password = [scarlet]⚠ Некорректный пароль!
commands-login-success = [green]Права админа получены
commands-login-confirmed = [green]Discord-доступ администратора подтверждён
commands-login-admin-password-created =
    [green]Пароль админа создан
    [red]Не забудьте свой пароль! Если Вы его забудете, Вам придется обратиться к главному администратору с просьбой сбросить его.
commands-login-request-approval-discord = [accent]Ни одна из ваших ролей на этом сервере не даёт вход как стафф. Попросите роль у администрации и попробуйте снова.
commands-login-verifying = [lightgray]Проверка пароля администратора...
commands-login-already-processing = [scarlet]⚠ Запрос на вход уже обрабатывается. Подождите.
commands-login-rate-limited = [scarlet]⚠ Слишком много неудачных попыток входа. Подождите перед повторной попыткой.
commands-discord-link-created =
    [green]Код привязки Discord создан: [accent]{ $code }[]
    [lightgray]На нашем Discord сервере вызовите slash-команду бота [accent]/link { $code }[] в течение [accent]{ $expireMinutes }[] мин.
    [cyan]{ $discordUrl }
commands-discord-link-confirmed = [green]Discord аккаунт привязан: [accent]{ $discordUsername }[]
commands-discord-link-already-linked = [lightgray]Этот аккаунт Mindustry уже привязан. Используйте [accent]/discord status[] или [accent]/discord unlink[].
commands-discord-link-error = [scarlet]Не удалось создать код привязки Discord. Попробуйте позже.
commands-discord-status-not-linked = [lightgray]Ваш аккаунт не привязан к Discord.
commands-discord-status-linked = [green]Привязанный Discord: [accent]{ $discordUsername }[] [gray]({ $discordId })[]
commands-discord-unlink-not-linked = [lightgray]Ваш аккаунт не привязан к Discord.
commands-discord-unlink-success = [green]Привязка Discord удалена.
commands-logout-description = Выйти. Это [scarlet]отзовет ваши права администратора.
commands-logout-successful = [green]Вы лишены прав администратора
# ==============================================================================
# Moderation (Ban, Mute, Kick)
# ==============================================================================
commands-ban-description = Забанить игрока.
commands-ban-success = { $nickname } [scarlet]забанен
commands-unban-description = Разбанить игрока.
commands-unban-success = { $nickname }[accent] #{ $pid } успешно разбанен
commands-mute-description = Замьютить игрока.
commands-mute-success = [accent]Игрок { $nickname }[accent] успешно замьючен
commands-unmute-description = Размутить игрока.
commands-unmute-success = Успешно размучено игрока { $nickname }
commands-alert-description = Отправить важное объявление игрокам или всем.
commands-toast-description = Отправить всплывающее уведомление игрокам.
commands-announcement-description = Отправить периодическое объявление или следующее из ротации.
commands-audit-description = Открыть журнал аудита действий персонала и модераторов.
ban-content = [scarlet]⚠ Доступ запрещён[]
    [accent]{ $nickname }[white] — вы навсегда заблокированы на этом сервере.
    [orange]» [accent]ID игрока: [white]{ $pid }
    [lightgray]Чтобы обжаловать блокировку, перейдите в Discord-канал [gray]{ support-channel }[]:
    [cyan]{ $discordUrl }
ban-cancelled = [accent]Бан игрока { $nickname }[accent] был отменен
tempban-content = [scarlet]⚠ Доступ запрещён[]
    { $permanent ->
        [true] [accent]{ $nickname }[white] — вы навсегда заблокированы на этом сервере.
       *[false] [accent]{ $nickname }[white] — вы временно заблокированы на этом сервере.
    }
    [orange]» [accent]Блокировка: [white]{ $banId }
    [orange]» [accent]ID игрока: [white]{ $pid }
    { "" }
    [orange]» [accent]Администратор: [white]{ $adminName }
    [orange]» [accent]Причина: [gold]{ $reason }
    { $permanent ->
        [true] [orange]» [accent]{ audit-menu-duration-permanent }[]
       *[false] [orange]» [accent]До разблокировки: { DURATION($duration, style: "full", colored: "true", maxUnits: 2) }
            { "" }[orange]» [accent]Истекает: [white]{ DATETIME($expireDate, dateStyle: "medium", timeStyle: "short") }
    }
    { "" }
    [lightgray]Чтобы обжаловать блокировку, перейдите в Discord-канал [gray]{ support-channel }[]:
    [lightgray]Укажите { $banId } в обращении.
    [cyan]{ $discordUrl }
tempban-player-banned = [scarlet] Админ { $adminName }[scarlet] забанил игрока [gray]'[]{ $playerName }[gray]'
you-are-muted-by =
    [orange]⚠ Чат ограничен[]
    [lightgray]Администратор [accent]{ $adminName }[lightgray] выдал вам мут.
    [orange]» [accent]Причина: [gold]{ $reason }
    [orange]» [accent]До снятия: { DURATION($duration, style: "full", colored: "true", maxUnits: 2) }
you-are-muted =
    [orange]⚠ Чат ограничен[]
    [lightgray]Вы не можете отправлять сообщения, пока действует мут.
    [orange]» [accent]Администратор: [white]{ $adminName }
    [orange]» [accent]Причина: [gold]{ $reason }
    [orange]» [accent]До снятия: { DURATION($duration, style: "full", colored: "true", maxUnits: 2) }
kick-pirated-game = [accent]Обнаружен неавторизованный клиент. [scarlet]Доступ запрещен[]. Пожалуйста, используйте [lime]официальную[] версию из [blue]Steam[], [blue]Google Play[] или [blue]itch.io[].
kick-recently-kicked =
    [accent]Вы недавно были кикнуты с этого сервера.
    Подождите [cyan]{ DURATION($remaining, style: "timer") }[accent] перед повторным входом.
kick-admintools-outdated =
    [green]Требуемая версия AdminTools: [grey]{ $requiredVersion }[]
    [scarlet]Ваша версия AdminTools: [grey]{ $version }[]
    { "" }
    [cyan]Пожалуйста, обновите AdminTools для входа на сервер.
support-channel = #tickets
# ==============================================================================
# Жалобы: уведомления и меню /report
# ==============================================================================
report-menu-title = [orange]{ -xcore } — Жалоба на игрока
report-menu-pick-player = Выберите игрока
report-menu-left-recently = Недавно вышли
report-menu-left-ago = { $minutes } мин назад
report-menu-empty = Нет игроков для жалобы
report-menu-staff-hint = [lightgray]Жалобы на администрацию — в Discord.
report-menu-pick-reason = Выберите причину
report-menu-comment = Комментарий (необязательно)
report-menu-comment-required = Для причины «Другое» нужен комментарий.
report-menu-confirm-title = Подтвердите жалобу
report-menu-confirm-note = [lightgray]Прикрепим последний чат и историю строительства. Ложные жалобы наказываются.
report-menu-next = Далее
report-menu-back = Назад
report-menu-send = Отправить
report-menu-close = Закрыть
report-result-sent = [green]Жалоба отправлена. Спасибо!
report-result-cooldown = [scarlet]Подождите [accent]{ $seconds } с[scarlet] до следующей жалобы.
report-result-same-target = [scarlet]Вы недавно уже жаловались на этого игрока.
report-result-daily-limit = [scarlet]Дневной лимит жалоб исчерпан. Попробуйте завтра.
report-result-invalid-target = [scarlet]На этого игрока нельзя пожаловаться.
report-result-playtime = [scarlet]Чтобы отправлять жалобы, наиграйте ещё [accent]{ $minutes } мин.
report-result-disabled = [scarlet]Жалобы отключены на этом сервере.
report-result-failed = [scarlet]Не удалось отправить жалобу. Попробуйте ещё раз.
report-reason-griefing = Гриферство
report-reason-toxicity = Токсичность
report-reason-cheating = Читерство
report-reason-nsfw = NSFW
report-reason-evasion = Обход бана
report-reason-other = Другое
case-notice-report-received = [lightgray]Жалоба зарегистрирована под номером [accent]#{ $number }[lightgray]. Спасибо!
case-notice-report-resolved = [lightgray]Жалоба [accent]#{ $number }[lightgray] рассмотрена: [green]меры приняты[lightgray]. Спасибо!
case-notice-report-rejected =
    { $reason ->
        [no_violation] [lightgray]Жалоба [accent]#{ $number }[lightgray] рассмотрена: нарушений не обнаружено. Спасибо!
        [no_evidence] [lightgray]Жалоба [accent]#{ $number }[lightgray] рассмотрена: доказательств недостаточно. Спасибо!
        [already_handled] [lightgray]Жалоба [accent]#{ $number }[lightgray] закрыта: с этим случаем уже разобрались. Спасибо!
        [duplicate] [lightgray]Жалоба [accent]#{ $number }[lightgray] закрыта: об этом уже сообщили раньше. Спасибо!
        [abuse] [lightgray]Жалоба [accent]#{ $number }[lightgray] отклонена: [scarlet]она сама нарушает правила сервера.
       *[other] [lightgray]Жалоба [accent]#{ $number }[lightgray] рассмотрена: мер не принято. Спасибо!
    }
case-notice-report-not-accepted = [scarlet]Ваша жалоба не принята.
case-notice-appeal-accepted = [lightgray]Апелляция [accent]#{ $number }[lightgray] принята: блокировка снята.
case-notice-appeal-shortened = [lightgray]Апелляция [accent]#{ $number }[lightgray] принята: срок блокировки сокращён.
case-notice-badge-granted = [lightgray]Заявка на значок [accent]#{ $number }[lightgray] одобрена: значок [green]{ $badge }[lightgray] выдан.
case-notice-badge-rejected = [lightgray]Заявка на значок [accent]#{ $number }[lightgray] отклонена.
case-notice-map-accepted = [lightgray]Проверка карты [accent]#{ $number }[lightgray]: карта [green]{ $map }[lightgray] принята.
case-notice-map-rejected = [lightgray]Проверка карты [accent]#{ $number }[lightgray]: карта [scarlet]{ $map }[lightgray] отклонена.
case-notice-complaint-closed = [lightgray]Обращение [accent]#{ $number }[lightgray] закрыто. Спасибо!
# ==============================================================================
# Voting (VoteKick)
# ==============================================================================
commands-votekick-description = Голосование за кик игрока.
commands-vote-description = Проголосовать в текущем активном голосовании
commands-vote-vote-with = [scarlet]⚠ Голосуйте с помощью [orange]/vote <y/n/c>
votekick-vote =
    { $starter } [grey]#[white]{ $starterId }[lightgray] хочет выгнать { $target } [grey]#[white]{ $targetId }[lightgray]. Причина: [orange]{ $reason }[lightgray]. ([accent]{ $votes }[]/[accent]{ $required }[])
    [lightgray]Напишите [orange]/vote <y/n>[], чтобы проголосовать.
votekick-left = { $player }[lightgray] покинул игру. Голос аннулирован. ([accent]{ $votes }[]/[accent]{ $required }[])
votekick-fail = [lightgray]Голосование не состоялось. Недостаточно голосов для изгнания { $target }[lightgray].
votekick-cancelled = [scarlet]Голосование за кик { $target }[scarlet] отменено администратором { $admin }[scarlet].
votekick-success =
    [orange]Голосование успешно. { $target }[orange] изгнан на [scarlet]{ $minutes }[] { $minutes ->
        [one] минуту
        [few] минуты
        [many] минут
       *[other] минуты
    }.
# ==============================================================================
# Maps & RTV
# ==============================================================================
commands-map-description = Статистика конкретной карты
commands-map-title = [orange]{ -xcore } — Карта
commands-map-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $name }[] [sky]{ $authorLabel }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Общее[]
    [gray]Размер: [white]{ $width }x{ $height } [darkgray]|[gray] Голоса: [lime]+{ $like } [darkgray]/[scarlet] -{ $dislike }[]
    { "" }
    [accent]■ Активность[]
    [gray]Сыграно всего: [white]{ $played } [darkgray]|[gray] За год: [white]{ $playedYear }[]
    [gray]Последняя игра: [white]{ $lastPlayed }[]
    [gray]Популярность: [white]{ $popularity } [darkgray]|[gray] Интерес: [white]{ $interest } [darkgray]|[gray] Репутация: [white]{ $reputation }[]
    { "" }
    [accent]■ Длительность партий[]
    [gray]Мин: [white]{ $min } [darkgray]|[gray] Сред: [white]{ $avg } [darkgray]|[gray] Макс: [white]{ $max }[]
commands-maps-description = Список всех карт на этом сервере.
commands-maps-title = [orange]{ -xcore } — Карты
commands-maps-content =
    [gray]Текущая карта: [accent]{ $current }[]
    [white]Страница [green]{ $page }[] из [green]{ $total }[]
commands-maps-current-row = { $name } ★
commands-maps-text-description = Список всех карт на этом сервере.
commands-maps-text-start-content =
    [accent]Текущая карта: []{ $name }[white]
    [orange][gold]Список карт [lightgray]{ $page }[gray]/[lightgray]{ $total }
commands-maps-text-content =
    { "" }
    { $index }. [orange] - [white]{ $name }[orange] | [green]{ $reputation }[orange] | [white]{ $width }x{ $height }[orange] | [white]{ $lastPlayed }[orange] | От: [sky]{ $author }
commands-artv-description = Изменить карту.
commands-artv-map-skipped = { $nickname }[accent] пропустил карту. Следующая карта: { $name }.
commands-artv-event-skipped = { $nickname }[accent] пропустил событие. Следующее событие: { $name }.
commands-rtv-description = Голосование за изменение карты
commands-vnw-description = Голосование за досрочный старт следующей волны
commands-avnw-description = Принудительно запустить следующую волну досрочно.
commands-like-description = Проголосовать за карту (повышает репутацию)
commands-dislike-description = Проголосовать против карты
map-vote-title = [orange]{ -xcore } — [scarlet]ИГРА ОКОНЧЕНА!
map-vote-content =
    { "" }
    Следующая карта: [accent]{ $mapName }[] от [accent]{ $author }[white].
    Новая игра начнется через [accent]{ $seconds }[white] { $seconds ->
        [one] секунду
        [few] секунды
        [many] секунд
       *[other] секунды
    }.
    { "" }
    [cyan]Понравилась эта карта?
map-vote-like = [green]👍 Нравится
map-vote-dislike = [red]👎 Не нравится
map-vote-like-selected = [gray]Вам нравится
map-vote-dislike-selected = [gray]Вам не нравится
map-rtv = [orange]Голосование
map-artv = [red]Мгновенная смена
map-maps = Карты
map-maps-back = К списку карт
current-map = Текущая карта
next-map = Следующая карта

# Map UI Modernized
map-ui-search-hint = Поиск карты или автора...
map-ui-total = [lightgray]Всего карт: [white]{ $count }[]
map-ui-about-title = О карте
map-ui-rtv-title = Смена карты
map-ui-no-maps-found = [lightgray]Карты по вашему запросу не найдены.[]
map-ui-by = от [lightgray]{ $author }[]
map-ui-mode = Режим: [white]{ $mode }[]
map-ui-loading = [gray]Загрузка...[]
map-ui-no-preview = [gray]Нет превью[]
map-ui-dimensions = [gray]Размеры: [white]{ $width } x { $height }[]
map-ui-total-plays = [gray]Всего сыграно: [white]{ $played } [lightgray]({ $playedYear } за год)[]
map-ui-last-played = [gray]Последняя игра: [white]{ $lastPlayed }[]
map-ui-description = [gray]Описание: [lightgray]{ $description }[]
map-ui-no-description = [gray]Описание: [lightgray]Описание отсутствует.[]

map-ui-col-duration = Длительность
map-ui-duration-min = [gray]Мин: [white]{ $value }[]
map-ui-duration-avg = [gray]Сред: [white]{ $value }[]
map-ui-duration-max = [gray]Макс: [white]{ $value }[]

map-ui-col-popularity = Популярность
map-ui-popularity-score = [gray]Рейтинг: [white]{ $value }[]
map-ui-popularity-pop = [gray]Популярность: [white]{ $value }[]
map-ui-popularity-interest = [gray]Интерес: [white]{ $value }[]

map-ui-col-community = Сообщество
map-ui-community-approval = [gray]Одобрение: [green]{ $rate }%[]

map-ui-btn-like = Лайк ({ $count })
map-ui-btn-dislike = Дизлайк ({ $count })

map-ui-rtv-active-status = [accent]● Идёт голосование: [white]{ $votes }/{ $required }[] [gray](осталось [white]{ $seconds } с[gray])[]
map-ui-rtv-vote-yes = Голосовать за карту
map-ui-rtv-start = Голосовать за эту карту
map-ui-admin-rtv = Сменить карту сразу
map-ui-admin-rtv-confirm = Нажмите ещё раз

gamemode-survival = Выживание
gamemode-attack = Атака
gamemode-pvp = PvP
gamemode-sandbox = Песочница
gamemode-editor = Редактор
rtv-vote =
    { $nickname }[lightgray] проголосовал за смену текущей карты на [orange]{ $mapName }[lightgray]. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Напишите [orange]y[] или [orange]n[], чтобы проголосовать.
rtv-left = { $nickname }[lightgray] покинул игру. Голос за смену карты аннулирован. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
rtv-fail = [lightgray]Голосование провалилось. Не хватило голосов, чтобы изменить текущую карту на [orange]{ $mapName }[].
rtv-success = [orange]Голосование завершено успешно. Карта [accent]{ $mapName }[] будет загружена через [accent]{ $mapLoadDelay }[] { $mapLoadDelay ->
    [one] секунду
    [few] секунды
    [many] секунд
   *[other] секунды
}…
rtv-cancelled = [lightgray]Голосование за смену карты на [orange]{ $mapName }[lightgray] было отменено администратором { $admin }.
vnw-vote =
    { $nickname }[lightgray] проголосовал за досрочный запуск волны [orange]{ $wave }[lightgray]. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Напишите [orange]y[] или [orange]n[], чтобы проголосовать.
vnw-left = { $nickname }[lightgray] вышел. Его голос за досрочный запуск волны [orange]{ $wave }[lightgray] отменён. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
vnw-fail = [lightgray]Голосование не прошло. Недостаточно голосов для досрочного запуска волны [orange]{ $wave }[].
vnw-success = [orange]Голосование прошло. Волна [accent]{ $wave }[] запускается прямо сейчас.
vnw-cancelled = [lightgray]Голосование за досрочный запуск волны [orange]{ $wave }[lightgray] было отменено администратором { $admin }.
vnw-obsolete = [lightgray]Волна [orange]{ $wave }[lightgray] уже началась, поэтому результат голосования больше не нужен.
# ==============================================================================
# Statistics & Ranks & Players
# ==============================================================================
commands-player-description = Статистика игрока
commands-settings-description = Открыть настройки игрока
player-menu-player = Игрок
player-menu-player-title = [orange]{ -xcore } — Статистика игрока
player-menu-player-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $customNickname }[] [gray]#{ $pid }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Профиль[]
    [gray]Имя: [white]{ $nickname } [darkgray]|[gray] Админ: [lime]{ $admin }[]
    [gray]Бейдж: [white]{ $activeBadge } [darkgray]|[gray] Системный: [coral]{ $systemBadge }[]
    [gray]Регистрация: [white]{ $accountCreated }[]
    { "" }
    [accent]■ Игровые рейтинги[]
    [gray]Время в игре: [white]{ $totalPlayTime }[]
    [gray]MiniPvP: [sky]{ $pvpRating } [darkgray]|[gray] Бывший MiniHexed: [sky]{ $hexedRankName } [gray]({ $hexedPoints } очк.) [darkgray]|[gray] Топ: [accent]{ $hexedTopRank }[]
    { "" }
    [accent]■ Матчи: [white]{ $gamesPlayed } [gray]игр [darkgray]|[lime] { $gamesWon } [gray]побед [darkgray]|[sky] { $winRate }% [gray]винрейт[]
    [gray]• [white]PvP: { $pvpSummary }[]
    [gray]• [white]Surv: { $survivalSummary }[]
    [gray]• [white]Бывший MiniHexed: { $hexedSummary }[]
    { "" }
    [accent]■ Боевая эффективность[]
    [gray]Блоки (Стр/Разб/Уничт): [lime]{ $blocksBuilt } [darkgray]/ [orange]{ $blocksDeconstructed } [darkgray]/ [scarlet]{ $blocksDestroyed }[]

# Modern Player Stats UI
player-stats-title = [orange]{ -xcore } — Профиль игрока
player-stats-tab-overview = Обзор
player-stats-tab-stats = Статистика
player-stats-tab-matches = Матчи
player-stats-tab-blocks = Блоки
player-stats-tab-players = Онлайн ({ $count })

player-stats-status-online = [lime]● Онлайн[]
player-stats-status-offline = [gray]○ Офлайн[]
player-stats-no-bio = [gray]Описание отсутствует.[]
player-stats-loading = [lightgray]Загрузка статистики...[]
player-stats-no-stats = [gray]Нет записей матчей[]
player-stats-filter-all = Фильтр: Все
player-stats-filter-admins = Фильтр: Только админы
player-stats-filter-non-admins = Фильтр: Обычные
player-stats-refresh = Обновить
player-stats-combat-efficiency = Боевая и строительная эффективность
player-stats-waves-summary = [gray]волны: макс [lime]{ $best }[], ср [white]{ $avg }[]
player-stats-hexed-top-placement = [gray]лучшее [accent]#{ $best }[], топ-3: [sky]{ $top3 }[]
player-stats-max-rank = [gold]★ МАКСИМАЛЬНЫЙ РАНГ ★[]
player-stats-max-league = [gold]★ МАКСИМАЛЬНАЯ ЛИГА ★[]
player-stats-hexed-wins-left = [lightgray]{ $wins } побед до [white]{ $rank }[]
player-stats-league-elo-left = [lightgray]{ $elo } ELO до [white]{ $league }[]
player-stats-ratio-legend = [lightgray]Соотношение блоков[]

player-stats-account-created = [gray]Регистрация:[]
player-stats-play-time = [gray]Время в игре:[]
player-stats-legacy-pvp-rating = [gray]Прошлый PvP:[]
player-stats-hexed-rank = [gray]Бывший Hexed:[]
player-stats-hexed-leaderboard = [gray]Топ Hexed:[]

player-stats-total-games = [gray]Всего игр:[]
player-stats-victories = [gray]Победы:[]
player-stats-survival-summary = [gray]Выживание:[]
player-stats-hexed-summary = [gray]Бывший Hexed:[]

player-stats-blocks-built = [gray]Построено:[]
player-stats-blocks-deconstructed = [gray]Разобрано:[]
player-stats-blocks-destroyed = [gray]Уничтожено:[]
player-stats-units-produced = [gray]Юнитов создано:[]
player-stats-units-lost = [gray]Юнитов потеряно:[]

player-stats-games-played-value = [white]{ $count }[] [gray]игр[]
player-stats-victories-value = [lime]{ $wins }[] [gray]побед[]  [darkgray]|[]  [sky]{ $winRate }%[] [gray]винрейт[]
player-stats-hexed-points = [gray]({ $points } очк.)[]

player-stats-btn-settings = Настройки
player-stats-btn-matches = Матчи
player-stats-btn-audit = Аудит
player-stats-btn-players = Онлайн
player-stats-btn-close = Закрыть
player-stats-admin-tag = [coral]<Админ>[]
player-menu-players = Онлайн игроки
player-menu-players-title = [orange]{ -xcore } — Онлайн игроки
player-menu-players-content = [white]Страница [green]{ $page }[] из [green]{ $total }[]
player-menu-players-empty = Онлайн игроков не найдено
player-menu-players-row = [white]{ $nickname } [gray](PID: { $pid })[]
player-menu-settings = Настройки
player-menu-settings-title = [orange]{ -xcore } — Настройки игрока
player-menu-settings-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $displayNickname }[] [gray]#{ $pid }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Профиль[]
    [gray]Имя: [white]{ $nickname } [darkgray]|[gray] Отображается: [lime]{ $customNickname }[]
    [gray]Бейдж: [white]{ $activeBadge } [darkgray]|[gray] Системный: [coral]{ $systemBadge }[]
    { "" }
    [accent]■ Видимость[]
    [gray]Лидерборд: [white]{ $leaderboard }[]
    { "" }
    [accent]■ Чат[]
    [gray]Global: [white]{ $globalChat } [darkgray]|[gray] Discord: [white]{ $discordRelay }[]
    [gray]Переводчик: [white]{ $translatorLanguage }[]
    { "" }
    [accent]■ Локализация[]
    [gray]Язык: [white]{ $language }[]
player-menu-settings-chat = Настройки чата
player-menu-settings-chat-title = [orange]{ -xcore } — Настройки чата

# Modern Player Settings Form
player-settings-tab-profile = Профиль
player-settings-tab-chat = Чат
player-settings-tab-badges = Значки
player-settings-chat-preview = Превью в чате
player-settings-chat-preview-sample = Пример
player-settings-chat-preview-message = Всем привет!
player-settings-symbol-color-mode = Цвет бейджа
player-settings-badges-my = Мои значки
player-settings-badges-all = Все значки
player-settings-badge-equip = Надеть
player-settings-badge-unequip = Снять
player-settings-badge-preview = Превью
player-settings-badge-previewing = В превью
player-settings-translator-lang = Переводчик чата
player-settings-translator-off = Выкл
player-settings-badges-empty = У вас пока нет открытых значков.
player-settings-manage-badges = К значкам
player-settings-global-chat = Global чат
player-settings-discord-relay = Discord relay
player-settings-leaderboard = Показывать таблицу лидеров
player-settings-language = Язык
player-settings-saved = [lime]Настройки сохранены![]
player-settings-reset-feedback = [lightgray]Имя сброшено.[]
player-settings-edit-badges = [accent]Редактировать[]
player-settings-tab-language = Язык
player-settings-identity = Имя и описание
player-settings-interface = Экран
player-settings-leaderboard-hint = Список лучших игроков поверх игры. Работает на Mini-PvP.
player-settings-global-chat-hint = Сообщения игроков с других серверов XCore.
player-settings-discord-relay-hint = Сообщения из Discord-канала сервера в игровом чате.
player-settings-translator-hint = Сообщения других игроков переводятся на выбранный язык.
player-settings-language-hint = Язык меню и сообщений сервера. Auto берёт язык вашей игры.
player-menu-settings-chat-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [accent]■ Видимость чата[]
    [gray]Global чат: [white]{ $globalChat }[]
    [gray]Discord relay: [white]{ $discordRelay }[]
    { "" }
    [accent]■ Перевод[]
    [gray]Язык переводчика: [white]{ $translatorLanguage }[]
player-menu-settings-translator-title = [orange]{ -xcore } — Выбор языка переводчика
player-menu-settings-language-title = [orange]{ -xcore } — Выбор языка
player-menu-settings-customNickname = Изменить имя
player-menu-settings-customNickname-title = [orange]{ -xcore } — Изменить имя
player-menu-settings-customNickname-message = [lightgray]Оставьте пустым, чтобы сбросить
player-menu-settings-customNickname-reset = [scarlet]Сбросить имя
player-menu-settings-description = Изменить описание
player-menu-settings-description-title = [orange]{ -xcore } — Изменить описание
player-menu-settings-badges = Бейджи
player-menu-settings-global-chat-on = [green]Global чат
player-menu-settings-global-chat-off = [red]Global чат
player-menu-settings-discord-relay-on = [green]Discord relay
player-menu-settings-discord-relay-off = [red]Discord relay
player-menu-player-max-rank = Максимальный ранг достигнут
player-menu-player-hexed-progress = [gray]До [white]{ $nextRankName } [gray]осталось побед: [accent]{ $requiredPoints }[]
player-menu-player-no-mode-stats = [gray]нет данных[]
player-menu-player-pvp-summary = [gray]игр [white]{ $gamesPlayed }[], побед [lime]{ $gamesWon }[], [sky]{ $winRate }%[]
player-menu-player-survival-summary = [gray]волны: макс [lime]{ $bestWave }[], ср [white]{ $averageWave }[] [gray](забегов: { $gamesPlayed })[]
player-menu-player-hexed-summary = [gray]матчей [white]{ $gamesPlayed }[], топ-1 [lime]{ $gamesWon }[], лучшее место [accent]#{ $bestPlacement }[]
player-menu-time-days = { $value }д
player-menu-time-hours = { $value }ч
player-menu-time-minutes = { $value }м
settings-language-label = Язык: [green]{ $lang }[]
settings-translator-label = Переводчик: [green]{ $lang }[]
badge-menu-title = [orange]{ -xcore } — Бейджи
badge-menu-content =
    [white]Системный бейдж: [green]{ $systemBadge }[]
    [white]Активный бейдж: [green]{ $activeBadge }[]
    [white]Цвет символа: [green]{ $symbolColorMode }[]
badge-menu-empty = [lightgray]У вас пока нет открытых бейджей.
badge-menu-row = [white]{ $badge }[] [gray]-[] { $description }
badge-menu-symbol-color-button = Цвет символа: [green]{ $mode }[]
badge-menu-symbol-color-title = [orange]{ -xcore } — Цвет символа бейджа
badge-menu-symbol-color-content =
    [white]Текущий режим: [green]{ $mode }[]
    [lightgray]Выберите, как должен окрашиваться символ бейджа.
badge-menu-symbol-color-default = Стандартный цвет бейджа
badge-menu-symbol-color-player-color = Символ цвета игрока
badge-menu-symbol-color-player-color-full = Весь бейдж цвета игрока
badge-menu-view-all = Все бейджи
badge-menu-all-title = [orange]{ -xcore } — Все бейджи
badge-menu-all-content = [lightgray]Просмотр всех бейджей, их статуса и описания.
badge-menu-all-row = [white]{ $badge }[] [gray]-[] [accent]{ $state }[] [gray]-[] { $description }
badge-clear-button = Снять активный бейдж
badge-state-system = Системный
badge-state-system-active = Системный активен
badge-state-active = Активен
badge-state-unlocked = Открыт
badge-state-locked = Закрыт
badge-set-success = [accent]Активный бейдж установлен: [green]{ $badge }[].
badge-clear-success = [accent]Активный бейдж снят.
badge-grant-success = [accent]Бейдж [green]{ $badge }[] выдан игроку [green]{ $nickname }[][gray]#{ $pid }[].
badge-revoke-success = [accent]Бейдж [green]{ $badge }[] снят у игрока [green]{ $nickname }[][gray]#{ $pid }[].
badge-already-unlocked = [scarlet]⚠ Бейдж [accent]{ $badge }[scarlet] уже открыт.
badge-not-owned = [scarlet]⚠ У игрока нет бейджа [accent]{ $badge }[scarlet].
error-badge-not-found = [scarlet]⚠ Бейдж [accent]{ $badge }[scarlet] не найден.
error-badge-not-unlocked = [scarlet]⚠ Бейдж [accent]{ $badge }[scarlet] не открыт.
error-badge-not-selectable = [scarlet]⚠ Бейдж [accent]{ $badge }[scarlet] нельзя выбрать вручную.
badge-admin-name = Админ
badge-admin-description = Автоматический бейдж для администраторов.
badge-developer-name = Разработчик
badge-developer-description = Выдаётся разработчикам XCore.
badge-translator-name = Переводчик
badge-translator-description = Выдаётся тем, кто помогает с переводом XCore.
badge-map-maker-name = Мапмейкер
badge-map-maker-description = Выдаётся авторам карт, используемых на сервере.
badge-contributor-name = Контрибьютор
badge-contributor-description = Выдаётся за вклад в XCore или сообщество.
badge-bug-finder-name = Багхантер
badge-bug-finder-description = Выдаётся за регулярные качественные баг-репорты.
badge-event-winner-name = Победитель ивента
badge-event-winner-description = Выдаётся победителям специальных серверных событий.
badge-veteran-name = Ветеран
badge-veteran-description = Выдаётся уважаемым старым игрокам.
badge-season-champion-name = Чемпион сезона
badge-season-champion-description = Выдаётся за место на подиуме рейтингового сезона.
commands-lb-description = Включить/выключить таблицу лидеров
commands-lb-success =
    { $leaderboardEnabled ->
        [true] [accent]Таблица лидеров [green]включена
       *[other] [accent]Таблица лидеров [scarlet]выключена
    }
leaderboard = [blue]Таблица лидеров
commands-observer-description = Перейти в режим наблюдателя. Это уничтожит вашего текущего юнита и переведёт вас в команду наблюдателей.
commands-rank-description = Показать ваш ранг или ранг другого игрока.
commands-rank-content =
    { $nickname }
    { $rankTag } [accent]{ $rankName }
    [gold]Побед: { $points }/{ $requiredPoints }
commands-ranks-description = Показывает информацию о рангах
commands-ranks-content =
    { $rankTag } [accent]{ $rankName }
    [gold]Требования: [grey]{ $requiredPoints } [accent]побед[]
commands-ranks-footer = Количество побед увеличивается только при победе над игроком вашего ранга или выше.
commands-top-description = Топ-игроки
commands-season-description = Рейтинговый сезон: сколько осталось, ваше место и победители прошлого
commands-matches-description = Ваши рейтинговые матчи: итоги, изменения рейтинга и с кем вы играли.
commands-pvp-description = Гайд по MiniPvP: раунд, правила сервера и как считается рейтинг.
commands-top-hexed-content = [orange]{ $index }. { $nickname }[accent]: [blue]{ $rankName } [cyan]{ $points } []побед
commands-top-pvp-content = [orange]{ $index }. { $nickname }[accent]: [cyan]{ $rating }
top-menu-title = [orange]{ -xcore } — Топ игроков: [accent]{ $category }
top-menu-content =
    [lightgray]Нажмите на игрока, чтобы открыть его профиль.[]
    [lightgray]Страница [green]{ $page }[]/[green]{ $totalPages }[] [gold]•[] [lightgray]Игроков: [green]{ $totalEntries }[]
    { $selfRankLine }
top-menu-empty =
    [accent]Категория: [green]{ $category }[]
    [gray]Игроков пока нет.
top-menu-categories-title = [orange]{ -xcore } — Категория топа
top-menu-categories-content =
    [lightgray]Выберите параметр рейтинга.[]
    [lightgray]Текущая: [green]{ $category }[]
top-menu-category-button = [accent]Категория: [green]{ $category }[]
top-menu-category-mini-pvp = MiniPvP
top-menu-category-playtime = Время игры
top-menu-category-hexed = Hexed
top-menu-self-rank-known = [lightgray]Ваша позиция: [accent]#{ $rank }[]
top-menu-self-rank-unknown = [lightgray]Ваша позиция: [gray]не найдена[]
top-menu-entry-mini-pvp = { $rankLabel } { $leagueIcon } [accent]{ $nickname }[] [gray]—[] [sky]{ $value }[]
top-menu-entry-playtime = { $rankLabel } [accent]{ $nickname }[] [gray]—[] [green]{ $value }[]
top-menu-entry-hexed = { $rankLabel } [accent]{ $nickname }[] [gray]—[] [violet]{ $rankName }[] [gold]•[] [cyan]{ $value }[]
top-menu-total-count = { $count } игроков
top-menu-btn-find-self = Найти себя
top-menu-on-this-page = на этой странице
top-menu-unranked = Вы пока не квалифицированы в этой категории
top-menu-self-rank-line = Ваша позиция: { $rank }
top-menu-score-points = { $points } очк.
top-menu-score-minutes = { $time }
# ==============================================================================
# Game Modes (Hexed, PvP, Surrender, AI)
# ==============================================================================
commands-surrender-description = Сдаться в Hexed. Это уничтожит вашу текущую команду, уберёт юнита и переведёт вас в команду наблюдателей.
commands-surrender-success = [green]Вы сдались и теперь наблюдаете за игрой
commands-observer-success = [green]Теперь вы наблюдаете за игрой
commands-observer-exit-success = [green]Вы больше не наблюдаете за игрой
commands-ai-description = Контролировать ИИ
commands-ai-usage = [red]attack(a) []или [accent]idle(i)
hexed-popup = [blue]{ DURATION($remaining, style: "timer") }[] до конца игры
hexed-eliminated = { $nickname } [gold]уничтожен!
hexed-leaderboard-content = [orange]{ $index }. { $nickname }[accent]: [cyan]{ $hexes } [accent]хексов
hexed-ranks-newbie = Новичок
hexed-ranks-regular = Базовый
hexed-ranks-advanced = Продвинутый
hexed-ranks-veteran = Ветеран
hexed-ranks-davastator = Разрушитель
hexed-ranks-the_legend = Легенда
hexed-game-over-header = Игра окончена. Победители:
hexed-game-over-winner-row =
    [orange]{ $index }. { $name }[][accent]: [cyan]{ $cores } { $cores ->
        [one] гекс
        [few] гекса
        [many] гексов
       *[other] гекса
    }
hexed-game-over-no-winners = Игра окончена. К сожалению, победители не найдены.
hexed-game-over-restart = Новая игра через 10 секунд…
rating_league_scrap = Лом
rating_league_copper = Медь
rating_league_lead = Свинец
rating_league_graphite = Графит
rating_league_silicon = Кремний
rating_league_titanium = Титан
rating_league_thorium = Торий
rating_league_plastanium = Пластан
rating_league_phase_fabric = Фазовая ткань
rating_league_surge_alloy = Кинетический сплав
pvp-team-won = Ваша команда победила. Ваш рейтинг увеличился на { $increased }
pvp-team-lose = Ваша команда проиграла. Ваш рейтинг снижен на { $reduced }
pvp-match-settlement-win = [accent]■ Итоги матча: [green]Победа![] Ваш рейтинг: [stat]{ $oldRating }[] -> [stat]{ $newRating }[] [green](+{ $delta })[] { $leagueIcon } { $leagueName }
pvp-match-settlement-loss = [accent]■ Итоги матча: [scarlet]Поражение![] Ваш рейтинг: [stat]{ $oldRating }[] -> [stat]{ $newRating }[] [scarlet]({ $delta })[] { $leagueIcon } { $leagueName }
pvp-match-settlement-draw = [accent]■ Итоги матча: [yellow]Ничья![] Ваш рейтинг: [stat]{ $oldRating }[] -> [stat]{ $newRating }[] [yellow]({ $delta })[] { $leagueIcon } { $leagueName }
pvp-match-settlement-exempt = [accent]■ Итоги матча: [lightgray]Рейтинг не изменился (недостаточно активного времени в матче).[]
pvp-match-settlement-details = [lightgray]Подробнее о матче: [white]/matches[][]
season-ending-soon = [accent]■ [white]{ $ladder }[]: сезон { $number } закончится через [stat]{ $remaining }[]. Успейте сыграть последние матчи!
season-started = [accent]■ [white]{ $ladder }[]: сезон { $previous } завершён. Начался сезон { $number }!
season-reset-soft = [lightgray]Рейтинги подтянуты к стартовому значению, счётчики матчей обнулены.[]
season-reset-hard = [lightgray]Все начинают заново со стартового рейтинга.[]
season-reset-none = [lightgray]Рейтинги сохранены; счётчики матчей обнулены.[]
season-title = Сезон { $number }
top-menu-scope-current = { $season } [lightgray]· до конца { $remaining }[]
top-menu-scope-past = { $season } [gray]· { $from } – { $to }[]
ladder-profile-standing = { $leagueIcon } [white]{ $league }[] [accent]{ $rating } ELO[]
ladder-profile-headline = [accent]{ $icon }[] [lightgray]{ $ladder }:[] { $standing }
ladder-profile-unplaced = [gray]в этом сезоне рейтинговых матчей нет[]
ladder-profile-matches = [lightgray]Матчи:[] [white]{ $matches }[]
ladder-profile-wins = [lightgray]Победы:[] [white]{ $wins }[] [gray]({ $rate }%)[]
ladder-profile-rank = [lightgray]Место:[] [accent]#{ $rank }[]
ladder-profile-peak = [lightgray]Пик:[] [white]{ $rating }[]
ladder-profile-season = [lightgray]{ $season } · до конца [white]{ $remaining }[][]
ladder-profile-season-closing = [lightgray]{ $season } · подводятся итоги[]
ladder-profile-history = [gray]{ $season } — { $rank }, { $league }, { $rating } ELO[]
season-menu-title = Рейтинговые сезоны
season-menu-card-title = { $ladder } — { $season }
season-menu-ends = [lightgray]До конца [white]{ $remaining }[] ({ $date })[]
season-menu-closing = [lightgray]Сезон завершён, подводятся итоги.[]
season-menu-participants = [lightgray]Игроков в сезоне: [white]{ $count }[][]
season-menu-you = [lightgray]Вы:[] { $standing }
season-menu-previous = [lightgray]{ $season } — победители:[]
season-menu-podium-entry = [gold]{ $place }.[] [white]{ $name }[] [gray]—[] { $league } [accent]{ $rating }[]
season-menu-open-top = Таблица лидеров
season-menu-prizes = [lightgray]Призы этого сезона:[]
season-menu-prize-entry = [gold]{ $places }.[] [white]{ $prize }[]
season-menu-podium-prizes = [gray] · [gold]{ $prizes }[]
prize-grant-line = [lightgray]{ $season } · приз:[] [gold]{ $prize }[] [gray]({ $status })[]
prize-status-pending = ждёт выдачи
prize-status-granted = получен
prize-status-delivered = выдан
prize-status-failed = администратор разберётся
season-menu-empty = Рейтинговых сезонов пока нет.
pvp-hud-status = [accent]MiniPvP[] | [stat]Живые:[] { $teams } | [gray]{ $time }[]
pvp-leaderboard-content = [orange]{ $index }. { $nickname }[accent]:[cyan] { $rating } [accent]рейтинг
pvp-you-spectator = [scarlet]Вы выбыли. Пожалуйста, дождитесь следующей игры.

# MiniPvP guide (/pvp info)
pvp-guide-title = Как здесь играть
pvp-guide-subtitle = Обычный PvP Mindustry: команды строят базы и уничтожают ядра друг друга. Здесь собрано, чем этот сервер отличается и как считается рейтинг.
pvp-guide-hint = Открыть снова: [accent]/pvp info[]
pvp-guide-join-hint = [lightgray]Правила MiniPvP и рейтинг:[] [accent]/pvp info[]
pvp-guide-menu-button = Как играть в MiniPvP
pvp-guide-tab-round = Раунд
pvp-guide-tab-rules = Правила
pvp-guide-tab-rating = Рейтинг
pvp-guide-stat-minutes = минут идёт раунд
pvp-guide-stat-teams = команд в игре
pvp-guide-stat-players = игроков в командах
pvp-guide-stat-zone = тайлов вокруг ядра
pvp-guide-stat-unitcap = лимит юнитов
pvp-guide-stat-banned = блоков под запретом
pvp-guide-stat-start = рейтинг на старте
pvp-guide-stat-top = лучшие игроки
pvp-guide-stat-matches = история матчей

pvp-guide-now-title = Сейчас
pvp-guide-now-map = Карта: [accent]{ $map }[]
pvp-guide-now-players = { $count } { $count ->
        [one] игрок
        [few] игрока
        [many] игроков
       *[other] игрока
    }
pvp-guide-win-title = Как победить
pvp-guide-win-text = Уничтожь все ядра соперников. Побеждает последняя команда, у которой остались ядра и игроки. Команда, в которой никого не осталось, выбывает.
pvp-guide-out-title = Выбыл: смотришь до конца
pvp-guide-out-text =
    Твоя команда потеряла последнее ядро: ты наблюдаешь до следующего раунда.
    Вышел посреди раунда и вернулся: попадёшь в ту же команду.
pvp-guide-commands-title = Команды
pvp-guide-cmd-info = это окно
pvp-guide-cmd-top = таблица лидеров
pvp-guide-cmd-season = сезон и призы
pvp-guide-cmd-matches = твои матчи и рейтинг за них
pvp-guide-cmd-rtv = голосовать за смену карты
pvp-guide-cmd-observer = смотреть со стороны

pvp-guide-zone-title = Не строй у чужого ядра
pvp-guide-zone-text = Ближе [accent]{ $tiles }[] тайлов к вражескому ядру строить нельзя. Чтобы продвинуться, сначала уничтожь это ядро.
pvp-guide-zone-polygon-title = Земля поделена между ядрами
pvp-guide-zone-polygon-text = Строить можно только там, где ближайшее ядро твоё. Чтобы занять чужую землю, уничтожь ядро, к которому она относится.
pvp-guide-unitcap-title = Лимит юнитов
pvp-guide-unitcap-text = Команда начинает с лимитом [accent]{ $cap }[], и каждое ядро поднимает его.
pvp-guide-unitcap-fixed-title = Лимит юнитов
pvp-guide-unitcap-fixed-text = У команды не больше [accent]{ $cap }[] юнитов, сколько бы ядер у неё ни было.
pvp-guide-multipliers-title = Другой баланс
pvp-guide-multipliers-text = На этой карте числа отличаются от обычных:
pvp-guide-rule-unit-damage = Урон юнитов
pvp-guide-rule-unit-health = Прочность юнитов
pvp-guide-rule-unit-cost = Цена юнитов
pvp-guide-rule-unit-build-speed = Скорость производства юнитов
pvp-guide-rule-block-damage = Урон построек
pvp-guide-rule-block-health = Прочность построек
pvp-guide-rule-build-cost = Цена построек
pvp-guide-rule-build-speed = Скорость строительства
pvp-guide-banned-blocks-title = Запрещённые постройки
pvp-guide-banned-units-title = Запрещённые юниты
pvp-guide-other-title = Ещё
pvp-guide-other-fog = Туман войны: видно только то, что видят твои постройки и юниты.
pvp-guide-other-capture = Уничтоженное ядро переходит к тому, кто его уничтожил.
pvp-guide-other-reactors = Реакторы не взрываются.
pvp-guide-vanilla-title = Обычные правила
pvp-guide-vanilla-text = На этой карте правила как в обычном PvP Mindustry.

pvp-guide-elo-title = Рейтинг ELO
pvp-guide-elo-text = Все начинают с [accent]{ $start }[]. Победа поднимает рейтинг, поражение опускает. Насколько, зависит от рейтинга твоей команды и соперников: победа над сильными даёт больше.
pvp-guide-leave-title = Выход не спасает от поражения
pvp-guide-leave-text = Если твоя команда проиграла, ты теряешь рейтинг полностью, даже если вышел или ушёл в наблюдатели раньше. Без потерь, только если ты пробыл в игре меньше [accent]{ $seconds }[] с.
pvp-guide-unrated-title = Когда рейтинг не меняется
pvp-guide-unrated-text =
    Раунд короче [accent]{ $seconds }[] с или в нём меньше [accent]{ $players }[] игроков с рейтингом: раунд не засчитывается.
    Победителю нужно отыграть хотя бы [accent]{ $winner }%[] раунда, иначе рейтинг не растёт.
    Зашёл поздно в проигравшую команду и отыграл меньше [accent]{ $late }%[] раунда: рейтинг не падает.
pvp-guide-seasons-title = Лиги и сезоны
pvp-guide-seasons-text = Рейтинг определяет лигу. Рейтинг идёт по сезонам: в конце сезона лучшие получают призы. Итоги каждого матча: [accent]/matches[].
# ==============================================================================
# Events & Notifications
# ==============================================================================
player-joined-pid = { $nickname } [grey]#[white]{ $pid }[grey] [accent]присоединился.
player-joined-username = { $nickname } [grey]@[white]{ $username }[grey] [accent]присоединился.
player-joined-both = { $nickname } [accent]@{ $username } [grey]#[white]{ $pid }[grey] [accent]присоединился.
player-joined-none = { $nickname } [accent]присоединился.

player-left-pid = { $nickname } [grey]#[white]{ $pid }[grey] [accent]покинул игру.
player-left-username = { $nickname } [grey]@[white]{ $username }[grey] [accent]покинул игру.
player-left-both = { $nickname } [accent]@{ $username } [grey]#[white]{ $pid }[grey] [accent]покинул игру.
player-left-none = { $nickname } [accent]покинул игру.

player-settings-identity-mode = [accent]Отображение идентификатора в оповещениях:[]
player-settings-identity-mode-pid = Только номер (#12)
player-settings-identity-mode-username = Только имя (@Steve)
player-settings-identity-mode-both = Имя и номер (@Steve #12)
player-settings-identity-mode-none = Скрыть все

notification-votekick-playtime =
    [accent]Поздравляем! Вы отыграли [lightgray]{ $votekickPlayTime }[] { $votekickPlayTime ->
        [one] минуту
        [few] минуты
        [many] минут
       *[other] минуты
    } и теперь можете начать голосование за кик игрока.
notification-global-chat-playtime =
    [accent]Поздравляем! Вы отыграли [lightgray]{ $globalChatPlayTime }[] { $globalChatPlayTime ->
        [one] минуту
        [few] минуты
        [many] минут
       *[other] минуты
    } и теперь можете писать в глобальный чат.
    [lightgray]Введите [accent]/g [gray]<сообщение...>[lightgray], чтобы отправить сообщение.
notification-admin-kick = { $admin }[accent] кикнул { $target }[].
notification-admin-wave-skip = { $admin }[accent] пропустил волну.
server-restart-countdown =
    Перезагрузка через { $seconds ->
        [one] { $seconds } секунду
        [few] { $seconds } секунды
        [many] { $seconds } секунд
       *[other] { $seconds } секунды
    }
like-map-success = [green]Вам понравилась эта карта!
like-map-changed = [green]Вы изменили мнение на лайк!
dislike-map-success = [orange]Вам не понравилась эта карта.
dislike-map-changed = [orange]Вы изменили мнение на дизлайк.
like-event-success = [green]Вам понравилось это событие!
like-event-changed = [green]Вы изменили мнение на лайк!
dislike-event-success = [orange]Вам не понравилось это событие.
dislike-event-changed = [orange]Вы изменили мнение на дизлайк.

# ==============================================================================
# Events (Server)
# ==============================================================================

commands-event-description = Меню управления событиями.
commands-events-description = Список всех событий на серверах.
event-events = События
event-menu-main = Основные события
event-menu-event = Событие
event-menu-create-start-default = Событие игрока { $playerName }
event-menu-create-start-map = Создать событие для этой карты
event-ui-title = События
event-ui-server-time = Время сервера: { $zone }
event-ui-tab-upcoming = Скоро
event-ui-tab-major = Крупные
event-ui-tab-past = Прошедшие
event-ui-tab-mine = Мои
event-ui-now-running = Идёт сейчас
event-ui-now-vote = Голосование
event-ui-empty-upcoming = Событий пока не запланировано. Создайте первое!
event-ui-empty-major = Крупных событий пока не запланировано.
event-ui-empty-past = Ещё ни одно событие не завершилось.
event-ui-empty-mine = Вы пока не создали ни одного события.
event-ui-create = Создать событие
event-ui-load-failed = Не удалось загрузить события. Попробуйте ещё раз.
event-ui-state-active = Идёт сейчас
event-ui-state-active-until = Идёт до { $time }
event-ui-state-finished = Завершено
event-ui-state-finished-at = Завершено { $date }
event-ui-state-unscheduled = Без даты
event-ui-major = Крупное
event-ui-major-hint = Отмечено звездой и собрано на отдельной вкладке
event-ui-temporary = Временное
event-ui-temporary-hint = Закончится само во время конца
event-ui-about = О событии
event-ui-no-description = Описания нет.
event-ui-author = Автор: { $author }
event-ui-created = Создано: { $date }
event-ui-map-schedule = Карта и расписание
event-ui-map-none = Карта не указана
event-ui-map-pick = Выберите карту
event-ui-planned-start = Начало: { $time }
event-ui-planned-end = Конец: { $time }
event-ui-started = Началось: { $time }
event-ui-ended = Завершилось: { $time }
event-ui-not-set = не задано
event-ui-open-map = Открыть карту
event-ui-rating = Оценка
event-ui-launch = Запуск
event-ui-launch-hint = Событие запускают сами игроки — голосованием. Оно начнётся со следующей сменой карты.
event-ui-launch-again = Это событие уже проводилось. Его можно запустить снова.
event-ui-launch-active = Событие идёт прямо сейчас.
event-ui-launch-voting = Идёт голосование: { $votes } из { $required }. Напишите y или n в чат.
event-ui-launch-busy = Сейчас идёт другое голосование. Дождитесь его конца.
event-ui-vote = Начать голосование
event-ui-vote-busy = Голосование уже идёт.
event-ui-vote-stopped = Голосование остановлено.
event-ui-force = Запустить сразу
event-ui-force-confirm = Точно запустить?
event-ui-stop = Остановить событие
event-ui-stop-confirm = Точно остановить?
event-ui-stop-vote = Остановить голосование
event-ui-stopped = Событие остановлено.
event-ui-to-list = К списку
event-ui-edit = Изменить
event-ui-editor-new = Новое событие
event-ui-editor-edit = Изменение события
event-ui-texts = Название и описание
event-ui-name = Название
event-ui-description = Описание
event-ui-description-hint = О чём это событие
event-ui-map = Карта
event-ui-schedule = Расписание и тип
event-ui-start = Начало
event-ui-end = Конец
event-ui-save = Сохранить
event-ui-saved = Событие сохранено.
event-ui-draft-restored = Ваше недописанное событие на месте.
event-ui-maps-title = Выбор карты
event-ui-to-editor = К редактору
event-ui-time-start = Начало события
event-ui-time-end = Конец события
event-ui-time-unset = Не задано
event-ui-time-unset-hint = Задайте стрелками или кнопкой ниже
event-ui-time-hours = { $value } ч
event-ui-time-minutes = { $value } мин
event-ui-time-now = Сейчас
event-ui-time-in-hour = Через час
event-ui-time-today-evening = Сегодня 20:00
event-ui-time-tomorrow-noon = Завтра 12:00
event-ui-time-plus-hour = +1 ч
event-ui-time-plus-three-hours = +3 ч
event-ui-time-plus-day = +1 день
event-ui-time-apply = Готово
event-ui-time-clear = Сбросить
event-ui-error-no-map = Сначала выберите карту.
event-ui-error-no-end = Временному событию нужно время конца.
event-ui-error-end-before-start = Конец должен быть позже начала.
event-ui-error-not-allowed = Вам нельзя менять это событие.
event-ui-error-gone = Этого события больше нет.
event-ui-error-save-failed = Не удалось сохранить событие. Попробуйте ещё раз.
vote-event-vote =
    { $nickname }[lightgray] проголосовал за смену текущего события на [orange]{ $name }[lightgray]. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Напишите [orange]y[] или [orange]n[], чтобы проголосовать.
vote-event-left = { $nickname }[lightgray] вышел. Его голос за смену события отменен. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
vote-event-fail = [lightgray]Голосование не прошло. Недостаточно голосов для смены события на [orange]{ $name }[].
vote-event-success = [orange]Голосование прошло. Событие [accent]{ $name }[] будет загружено при следующей смене карты.
vote-event-cancelled = [lightgray]Голосование за смену события на [orange]{ $name }[lightgray] было отменено администратором { $admin }.
event-end = Событие [green]{ $name }[] завершилось!
# ==============================================================================
# Errors
# ==============================================================================
error-access-denied = [scarlet]⚠ Доступ запрещен
error-ip-changed = [scarlet]⚠ Ваш IP адрес изменился. Привилегии администратора были отозваны.
error-not-enough-params = [scarlet]⚠ Недостаточно параметров
error-player-not-found = [scarlet]Игрок не найден.
error-player-not-teammate = [scarlet]⚠ Игрок не в вашей команде
error-player-admin = [scarlet]⚠ Попытка выгнать администратора ⚠
error-already-voted = [scarlet]⚠ Вы уже проголосовали.
error-playtime-requirement =
    [scarlet]⚠ Вам нужно отыграть минимум { $time } { $time ->
        [one] минуту
        [few] минуты
        [many] минут
       *[other] минуты
    } для использования этой функции.
error-globalchat-total-playtime =
    [scarlet]⚠ Для того чтобы отправить сообщение в глобальный чат, вам необходимо отыграть { $globalChatPlayTime } { $globalChatPlayTime ->
        [one] минуту
        [few] минуты
        [many] минут
       *[other] минуты
    }.
error-votekick-total-playtime =
    [scarlet]⚠ Для того чтобы проголосовать, вам необходимо отыграть { $votekickPlayTime } { $votekickPlayTime ->
        [one] минуту
        [few] минуты
        [many] минут
       *[other] минуты
    }.
error-vote-yourself = [scarlet]⚠ Вы не можете голосовать за себя.
error-vote-in-progress = [scarlet]⚠ Голосование уже идет
error-no-voting = [scarlet]⚠ На данный момент голосование не проводится.
error-wave-vote-unavailable = [scarlet]⚠ Досрочный запуск новой волны доступен только в режимах с волнами.
error-no-map = [scarlet]⚠ Карта не выбрана.
error-map-not-event = [scarlet]⚠ Карта не относится к текущему событию.
error-map-not-found = [scarlet]⚠ Карта не найдена! [accent]Используйте [cyan]/maps[] для просмотра списка всех доступных карт
error-maps-empty = [scarlet]⚠ Список карт пуст.
error-event-not-found = [scarlet]⚠ Событие не найдено! [accent]Используйте [cyan]/events[] для просмотра списка доступных событий.
error-page-between = [scarlet]⚠ 'страница' должна быть числом между[orange] 1[] и [orange]{ $totalPages }[]
error-page-number = [scarlet]'страница' должна быть числом.
error-wrong-number = [scarlet]⚠ Неправильный формат числа
error-wrong-period-format = [scarlet]⚠ Неправильный формат периода. Пример: 1h 30m, 30 ({ hours })
error-invalid-id = [scarlet]⚠ Некорректный ID игрока.
error-spectator = [scarlet]⚠ Вы наблюдатель и не можете использовать эту команду.
error-admin-password-too-short = [scarlet]⚠ Пароль должен быть длиннее 8 символов
error-wrong-admin-password = [scarlet]⚠ Неправильный пароль
error-internal = [scarlet]Внутренняя ошибка сервера
error-processing-request = [scarlet]Произошла ошибка при обработке запроса.
error-no-access = [scarlet]⚠ Нет доступа.
error-nickname-too-long = [scarlet]⚠ Никнейм слишком длинный. Максимум { $max } видимых символов.
error-private-message-invalid-pid = [scarlet]⚠ Неверный ID игрока. Используйте формат [lightgray]#123[].
error-private-message-self = [scarlet]⚠ Нельзя отправить личное сообщение самому себе.
error-private-message-empty = [scarlet]⚠ Сообщение не может быть пустым.
error-private-message-too-long = [scarlet]⚠ Сообщение слишком длинное. Максимум { $max } символов.
error-private-message-cooldown = [scarlet]⚠ Подождите { DURATION($seconds) } перед отправкой следующего личного сообщения.
error-private-message-target-unavailable = [scarlet]⚠ Этот игрок сейчас недоступен для личных сообщений.
error-private-message-no-reply-target = [scarlet]⚠ Некому ответить в личных сообщениях.
error-private-message-not-found = [scarlet]⚠ Сообщение не найдено.
error-private-message-block-self = [scarlet]⚠ Нельзя заблокировать самого себя.
error-private-message-block-limit = [scarlet]⚠ Достигнут лимит блок-листа ({ $limit }).
ban-menu-duration-title = [orange]{ -xcore } - Длительность бана
ban-menu-duration-message = Введите длительность бана для { $nickname }. Например: 1d, 12h, 30m
ban-menu-reason-title = [orange]{ -xcore } - Причина бана
ban-menu-reason-message = Введите причину бана для { $nickname }. Оставьте пустым для причины по умолчанию.
ban-menu-confirm-title = [orange]{ -xcore } - Подтверждение бана
ban-menu-confirm-content =
    [white]Игрок: { $nickname }[]
    [white]Длительность: [accent]{ $duration }[]
    [white]Причина: [accent]{ $reason }[]
ban-menu-confirm-action = [scarlet]Забанить игрока
error-invalid-syntax = [scarlet]⚠ Неверный синтаксис команды. Использование: [lightgray]/'{ $syntax }'.
error-invalid-sender = [scarlet]⚠ Неверный отправитель команды. Требуется: '[lightgray]{ $type }[]'.
error-argument-parse-generic = [scarlet]⚠ Ошибка аргумента: '{ $error }'.
exception-unexpected = [scarlet]⚠ Внутренняя ошибка при выполнении команды.
exception-invalid-argument = [scarlet]⚠ Неверный аргумент команды: '{ $cause }'.
exception-no-such-command = [scarlet]⚠ Неизвестная команда.
exception-no-permission = [scarlet]⚠ Доступ запрещен.
exception-invalid-sender = [scarlet]⚠ '{ $actual }' не может выполнить эту команду. Требуется: [lightgray]{ $expected }[].
exception-invalid-sender-list = [scarlet]⚠ '{ $actual }' не может выполнить эту команду. Допустимые отправители: [lightgray]{ $expected }[].
exception-invalid-syntax = [scarlet]⚠ Неверный синтаксис команды. Использование: [lightgray]/'{ $syntax }'.
argument-parse-failure-boolean = [scarlet]⚠ Не удалось распознать '{ $input }' как логическое значение (true/false).
argument-parse-failure-number = [scarlet]⚠ Число '{ $input }' вне допустимого диапазона [{ $min }, { $max }].
argument-parse-failure-char = [scarlet]⚠ '{ $input }' не является допустимым символом.
argument-parse-failure-enum = [scarlet]⚠ '{ $input }' недопустимая опция. Доступно: [lightgray]{ $acceptableValues }
argument-parse-failure-string = [scarlet]⚠ Неверный формат строки: '{ $input }'.
argument-parse-failure-uuid = [scarlet]⚠ Неверный формат UUID: '{ $input }'.
argument-parse-failure-regex = [scarlet]⚠ Ввод '{ $input }' не соответствует шаблону '{ $pattern }'.
argument-parse-failure-color = [scarlet]⚠ '{ $input }' не является допустимым цветом.
argument-parse-failure-duration = [scarlet]⚠ '{ $input }' не является допустимым форматом длительности.
argument-parse-failure-aggregate-missing = [scarlet]⚠ Отсутствует компонент '{ $component }'.
argument-parse-failure-aggregate-failure = [scarlet]⚠ Некорректный компонент '{ $component }': '{ $failure }'.
argument-parse-failure-either = [scarlet]⚠ Не удалось определить { $primary } или { $fallback } из '{ $input }'.
argument-parse-failure-flag-unknown = [scarlet]⚠ Неизвестный флаг: '{ $flag }'.
argument-parse-failure-flag-duplicate = [scarlet]⚠ Повторяющийся флаг: '{ $flag }'.
argument-parse-failure-flag-duplicate-flag = [scarlet]⚠ Повторяющийся флаг: '{ $flag }'.
argument-parse-failure-flag-no-flag-started = [scarlet]⚠ Флаг не был начат. Непонятно, что делать с '{ $input }'.
argument-parse-failure-flag-missing-argument = [scarlet]⚠ Отсутствует аргумент для флага: '{ $flag }'.
argument-parse-failure-flag-no-permission = [scarlet]⚠ У вас нет прав на использование флага '{ $flag }'.
argument-parse-failure-selector-syntax = [scarlet]⚠ Неверный селектор '{ $input }': [lightgray]{ $reason }
argument-parse-failure-selector-no-such-target = [scarlet]⚠ Под '{ $input }' ничего не подходит.
argument-parse-failure-selector-too-many-targets = [scarlet]⚠ Под '{ $input }' подходит несколько целей, а команде нужна одна.
argument-parse-failure-selector-denied = [scarlet]⚠ Селекторы здесь запрещены: [lightgray]{ $reason }
argument-parse-failure-selector-kind-not-allowed = [scarlet]⚠ Селектор '{ $kind }' здесь запрещён.
argument-parse-failure-selector-sender-required = [scarlet]⚠ Селектор '{ $kind }' можно использовать только в игре.
argument-parse-failure-selector-limit-exceeded = [scarlet]⚠ Селектор нашёл { $count } целей, лимит — { $limit }.
argument-parse-failure-team = [scarlet]⚠ Команда '{ $input }' не найдена.
argument-parse-failure-content = [scarlet]⚠ '{ $input }' не является допустимым { $type }.
# ==============================================================================
# Button Status
# ==============================================================================
finished = завершено
finished-neutral = [orange]Завершено
finished-active = [green]Завершено
finished-inactive = [red]Завершено
major = Крупное
major-neutral = [orange]Крупное
major-active = [green]Крупное
major-inactive = [red]Крупное
active = Активно
active-neutral = [orange]Активно
active-active = [green]Активно
active-inactive = [red]Активно
admin = Админ
admin-neutral = [orange]Админ
admin-active = [green]Админ
admin-inactive = [red]Админ
player-leaderboard-active = [green]Лидерборд: включён[]
player-leaderboard-inactive = [red]Лидерборд: выключен[]
audit-menu-open = История аудита
audit-menu-actions-open = Действия
audit-menu-history-title = [orange]{ -xcore } — История аудита
audit-menu-history-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $player }[] [gray]#{ $pid }[]
    [gray]Показано записей: [accent]{ $entriesShown }[]
    [gray]{ $pageState } [darkgray]|[] { $nextState }
    [lightgray]{ $hint }[]
audit-menu-history-page-first = Новые записи
audit-menu-history-page-older = Более старые записи
audit-menu-tab-sanctions = Полученные санкции
audit-menu-tab-actions = Административные действия
audit-menu-filter-all = Все
audit-menu-filter-bans = Баны
audit-menu-filter-mutes = Муты
audit-menu-filter-warns = Варны
audit-menu-filter-other = Прочее
audit-menu-btn-back = Назад к истории
audit-menu-btn-copy-id = Копировать ID
audit-menu-copy-id-success = ID записи отправлен в чат
audit-menu-field-id = ID записи
audit-menu-page = Стр. { $page }
audit-menu-details-unavailable = Запись недоступна.
audit-menu-field-target = Игрок
audit-menu-field-actor = Кто выполнил
audit-menu-field-server = Сервер
audit-menu-field-reason = Причина
audit-menu-section-time = Время
audit-menu-field-occurred = Когда
audit-menu-field-duration = Длительность
audit-menu-field-expires = Истекает
audit-menu-btn-revoke = Снять наказание
audit-menu-revoke-success = Наказание успешно аннулировано.
audit-menu-status-active = АКТИВНО
audit-menu-status-expired = ИСТЕКЛО
audit-menu-status-permanent = НАВСЕГДА
audit-menu-history-more = Есть ещё записи
audit-menu-history-end = Конец истории
audit-menu-history-empty = Для этого игрока пока нет записей аудита.
audit-menu-history-hint = Выберите запись ниже, чтобы посмотреть детали.
audit-menu-actions-title = [orange]{ -xcore } — Действия аудита
audit-menu-actions-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $player }[] [gray]#{ $pid }[]
    [gray]Показано записей: [accent]{ $entriesShown }[]
    [gray]{ $pageState } [darkgray]|[] { $nextState }
    [lightgray]{ $hint }[]
audit-menu-actions-empty = Для этого игрока пока нет действий аудита.
audit-menu-actions-hint = Выберите запись ниже, чтобы посмотреть, что делал этот игрок.
audit-menu-summary-row = [accent]{ $action }[] [darkgray]•[] [white]{ $actor }[] [gray]— { $reason }
audit-menu-action-summary-row = [accent]{ $action }[] [darkgray]•[] [white]{ $target }[] [gray]— { $reason }
audit-menu-details-title = [orange]{ -xcore } — Детали аудита
audit-menu-details-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $player }[] [gray]#{ $pid }[]
    { "" }
    [accent]■ Событие аудита[]
    [gray]Действие: [white]{ $action }[]
    [gray]Кто выполнил: [white]{ $actor }[]
    [gray]Причина: [white]{ $reason }[]
    { "" }
    [accent]■ Время[]
    [gray]Когда: [white]{ $occurredAt }[]
    [gray]Длительность: [white]{ $duration }[]
    [gray]Истекает: [white]{ $expiresAt }[]
    { "" }
    [accent]■ Метаданные[]
    [gray]Audit ID: [white]{ $auditId }[]
audit-menu-unknown-actor = Неизвестно
audit-menu-unknown-target = Неизвестно
audit-menu-reason-unspecified = Не указано
audit-menu-duration-permanent = Постоянно
audit-menu-action-ban = Бан
audit-menu-action-unban = Разбан
audit-menu-action-mute = Мут
audit-menu-action-unmute = Размут
audit-menu-action-warn = Предупреждение
audit-menu-action-kick = Кик
audit-menu-action-note = Заметка
audit-menu-action-quarantine = Карантин
audit-menu-action-unquarantine = Снятие карантина
# ==============================================================================
# Miscellaneous
# ==============================================================================
hours = часы
days = дни
success = [green]Успешно
empty = [accent]Пусто
never = Никогда
save = Сохранить
close = [scarlet]Закрыть
previous = [accent]« Предыдущая
next = [accent]Следующая »
cancel = Отмена
back = Назад
yes = Да
no = Нет
test = Тест
no-description = Без описания
site = Сайт
discord = Discord
github = GitHub
donatello = Donatello
weblate = Weblate
discord-red-vs-blue = RedVSBlue
auto = Авто
on = Включено
off = Выключено
error-command-disabled = [scarlet]⚠ Команда [accent]/{ $command }[scarlet] отключена на этом сервере.
error-feature-disabled = [scarlet]⚠ Эта функция отключена на этом сервере.
none = Нет
unknown = Неизвестно
error-nickname-badge-glyph = [scarlet]⚠ Пользовательский ник не может содержать зарезервированные иконки бейджей.

# Серверный браузер (/servers)
player-servers-title = ИГРОВЫЕ СЕРВЕРЫ XCORE
player-servers-cat-all = Все
player-servers-cat-attack = Нападение
player-servers-cat-pvp = PvP
player-servers-cat-survival = Выживание
player-servers-cat-special = Спец
player-servers-hint = Нажмите на сервер для подключения
player-servers-refresh = Обновить
player-servers-already-connected = [gold]● Вы уже подключены к этому серверу!
player-servers-transferring = [accent]Переключение на сервер [white]{ $server }[]...
player-servers-full = [scarlet]Сервер { $server } заполнен! Подождите освобождения слота.
player-servers-offline = [scarlet]Сервер { $server } сейчас оффлайн.
player-servers-not-found = [scarlet]Сервер '{ $server }' не найден.
player-servers-empty-category = [lightgray]В этой категории нет доступных серверов.[]
player-servers-online-summary = [green]● { $players } [gray]в игре[] [darkgray]|[] [sky]{ $servers } [gray]онлайн[]
player-servers-badge-current = [gold]● ВЫ ЗДЕСЬ[]
player-servers-offline-badge = [darkgray]● ОФФЛАЙН[]
player-servers-capacity-full = [scarlet]● { $players }/{ $max } МЕСТ НЕТ[]
player-servers-capacity-normal = [green]● { $players }/{ $max } { $bar }
player-servers-card-current = [lightgray]Вы подключены к этому серверу[]
player-servers-card-wave = [darkgray]|[] [accent]Волна { $wave }[]
player-servers-card-empty = [sky]Будьте первым! Запустите сессию[]
player-servers-card-map = [gray]Карта:[] [white]{ $map }[]
player-servers-card-mode = [gray]Режим:[] [white]{ $mode }[]

announcement-hub =
    [gold]★ [accent]Серверы XCore [lightgray]» [white]Наскучила эта игра?
    [lightgray]Откройте навигатор серверов командой [accent]/hub[lightgray]!
announcement-discord =
    [gold]★ [accent]Сообщество XCore [lightgray]» [white]Хотите общаться и следить за новостями?
    [lightgray]Присоединяйтесь к нашему Discord: [accent]/discord[lightgray]!
announcement-help =
    [gold]★ [accent]Помощь XCore [lightgray]» [white]Нужны все доступные команды?
    [lightgray]Используйте [accent]/help[lightgray], чтобы ознакомиться со всеми командами!
announcement-report =
    [gold]★ [accent]Жалобы XCore [lightgray]» [white]Кто-то гриферит или нарушает правила?
    [lightgray]Пожалуйтесь командой [accent]/report[lightgray] — администрация разберётся!


error-only-players = [scarlet]⚠ Эту команду могут использовать только игроки.

player-settings-username-editable = [gold]★ Имя пользователя (Награда за событие):[]
player-settings-username-hint = Введите уникальное имя (4-32 симв.)...
player-settings-username-locked = [gray]Имя пользователя:[] [accent]@{ $username }[] [darkgray](Заблокировано)[]
player-settings-username-none = [gray]Имя пользователя: [darkgray]Не установлено (Выиграйте в событии)[]

error-username-empty = Имя пользователя не может быть пустым!
error-username-length = Длина имени пользователя должна быть от 4 до 32 символов!
error-username-invalid-chars = Имя пользователя может содержать только латинские буквы, цифры и знак подчеркивания!
error-username-taken = Это имя пользователя уже занято другим игроком!

# ==============================================================================
# Permission nodes
# ==============================================================================
permission-mindustry-admin = Встроенное меню администратора игры и пропуск волн
permission-xcore-moderation-mute = Выдавать мут
permission-xcore-moderation-unmute = Снимать мут
permission-xcore-moderation-kick = Кикать игроков
permission-xcore-moderation-ban = Банить игроков
permission-xcore-moderation-unban = Снимать баны
permission-xcore-moderation-audit-others = Смотреть историю модерации других игроков
permission-xcore-moderation-votekick-immune = Нельзя выгнать голосованием
permission-xcore-admin-tp = Телепортировать игроков
permission-xcore-admin-broadcast = Отправлять объявления всем
permission-xcore-admin-kill = Убивать юнитов и игроков
permission-xcore-admin-heal = Лечить юнитов и игроков
permission-xcore-admin-set-team = Менять команду игрока
permission-xcore-maps-force-rtv = Менять карту без голосования
permission-xcore-maps-force-vnw = Пропускать волну без голосования
permission-xcore-votes-cancel = Отменять идущее голосование
permission-xcore-events-create-major = Создавать крупные ивенты
permission-xcore-events-edit-others = Редактировать чужие ивенты
permission-xcore-events-force-vote = Запускать ивент без голосования
permission-xcore-events-stop = Останавливать идущий ивент
permission-xcore-players-settings-others = Менять настройки других игроков
permission-xcore-players-private-info = Видеть приватные данные игроков, например IP-адреса
permission-xcore-bypass-playtime = Не учитывать требования к наигранному времени в командах
permission-xcore-permissions-inspect = Смотреть, у кого какие права
permission-xcore-permissions-manage = Менять роли и права

error-target-outranks = [scarlet]⚠ Нельзя применить это к игроку, чья роль не ниже вашей.
perm-me-header = [accent]Ваши роли здесь (вес [white]{ $weight }[accent]):
perm-me-none = [lightgray] - нет
perm-me-logged-in = [green]Вы вошли как стафф.
perm-me-not-logged-in = [yellow]Вы не вошли как стафф. Используйте [white]/login <пароль>[yellow].
perm-me-stale = [scarlet]Права стаффа приостановлены: сервер не смог их обновить. Они вернутся сами.

help-ui-search-hint = Поиск по командам, алиасам и описаниям
help-ui-search-empty = [lightgray]По вашему запросу команды не найдены.[]

match-history-title = История матчей
match-history-rank = #{ $rank } в сезоне
match-history-form = Последние { $count }:
match-history-empty = Здесь появятся ваши рейтинговые матчи.
match-history-since = История ведётся с { $date }.
match-history-load-failed = Не удалось загрузить историю матчей. Попробуйте чуть позже.
match-history-back-to-list = К списку
match-history-your-result = Ваш результат
match-history-places = Места
match-history-team-title = { $place } место · { $team } · средний { $average }
match-history-time-now = только что
match-history-time-minutes = { $count } мин назад
match-history-time-hours = { $count } ч назад
match-history-time-yesterday = вчера
match-history-outcome-win = [lime]Победа[]
match-history-outcome-loss = [scarlet]Поражение[]
match-history-outcome-unrated = [lightgray]Без рейтинга[]
match-history-outcome-uncounted = [lightgray]Не засчитан[]
match-history-lineup = { $own } на { $other }
match-history-team-place = { $place } место из { $teams } команд
match-history-place = #{ $place } из { $players }
match-history-counted = Засчитан: { $reason }
match-history-not-counted = Не засчитан: { $reason }
match-history-counted-share = Засчитано { $percent }% матча
match-history-reason-winner = победа
match-history-reason-defeated = поражение
match-history-reason-short-play = сыграл меньше половины матча
match-history-reason-late-join = зашёл в конце
match-history-reason-left-at-once = почти сразу вышел
match-history-reason-match-unrated = матч без рейтинга
match-history-reason-unknown = причина не записана
match-history-skip-not-enough-players = мало игроков
match-history-skip-admin-stop = остановлен администратором
match-history-skip-technical-error = техническая ошибка
match-history-skip-unknown = без рейтинга
match-history-finish-surrender = сдачей
match-history-finish-timeout = по таймеру
match-history-finish-admin-stop = остановлен администратором
match-history-finish-technical-error = техническая ошибка

# Discord submission attribution; the declared creator is supplied by the uploader.
map-upload-file-author = Автор из файла: { $author }
map-upload-creator = Указанный автор: { $person }
map-upload-submitter = Добавил через Discord: { $person }
map-upload-date = Загружена: [lightgray]{ $date }[] (UTC)

map-upload-current-creator = [accent]{ $map }[] — { $creator }
