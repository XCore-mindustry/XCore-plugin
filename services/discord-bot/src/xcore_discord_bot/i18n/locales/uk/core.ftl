## Спільні рядки ядра звернень. Кожен сценарій додає свій файл (reports.ftl, appeals.ftl, ...)
## і починає ідентифікатори зі свого префікса: ідентифікатори глобальні в межах мови.
## Числа виводяться без роздільників розрядів (1234, а не 1 234).

# Помилки та відмови

error-generic = Щось пішло не так. Спробуйте ще раз пізніше.
error-try-again-later = Наразі це недоступно. Спробуйте ще раз трохи згодом.
error-not-allowed = У вас немає прав на цю дію.
error-not-found = Нічого не знайдено.
error-already-handled = Це вже оброблено.
error-already-handled-by = Це вже обробив користувач { $name }.
error-opening-blocked = Наразі ви не можете створювати нові звернення.
error-cooldown =
    Зачекайте ще { $seconds ->
        [one] { $seconds } секунду
        [few] { $seconds } секунди
        [many] { $seconds } секунд
       *[other] { $seconds } секунди
    } перед наступною спробою.

# Кнопки

button-yes = Так
button-no = Ні
button-cancel = Скасувати
button-confirm = Підтвердити

# Вибір мови

language-name-en = English
language-name-ru = Русский
language-name-uk = Українська
language-select-prompt = Оберіть мову, якою бот відповідатиме вам і писатиме в особисті повідомлення.
language-select-placeholder = Оберіть мову
language-changed = Мову змінено: { $language }.
cmd-language-name = мова
cmd-language-description = Обрати мову, якою бот спілкується з вами

# Звернення

cases-open-limit = У вас уже відкрито максимальну кількість звернень цього типу.
cases-action-failed = Не вдалося виконати дію. Перевірте звернення і спробуйте ще раз.
cases-form-invalid = Форму заповнено не повністю або з помилкою.
cases-press-again = Не вдалося відкрити форму. Натисніть кнопку ще раз.
cases-cancelled = Скасовано.
cases-choice-placeholder = Оберіть варіант
cases-unknown = Невідомо
cases-pid = ID { $pid }
cases-opener = Автор звернення
cases-subject = Кого стосується
cases-status = Статус
cases-assignee = Відповідальний
cases-resolution = Результат
cases-reason = Причина
cases-reject-details = Подробиці
cases-note = Коментар
cases-files = Знімки екрана або файли (необов’язково)
cases-files-retry = Не всі файли вдалося прикріпити. Додайте їх у гілку звернення.
cases-sanctions = Історія покарань
cases-sanctions-counts = Бани: { $bans } · мути: { $mutes } · кіки: { $kicks } · попередження: { $warns }
cases-block-summary = Блоки за останні { $minutes } хв: поставлено або змінено { $built } (разом із поворотами та зміною налаштувань), знесено { $destroyed }.
cases-user-thread-note = Звернення #{ $number } зареєстровано. Адміністрація розгляне його й відповість тут. Сюди ж можна додати знімки екрана та подробиці.
cases-user-message = Нове повідомлення від { $name }: { $url }
cases-registered = Звернення #{ $number } зареєстровано.
cases-updated = Звернення #{ $number } оновлено.
cases-subject-complaint-only = Співробітника можна вказати лише у скарзі на стафф.
cases-found = Звернення #{ $number } · { $kind } · { $status }
cases-outcome = Ваше звернення #{ $number } розглянуто.

# Загальна панель

cases-panel-text = Оберіть тип звернення кнопкою нижче. Адміністрація розгляне його в закритій гілці. Мову відповідей можна змінити кнопкою 🌐.
cases-panel-language = Language
cases-panel-empty = Не ввімкнено жодного типу звернень із кнопкою на панелі.
cases-panel-published = Панель звернень оновлено: { $url }
cases-configuration-error = Звернення налаштовано не повністю: { $details }

# Типи, статуси й результати на картці

cases-kind-report = Скарга на гравця
cases-kind-appeal = Оскарження бану
cases-kind-badge = Заявка на значок
cases-kind-complaint = Скарга на адміністрацію
cases-kind-map = Перевірка мапи
cases-status-creating = Зареєстровано
cases-status-open = Відкрито
cases-status-taken = У роботі
cases-status-pending = Виконується дія
cases-status-resolved = Розглянуто
cases-status-rejected = Відхилено
cases-status-cancelled = Скасовано
cases-resolution-banned = Гравця забанено
cases-resolution-muted = Гравцю видано мут
cases-resolution-unbanned = Бан знято
cases-resolution-shortened = Термін бану скорочено
cases-resolution-granted = Значок видано
cases-resolution-upheld = Скаргу визнано обґрунтованою
cases-resolution-accepted = Мапу прийнято
cases-resolution-rejected = Відхилено

# Кнопки картки. «Взяти», «Повернути» й «Відхилити» належать ядру; решта — спільні підписи для сценаріїв.

cases-button-take = Взяти в роботу
cases-button-release = Повернути в чергу
cases-button-reject = Відхилити
cases-button-history = Історія
cases-button-refresh = Оновити
cases-button-ban = Бан
cases-button-mute = Мут
cases-button-unban = Зняти бан
cases-button-shorten = Скоротити бан
cases-button-grant = Видати значок
cases-button-accept = Прийняти мапу
cases-button-resolve = Завершити
cases-button-recheck = Перевірити ще раз
cases-button-subject = Вказати, на кого скарга

# Чому дію не виконано

cases-error-player = Не вдалося визначити гравця. Перевірте ID.
cases-error-duration = Термін указано неправильно.
cases-error-ban = Цей бан уже не діє.
cases-error-expiry = Новий термін має закінчуватися раніше за поточний.
cases-error-badge = Цей значок не можна видати вручну.
cases-error-map = Перевірте файл мапи та вибраний сервер.
cases-error-map-unconfirmed = Сервер не підтвердив завантаження. Перш ніж повторювати, перевірте список мап на сервері.
cases-error-no-server = Зараз немає доступних ігрових серверів. Спробуйте пізніше.
cases-error-timeout = Сервер не відповів вчасно. Перш ніж повторювати, перевірте, чи не виконано дію.
cases-error-server = Серверу не вдалося виконати дію. Перш ніж повторювати, перевірте, чи не виконано її.

# Слеш-команди

cmd-cases-name = звернення
cmd-cases-description = Пошук звернень і керування ними
cmd-cases-panel-name = панель
cmd-cases-panel-description = Панель звернень
cmd-cases-panel-publish-name = опублікувати
cmd-cases-panel-publish-description = Опублікувати або оновити панель звернень
cmd-cases-find-name = знайти
cmd-cases-find-description = Знайти звернення за номером
cmd-cases-find-param-number-name = номер
cmd-cases-find-param-number-description = Номер звернення, наприклад #57
cmd-cases-reopen-name = перевідкрити
cmd-cases-reopen-description = Повернути в роботу закрите звернення
cmd-cases-reopen-param-number-name = номер
cmd-cases-reopen-param-number-description = Номер звернення, наприклад #57
cmd-cases-subject-name = на-кого
cmd-cases-subject-description = Вказати, на кого подано скаргу
cmd-cases-subject-param-number-name = номер
cmd-cases-subject-param-number-description = Номер звернення, наприклад #57
cmd-cases-subject-param-user-name = учасник
cmd-cases-subject-param-user-description = Учасник, на якого подано скаргу
