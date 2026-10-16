## Общие строки ядра обращений. Каждый сценарий добавляет свой файл (reports.ftl, appeals.ftl, ...)
## и начинает идентификаторы со своего префикса: идентификаторы глобальны в пределах языка.
## Числа выводятся без разделителей разрядов (1234, а не 1 234).

# Ошибки и отказы

error-generic = Что-то пошло не так. Попробуйте ещё раз позже.
error-try-again-later = Сейчас это недоступно. Попробуйте ещё раз чуть позже.
error-not-allowed = У вас нет прав на это действие.
error-not-found = Ничего не найдено.
error-already-handled = Это уже обработано.
error-already-handled-by = Это уже обработал пользователь { $name }.
error-opening-blocked = Сейчас вы не можете создавать новые обращения.
error-cooldown =
    Подождите ещё { $seconds ->
        [one] { $seconds } секунду
        [few] { $seconds } секунды
        [many] { $seconds } секунд
       *[other] { $seconds } секунды
    } перед следующей попыткой.

# Кнопки

button-yes = Да
button-no = Нет
button-cancel = Отмена
button-confirm = Подтвердить

# Выбор языка

language-name-en = English
language-name-ru = Русский
language-name-uk = Українська
language-select-prompt = Выберите язык, на котором бот будет отвечать вам и писать в личные сообщения.
language-select-placeholder = Выберите язык
language-changed = Язык изменён: { $language }.
cmd-language-name = язык
cmd-language-description = Выбрать язык, на котором бот общается с вами

# Обращения

cases-open-limit = У вас уже открыто максимальное число обращений этого типа.
cases-action-failed = Не удалось выполнить действие. Проверьте обращение и попробуйте ещё раз.
cases-form-invalid = Форма заполнена не полностью или с ошибкой.
cases-press-again = Не удалось открыть форму. Нажмите кнопку ещё раз.
cases-cancelled = Отменено.
cases-choice-placeholder = Выберите вариант
cases-unknown = Неизвестно
cases-pid = ID { $pid }
cases-opener = Автор обращения
cases-subject = Кого касается
cases-status = Статус
cases-assignee = Ответственный
cases-resolution = Результат
cases-reason = Причина
cases-reject-details = Подробности
cases-note = Комментарий
cases-files = Скриншоты или файлы (необязательно)
cases-files-retry = Не все файлы удалось прикрепить. Добавьте их в ветку обращения.
cases-sanctions = История наказаний
cases-sanctions-counts = Баны: { $bans } · муты: { $mutes } · кики: { $kicks } · предупреждения: { $warns }
cases-block-summary = Блоки за последние { $minutes } мин: поставлено или изменено { $built } (включая повороты и смену настроек), снесено { $destroyed }.
cases-user-thread-note = Обращение #{ $number } зарегистрировано. Администрация рассмотрит его и ответит здесь. Сюда же можно добавить скриншоты и подробности.
cases-user-message = Новое сообщение от { $name }: { $url }
cases-registered = Обращение #{ $number } зарегистрировано.
cases-updated = Обращение #{ $number } обновлено.
cases-subject-complaint-only = Сотрудника можно указать только в жалобе на стафф.
cases-found = Обращение #{ $number } · { $kind } · { $status }
cases-outcome = Ваше обращение #{ $number } рассмотрено.

# Общая панель

cases-panel-title = Связь с администрацией
cases-panel-text = Ответ придёт в закрытую ветку, её видите только вы и администрация. Кнопка 🌐 **Language** меняет язык ответов.
cases-panel-hint-report = игрок нарушает правила
cases-panel-hint-appeal = вы считаете бан ошибкой
cases-panel-hint-badge = запросить заслуженный значок
cases-panel-hint-complaint = сотрудник поступил с вами несправедливо
cases-panel-language = Language
cases-panel-empty = Не включён ни один тип обращений с кнопкой на панели.
cases-panel-published = Панель обращений обновлена: { $url }
cases-configuration-error = Обращения настроены не полностью: { $details }

# Типы, статусы и результаты на карточке

cases-kind-report = Жалоба на игрока
cases-kind-appeal = Обжалование бана
cases-kind-badge = Заявка на значок
cases-kind-complaint = Жалоба на администрацию
cases-kind-map = Проверка карты
cases-status-creating = Зарегистрировано
cases-status-open = Открыто
cases-status-taken = В работе
cases-status-pending = Выполняется действие
cases-status-resolved = Рассмотрено
cases-status-rejected = Отклонено
cases-status-cancelled = Отменено
cases-resolution-banned = Игрок забанен
cases-resolution-muted = Игроку выдан мут
cases-resolution-unbanned = Бан снят
cases-resolution-shortened = Срок бана сокращён
cases-resolution-granted = Значок выдан
cases-resolution-upheld = Жалоба признана обоснованной
cases-resolution-accepted = Карта принята
cases-resolution-rejected = Отклонено

# Кнопки карточки. «Взять», «Вернуть» и «Отклонить» принадлежат ядру; остальные — общие подписи для сценариев.

cases-button-take = Взять в работу
cases-button-release = Вернуть в очередь
cases-button-reject = Отклонить
cases-button-history = История
cases-button-refresh = Обновить
cases-button-ban = Бан
cases-button-mute = Мут
cases-button-unban = Снять бан
cases-button-shorten = Сократить бан
cases-button-grant = Выдать значок
cases-button-accept = Принять карту
cases-button-resolve = Завершить
cases-button-recheck = Перепроверить
cases-button-subject = Указать, на кого жалоба

# Почему действие не выполнено

cases-error-player = Не удалось определить игрока. Проверьте ID.
cases-error-duration = Срок указан неверно.
cases-error-ban = Этот бан уже не действует.
cases-error-expiry = Новый срок должен заканчиваться раньше текущего.
cases-error-badge = Этот значок нельзя выдать вручную.
cases-error-map = Проверьте файл карты и выбранный сервер.
cases-error-map-unconfirmed = Сервер не подтвердил загрузку. Прежде чем повторять, проверьте список карт на сервере.
cases-error-no-server = Сейчас нет доступных игровых серверов. Попробуйте позже.
cases-error-timeout = Сервер не ответил вовремя. Прежде чем повторять, проверьте, не выполнено ли действие.
cases-error-server = Серверу не удалось выполнить действие. Прежде чем повторять, проверьте, не выполнено ли оно.

# Слэш-команды

cmd-cases-name = обращения
cmd-cases-description = Поиск обращений и управление ими
cmd-cases-panel-name = панель
cmd-cases-panel-description = Панель обращений
cmd-cases-panel-publish-name = опубликовать
cmd-cases-panel-publish-description = Опубликовать или обновить панель обращений
cmd-cases-find-name = найти
cmd-cases-find-description = Найти обращение по номеру
cmd-cases-find-param-number-name = номер
cmd-cases-find-param-number-description = Номер обращения, например #57
cmd-cases-reopen-name = переоткрыть
cmd-cases-reopen-description = Вернуть в работу закрытое обращение
cmd-cases-reopen-param-number-name = номер
cmd-cases-reopen-param-number-description = Номер обращения, например #57
cmd-cases-subject-name = на-кого
cmd-cases-subject-description = Указать, на кого подана жалоба
cmd-cases-subject-param-number-name = номер
cmd-cases-subject-param-number-description = Номер обращения, например #57
cmd-cases-subject-param-user-name = участник
cmd-cases-subject-param-user-description = Участник, на которого подана жалоба
