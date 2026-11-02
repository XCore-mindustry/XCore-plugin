# The report flow. Every message takes the same arguments in en, ru and uk.

# The panel button and its form (labels: 45 characters at most).
reports-panel = Поскаржитися на гравця
reports-field-who = ID гравця або точний нік
reports-field-what = Що сталося?
reports-field-when = Коли (необов’язково)
reports-field-where = Сервер або мапа (необов’язково)

# The staff card.
reports-card-reported-as = Вказано як
reports-card-unresolved = Гравця визначити не вдалося. Для бану чи муту знадобиться його ID.
reports-card-reason = Причина скарги
reports-card-comment = Опис від заявника
reports-card-when = Коли
reports-card-where = Де
reports-card-context = У грі
reports-card-in-force = Діє зараз
reports-card-reporters = Заявників: { $count }
reports-card-more = …і ще { $count }
reports-online = Гравець на сервері
reports-offline = Гравець вийшов із сервера
reports-no-live = Чату й історії блоків немає: коли подали скаргу, гравця не було на сервері.
reports-same-network = Та сама мережа, що й у заявника
reports-current-ban = Бан до { $until }
reports-current-ban-permanent = Безстроковий бан
reports-current-mute = Мут до { $until }

# Reasons a player picks in the game.
reports-reason-griefing = Гриферство
reports-reason-toxicity = Образи в чаті
reports-reason-cheating = Чити
reports-reason-nsfw = Неприпустимий вміст
reports-reason-evasion = Обхід покарання
reports-reason-other = Інше

# Reasons staff pick when rejecting.
reports-reject-no-violation = Порушення немає
reports-reject-no-evidence = Недостатньо доказів
reports-reject-already-handled = Заходів уже вжито
reports-reject-duplicate = Дублікат
reports-reject-abuse = Зловживання скаргами

# What the reporter is told. Never who decided, never what the sanction was.
reports-outcome-action = Вашу скаргу #{ $number } розглянуто, заходів ужито. Дякуємо.
reports-outcome-no-violation = Вашу скаргу #{ $number } розглянуто: порушень правил не виявлено.
reports-outcome-no-evidence = Вашу скаргу #{ $number } розглянуто: доказів забракло, щоб ужити заходів.
reports-outcome-already-handled = Вашу скаргу #{ $number } розглянуто: щодо цього випадку заходів уже вжито.
reports-outcome-duplicate = Вашу скаргу #{ $number } закрито: вона повторює подану раніше.
reports-outcome-abuse = Вашу скаргу #{ $number } відхилено: вона сама порушує правила.
reports-outcome-closed = Вашу скаргу #{ $number } розглянуто й закрито без ужиття заходів.

# Ban, Mute, History and Refresh.
reports-form-pid = ID гравця
reports-form-length = Термін (30m, 12h, 7d)
reports-case-reference = справа #{ $number }
reports-confirm-ban = Забанити гравця { $player } на { $duration }?
reports-confirm-mute = Видати мут гравцю { $player } на { $duration }?
reports-error-other-player = Цей ID належить іншому гравцеві, а не тому, на кого подано скаргу.
reports-error-open-report = На цього гравця вже відкрито скаргу #{ $number }. Спочатку вирішіть або закрийте її.
reports-history-title = Останні записи про гравця { $player }:
reports-history-empty = Записів про цього гравця немає.
reports-refreshed = Чат, історію блоків і покарання оновлено.
reports-refreshed-offline = Гравця немає на сервері: оновлено лише покарання.

# Lines for staff in the case post and the forum.
reports-merged = +1 скарга (усього { $total }) від { $reporter }: { $reason }
reports-external-banned = Гравця забанили поза цією справою ({ $name }). Скаргу закрито: заходів ужито.
reports-external-muted = Гравцю видали мут поза цією справою ({ $name }). Скаргу закрито: заходів ужито.
reports-burst-title = Приймання скарг обмежено
reports-burst-alert = Досягнуто межі справ за скаргами з гри: { $limit } за { $minutes } хв. Доки потік не спаде, нові скарги з гри не приймаються; скарга на гравця, щодо якого справу вже відкрито, і далі додається до неї.
