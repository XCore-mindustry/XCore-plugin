# ==============================================================================
# Terms
# ==============================================================================
-xcore = Server XCore
# ==============================================================================
# General & Help
# ==============================================================================
menu-main = Hlavní menu
commands-main-description = Otevře interaktivní hlavní menu.
menu-main-title = [orange]{ -xcore } — Hlavní menu
menu-main-content = Hlavní menu serveru
help-menu = Menu nápovědy
commands-help-description = Otevře interaktivní menu nápovědy.
help-menu-title = [orange]{ -xcore } — Příkazy
help-menu-content =
    [gray]Strana [white]{ $page }[gray]/[white]{ $total }
    [lightgray]Vyber příkaz a zobrazí se podrobné použití:
help-menu-button = [accent]/{ $command } [gray]» Popis: [white]{ $description }
help-command-with-overload-count = { $name } z ({ $count })
help-command-title = [orange]» Název: [white]/{ $name }
help-command-header =
    [orange]» [accent]Syntaxe: [white]{ $syntax }
    [orange]» [accent]Info: [lightgray]{ $description }
help-aliases = [orange]» [accent]Aliasy: [white]{ $aliases }
help-args-title = [orange]» [accent]Argumenty:
help-usages-title = [orange]» [accent]Použití:
help-usage-entry = [gray]• [white]{ $syntax }
help-usage-args-title = [orange]» [accent]Pro [white]{ $syntax }[accent]:
help-arg-entry = [gray]• [white]{ $arg } [lightgray]- { $description }
help-no-arguments = [gray]Nejsou potřeba žádné další argumenty.
help-no-arg-description = Bez popisu.
help-no-description = Tento příkaz nemá popis.
help-legacy-command-content =
    [orange]» [accent]Příkaz: [white]/{ $name }
    [orange]» [accent]Parametry: [white]{ $params }
    [orange]» [accent]Info: [lightgray]{ $description }
    { "" }
    [gray](Starší příkaz s omezenými informacemi)
help-legacy-command-content-no-params =
    [orange]» [accent]Příkaz: [white]/{ $name }
    [orange]» [accent]Info: [lightgray]{ $description }
    { "" }
    [gray](Starší příkaz s omezenými informacemi)
help-back = [lightgray]« Zpět

# ==============================================================================
# Modern Reactive Help & Commands Guide (xcore-ui)
# ==============================================================================
help-ui-title = PŘEHLED PŘÍKAZŮ
help-ui-summary = [gray]Dostupné příkazy: { $count }
help-ui-empty-category = [lightgray]V této kategorii nejsou žádné příkazy.[]
help-ui-overloads = (variant: { $count })
help-ui-aliases = [lightgray]Aliasy:[] { $aliases }
help-ui-syntax-title = Syntaxe
help-ui-args-title = Parametry
help-ui-arg-required = [scarlet]Povinný
help-ui-arg-optional = [sky]Volitelný
help-ui-btn-run = Spustit
help-ui-btn-copy = Do chatu
help-ui-btn-back = Zpět
help-ui-copied = [accent]Příkaz: [white]/{ $syntax }
help-ui-executed = [accent]Spouští se příkaz: [white]/{ $syntax }

# Categories
help-cat-all = Vše
help-cat-general = Obecné
help-cat-game = Hra
help-cat-social = Chat
help-cat-votes = Hlasování
help-cat-admin = Admin
# ==============================================================================
# Command Argument Descriptions
# ==============================================================================
# help
commands-help-page-description = Číslo strany k zobrazení.
# login
commands-login-password-description = Tvoje administrátorské heslo.
# ban
commands-ban-id-description = ID hráče, který bude zabanován.
commands-ban-period-description = Délka banu (např. 1d, 2h, 30m).
commands-ban-reason-description = Důvod banu.
# unban
commands-unban-id-description = ID hráče, kterému bude zrušen ban.
# mute
commands-mute-id-description = ID hráče, který bude umlčen.
commands-mute-period-description = Délka umlčení (např. 1h, 30m).
commands-mute-reason-description = Důvod umlčení.
# unmute
commands-unmute-id-description = ID hráče, kterému bude zrušeno umlčení.
# votekick
commands-votekick-target-description = Hráč k vyhození (ID nebo jméno).
commands-votekick-reason-description = Důvod vyhození.
# vote
commands-vote-choice-description = Tvůj hlas: y (ano), n (ne) nebo c (zrušit, jen admini).
# t (team chat)
commands-t-message-description = Zpráva pro spoluhráče.
# g (global chat)
commands-g-message-description = Zpráva pro všechny servery.
# tr (translator)
commands-tr-language-description = Kód jazyka, 'auto' nebo 'off'.
# stats
commands-stats-id-description = ID hráče, jehož statistiky chceš zobrazit
# rank
commands-rank-player-description = Hráč, jehož hodnost chceš zobrazit
# map
commands-map-map-description = Název nebo číslo mapy.
# maps / maps-text
commands-maps-page-description = Číslo strany.
commands-maps-text-page-description = Číslo strany.
# rtv / artv
commands-rtv-map-description = Mapa, pro kterou hlasuješ (volitelné).
commands-artv-map-description = Mapa, na kterou se okamžitě přepne.
# ai
commands-ai-state-description = Stav AI: attack (a) nebo idle (i).
# event / events
commands-events-page-description = Číslo strany.
# ==============================================================================
# General & Help (continued)
# ==============================================================================
commands-information-description = Zobrazí informace o serveru.
commands-info = Informace
commands-info-title = [orange]{ -xcore } — Název serveru: [orange]{ $server-name }
commands-info-text =
    [accent]XCore[white] je [cyan]bezplatný[white] server pro hraní [accent]Mindustry[white].
    { "" }
    Verze XCore — [accent]{ $version }[white]
commands-sync-description = Synchronizuje tvou hru se serverem. Pomáhá při chybách, jako jsou jednotky-duchové.
commands-discord-description = Přesměruje tě na Discord server.
discord-menu-title = [orange]{ -xcore } — Discord
discord-menu-content =
    [white]Tady spravuješ propojení s Discordem.
    { "" }
    [white]Stav: { $status }
    [white]Server: [accent]{ $discordUrl }[]
discord-menu-open = Otevřít Discord
discord-menu-link = Propojit účet
discord-menu-status = Obnovit stav
discord-menu-unlink = Zrušit propojení
discord-menu-status-not-linked = [lightgray]nepropojeno[]
discord-menu-status-linked = [green]{ $discordUsername }[] [gray]({ $discordId })[]
discord-link-menu-title = [orange]{ -xcore } — Propojit účet Discord
discord-link-menu-content =
    [white]Na našem Discord serveru spusť příkaz bota:
    { "" }
    [accent]/link { $code }[]
    { "" }
    [white]Vyprší za: [accent]{ $expireMinutes }[] min
    [white]Discord: [accent]{ $discordUrl }[]
discord-link-menu-refresh = Obnovit kód
discord-link-menu-copy = Kopírovat kód
discord-link-menu-regenerate = Vytvořit nový kód
discord-link-menu-status = Zpět do menu Discordu
welcome =
    [accent]Vítej na { $serverName }!
    [lightgray]Napiš [accent]/help[lightgray] a zobrazí se seznam příkazů
    [lightgray]Napiš [accent]/vote [gray]<y/n>[lightgray] a hlasuj o vyhození hráče
    [lightgray]Napiš [accent]/votekick [gray]<ID/jméno> <důvod…>[lightgray] a zahaj hlasování o vyhození
    [lightgray]Napiš [accent]/t [gray]<zpráva…>[lightgray] a pošli zprávu svému týmu
    [lightgray]Napiš [accent]/g [gray]<zpráva…>[lightgray] a pošli zprávu na všechny servery
    [lightgray]Napiš [accent]/tr [gray]<jazyk/auto>[lightgray] a zapni překladač
    [lightgray]Napiš [accent]/discord[lightgray] a otevři menu Discordu pro propojení účtu
# ==============================================================================
# Chat & Social
# ==============================================================================
commands-t-description = Pošle zprávu jen tvému týmu.
commands-t-chat = [{ "#" }{ $color }][Tým] [coral]> { $badge }[accent]{ $name }[lightgray]: [white]{ $message }
commands-g-description = Pošle zprávu na všechny servery.
commands-a-description = Pošle zprávu jen adminům.
commands-msg-description = Pošle hráči soukromou zprávu.
commands-msg-id-description = ID hráče.
commands-msg-message-description = Text soukromé zprávy.
commands-reply-description = Odpoví poslednímu hráči v soukromých zprávách.
commands-reply-message-description = Text odpovědi.
commands-inbox-description = Otevře menu soukromých zpráv.
commands-inbox-id-description = ID hráče.
commands-tr-description = Nastaví jazyk překladače.
commands-badge-description = Otevře menu odznaků a správu aktivního odznaku.
commands-tr-success = [accent]Jazyk překladače byl změněn na [grey]{ $translatorLanguage }[]!
commands-tr-off = [accent]Překladač je [scarlet]vypnutý[]!
commands-tr-not-found = [scarlet]⚠ Takový jazyk neexistuje.
discord-chat-format = [#5865F2][DISCORD][] [lightgray]| [accent]{ $author }[lightgray] >> [white]{ $message }
global-chat-format = [royal][[[orange]GLOBÁLNÍ [lightgray](z [accent]{ $server }[])[] { $author }[]]: [white]{ $message }
private-message-received = [sky][SZ][] [lightgray]od [accent]{ $author } [gray]#{ $pid }[lightgray]: [white]{ $message }
private-message-sent = [sky][SZ][] [lightgray]pro [accent]{ $target } [gray]#{ $pid }[lightgray]: [white]{ $message }
private-message-unread-count =
    [accent]Máš [white]{ $count }[accent] { $count ->
        [one] nepřečtenou soukromou zprávu
        [few] nepřečtené soukromé zprávy
       *[other] nepřečtených soukromých zpráv
    }.
private-message-join-notification =
    [accent]Máš [white]{ $count }[accent] { $count ->
        [one] nepřečtenou soukromou zprávu
        [few] nepřečtené soukromé zprávy
       *[other] nepřečtených soukromých zpráv
    }. Otevři { $count ->
        [one] ji
       *[other] je
    } příkazem [white]/inbox[accent].
private-message-block-success = [accent]Soukromé zprávy od [white]{ $target } [gray]#{ $pid }[accent] jsou nyní blokované.
private-message-block-already = [lightgray]Soukromé zprávy od [white]{ $target } [gray]#{ $pid }[lightgray] už jsou blokované.
private-message-unblock-success = [accent]Soukromé zprávy od [white]{ $target } [gray]#{ $pid }[accent] už nejsou blokované.
private-message-unblock-missing = [lightgray][white]{ $target } [gray]#{ $pid }[lightgray] není blokován.
private-message-menu-title = [orange]{ -xcore } — Soukromé zprávy
private-message-menu-content =
    [white]Strana [green]{ $page }[] z [green]{ $total }[]
    [white]Nepřečtené: [accent]{ $unread }[]
private-message-menu-empty = [lightgray]Tvoje schránka je prázdná.
private-message-menu-entry-unread = [accent]Nepřečtená[] [white]{ $author } [gray]#{ $pid }[] [lightgray]({ $time })[]: [white]{ $message }
private-message-menu-entry-read = [gray]Přečtená[] [white]{ $author } [gray]#{ $pid }[] [lightgray]({ $time })[]: [white]{ $message }
private-message-details-title = [orange]{ -xcore } — Zpráva
private-message-details-content =
    [white]Od: [accent]{ $author } [gray]#{ $pid }[]
    [white]Čas: [accent]{ $time }[]
    [white]Stav: [accent]{ $status }[]
    { "" }
    [white]{ $message }
private-message-status-unread = nepřečtená
private-message-status-read = přečtená
private-message-blocked-title = [orange]{ -xcore } — Blokovaní hráči
private-message-blocked-content =
    [white]Strana [green]{ $page }[] z [green]{ $total }[]
    [white]Blokováno: [accent]{ $count }[]
private-message-blocked-empty = [lightgray]Nemáš žádné blokované hráče.
private-message-blocked-entry = [white]{ $target } [gray]#{ $pid }[]
private-message-compose = Nová zpráva
private-message-blocked = Blokovaní
private-message-block = Blokovat odesílatele
private-message-unblock = Odblokovat odesílatele
private-message-reply-title = Odpovědět
private-message-reply-message = Zadej zprávu pro [accent]#{ $pid }[]
private-message-compose-target-title = Nová zpráva
private-message-compose-target-message = Zadej ID hráče ve formátu [accent]#123[]
private-message-compose-body-title = Text zprávy
private-message-compose-body-message = Zadej soukromou zprávu pro [accent]{ $pid }[]
# ==============================================================================
# Authentication & Admin Access
# ==============================================================================
commands-login-description = Aktivuje admin práva, pokud tvůj propojený Discord účet už má přístup.
commands-login-incorrect-password = [scarlet]⚠ Špatné heslo!
commands-login-success = [green]Admin práva udělena.
commands-login-confirmed = [green]Admin přístup přes Discord potvrzen.
commands-login-admin-password-created =
    [green]Admin heslo vytvořeno.
    [red]Nezapomeň své heslo! Pokud ho zapomeneš, budeš muset požádat hlavního administrátora o reset.
commands-login-request-approval-discord = [accent]Váš účet nemá přístup administrátora Discordu. Získejte roli administrátora na Discordu a zkuste to znovu.
commands-login-verifying = [lightgray]Ověřování hesla správce...
commands-login-already-processing = [scarlet]⚠ Požadavek na přihlášení již probíhá. Počkejte prosím.
commands-login-rate-limited = [scarlet]⚠ Příliš mnoho neúspěšných pokusů o přihlášení. Zkuste to prosím později.
commands-discord-link-created =
    [green]Kód pro propojení s Discordem vytvořen: [accent]{ $code }[]
    [lightgray]Na našem Discord serveru spusť do [accent]{ $expireMinutes }[] min příkaz bota [accent]/link { $code }[].
    [cyan]{ $discordUrl }
commands-discord-link-confirmed = [green]Discord účet propojen: [accent]{ $discordUsername }[]
commands-discord-link-already-linked = [lightgray]Tento Mindustry účet už je propojený. Použij [accent]/discord status[] nebo [accent]/discord unlink[].
commands-discord-link-error = [scarlet]Kód pro propojení s Discordem se nepodařilo vytvořit. Zkus to později.
commands-discord-status-not-linked = [lightgray]Tvůj účet není propojený s Discordem.
commands-discord-status-linked = [green]Propojený Discord: [accent]{ $discordUsername }[] [gray]({ $discordId })[]
commands-discord-unlink-not-linked = [lightgray]Tvůj účet není propojený s Discordem.
commands-discord-unlink-success = [green]Propojení s Discordem odstraněno.
commands-logout-description = Odhlásit se. Tím [scarlet]přijdeš o admin práva.
commands-logout-successful = [green]Admin práva odebrána.
# ==============================================================================
# Moderation (Ban, Mute, Kick)
# ==============================================================================
commands-ban-description = Zabanuje hráče.
commands-ban-success = { $nickname } [scarlet]zabanován
commands-unban-description = Zruší ban hráče.
commands-unban-success = { $nickname }[accent] #{ $pid } [green]byl odbanován.
commands-mute-description = Umlčí hráče.
commands-mute-success = [accent]{ $nickname } byl umlčen
commands-unmute-description = Zruší umlčení hráče.
commands-unmute-success = [green]Umlčení zrušeno pro []{ $nickname }
commands-alert-description = Zobrazí vybraným nebo všem hráčům výrazný banner s oznámením.
commands-toast-description = Zobrazí vybraným hráčům varovné upozornění.
commands-announcement-description = Odešle pravidelné oznámení podle klíče nebo další v pořadí.
commands-audit-description = Zobrazí historii a akce týmu a moderace.
ban-content = [scarlet]⚠ Zabanován[]
    [accent]{ $nickname }[white] — na tomto serveru máš trvalý ban.
    [lightgray]Pro odvolání navštiv Discord kanál [gray]{ support-channel }[]:
    [cyan]{ $discordUrl }
ban-cancelled = [accent]Ban hráče [scarlet]{ $nickname }[accent] byl zrušen
tempban-content = [scarlet]⚠ Zabanován[]
    [accent]{ $nickname }[white] — na tomto serveru máš dočasný ban.
    { "" }
    [orange]» [accent]Admin: [white]{ $adminName }
    [orange]» [accent]Důvod: [gold]{ $reason }
    [orange]» [accent]Zbývá: { DURATION($duration, style: "full", colored: "true", maxUnits: 2) }
    [orange]» [accent]Vyprší: [white]{ DATETIME($expireDate, dateStyle: "medium", timeStyle: "short") }
    { "" }
    [lightgray]Pro odvolání navštiv Discord kanál [gray]{ support-channel }[]:
    [cyan]{ $discordUrl }
tempban-player-banned = [scarlet] Admin { $adminName }[scarlet] zabanoval hráče [gray]'[]{ $playerName }[gray]'
you-are-muted-by =
    [orange]⚠ Chat omezen[]
    [lightgray]Administrátor [accent]{ $adminName }[lightgray] tě umlčel.
    [orange]» [accent]Důvod: [gold]{ $reason }
    [orange]» [accent]Zbývá: { DURATION($duration, style: "full", colored: "true", maxUnits: 2) }
you-are-muted =
    [orange]⚠ Chat omezen[]
    [lightgray]Dokud platí umlčení, nemůžeš posílat zprávy.
    [orange]» [accent]Admin: [white]{ $adminName }
    [orange]» [accent]Důvod: [gold]{ $reason }
    [orange]» [accent]Zbývá: { DURATION($duration, style: "full", colored: "true", maxUnits: 2) }
kick-pirated-game = [accent]Zjištěn neautorizovaný klient. [scarlet]Přístup odepřen[]. Hraj prosím [lime]oficiální[] verzi ze [blue]Steamu[], [blue]Google Play[] nebo [blue]itch.io[].
kick-recently-kicked =
    [accent]Z tohoto serveru jsi byl nedávno vyhozen.
    Počkej [cyan]{ DURATION($remaining, style: "timer") }[accent], než se znovu připojíš.
kick-admintools-outdated =
    [green]Požadovaná verze AdminTools: [grey]{ $requiredVersion }[]
    [scarlet]Tvoje verze AdminTools: [grey]{ $version }[]
    { "" }
    [cyan]Aktualizuj AdminTools, aby ses mohl připojit k tomuto serveru.
support-channel = #reports-appeals
# ==============================================================================
# Voting (VoteKick)
# ==============================================================================
commands-votekick-description = Hlasování o vyhození hráče ze serveru.
commands-vote-description = Hlasuj v probíhajícím hlasování.
commands-vote-vote-with = [scarlet]⚠ Hlasuj pomocí [orange]/vote <y/n/c>
votekick-vote =
    { $starter } [grey]#[white]{ $starterId }[lightgray] hlasoval pro vyhození { $target } [grey]#[white]{ $targetId }[lightgray] z důvodu [orange]{ $reason }[lightgray]. ([accent]{ $votes }[]/[accent]{ $required }[])
    [lightgray]Napiš [orange]/vote <y/n>[] a hlasuj.
votekick-left = { $player }[lightgray] odešel. Jeho hlas byl zrušen. ([accent]{ $votes }[]/[accent]{ $required }[])
votekick-fail = [lightgray]Hlasování neuspělo. Málo hlasů pro vyhození { $target }[lightgray].
votekick-cancelled = [scarlet]Hlasování o vyhození { $target }[scarlet] zrušil { $admin }.
votekick-success =
    [orange]Hlasování prošlo. { $target }[orange] je vyhozen na [scarlet]{ $minutes }[] { $minutes ->
        [one] minutu
        [few] minuty
       *[other] minut
    }.
# ==============================================================================
# Maps & RTV
# ==============================================================================
commands-map-description = Statistiky konkrétní mapy.
commands-map-title = [orange]{ -xcore } — Mapa
commands-map-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $name }[] [gray]autor [sky]{ $author }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Přehled[]
    [gray]Velikost: [white]{ $width }x{ $height } [darkgray]|[gray] Hlasy: [lime]+{ $like } [darkgray]/[scarlet] -{ $dislike }[]
    { "" }
    [accent]■ Aktivita[]
    [gray]Odehráno celkem: [white]{ $played } [darkgray]|[gray] Letos: [white]{ $playedYear }[]
    [gray]Naposledy hráno: [white]{ $lastPlayed }[]
    [gray]Popularita: [white]{ $popularity } [darkgray]|[gray] Zájem: [white]{ $interest } [darkgray]|[gray] Reputace: [white]{ $reputation }[]
    { "" }
    [accent]■ Délka zápasu[]
    [gray]Min.: [white]{ $min } [darkgray]|[gray] Prům.: [white]{ $avg } [darkgray]|[gray] Max.: [white]{ $max }[]
commands-maps-description = Seznam všech map na tomto serveru.
commands-maps-title = [orange]{ -xcore } — Mapy
commands-maps-content =
    [gray]Aktuální mapa: [accent]{ $current }[]
    [white]Strana [green]{ $page }[] z [green]{ $total }[]
commands-maps-current-row = { $name } ★
commands-maps-text-description = Seznam všech map na tomto serveru.
commands-maps-text-start-content =
    [accent]Aktuální mapa: []{ $name }[white]
    [orange][gold]Seznam map [lightgray]{ $page }[gray]/[lightgray]{ $total }
commands-maps-text-content =
    { "" }
    { $index }. [orange] - [white]{ $name }[orange] | [green]{ $reputation }[orange] | [white]{ $width }x{ $height }[orange] | [white]{ $lastPlayed }[orange] | Autor: [sky]{ $author }
commands-artv-description = Okamžitě změní mapu.
commands-artv-map-skipped = { $nickname }[accent] přeskočil mapu. Další mapa: { $name }.
commands-artv-event-skipped = { $nickname }[accent] přeskočil událost. Další událost: { $name }.
commands-rtv-description = Hlasování o změně mapy.
commands-vnw-description = Hlasování o dřívějším spuštění další vlny.
commands-avnw-description = Okamžitě spustí další vlnu.
commands-like-description = Hlasuj pro aktuální mapu (zvyšuje její reputaci).
commands-dislike-description = Hlasuj proti aktuální mapě.
map-vote-title = [orange]{ -xcore } — [scarlet]KONEC HRY!
map-vote-content =
    { "" }
    Další mapa: [accent]{ $mapName }[], autor [accent]{ $author }[white].
    Nová hra začne za [accent]{ $seconds }[white] { $seconds ->
        [one] sekundu
        [few] sekundy
       *[other] sekund
    }.
    { "" }
    [cyan]Líbila se ti tato mapa?
map-vote-like = [green]👍 Líbí se mi
map-vote-dislike = [red]👎 Nelíbí se mi
map-vote-like-selected = [gray]Líbila se ti
map-vote-dislike-selected = [gray]Nelíbila se ti
map-rtv = [orange]Hlasování
map-artv = [red]Okamžitá změna
map-maps = Mapy
map-maps-back = Zpět na seznam map
current-map = Aktuální mapa
next-map = Další mapa

# Map UI Modernized
map-ui-search-hint = Hledat mapu nebo autora...
map-ui-total = [lightgray]Mapy: [white]{ $count }[]
map-ui-about-title = O mapě
map-ui-rtv-title = Změna mapy
map-ui-no-maps-found = [lightgray]Žádné mapy neodpovídají hledání.[]
map-ui-by = autor [lightgray]{ $author }[]
map-ui-mode = Režim: [white]{ $mode }[]
map-ui-loading = [gray]Načítání...[]
map-ui-no-preview = [gray]Bez náhledu[]
map-ui-dimensions = [gray]Rozměry: [white]{ $width } x { $height }[]
map-ui-total-plays = [gray]Odehráno: [white]{ $played } [lightgray]({ $playedYear } letos)[]
map-ui-last-played = [gray]Naposledy hráno: [white]{ $lastPlayed }[]
map-ui-description = [gray]Popis: [lightgray]{ $description }[]
map-ui-no-description = [gray]Popis: [lightgray]Bez popisu.[]

map-ui-col-duration = Délka
map-ui-duration-min = [gray]Min.: [white]{ $value }[]
map-ui-duration-avg = [gray]Prům.: [white]{ $value }[]
map-ui-duration-max = [gray]Max.: [white]{ $value }[]

map-ui-col-popularity = Popularita
map-ui-popularity-score = [gray]Skóre: [white]{ $value }[]
map-ui-popularity-pop = [gray]Popularita: [white]{ $value }[]
map-ui-popularity-interest = [gray]Zájem: [white]{ $value }[]

map-ui-col-community = Komunita
map-ui-community-approval = [gray]Schválení: [green]{ $rate }%[]

map-ui-btn-like = Líbí se mi ({ $count })
map-ui-btn-dislike = Nelíbí se mi ({ $count })

map-ui-rtv-active-status = [accent]● Probíhá hlasování: [white]{ $votes }/{ $required }[] [gray](konec za [white]{ $seconds } s[gray])[]
map-ui-rtv-vote-yes = Hlasovat pro tuto mapu
map-ui-rtv-start = Zahájit hlasování o této mapě
map-ui-admin-rtv = Změnit mapu hned
map-ui-admin-rtv-confirm = Stiskni znovu pro potvrzení

gamemode-survival = Přežití
gamemode-attack = Útok
gamemode-pvp = PvP
gamemode-sandbox = Sandbox
gamemode-editor = Editor
rtv-vote =
    { $nickname }[lightgray] hlasoval pro změnu mapy na [orange]{ $mapName }[lightgray]. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Napiš [orange]y[] nebo [orange]n[] a hlasuj.
rtv-left = { $nickname }[lightgray] odešel. Jeho hlas pro změnu mapy byl zrušen. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
rtv-fail = [lightgray]Hlasování neuspělo. Málo hlasů pro změnu mapy na [orange]{ $mapName }[].
rtv-success = [orange]Hlasování prošlo. Mapa [accent]{ $mapName }[] se načte za [accent]{ $mapLoadDelay }[] { $mapLoadDelay ->
    [one] sekundu
    [few] sekundy
   *[other] sekund
}…
rtv-cancelled = [lightgray]Hlasování o změně mapy na [orange]{ $mapName }[lightgray] zrušil { $admin }.
vnw-vote =
    { $nickname }[lightgray] hlasoval pro dřívější spuštění vlny [orange]{ $wave }[lightgray]. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Napiš [orange]y[] nebo [orange]n[] a hlasuj.
vnw-left = { $nickname }[lightgray] odešel. Jeho hlas pro dřívější spuštění vlny [orange]{ $wave }[lightgray] byl zrušen. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
vnw-fail = [lightgray]Hlasování neuspělo. Málo hlasů pro dřívější spuštění vlny [orange]{ $wave }[].
vnw-success = [orange]Hlasování prošlo. Vlna [accent]{ $wave }[] právě začíná.
vnw-cancelled = [lightgray]Hlasování o dřívějším spuštění vlny [orange]{ $wave }[lightgray] zrušil { $admin }.
vnw-obsolete = [lightgray]Vlna [orange]{ $wave }[lightgray] už začala, výsledek hlasování už není potřeba.
# ==============================================================================
# Statistics & Ranks & Players
# ==============================================================================
commands-player-description = Zobrazí statistiky hráče.
commands-settings-description = Otevře tvoje nastavení hráče.
player-menu-player = Hráč
player-menu-player-title = [orange]{ -xcore } — Statistiky hráče
player-menu-player-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $customNickname }[] [gray]#{ $pid }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Profil[]
    [gray]Jméno: [white]{ $nickname } [darkgray]|[gray] Admin: [lime]{ $admin }[]
    [gray]Odznak: [white]{ $activeBadge } [darkgray]|[gray] Systémový: [coral]{ $systemBadge }[]
    [gray]Připojen: [white]{ $accountCreated }[]
    { "" }
    [accent]■ Hodnocení[]
    [gray]Herní čas: [white]{ $totalPlayTime }[]
    [gray]MiniPvP: [sky]{ $pvpRating } [darkgray]|[gray] Starý Hexed: [sky]{ $hexedRankName } [gray]({ $hexedPoints } b.) [darkgray]|[gray] Top: [accent]{ $hexedTopRank }[]
    { "" }
    [accent]■ Zápasy: [white]{ $gamesPlayed } [gray]her [darkgray]|[lime] { $gamesWon } [gray]výher [darkgray]|[sky] { $winRate }% [gray]úspěšnost[]
    [gray]• [white]PvP: { $pvpSummary }[]
    [gray]• [white]Přežití: { $survivalSummary }[]
    [gray]• [white]Starý Hexed: { $hexedSummary }[]
    { "" }
    [accent]■ Bojová efektivita[]
    [gray]Bloky (Postavené/Rozebrané/Zničené): [lime]{ $blocksBuilt } [darkgray]/ [orange]{ $blocksDeconstructed } [darkgray]/ [scarlet]{ $blocksDestroyed }[]

# Modern Player Stats UI
player-stats-title = [orange]{ -xcore } — Profil hráče
player-stats-tab-overview = Přehled
player-stats-tab-stats = Statistiky
player-stats-tab-matches = Zápasy
player-stats-tab-blocks = Bloky
player-stats-tab-players = Online ({ $count })

player-stats-status-online = [lime]● Online[]
player-stats-status-offline = [gray]○ Offline[]
player-stats-no-bio = [gray]Zatím bez popisu.[]
player-stats-loading = [lightgray]Načítání dat...[]
player-stats-no-stats = [gray]Zatím nejsou zaznamenaná žádná data ze zápasů.[]
player-stats-filter-all = Filtr: Všichni
player-stats-filter-admins = Filtr: Jen admini
player-stats-filter-non-admins = Filtr: Bez adminů
player-stats-refresh = Obnovit
player-stats-combat-efficiency = Efektivita boje a stavění
player-stats-waves-summary = [gray]vlny: max. [lime]{ $best }[], prům. [white]{ $avg }[]
player-stats-hexed-top-placement = [gray]nejlepší [accent]#{ $best }[], top 3: [sky]{ $top3 }[]
player-stats-max-rank = [gold]★ DOSAŽENA NEJVYŠŠÍ HODNOST ★[]
player-stats-max-league = [gold]★ DOSAŽENA NEJVYŠŠÍ LIGA ★[]
player-stats-hexed-wins-left = [lightgray]Výher do [white]{ $rank }[]: { $wins }
player-stats-league-elo-left = [lightgray]ELO do [white]{ $league }[]: { $elo }
player-stats-ratio-legend = [lightgray]Poměr bloků[]

player-stats-account-created = [gray]Připojen:[]
player-stats-play-time = [gray]Herní čas:[]
player-stats-legacy-pvp-rating = [gray]Staré PvP:[]
player-stats-hexed-rank = [gray]Starý Hexed:[]
player-stats-hexed-leaderboard = [gray]Hexed top:[]

player-stats-total-games = [gray]Her celkem:[]
player-stats-victories = [gray]Výhry:[]
player-stats-survival-summary = [gray]Přežití:[]
player-stats-hexed-summary = [gray]Starý Hexed:[]

player-stats-blocks-built = [gray]Postavené bloky:[]
player-stats-blocks-deconstructed = [gray]Rozebrané:[]
player-stats-blocks-destroyed = [gray]Zničené:[]
player-stats-units-produced = [gray]Vyrobené jednotky:[]
player-stats-units-lost = [gray]Ztracené jednotky:[]

player-stats-games-played-value = [gray]Hry:[] [white]{ $count }[]
player-stats-victories-value = [gray]Výhry:[] [lime]{ $wins }[]  [darkgray]|[]  [sky]{ $winRate }%[] [gray]úspěšnost[]
player-stats-hexed-points = [gray]({ $points } b.)[]

player-stats-btn-settings = Nastavení
player-stats-btn-audit = Historie
player-stats-btn-players = Online
player-stats-btn-close = Zavřít
player-stats-admin-tag = [coral]<Admin>[]
player-menu-players = Hráči online
player-menu-players-title = [orange]{ -xcore } — Hráči online
player-menu-players-content = [white]Strana [green]{ $page }[] z [green]{ $total }[]
player-menu-players-empty = Žádní hráči online
player-menu-players-row = [white]{ $nickname } [gray](PID: { $pid })[]
player-menu-settings = Nastavení
player-menu-settings-title = [orange]{ -xcore } — Nastavení hráče
player-menu-settings-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $displayNickname }[] [gray]#{ $pid }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Profil[]
    [gray]Jméno: [white]{ $nickname } [darkgray]|[gray] Zobrazované: [lime]{ $customNickname }[]
    [gray]Odznak: [white]{ $activeBadge } [darkgray]|[gray] Systémový: [coral]{ $systemBadge }[]
    { "" }
    [accent]■ Viditelnost[]
    [gray]Žebříček: [white]{ $leaderboard }[]
    { "" }
    [accent]■ Chat[]
    [gray]Globální: [white]{ $globalChat } [darkgray]|[gray] Discord: [white]{ $discordRelay }[]
    [gray]Překladač: [white]{ $translatorLanguage }[]
    { "" }
    [accent]■ Jazyk[]
    [gray]Jazyk: [white]{ $language }[]
player-menu-settings-chat = Nastavení chatu
player-menu-settings-chat-title = [orange]{ -xcore } — Nastavení chatu

# Modern Player Settings Form
player-settings-tab-profile = Profil
player-settings-tab-chat = Chat
player-settings-tab-badges = Odznaky
player-settings-chat-preview = Náhled chatu
player-settings-chat-preview-sample = Ukázka
player-settings-chat-preview-message = Ahoj světe!
player-settings-symbol-color-mode = Barva symbolu
player-settings-badges-my = Moje odznaky
player-settings-badges-all = Všechny odznaky
player-settings-badge-equip = Nasadit
player-settings-badge-unequip = Sundat
player-settings-badge-preview = Náhled
player-settings-badge-previewing = V náhledu
player-settings-translator-lang = Překladač chatu
player-settings-translator-off = Vypnuto
player-settings-badges-empty = Zatím nemáš odemčené žádné odznaky.
player-settings-manage-badges = Spravovat odznaky
player-settings-global-chat = Globální chat
player-settings-discord-relay = Přenos z Discordu
player-settings-leaderboard = Zobrazovat žebříček
player-settings-language = Jazyk
player-settings-saved = [lime]Nastavení uloženo![]
player-settings-reset-feedback = [lightgray]Vlastní jméno bylo obnoveno.[]
player-settings-edit-badges = [accent]Upravit[]
player-settings-tab-language = Jazyk
player-settings-identity = Jméno a popis
player-settings-interface = Obrazovka
player-settings-leaderboard-hint = Seznam nejlepších hráčů nad hrou. Funguje v Mini-PvP.
player-settings-global-chat-hint = Zprávy hráčů z ostatních serverů XCore.
player-settings-discord-relay-hint = Zprávy z Discord kanálu serveru v herním chatu.
player-settings-translator-hint = Zprávy ostatních hráčů se překládají do zvoleného jazyka.
player-settings-language-hint = Jazyk menu a zpráv serveru. Auto se řídí jazykem tvé hry.
player-menu-settings-chat-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [accent]■ Viditelnost chatu[]
    [gray]Globální chat: [white]{ $globalChat }[]
    [gray]Přenos z Discordu: [white]{ $discordRelay }[]
    { "" }
    [accent]■ Překlad[]
    [gray]Jazyk překladače: [white]{ $translatorLanguage }[]
player-menu-settings-translator-title = [orange]{ -xcore } — Výběr jazyka překladače
player-menu-settings-language-title = [orange]{ -xcore } — Výběr jazyka
player-menu-settings-customNickname = Upravit jméno
player-menu-settings-customNickname-title = [orange]{ -xcore } — Upravit jméno
player-menu-settings-customNickname-message = [lightgray]Nech prázdné pro obnovení
player-menu-settings-customNickname-reset = [scarlet]Obnovit jméno
player-menu-settings-description = Upravit popis
player-menu-settings-description-title = [orange]{ -xcore } — Upravit popis
player-menu-settings-badges = Odznaky
player-menu-settings-global-chat-on = [green]Globální chat
player-menu-settings-global-chat-off = [red]Globální chat
player-menu-settings-discord-relay-on = [green]Přenos z Discordu
player-menu-settings-discord-relay-off = [red]Přenos z Discordu
audit-menu-open = Historie
audit-menu-actions-open = Akce
audit-menu-history-title = [orange]{ -xcore } — Historie
audit-menu-history-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $player }[] [gray]#{ $pid }[]
    [gray]Zobrazené záznamy: [accent]{ $entriesShown }[]
    [gray]{ $pageState } [darkgray]|[] { $nextState }
    [lightgray]{ $hint }[]
audit-menu-history-page-first = Nejnovější záznamy
audit-menu-history-page-older = Starší záznamy
audit-menu-tab-sanctions = Obdržené tresty
audit-menu-tab-actions = Provedené akce
audit-menu-filter-all = Vše
audit-menu-filter-bans = Bany
audit-menu-filter-mutes = Umlčení
audit-menu-filter-warns = Varování
audit-menu-filter-other = Ostatní
audit-menu-btn-back = Zpět na historii
audit-menu-btn-copy-id = Kopírovat ID
audit-menu-copy-id-success = ID záznamu bylo odesláno do chatu
audit-menu-field-id = ID záznamu
audit-menu-page = Strana { $page }
audit-menu-details-unavailable = Záznam není dostupný.
audit-menu-field-target = Hráč
audit-menu-field-actor = Provedl
audit-menu-field-server = Server
audit-menu-field-reason = Důvod
audit-menu-section-time = Čas
audit-menu-field-occurred = Kdy
audit-menu-field-duration = Délka
audit-menu-field-expires = Vyprší
audit-menu-btn-revoke = Zrušit trest
audit-menu-revoke-success = Trest byl zrušen.
audit-menu-status-active = AKTIVNÍ
audit-menu-status-expired = VYPRŠEL
audit-menu-status-permanent = TRVALÝ
audit-menu-history-more = Jsou k dispozici další záznamy
audit-menu-history-end = Konec historie
audit-menu-history-empty = Pro tohoto hráče zatím nejsou žádné záznamy.
audit-menu-history-hint = Vyber níže záznam a zobrazí se podrobnosti.
audit-menu-actions-title = [orange]{ -xcore } — Akce
audit-menu-actions-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $player }[] [gray]#{ $pid }[]
    [gray]Zobrazené záznamy: [accent]{ $entriesShown }[]
    [gray]{ $pageState } [darkgray]|[] { $nextState }
    [lightgray]{ $hint }[]
audit-menu-actions-empty = Tento hráč zatím neprovedl žádné akce.
audit-menu-actions-hint = Vyber níže záznam a uvidíš, co tento hráč udělal.
audit-menu-summary-row = [accent]{ $action }[] [darkgray]•[] [white]{ $actor }[] [gray]— { $reason }
audit-menu-action-summary-row = [accent]{ $action }[] [darkgray]•[] [white]{ $target }[] [gray]— { $reason }
audit-menu-details-title = [orange]{ -xcore } — Podrobnosti záznamu
audit-menu-details-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $player }[] [gray]#{ $pid }[]
    { "" }
    [accent]■ Událost[]
    [gray]Akce: [white]{ $action }[]
    [gray]Provedl: [white]{ $actor }[]
    [gray]Důvod: [white]{ $reason }[]
    { "" }
    [accent]■ Čas[]
    [gray]Kdy: [white]{ $occurredAt }[]
    [gray]Délka: [white]{ $duration }[]
    [gray]Vyprší: [white]{ $expiresAt }[]
    { "" }
    [accent]■ Metadata[]
    [gray]ID záznamu: [white]{ $auditId }[]
audit-menu-unknown-actor = Neznámý
audit-menu-unknown-target = Neznámý
audit-menu-reason-unspecified = Neuvedeno
audit-menu-duration-permanent = Trvale
audit-menu-action-ban = Ban
audit-menu-action-unban = Zrušení banu
audit-menu-action-mute = Umlčení
audit-menu-action-unmute = Zrušení umlčení
audit-menu-action-warn = Varování
audit-menu-action-kick = Vyhození
audit-menu-action-note = Poznámka
audit-menu-action-quarantine = Karanténa
audit-menu-action-unquarantine = Zrušení karantény
player-menu-player-max-rank = Dosažena nejvyšší hodnost
player-menu-player-hexed-progress = [gray]Potřebné výhry pro [white]{ $nextRankName }[gray]: [accent]{ $requiredPoints }[]
player-menu-player-no-mode-stats = [gray]žádná data[]
player-menu-player-pvp-summary = [gray]hry [white]{ $gamesPlayed }[], výhry [lime]{ $gamesWon }[], [sky]{ $winRate }%[]
player-menu-player-survival-summary = [gray]vlny: max. [lime]{ $bestWave }[], prům. [white]{ $averageWave }[] [gray](hry: { $gamesPlayed })[]
player-menu-player-hexed-summary = [gray]zápasy [white]{ $gamesPlayed }[], 1. místo [lime]{ $gamesWon }[], nejlepší místo [accent]#{ $bestPlacement }[]
player-menu-time-days = { $value } d
player-menu-time-hours = { $value } h
player-menu-time-minutes = { $value } min
settings-language-label = Jazyk: [green]{ $lang }[]
settings-translator-label = Překladač: [green]{ $lang }[]
badge-menu-title = [orange]{ -xcore } — Odznaky
badge-menu-content =
    [white]Systémový odznak: [green]{ $systemBadge }[]
    [white]Aktivní odznak: [green]{ $activeBadge }[]
    [white]Barva symbolu: [green]{ $symbolColorMode }[]
badge-menu-empty = [lightgray]Zatím nemáš odemčené žádné odznaky.
badge-menu-row = [white]{ $badge }[] [gray]-[] { $description }
badge-menu-symbol-color-button = Barva symbolu: [green]{ $mode }[]
badge-menu-symbol-color-title = [orange]{ -xcore } — Barva symbolu odznaku
badge-menu-symbol-color-content =
    [white]Aktuální režim: [green]{ $mode }[]
    [lightgray]Vyber, jak se má symbol odznaku obarvit.
badge-menu-symbol-color-default = Výchozí barva odznaku
badge-menu-symbol-color-player-color = Barva hráče
badge-menu-view-all = Zobrazit všechny odznaky
badge-menu-all-title = [orange]{ -xcore } — Všechny odznaky
badge-menu-all-content = [lightgray]Všechny odznaky s jejich stavem a popisem.
badge-menu-all-row = [white]{ $badge }[] [gray]-[] [accent]{ $state }[] [gray]-[] { $description }
badge-clear-button = Sundat aktivní odznak
badge-state-system = Systémový
badge-state-system-active = Systémový aktivní
badge-state-active = Aktivní
badge-state-unlocked = Odemčený
badge-state-locked = Zamčený
badge-set-success = [accent]Aktivní odznak: [green]{ $badge }[].
badge-clear-success = [accent]Aktivní odznak sundán.
badge-grant-success = [accent]Odznak [green]{ $badge }[] udělen hráči [green]{ $nickname }[][gray]#{ $pid }[].
badge-revoke-success = [accent]Odznak [green]{ $badge }[] odebrán hráči [green]{ $nickname }[][gray]#{ $pid }[].
badge-already-unlocked = [scarlet]⚠ Odznak [accent]{ $badge }[scarlet] už je odemčený.
badge-not-owned = [scarlet]⚠ Hráč nemá odznak [accent]{ $badge }[scarlet].
error-badge-not-found = [scarlet]⚠ Odznak [accent]{ $badge }[scarlet] nebyl nalezen.
error-badge-not-unlocked = [scarlet]⚠ Odznak [accent]{ $badge }[scarlet] není odemčený.
error-badge-not-selectable = [scarlet]⚠ Odznak [accent]{ $badge }[scarlet] nelze vybrat ručně.
badge-admin-name = Admin
badge-admin-description = Automatický odznak pro administrátory.
badge-developer-name = Vývojář
badge-developer-description = Udělován vývojářům XCore.
badge-translator-name = Překladatel
badge-translator-description = Udělován těm, kdo překládají XCore.
badge-map-maker-name = Tvůrce map
badge-map-maker-description = Udělován autorům map používaných na serveru.
badge-contributor-name = Přispěvatel
badge-contributor-description = Udělován za přínos pro XCore nebo jeho komunitu.
badge-bug-finder-name = Lovec chyb
badge-bug-finder-description = Udělován za pravidelná a kvalitní hlášení chyb.
badge-event-winner-name = Vítěz události
badge-event-winner-description = Udělován vítězům speciálních událostí na serveru.
badge-veteran-name = Veterán
badge-veteran-description = Udělován dlouholetým a váženým hráčům.
badge-season-champion-name = Šampion sezóny
badge-season-champion-description = Udělován za umístění na stupních vítězů hodnocené sezóny.
commands-lb-description = Zapne/vypne žebříček.
commands-lb-success =
    { $leaderboardEnabled ->
        [true] [accent]Žebříček [green]zapnut.
       *[other] [accent]Žebříček [scarlet]vypnut.
    }
leaderboard = [blue]Žebříček
commands-observer-description = Přepne do režimu pozorovatele. Tvoje aktuální jednotka zmizí a přesuneš se do týmu diváků.
commands-rank-description = Zobrazí tvoji hodnost nebo hodnost jiného hráče.
commands-rank-content =
    { $nickname }
    { $rankTag } [accent]{ $rankName }
    [gold]Výhry: { $points }/{ $requiredPoints }
commands-ranks-description = Zobrazí informace o hodnostech.
commands-ranks-content =
    { $rankTag } [accent]{ $rankName }
    [gold]Požadavek: [grey]{ $requiredPoints } [accent]výher[]
commands-ranks-footer = Výhry se počítají jen za porážku hráče stejné nebo vyšší hodnosti.
commands-top-description = Nejlepší hráči.
commands-season-description = Hodnocená sezóna: zbývající čas, tvoje umístění a vítězové minulé sezóny.
commands-top-hexed-content = [orange]{ $index }. { $nickname }[accent]: [blue]{ $rankName } [cyan]{ $points } []výher
commands-top-pvp-content = [orange]{ $index }. { $nickname }[accent]: [cyan]{ $rating }
top-menu-title = [orange]{ -xcore } — Nejlepší hráči: [accent]{ $category }
top-menu-content =
    [lightgray]Vyber hráče a otevře se jeho profil.[]
    [lightgray]Strana [green]{ $page }[]/[green]{ $totalPages }[] [gold]•[] [lightgray]Hráči: [green]{ $totalEntries }[]
    { $selfRankLine }
top-menu-empty =
    [accent]Kategorie: [green]{ $category }[]
    [gray]Zatím žádní hráči.
top-menu-categories-title = [orange]{ -xcore } — Kategorie žebříčku
top-menu-categories-content =
    [lightgray]Vyber, který žebříček se zobrazí.[]
    [lightgray]Aktuální: [green]{ $category }[]
top-menu-category-button = [accent]Kategorie: [green]{ $category }[]
top-menu-category-mini-pvp = MiniPvP
top-menu-category-playtime = Herní čas
top-menu-category-hexed = Hexed
top-menu-self-rank-known = [lightgray]Tvoje umístění: [accent]#{ $rank }[]
top-menu-self-rank-unknown = [lightgray]Tvoje umístění: [gray]nenalezeno[]
top-menu-entry-mini-pvp = { $rankLabel } { $leagueIcon } [accent]{ $nickname }[] [gray]—[] [sky]{ $value }[]
top-menu-entry-playtime = { $rankLabel } [accent]{ $nickname }[] [gray]—[] [green]{ $value }[]
top-menu-entry-hexed = { $rankLabel } [accent]{ $nickname }[] [gray]—[] [violet]{ $rankName }[] [gold]•[] [cyan]{ $value }[]
top-menu-total-count = Hráči: { $count }
top-menu-btn-find-self = Najít mě
top-menu-on-this-page = na této stránce
top-menu-unranked = V této kategorii zatím nejsi hodnocen
top-menu-self-rank-line = Tvoje pořadí: { $rank }
top-menu-score-points = { $points } b.
top-menu-score-minutes = { $time }
# ==============================================================================
# Game Modes (Hexed, PvP, Surrender, AI)
# ==============================================================================
commands-surrender-description = Vzdát se v Hexed. Tvůj aktuální tým bude zničen, jednotka zmizí a přesuneš se do týmu diváků.
commands-surrender-success = [green]Vzdal ses a teď sleduješ hru
commands-observer-success = [green]Teď sleduješ hru
commands-observer-exit-success = [green]Už hru nesleduješ
commands-ai-description = Ovládá AI.
commands-ai-usage = [red]attack(a) []nebo [accent]idle(i)
hexed-popup = [blue]{ DURATION($remaining, style: "timer") }[] do konce hry.
hexed-eliminated = { $nickname } [gold]byl [scarlet]vyřazen[]!
hexed-leaderboard-content = [orange]{ $index }. { $nickname }[accent]: [cyan]{ $hexes } [accent]hexů
hexed-ranks-newbie = Nováček
hexed-ranks-regular = Stálý hráč
hexed-ranks-advanced = Pokročilý
hexed-ranks-veteran = Veterán
hexed-ranks-davastator = Ničitel
hexed-ranks-the_legend = Legenda
hexed-game-over-header = Konec hry. Vítězové:
hexed-game-over-winner-row =
    [orange]{ $index }. { $name }[][accent]: [cyan]{ $cores } { $cores ->
        [one] hex
        [few] hexy
       *[other] hexů
    }
hexed-game-over-no-winners = Konec hry. Bohužel se nepodařilo najít vítěze.
hexed-game-over-restart = Nová hra za 10 sekund…
rating_league_scrap = Šrot
rating_league_copper = Měď
rating_league_lead = Olovo
rating_league_graphite = Grafit
rating_league_silicon = Křemík
rating_league_titanium = Titan
rating_league_thorium = Thorium
rating_league_plastanium = Plastanium
rating_league_phase_fabric = Fázová tkanina
rating_league_surge_alloy = Rázová slitina
pvp-team-won = Tvůj tým vyhrál. Tvoje hodnocení stoupá o { $increased }
pvp-team-lose = Tvůj tým prohrál. Tvoje hodnocení klesá o { $reduced }
pvp-match-settlement-win = [accent]■ Výsledek: [green]Výhra![] Tvoje hodnocení: [stat]{ $oldRating }[] -> [stat]{ $newRating }[] [green](+{ $delta })[] { $leagueIcon } { $leagueName }
pvp-match-settlement-loss = [accent]■ Výsledek: [scarlet]Prohra![] Tvoje hodnocení: [stat]{ $oldRating }[] -> [stat]{ $newRating }[] [scarlet]({ $delta })[] { $leagueIcon } { $leagueName }
pvp-match-settlement-draw = [accent]■ Výsledek: [yellow]Remíza![] Tvoje hodnocení: [stat]{ $oldRating }[] -> [stat]{ $newRating }[] [yellow]({ $delta })[] { $leagueIcon } { $leagueName }
pvp-match-settlement-exempt = [accent]■ Výsledek: [lightgray]Bez změny hodnocení (málo aktivního hraní).[]
season-ending-soon = [accent]■ [white]{ $ladder }[]: sezóna { $number } končí za [stat]{ $remaining }[]. Využij poslední zápasy!
season-started = [accent]■ [white]{ $ladder }[]: sezóna { $previous } skončila. Začala sezóna { $number }!
season-reset-soft = [lightgray]Hodnocení se přiblížila výchozí hodnotě a počítadla zápasů začínají od nuly.[]
season-reset-hard = [lightgray]Všichni začínají znovu s výchozím hodnocením.[]
season-reset-none = [lightgray]Hodnocení zůstávají, počítadla zápasů začínají od nuly.[]
season-title = Sezóna { $number }
top-menu-scope-current = { $season } [lightgray]· končí za { $remaining }[]
top-menu-scope-past = { $season } [gray]· { $from } – { $to }[]
ladder-profile-standing = { $leagueIcon } [white]{ $league }[] [accent]{ $rating } ELO[]
ladder-profile-headline = [accent]{ $icon }[] [lightgray]{ $ladder }:[] { $standing }
ladder-profile-unplaced = [gray]v této sezóně žádné hodnocené zápasy[]
ladder-profile-matches = [lightgray]Zápasy:[] [white]{ $matches }[]
ladder-profile-wins = [lightgray]Výhry:[] [white]{ $wins }[] [gray]({ $rate }%)[]
ladder-profile-rank = [lightgray]Místo:[] [accent]#{ $rank }[]
ladder-profile-peak = [lightgray]Maximum:[] [white]{ $rating }[]
ladder-profile-season = [lightgray]{ $season } · končí za [white]{ $remaining }[][]
ladder-profile-season-closing = [lightgray]{ $season } · výsledky se sčítají[]
ladder-profile-history = [gray]{ $season } — { $rank }, { $league }, { $rating } ELO[]
season-menu-title = Hodnocené sezóny
season-menu-card-title = { $ladder } — { $season }
season-menu-ends = [lightgray]Končí za [white]{ $remaining }[] ({ $date })[]
season-menu-closing = [lightgray]Sezóna skončila, výsledky se sčítají.[]
season-menu-participants = [lightgray]Hráči v této sezóně: [white]{ $count }[][]
season-menu-you = [lightgray]Ty:[] { $standing }
season-menu-previous = [lightgray]{ $season } — vítězové:[]
season-menu-podium-entry = [gold]{ $place }.[] [white]{ $name }[] [gray]—[] { $league } [accent]{ $rating }[]
season-menu-open-top = Žebříček
season-menu-prizes = [lightgray]Ceny v této sezóně:[]
season-menu-prize-entry = [gold]{ $places }.[] [white]{ $prize }[]
season-menu-podium-prizes = [gray] · [gold]{ $prizes }[]
prize-grant-line = [lightgray]{ $season } · cena:[] [gold]{ $prize }[] [gray]({ $status })[]
prize-status-pending = čeká na předání
prize-status-granted = obdržena
prize-status-delivered = doručena
prize-status-failed = vyřeší to admin
season-menu-empty = Zatím nejsou žádné hodnocené sezóny.
pvp-hud-status = [accent]MiniPvP[] | [stat]Naživu:[] { $teams } | [gray]{ $time }[]
pvp-leaderboard-content = [orange]{ $index }. { $nickname }[accent]:[cyan] { $rating } [accent]bodů hodnocení
pvp-you-spectator = [scarlet]Byl jsi vyřazen. Počkej na další hru.
# ==============================================================================
# Events & Notifications
# ==============================================================================
player-joined-pid = { $nickname } [grey]#[white]{ $pid }[grey] [accent]se připojil.
player-joined-username = { $nickname } [grey]@[white]{ $username }[grey] [accent]se připojil.
player-joined-both = { $nickname } [accent]@{ $username } [grey]#[white]{ $pid }[grey] [accent]se připojil.
player-joined-none = { $nickname } [accent]se připojil.

player-left-pid = { $nickname } [grey]#[white]{ $pid }[grey] [accent]odešel.
player-left-username = { $nickname } [grey]@[white]{ $username }[grey] [accent]odešel.
player-left-both = { $nickname } [accent]@{ $username } [grey]#[white]{ $pid }[grey] [accent]odešel.
player-left-none = { $nickname } [accent]odešel.

player-settings-identity-mode = [accent]Identifikátor v oznámeních:[]
player-settings-identity-mode-pid = Jen ID (#12)
player-settings-identity-mode-username = Jen uživatelské jméno (@Steve)
player-settings-identity-mode-both = Obojí (@Steve #12)
player-settings-identity-mode-none = Skryté

notification-votekick-playtime =
    [accent]Gratulujeme! Odehrál jsi [lightgray]{ $votekickPlayTime }[] { $votekickPlayTime ->
        [one] minutu
        [few] minuty
       *[other] minut
    } a teď můžeš zahajovat hlasování o vyhození.
notification-global-chat-playtime =
    [accent]Gratulujeme! Odehrál jsi [lightgray]{ $globalChatPlayTime }[] { $globalChatPlayTime ->
        [one] minutu
        [few] minuty
       *[other] minut
    } a teď můžeš psát do globálního chatu.
    [lightgray]Napiš [accent]/g [gray]<zpráva…>[lightgray] a pošli zprávu.
notification-admin-kick = { $admin }[accent] vyhodil { $target }[].
notification-admin-wave-skip = { $admin }[accent] přeskočil vlnu.
server-restart-countdown =
    Restart za { $seconds ->
        [one] { $seconds } sekundu
        [few] { $seconds } sekundy
       *[other] { $seconds } sekund
    }
like-map-success = [green]Tahle mapa se ti líbí!
like-map-changed = [green]Změnil jsi názor na „líbí se mi“!
dislike-map-success = [orange]Tahle mapa se ti nelíbí.
dislike-map-changed = [orange]Změnil jsi názor na „nelíbí se mi“.
like-event-success = [green]Tahle událost se ti líbí!
like-event-changed = [green]Změnil jsi názor na „líbí se mi“!
dislike-event-success = [orange]Tahle událost se ti nelíbí.
dislike-event-changed = [orange]Změnil jsi názor na „nelíbí se mi“.

# ==============================================================================
# Events (Server)
# ==============================================================================

commands-event-description = Menu správy událostí.
commands-events-description = Seznam všech událostí na serverech.
event-events = Události
event-menu-main = Hlavní události
event-menu-main-title = [orange]{ -xcore } — Události
event-menu-main-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]Centrum událostí[]
    [lightgray]Všechny aktivní a plánované události serveru na jednom místě.[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Aktuální událost[]
    [gray]Stav: [white]{ $currentEventState }[]
    [gray]Vybraná: [white]{ $currentEventName }[]
    { "" }
    [accent]■ Hlasování[]
    [gray]Hlasování: [white]{ $voteStatus }[]
    { "" }
    [accent]■ Akce[]
    [gray]Otevři katalog, prohlédni si aktuální událost nebo připrav novou.[]
event-menu-event = Událost
event-menu-event-title = [orange]{ -xcore } — Událost
event-menu-event-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $name }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Přehled[]
    [gray]Autor: [white]{ $author }[]
    [gray]Mapa: [white]{ $mapName }[]
    [gray]Typ: [white]{ $eventType }[] [darkgray]|[gray] Stav: [white]{ $eventState }[]
    [gray]Dočasná: [white]{ $isTemporary }[]
    { "" }
    [accent]■ Plán[]
    [gray]Vytvořena: [white]{ $createdEventTime }[]
    [gray]Plánovaný začátek: [white]{ $plannedStartTime }[]
    [gray]Plánovaný konec: [white]{ $plannedEndTime }[]
    { "" }
    [accent]■ Reputace[]
    [gray]Líbí se: [white]{ $like }[] [darkgray]|[gray] Nelíbí se: [white]{ $dislike }[]
event-menu-event-map = Zobrazit mapu
event-menu-events = Seznam událostí
event-menu-events-title = [orange]{ -xcore } — Seznam událostí
event-menu-events-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]Katalog událostí[]
    [lightgray]Strana [green]{ $page }[]/[green]{ $total }[] [gold]•[] [lightgray]Události: [green]{ $count }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Filtry[]
    [gray]Skončené: [white]{ $finished }[]
    [gray]Velké: [white]{ $major }[] [darkgray]|[gray] Aktivní: [white]{ $active }[]
    { "" }
    [accent]■ Seznam[]
    [gray]Vyber níže událost a zobrazí se její karta.[]
event-menu-events-empty =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]Katalog událostí[]
    [lightgray]Aktuálním filtrům zatím neodpovídají žádné události.[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Filtry[]
    [gray]Skončené: [white]{ $finished }[]
    [gray]Velké: [white]{ $major }[] [darkgray]|[gray] Aktivní: [white]{ $active }[]
event-menu-events-row = [accent]{ $state }[] [darkgray]•[] [white]{ $type }[] [darkgray]—[] { $name }
event-menu-events-selected = [green]●[] [accent]{ $state }[] [darkgray]•[] [white]{ $type }[] [darkgray]—[] { $name }
event-menu-create-start = Vytvořit
event-menu-create-start-title = [orange]{ -xcore } — Vytvoření události
event-menu-create-start-message = Zadej název nové události
event-menu-create-start-default = Událost hráče { $playerName }
event-menu-create-start-map = Vytvořit událost pro tuto mapu
event-menu-edit = Upravit
event-menu-edit-title = [orange]{ -xcore } — Úprava události
event-menu-edit-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $name }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Údaje[]
    [gray]Autor: [white]{ $author }[]
    [gray]Typ: [white]{ $eventType }[]
    { "" }
    [accent]■ Mapa[]
    [gray]Vybraná mapa: [white]{ $mapName }[]
    { "" }
    [accent]■ Plán[]
    [gray]Plánovaný začátek: [white]{ $plannedStartTime }[]
    [gray]Plánovaný konec: [white]{ $plannedEndTime }[]
    { "" }
    [accent]■ Možnosti[]
    [gray]Dočasná: [white]{ $isTemporary }[]
event-menu-edit-name = Název
event-menu-edit-name-reset = [scarlet]Obnovit název
event-menu-edit-name-title = [orange]{ -xcore } — Úprava události
event-menu-edit-name-message = Upravit název:
event-menu-edit-description = Popis
event-menu-edit-description-title = [orange]{ -xcore } — Úprava události
event-menu-edit-description-message = Upravit popis:
event-menu-edit-map = Změnit mapu
event-menu-edit-temporary-active = [green]Dočasná
event-menu-edit-temporary-inactive = [gray]Dočasná
event-menu-edit-major-active = [green]Velká
event-menu-edit-major-inactive = [gray]Velká
event-menu-edit-planned-start = Začátek události
event-menu-edit-planned-start-title = [orange]{ -xcore } — Úprava události
event-menu-edit-planned-start-message = Zadej čas začátku v ms nebo pomocí m/h/d:
event-menu-edit-planned-end = Konec události
event-menu-edit-planned-end-title = [orange]{ -xcore } — Úprava události
event-menu-edit-planned-end-message = Zadej čas konce v ms nebo pomocí m/h/d:
event-menu-maps = Mapy
event-menu-maps-title = [orange]{ -xcore } — Výběr mapy
event-menu-maps-content = [white]Strana [green]{ $page }[] z [green]{ $total }[]
vote-event-vote =
    { $nickname }[lightgray] hlasoval pro změnu události na [orange]{ $name }[lightgray]. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Napiš [orange]y[] nebo [orange]n[] a hlasuj.
vote-event-left = { $nickname }[lightgray] odešel. Jeho hlas pro změnu události byl zrušen. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
vote-event-fail = [lightgray]Hlasování neuspělo. Málo hlasů pro změnu události na [orange]{ $name }[].
vote-event-success = [orange]Hlasování prošlo. Událost [accent]{ $name }[] se načte při příští změně mapy.
vote-event-cancelled = [lightgray]Hlasování o změně události na [orange]{ $name }[lightgray] zrušil administrátor { $admin }.
event-vote = [orange]Hlasování
event-avote = [red]Okamžitá změna
event-menu-vote-stop = Zastavit hlasování
event-menu-stop = Zastavit událost
event-menu-this-event = [orange]Aktuální událost
event-menu-type-major = Velká událost
event-menu-type-regular = Běžná událost
event-menu-state-none = Žádná aktivní událost
event-menu-state-planned = Plánovaná
event-menu-state-active = Právě probíhá
event-menu-state-finished = Skončená
event-menu-vote-status-running = Probíhá
event-menu-vote-status-idle = Neprobíhá
date-time-picker-title = [orange]{ -xcore } — Datum a čas
date-time-picker-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $field }[]
    [lightgray]Aktuální hodnota: [white]{ $value }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Datum[]
    [gray]Nejdřív vyber den, pak níže uprav čas.[]
    { "" }
    [accent]■ Čas[]
    [gray]Použij předvolby nebo jemné úpravy pro přesné plánování.[]
    { "" }
    [accent]■ Ruční zadání[]
    [gray]Jen když potřebuješ přesné milisekundy nebo relativní hodnotu +m/+h/+d.[]
date-time-picker-field-generic = Plánovaný čas
date-time-picker-today = Dnes
date-time-picker-tomorrow = Zítra
date-time-picker-plus-2d = +2 dny
date-time-picker-plus-7d = +7 dní
date-time-picker-now = Teď
date-time-picker-time-0000 = 00:00
date-time-picker-time-0600 = 06:00
date-time-picker-time-1200 = 12:00
date-time-picker-time-1800 = 18:00
date-time-picker-minus-1d = -1 d
date-time-picker-plus-1d = +1 d
date-time-picker-minus-1h = -1 h
date-time-picker-plus-1h = +1 h
date-time-picker-minus-15m = -15 min
date-time-picker-plus-15m = +15 min
date-time-picker-reset = Obnovit
date-time-picker-manual = Ruční zadání
date-time-picker-manual-title = [orange]{ -xcore } — Ruční zadání času
date-time-picker-manual-message = Zadej absolutní milisekundy nebo relativní čas, např. +30m, +2h, +1d.
event-end = Událost [green]{ $name }[] skončila!
# ==============================================================================
# Errors
# ==============================================================================
error-access-denied = [scarlet]⚠ Přístup odepřen.
error-ip-changed = [scarlet]⚠ Tvoje IP adresa se změnila. Admin práva byla odebrána.
error-not-enough-params = [scarlet]⚠ Nedostatek pozičních parametrů.
error-player-not-found = [scarlet]Hráč nebyl nalezen.
error-player-not-teammate = [scarlet]⚠ Tento hráč není ve tvém týmu.
error-player-admin = [scarlet]⚠ Nezkoušej vyhodit admina. ⚠
error-already-voted = [scarlet]⚠ Už jsi hlasoval. Klid.
error-playtime-requirement =
    [scarlet]⚠ Pro použití této funkce musíš odehrát aspoň { $time } { $time ->
        [one] minutu
        [few] minuty
       *[other] minut
    }.
error-globalchat-total-playtime =
    [scarlet]⚠ Abys mohl psát do globálního chatu, musíš odehrát { $globalChatPlayTime } { $globalChatPlayTime ->
        [one] minutu
        [few] minuty
       *[other] minut
    }.
error-votekick-total-playtime =
    [scarlet]⚠ Abys mohl zahájit hlasování o vyhození, musíš odehrát { $votekickPlayTime } { $votekickPlayTime ->
        [one] minutu
        [few] minuty
       *[other] minut
    }.
error-vote-yourself = [scarlet]⚠ Nemůžeš hlasovat ve vlastním hlasování.
error-vote-in-progress = [scarlet]⚠ Hlasování už probíhá.
error-no-voting = [scarlet]⚠ Momentálně neprobíhá žádné hlasování.
error-wave-vote-unavailable = [scarlet]⚠ Dřívější spuštění vlny je dostupné jen v režimech s vlnami.
error-no-map = [scarlet]⚠ Mapa není nastavena.
error-map-not-event = [scarlet]⚠ Mapa není součástí aktuální události.
error-map-not-found = [scarlet]⚠ Mapa nenalezena! [accent]Příkazem [cyan]/maps[] zobrazíš všechny dostupné mapy.
error-maps-empty = [scarlet]⚠ Seznam map je prázdný.
error-event-not-found = [scarlet]⚠ Událost nenalezena! [accent]Příkazem [cyan]/events[] zobrazíš dostupné události.
error-page-between = [scarlet]⚠ 'page' musí být číslo mezi[orange] 1[] a [orange]{ $totalPages }[].
error-page-number = [scarlet]'page' musí být číslo.
error-wrong-number = [scarlet]⚠ Špatný formát čísla.
error-wrong-period-format = [scarlet]⚠ Špatný formát doby. Příklad: 1h 30m, 30 ({ hours })
error-invalid-id = [scarlet]⚠ Neplatné ID hráče.
error-spectator = [scarlet]⚠ Jako divák nemůžeš použít tento příkaz.
error-admin-password-too-short = [scarlet]⚠ Heslo administrátora musí mít alespoň 8 znaků.
error-wrong-admin-password = [scarlet]⚠ Špatné admin heslo.
error-internal = [scarlet]Interní chyba.
error-processing-request = [scarlet]Při zpracování požadavku došlo k chybě.
error-no-access = [scarlet]⚠ Bez přístupu.
error-nickname-too-long = [scarlet]⚠ Jméno je příliš dlouhé. Nejvýše { $max } viditelných znaků.
error-private-message-invalid-pid = [scarlet]⚠ Neplatné PID soukromé zprávy. Formát: [lightgray]#123[].
error-private-message-self = [scarlet]⚠ Nemůžeš poslat soukromou zprávu sám sobě.
error-private-message-empty = [scarlet]⚠ Zpráva nemůže být prázdná.
error-private-message-too-long = [scarlet]⚠ Zpráva je příliš dlouhá. Nejvýše { $max } znaků.
error-private-message-cooldown = [scarlet]⚠ Počkej { DURATION($seconds) }, než pošleš další soukromou zprávu.
error-private-message-target-unavailable = [scarlet]⚠ Tento hráč teď nemůže přijímat soukromé zprávy.
error-private-message-no-reply-target = [scarlet]⚠ Nemáš nedávný kontakt, kterému bys mohl odpovědět.
error-private-message-not-found = [scarlet]⚠ Zpráva nenalezena.
error-private-message-block-self = [scarlet]⚠ Nemůžeš zablokovat sám sebe.
error-private-message-block-limit = [scarlet]⚠ Dosažen limit seznamu blokovaných ({ $limit }).
ban-menu-duration-title = [orange]{ -xcore } - Délka banu
ban-menu-duration-message = Zadej délku banu pro { $nickname }. Příklad: 1d, 12h, 30m
ban-menu-reason-title = [orange]{ -xcore } - Důvod banu
ban-menu-reason-message = Zadej důvod banu pro { $nickname }. Nech prázdné pro výchozí důvod.
ban-menu-confirm-title = [orange]{ -xcore } - Potvrdit ban
ban-menu-confirm-content =
    [white]Hráč: { $nickname }[]
    [white]Délka: [accent]{ $duration }[]
    [white]Důvod: [accent]{ $reason }[]
ban-menu-confirm-action = [scarlet]Zabanovat hráče
error-invalid-syntax = [scarlet]⚠ Neplatná syntaxe příkazu. Použití: [lightgray]/'{ $syntax }'.
error-invalid-sender = [scarlet]⚠ Neplatný odesílatel příkazu. Tento příkaz vyžaduje: '[lightgray]{ $type }[]'.
error-argument-parse-generic = [scarlet]⚠ Neplatný argument: '{ $error }'.
exception-unexpected = [scarlet]⚠ Při provádění tohoto příkazu došlo k interní chybě.
exception-invalid-argument = [scarlet]⚠ Neplatný argument příkazu: '{ $cause }'.
exception-no-such-command = [scarlet]⚠ Neznámý příkaz.
exception-no-permission = [scarlet]⚠ Přístup odepřen.
exception-invalid-sender = [scarlet]⚠ '{ $actual }' nemůže spustit tento příkaz. Požadovaný odesílatel: [lightgray]{ $expected }[].
exception-invalid-sender-list = [scarlet]⚠ '{ $actual }' nemůže spustit tento příkaz. Povolení odesílatelé: [lightgray]{ $expected }[].
exception-invalid-syntax = [scarlet]⚠ Neplatná syntaxe příkazu. Použití: [lightgray]/'{ $syntax }'.
argument-parse-failure-boolean = [scarlet]⚠ Z '{ $input }' nelze přečíst logickou hodnotu.
argument-parse-failure-number = [scarlet]⚠ '{ $input }' není platné číslo v rozsahu [{ $min }, { $max }].
argument-parse-failure-char = [scarlet]⚠ '{ $input }' není platný znak.
argument-parse-failure-enum = [scarlet]⚠ '{ $input }' není platná možnost. Povoleno: [lightgray]{ $acceptableValues }
argument-parse-failure-string = [scarlet]⚠ Neplatný formát textu pro '{ $input }'.
argument-parse-failure-uuid = [scarlet]⚠ Neplatný formát UUID: '{ $input }'.
argument-parse-failure-regex = [scarlet]⚠ Vstup '{ $input }' neodpovídá vzoru '{ $pattern }'.
argument-parse-failure-color = [scarlet]⚠ '{ $input }' není platná barva.
argument-parse-failure-duration = [scarlet]⚠ '{ $input }' není platný formát doby.
argument-parse-failure-aggregate-missing = [scarlet]⚠ Chybí součást '{ $component }'.
argument-parse-failure-aggregate-failure = [scarlet]⚠ Neplatná součást '{ $component }': '{ $failure }'.
argument-parse-failure-either = [scarlet]⚠ Z '{ $input }' nelze získat { $primary } ani { $fallback }.
argument-parse-failure-flag-unknown = [scarlet]⚠ Neznámý přepínač: '{ $flag }'.
argument-parse-failure-flag-duplicate = [scarlet]⚠ Duplicitní přepínač: '{ $flag }'.
argument-parse-failure-flag-duplicate-flag = [scarlet]⚠ Duplicitní přepínač: '{ $flag }'.
argument-parse-failure-flag-no-flag-started = [scarlet]⚠ Žádný přepínač nezačal. Nevím, co dělat s '{ $input }'.
argument-parse-failure-flag-missing-argument = [scarlet]⚠ Chybí argument přepínače: '{ $flag }'.
argument-parse-failure-flag-no-permission = [scarlet]⚠ Nemáš oprávnění použít přepínač '{ $flag }'.
argument-parse-failure-selector-syntax = [scarlet]⚠ Neplatný selektor '{ $input }': [lightgray]{ $reason }
argument-parse-failure-selector-no-such-target = [scarlet]⚠ Nic neodpovídá '{ $input }'.
argument-parse-failure-selector-too-many-targets = [scarlet]⚠ '{ $input }' odpovídá více cílům, ale tento příkaz potřebuje jeden.
argument-parse-failure-selector-denied = [scarlet]⚠ Selektory tu nejsou povoleny: [lightgray]{ $reason }
argument-parse-failure-selector-kind-not-allowed = [scarlet]⚠ Selektor '{ $kind }' tu není povolen.
argument-parse-failure-selector-sender-required = [scarlet]⚠ Selektor '{ $kind }' lze použít jen ve hře.
argument-parse-failure-selector-limit-exceeded = [scarlet]⚠ Selektor odpovídá { $count } cílům, limit je { $limit }.
argument-parse-failure-team = [scarlet]⚠ Tým '{ $input }' nebyl nalezen.
argument-parse-failure-content = [scarlet]⚠ '{ $input }' není platná hodnota typu { $type }.
# ==============================================================================
# Button Status
# ==============================================================================
finished = skončené
finished-neutral = [orange]Skončené
finished-active = [green]Skončené
finished-inactive = [red]Skončené
major = Velké
major-neutral = [orange]Velké
major-active = [green]Velké
major-inactive = [red]Velké
active = Aktivní
active-neutral = [orange]Aktivní
active-active = [green]Aktivní
active-inactive = [red]Aktivní
admin = Admin
admin-neutral = [orange]Admin
admin-active = [green]Admin
admin-inactive = [red]Admin
player-leaderboard-active = [green]Žebříček: zapnut[]
player-leaderboard-inactive = [red]Žebříček: vypnut[]
# ==============================================================================
# Miscellaneous
# ==============================================================================
hours = hodiny
days = dny
success = [green]Úspěšně
empty = [accent]Prázdné
never = Nikdy
save = Uložit
close = [scarlet]Zavřít
previous = [accent]« Předchozí
next = [accent]Další »
cancel = Zrušit
back = Zpět
yes = Ano
no = Ne
test = Test
no-description = Bez popisu
discord = Discord
github = Github
donatello = Donatello
weblate = Weblate
discord-red-vs-blue = RedVSBlue
auto = Auto
on = Zapnuto
off = Vypnuto
error-command-disabled = [scarlet]⚠ Příkaz [accent]/{ $command }[scarlet] je na tomto serveru vypnutý.
error-feature-disabled = [scarlet]⚠ Tato funkce je na tomto serveru vypnutá.
none = Žádné
unknown = Neznámé
error-nickname-badge-glyph = [scarlet]⚠ Vlastní jméno nesmí obsahovat vyhrazené ikony odznaků.

# Server browser (/servers)
player-servers-title = SÍŤ SERVERŮ XCORE
player-servers-cat-all = Vše
player-servers-cat-pvp = PvP
player-servers-cat-survival = Přežití
player-servers-cat-special = Speciální
player-servers-hint = Klikni na kartu serveru a připoj se
player-servers-refresh = Obnovit
player-servers-already-connected = [gold]● K tomuto serveru už jsi připojen!
player-servers-transferring = [accent]Přesun na server [white]{ $server }[]...
player-servers-full = [scarlet]Server { $server } je plný! Počkej na volné místo.
player-servers-offline = [scarlet]Server { $server } je momentálně offline.
player-servers-not-found = [scarlet]Server '{ $server }' nebyl nalezen.
player-servers-empty-category = [lightgray]V této kategorii nejsou dostupné žádné servery.[]
player-servers-online-summary = [green]● { $players } [gray]hraje[] [darkgray]|[] [sky]{ $servers } [gray]online[]
player-servers-badge-current = [gold]● JSI TADY[]
player-servers-offline-badge = [darkgray]● OFFLINE[]
player-servers-capacity-full = [scarlet]● { $players }/{ $max } PLNÝ[]
player-servers-capacity-normal = [green]● { $players }/{ $max } { $bar }
player-servers-card-current = [lightgray]K tomuto serveru jsi připojen[]
player-servers-card-wave = [darkgray]|[] [accent]Vlna { $wave }[]
player-servers-card-empty = [sky]Buď první! Začni hru[]
player-servers-card-map = [gray]Mapa:[] [white]{ $map }[]
player-servers-card-mode = [gray]Režim:[] [white]{ $mode }[]

announcement-hub =
    [gold]★ [accent]Servery XCore [lightgray]» [white]Nudí tě tento zápas?
    [lightgray]Kdykoli můžeš navštívit jiné servery příkazem [accent]/hub[lightgray]!
announcement-discord =
    [gold]★ [accent]Komunita XCore [lightgray]» [white]Hledáš spoluhráče a novinky?
    [lightgray]Připoj se na náš Discord server příkazem [accent]/discord[lightgray]!
announcement-help =
    [gold]★ [accent]Nápověda XCore [lightgray]» [white]Potřebuješ pomoc nebo seznam příkazů?
    [lightgray]Napiš [accent]/help[lightgray] a uvidíš všechny dostupné příkazy!


error-only-players = [scarlet]⚠ Tento příkaz mohou používat pouze hráči.

player-settings-username-editable = [gold]★ Uživatelské jméno (odměna z události):[]
player-settings-username-hint = Zadej jedinečné uživatelské jméno (4–32 znaků)...
player-settings-username-locked = [gray]Uživatelské jméno:[] [accent]@{ $username }[] [darkgray](Zamčeno)[]
player-settings-username-none = [gray]Uživatelské jméno: [darkgray]Nenastaveno (vyhraj událost a odemkni ho)[]

error-username-empty = Uživatelské jméno nesmí být prázdné!
error-username-length = Uživatelské jméno musí mít 4 až 32 znaků!
error-username-invalid-chars = Uživatelské jméno smí obsahovat jen latinská písmena, číslice a podtržítka!
error-username-taken = Toto uživatelské jméno už používá jiný hráč!

# ==============================================================================
# Permission nodes
# ==============================================================================
permission-mindustry-admin = Používat vestavěné admin menu hry a přeskakovat vlny
permission-xcore-moderation-mute = Umlčovat hráče
permission-xcore-moderation-unmute = Rušit umlčení hráčů
permission-xcore-moderation-kick = Vyhazovat hráče
permission-xcore-moderation-ban = Banovat hráče
permission-xcore-moderation-unban = Rušit bany hráčů
permission-xcore-moderation-audit-others = Prohlížet historii moderace ostatních hráčů
permission-xcore-moderation-votekick-immune = Nelze vyhodit hlasováním
permission-xcore-admin-tp = Teleportovat hráče
permission-xcore-admin-broadcast = Posílat oznámení všem
permission-xcore-admin-kill = Zabíjet jednotky a hráče
permission-xcore-admin-heal = Léčit jednotky a hráče
permission-xcore-admin-set-team = Měnit tým hráče
permission-xcore-maps-force-rtv = Měnit mapu bez hlasování
permission-xcore-maps-force-vnw = Přeskočit vlnu bez hlasování
permission-xcore-votes-cancel = Zrušit probíhající hlasování
permission-xcore-events-create-major = Vytvářet velké události
permission-xcore-events-edit-others = Upravovat události ostatních hráčů
permission-xcore-events-force-vote = Spustit událost bez hlasování
permission-xcore-events-stop = Zastavit probíhající událost
permission-xcore-players-settings-others = Měnit nastavení ostatních hráčů
permission-xcore-players-private-info = Vidět soukromé údaje hráčů, například jejich IP adresy
permission-xcore-bypass-playtime = Obejít požadavky příkazů na herní čas
permission-xcore-permissions-inspect = Vidět, kdo má jaká oprávnění
permission-xcore-permissions-manage = Měnit role a oprávnění

error-target-outranks = [scarlet]⚠ Tohle nemůžeš udělat hráči, jehož role není nižší než tvoje.
perm-me-legacy = [accent]Role jsou na tomto serveru vypnuté. Admin: [white]{ $admin }
perm-me-header = [accent]Tvoje role tady (váha [white]{ $weight }[accent]):
perm-me-none = [lightgray] - žádné
perm-me-logged-in = [green]Jsi přihlášen jako člen týmu.
perm-me-not-logged-in = [yellow]Nejsi přihlášen jako člen týmu. Použij [white]/login <heslo>[yellow].
perm-me-stale = [scarlet]Tvoje týmová práva jsou pozastavena: server je nemohl obnovit. Vrátí se sama.

help-ui-search-hint = Hledat příkazy, aliasy nebo popisy
help-ui-search-empty = [lightgray]Žádné příkazy neodpovídají hledání.[]
