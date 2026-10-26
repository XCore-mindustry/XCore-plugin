## /stats: профіль гравця. Ідентифікатори починаються з "stats-". Числа, які можуть бути великими,
## пишуться через NUMBER(..., useGrouping: 1), щоб розряди розділялися за правилами мови.

stats-no-linked-account = У вас немає прив’язаного ігрового акаунта. Отримайте код у грі та скористайтеся `/link` або вкажіть ID гравця.
stats-user-not-linked = У цього користувача немає прив’язаного ігрового акаунта.
stats-player-not-found = Гравця не знайдено. Введіть ID гравця або @username чи почніть вводити нік і виберіть гравця з підказок.
stats-lookup-by-user-admin-only = Шукати гравця за акаунтом Discord можуть лише адміністратори. Вкажіть ID гравця.

# Хто такий гравець

stats-unknown = Невідомо
stats-unavailable = Зараз недоступно
stats-no-id = немає ID
stats-admin = 🛡️ Адмін
stats-online = 🟢 **У мережі**
stats-online-on = 🟢 **У мережі** на `{ $server }`
stats-joined = вхід { $since }
stats-offline = ⚫ Не в мережі
stats-offline-updated = ⚫ Не в мережі · профіль оновлено { $updated }
stats-discord-not-linked = Не прив’язано
stats-discord-linked = прив’язано { $since }
stats-badge-shown = **{ $badge }** (показано)
stats-also-linked = Також прив’язані: { $accounts }

# Назви полів

stats-playtime = ⏱️ Час у грі
stats-first-seen = 📅 Перший вхід
stats-season-ratings = 🏆 Рейтинг сезону
stats-games = 🎮 Ігри
stats-building = 🧱 Будівництво
stats-hexed-rank = ⬡ Ранг Hexed
stats-badges = 🎖️ Значки
stats-staff-notes = 🔒 Нотатки для персоналу

# Ігри

stats-games-none = Записаних ігор поки немає
stats-games-total = Зіграно `{ NUMBER($games, useGrouping: 1) }` · виграно `{ NUMBER($wins, useGrouping: 1) }` · `{ $rate }%` перемог
stats-games-pvp = ⚔️ **PvP** · { $games ->
        [one] { NUMBER($games, useGrouping: 1) } гра
        [few] { NUMBER($games, useGrouping: 1) } гри
        [many] { NUMBER($games, useGrouping: 1) } ігор
       *[other] { NUMBER($games, useGrouping: 1) } гри
    } · { $wins ->
        [one] { NUMBER($wins, useGrouping: 1) } перемога
        [few] { NUMBER($wins, useGrouping: 1) } перемоги
        [many] { NUMBER($wins, useGrouping: 1) } перемог
       *[other] { NUMBER($wins, useGrouping: 1) } перемоги
    } ({ $rate }%)
stats-games-survival = 🛡️ **Виживання** · { $games ->
        [one] { NUMBER($games, useGrouping: 1) } гра
        [few] { NUMBER($games, useGrouping: 1) } гри
        [many] { NUMBER($games, useGrouping: 1) } ігор
       *[other] { NUMBER($games, useGrouping: 1) } гри
    } · найкраща хвиля { NUMBER($best, useGrouping: 1) } · у середньому { NUMBER($average, useGrouping: 1) }
stats-games-hexed = ⬡ **Hexed** · { $games ->
        [one] { NUMBER($games, useGrouping: 1) } гра
        [few] { NUMBER($games, useGrouping: 1) } гри
        [many] { NUMBER($games, useGrouping: 1) } ігор
       *[other] { NUMBER($games, useGrouping: 1) } гри
    } · { $wins ->
        [one] { NUMBER($wins, useGrouping: 1) } перемога
        [few] { NUMBER($wins, useGrouping: 1) } перемоги
        [many] { NUMBER($wins, useGrouping: 1) } перемог
       *[other] { NUMBER($wins, useGrouping: 1) } перемоги
    }
stats-games-hexed-best = найкраще місце `#{ $place }` · топ-3 ×{ NUMBER($top3, useGrouping: 1) }
stats-building-blocks = Блоки: збудовано `{ NUMBER($built, useGrouping: 1) }` · розібрано `{ NUMBER($deconstructed, useGrouping: 1) }` · втрачено `{ NUMBER($destroyed, useGrouping: 1) }`
stats-building-units = Юніти: вироблено `{ NUMBER($produced, useGrouping: 1) }` · втрачено `{ NUMBER($destroyed, useGrouping: 1) }`

# Ранг старого режиму Hexed

stats-hexed-rank-newbie = Новачок
stats-hexed-rank-regular = Завсідник
stats-hexed-rank-advanced = Досвідчений
stats-hexed-rank-veteran = Ветеран
stats-hexed-rank-devastator = Спустошувач
stats-hexed-rank-legend = Легенда
stats-hexed-progress = { $points }/{ $required } перемог до рангу «{ $next }»
stats-hexed-max = { $points ->
        [one] { $points } перемога
        [few] { $points } перемоги
        [many] { $points } перемог
       *[other] { $points } перемоги
    } (максимальний ранг)

# Нотатки для персоналу, їх бачать лише адміни

stats-ban = 🔨 Бан: { $state }
stats-mute = 🔇 Мут: { $state }
stats-punishments-unavailable = 🔨 Бан і мут: { stats-unavailable }
stats-punishment-none = немає
stats-punishment =
    до { $until }
    └ `{ $reason }` — { $admin }
stats-admin-source = 🛡️ Джерело адмінки: `{ $source }`
stats-language = 🌐 Мова: `{ $language }` · перекладач: `{ $translator }`
stats-hidden = 🙈 Приховано з таблиць лідерів

# Кнопки адмінів під профілем

stats-button-ban = Бан
stats-button-mute = Мут
stats-button-history = Історія
stats-button-actions = Дії
