## Рейтинговые сезоны в /season info, /season top и /stats. Идентификаторы начинаются с "season-",
## лиги — с "league-": названия, которые им даёт игра.

season-default-name = Сезон { $number }
season-none = Рейтинговых сезонов пока нет.
season-not-found = Такого сезона для { $ladder } нет.
season-started = Начало
season-ends = Конец
season-ended = Закончился
season-wrapping-up = Подводим итоги…
season-players = Игроки
season-count = `{ NUMBER($count, useGrouping: 1) }`
season-matches = Матчи
season-prizes = Призы
season-prize-badge = 🎖️ значок «{ $badge }»
season-top-title = { $season } · Топ
season-top-empty = В этом сезоне ещё никто не играл.
season-top-wins = { NUMBER($wins, useGrouping: 1) }/{ NUMBER($matches, useGrouping: 1) } побед
season-top-footer = { $players ->
        [one] { NUMBER($players, useGrouping: 1) } игрок в этом сезоне
        [few] { NUMBER($players, useGrouping: 1) } игрока в этом сезоне
        [many] { NUMBER($players, useGrouping: 1) } игроков в этом сезоне
       *[other] { NUMBER($players, useGrouping: 1) } игрока в этом сезоне
    }

# Строка игрока в /stats

season-no-rated-matches = В этом сезоне нет рейтинговых матчей
season-ends-in = закончится { $when }
season-wins = { NUMBER($wins, useGrouping: 1) }/{ NUMBER($matches, useGrouping: 1) } побед
season-wins-rate = { NUMBER($wins, useGrouping: 1) }/{ NUMBER($matches, useGrouping: 1) } побед ({ $rate }%)
season-standing = { $league } `{ $rating }` · #{ $rank } из { NUMBER($participants, useGrouping: 1) } · { $games }
season-peak = пик `{ $rating }`
season-top-league = высшая лига
season-next-league = `{ $left }` до лиги «{ $league }»

league-scrap = Лом
league-copper = Медь
league-lead = Свинец
league-graphite = Графит
league-silicon = Кремний
league-titanium = Титан
league-thorium = Торий
league-plastanium = Пластан
league-phase-fabric = Фазовая ткань
league-surge-alloy = Кинетический сплав
