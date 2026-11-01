## Рейтингові сезони в /season info, /season top і /stats. Ідентифікатори починаються з "season-",
## ліги — з "league-": назви, які їм дає гра.

season-default-name = Сезон { $number }
season-none = Рейтингових сезонів поки немає.
season-not-found = Такого сезону для { $ladder } немає.
season-started = Початок
season-ends = Кінець
season-ended = Завершився
season-wrapping-up = Підбиваємо підсумки…
season-players = Гравці
season-count = `{ NUMBER($count, useGrouping: 1) }`
season-matches = Матчі
season-prizes = Призи
season-prize-badge = 🎖️ значок «{ $badge }»
season-top-title = { $season } · Топ
season-top-empty = У цьому сезоні ще ніхто не грав.
season-top-wins = { NUMBER($wins, useGrouping: 1) }/{ NUMBER($matches, useGrouping: 1) } перемог
season-top-footer = { $players ->
        [one] { NUMBER($players, useGrouping: 1) } гравець у цьому сезоні
        [few] { NUMBER($players, useGrouping: 1) } гравці у цьому сезоні
        [many] { NUMBER($players, useGrouping: 1) } гравців у цьому сезоні
       *[other] { NUMBER($players, useGrouping: 1) } гравця у цьому сезоні
    }

# Рядок гравця в /stats

season-no-rated-matches = У цьому сезоні немає рейтингових матчів
season-ends-in = завершиться { $when }
season-wins = { NUMBER($wins, useGrouping: 1) }/{ NUMBER($matches, useGrouping: 1) } перемог
season-wins-rate = { NUMBER($wins, useGrouping: 1) }/{ NUMBER($matches, useGrouping: 1) } перемог ({ $rate }%)
season-standing = { $league } `{ $rating }` · #{ $rank } з { NUMBER($participants, useGrouping: 1) } · { $games }
season-peak = пік `{ $rating }`
season-top-league = найвища ліга
season-next-league = `{ $left }` до ліги «{ $league }»

league-scrap = Брухт
league-copper = Мідь
league-lead = Свинець
league-graphite = Графіт
league-silicon = Кремній
league-titanium = Титан
league-thorium = Торій
league-plastanium = Пластаній
league-phase-fabric = Фазова тканина
league-surge-alloy = Кінетичний сплав
