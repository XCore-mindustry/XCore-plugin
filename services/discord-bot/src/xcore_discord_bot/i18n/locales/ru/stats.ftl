## /stats: профиль игрока. Идентификаторы начинаются с "stats-". Числа, которые могут быть большими,
## пишутся через NUMBER(..., useGrouping: 1), чтобы разряды разделялись по правилам языка.

stats-no-linked-account = У вас нет привязанного игрового аккаунта. Получите код в игре и используйте `/link` или укажите ID игрока.
stats-user-not-linked = У этого пользователя нет привязанного игрового аккаунта.
stats-player-not-found = Игрок не найден. Введите ID игрока или @username либо начните вводить ник и выберите игрока из подсказок.
stats-lookup-by-user-admin-only = Искать игрока по аккаунту Discord могут только администраторы. Укажите ID игрока.

# Кто такой игрок

stats-unknown = Неизвестно
stats-unavailable = Сейчас недоступно
stats-no-id = нет ID
stats-online = 🟢 **В сети**
stats-online-on = 🟢 **В сети** на `{ $server }`
stats-joined = вход { $since }
stats-offline = ⚫ Не в сети
stats-offline-updated = ⚫ Не в сети · профиль обновлён { $updated }
stats-discord-not-linked = Не привязан
stats-discord-linked = привязан { $since }
stats-badge-shown = **{ $badge }** (показан)
stats-also-linked = Также привязаны: { $accounts }

# Названия полей

stats-playtime = ⏱️ Время в игре
stats-first-seen = 📅 Первый вход
stats-season-ratings = 🏆 Рейтинг сезона
stats-games = 🎮 Игры
stats-building = 🧱 Строительство
stats-hexed-rank = ⬡ Ранг Hexed
stats-badges = 🎖️ Значки
stats-staff-notes = 🔒 Заметки для персонала

# Игры

stats-games-none = Записанных игр пока нет
stats-games-total = Сыграно `{ NUMBER($games, useGrouping: 1) }` · выиграно `{ NUMBER($wins, useGrouping: 1) }` · `{ $rate }%` побед
stats-games-pvp = ⚔️ **PvP** · { $games ->
        [one] { NUMBER($games, useGrouping: 1) } игра
        [few] { NUMBER($games, useGrouping: 1) } игры
        [many] { NUMBER($games, useGrouping: 1) } игр
       *[other] { NUMBER($games, useGrouping: 1) } игры
    } · { $wins ->
        [one] { NUMBER($wins, useGrouping: 1) } победа
        [few] { NUMBER($wins, useGrouping: 1) } победы
        [many] { NUMBER($wins, useGrouping: 1) } побед
       *[other] { NUMBER($wins, useGrouping: 1) } победы
    } ({ $rate }%)
stats-games-survival = 🛡️ **Выживание** · { $games ->
        [one] { NUMBER($games, useGrouping: 1) } игра
        [few] { NUMBER($games, useGrouping: 1) } игры
        [many] { NUMBER($games, useGrouping: 1) } игр
       *[other] { NUMBER($games, useGrouping: 1) } игры
    } · лучшая волна { NUMBER($best, useGrouping: 1) } · в среднем { NUMBER($average, useGrouping: 1) }
stats-games-hexed = ⬡ **Hexed** · { $games ->
        [one] { NUMBER($games, useGrouping: 1) } игра
        [few] { NUMBER($games, useGrouping: 1) } игры
        [many] { NUMBER($games, useGrouping: 1) } игр
       *[other] { NUMBER($games, useGrouping: 1) } игры
    } · { $wins ->
        [one] { NUMBER($wins, useGrouping: 1) } победа
        [few] { NUMBER($wins, useGrouping: 1) } победы
        [many] { NUMBER($wins, useGrouping: 1) } побед
       *[other] { NUMBER($wins, useGrouping: 1) } победы
    }
stats-games-hexed-best = лучшее место `#{ $place }` · топ-3 ×{ NUMBER($top3, useGrouping: 1) }
stats-building-blocks = Блоки: построено `{ NUMBER($built, useGrouping: 1) }` · разобрано `{ NUMBER($deconstructed, useGrouping: 1) }` · потеряно `{ NUMBER($destroyed, useGrouping: 1) }`
stats-building-units = Юниты: произведено `{ NUMBER($produced, useGrouping: 1) }` · потеряно `{ NUMBER($destroyed, useGrouping: 1) }`

# Ранг старого режима Hexed

stats-hexed-rank-newbie = Новичок
stats-hexed-rank-regular = Завсегдатай
stats-hexed-rank-advanced = Опытный
stats-hexed-rank-veteran = Ветеран
stats-hexed-rank-devastator = Опустошитель
stats-hexed-rank-legend = Легенда
stats-hexed-progress = { $points }/{ $required } побед до ранга «{ $next }»
stats-hexed-max = { $points ->
        [one] { $points } победа
        [few] { $points } победы
        [many] { $points } побед
       *[other] { $points } победы
    } (максимальный ранг)

# Заметки для персонала, их видят только админы

stats-ban = 🔨 Бан: { $state }
stats-mute = 🔇 Мут: { $state }
stats-punishments-unavailable = 🔨 Бан и мут: { stats-unavailable }
stats-punishment-none = нет
stats-punishment =
    до { $until }
    └ `{ $reason }` — { $admin }
stats-language = 🌐 Язык: `{ $language }` · переводчик: `{ $translator }`
stats-hidden = 🙈 Скрыт из таблиц лидеров

# Кнопки админов под профилем

stats-button-ban = Бан
stats-button-mute = Мут
stats-button-history = История
stats-button-actions = Действия
