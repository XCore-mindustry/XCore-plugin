# ==============================================================================
# Terms
# ==============================================================================
-xcore = Serwer XCore
# ==============================================================================
# General & Help
# ==============================================================================
menu-main = Menu główne
commands-main-description = Otwiera interaktywne menu główne.
menu-main-title = [orange]{ -xcore } — Menu główne
menu-main-content = Menu główne serwera
help-menu = Menu pomocy
commands-help-description = Otwiera interaktywne menu pomocy.
help-menu-title = [orange]{ -xcore } — Komendy
help-menu-content =
    [gray]Strona [white]{ $page }[gray]/[white]{ $total }
    [lightgray]Wybierz komendę, aby zobaczyć szczegóły użycia:
help-menu-button = [accent]/{ $command } [gray]» Opis: [white]{ $description }
help-command-with-overload-count = { $name } z ({ $count })
help-command-title = [orange]» Nazwa: [white]/{ $name }
help-command-header =
    [orange]» [accent]Składnia: [white]{ $syntax }
    [orange]» [accent]Info: [lightgray]{ $description }
help-aliases = [orange]» [accent]Aliasy: [white]{ $aliases }
help-args-title = [orange]» [accent]Argumenty:
help-usages-title = [orange]» [accent]Użycie:
help-usage-entry = [gray]• [white]{ $syntax }
help-usage-args-title = [orange]» [accent]Dla [white]{ $syntax }[accent]:
help-arg-entry = [gray]• [white]{ $arg } [lightgray]- { $description }
help-no-arguments = [gray]Nie są potrzebne dodatkowe argumenty.
help-no-arg-description = Brak opisu.
help-no-description = Ta komenda nie ma opisu.
help-legacy-command-content =
    [orange]» [accent]Komenda: [white]/{ $name }
    [orange]» [accent]Parametry: [white]{ $params }
    [orange]» [accent]Info: [lightgray]{ $description }
    { "" }
    [gray](To starsza komenda z ograniczonymi informacjami)
help-legacy-command-content-no-params =
    [orange]» [accent]Komenda: [white]/{ $name }
    [orange]» [accent]Info: [lightgray]{ $description }
    { "" }
    [gray](To starsza komenda z ograniczonymi informacjami)
help-back = [lightgray]« Wstecz

# ==============================================================================
# Modern Reactive Help & Commands Guide (xcore-ui)
# ==============================================================================
help-ui-title = PRZEWODNIK PO KOMENDACH
help-ui-summary = [gray]Dostępne komendy: { $count }
help-ui-empty-category = [lightgray]W tej kategorii nie ma komend.[]
help-ui-overloads = (wariantów: { $count })
help-ui-aliases = [lightgray]Aliasy:[] { $aliases }
help-ui-syntax-title = Składnia
help-ui-args-title = Parametry
help-ui-arg-required = [scarlet]Wymagany
help-ui-arg-optional = [sky]Opcjonalny
help-ui-btn-run = Uruchom
help-ui-btn-copy = Do czatu
help-ui-btn-back = Wstecz
help-ui-copied = [accent]Komenda: [white]/{ $syntax }
help-ui-executed = [accent]Wykonywanie komendy: [white]/{ $syntax }

# Categories
help-cat-all = Wszystkie
help-cat-general = Ogólne
help-cat-game = Gra
help-cat-social = Czat
help-cat-votes = Głosowania
help-cat-admin = Admin
# ==============================================================================
# Command Argument Descriptions
# ==============================================================================
# help
commands-help-page-description = Numer strony do wyświetlenia.
# login
commands-login-password-description = Twoje hasło administratora.
# ban
commands-ban-id-description = ID gracza do zbanowania.
commands-ban-period-description = Czas bana (np. 1d, 2h, 30m).
commands-ban-reason-description = Powód bana.
# unban
commands-unban-id-description = ID gracza do odbanowania.
# mute
commands-mute-id-description = ID gracza do wyciszenia.
commands-mute-period-description = Czas wyciszenia (np. 1h, 30m).
commands-mute-reason-description = Powód wyciszenia.
# unmute
commands-unmute-id-description = ID gracza, któremu zostanie zdjęte wyciszenie.
# votekick
commands-votekick-target-description = Gracz do wyrzucenia (ID lub nick).
commands-votekick-reason-description = Powód wyrzucenia.
# vote
commands-vote-choice-description = Twój głos: y (tak), n (nie) lub c (anuluj, tylko admini).
# t (team chat)
commands-t-message-description = Wiadomość do członków drużyny.
# g (global chat)
commands-g-message-description = Wiadomość do wszystkich serwerów.
# tr (translator)
commands-tr-language-description = Kod języka, 'auto' lub 'off'.
# stats
commands-stats-id-description = ID gracza, którego statystyki chcesz zobaczyć
# rank
commands-rank-player-description = Gracz, którego rangę chcesz zobaczyć
# map
commands-map-map-description = Nazwa lub numer mapy.
# maps / maps-text
commands-maps-page-description = Numer strony.
commands-maps-text-page-description = Numer strony.
# rtv / artv
commands-rtv-map-description = Mapa, na którą głosujesz (opcjonalnie).
commands-artv-map-description = Mapa, na którą nastąpi natychmiastowa zmiana.
# ai
commands-ai-state-description = Stan SI: attack (a) lub idle (i).
# event / events
commands-events-page-description = Numer strony.
# ==============================================================================
# General & Help (continued)
# ==============================================================================
commands-information-description = Pokazuje informacje o serwerze.
commands-info = Informacje
commands-info-title = [orange]{ -xcore } — Nazwa serwera: [orange]{ $server-name }
commands-info-text =
    [accent]XCore[white] to [cyan]darmowy[white] serwer do gry w [accent]Mindustry[white].
    { "" }
    Wersja XCore — [accent]{ $version }[white]
commands-sync-description = Synchronizuje twoją grę z serwerem. Pomaga przy błędach, np. jednostkach-duchach.
commands-discord-description = Przekierowuje na serwer Discord.
discord-menu-title = [orange]{ -xcore } — Discord
discord-menu-content =
    [white]Tutaj zarządzasz połączeniem z Discordem.
    { "" }
    [white]Status: { $status }
    [white]Serwer: [accent]{ $discordUrl }[]
discord-menu-open = Otwórz Discord
discord-menu-link = Połącz konto
discord-menu-status = Odśwież status
discord-menu-unlink = Odłącz konto
discord-menu-status-not-linked = [lightgray]niepołączone[]
discord-menu-status-linked = [green]{ $discordUsername }[] [gray]({ $discordId })[]
discord-link-menu-title = [orange]{ -xcore } — Połącz konto Discord
discord-link-menu-content =
    [white]Na naszym serwerze Discord użyj komendy bota:
    { "" }
    [accent]/link { $code }[]
    { "" }
    [white]Wygasa za: [accent]{ $expireMinutes }[] min
    [white]Discord: [accent]{ $discordUrl }[]
discord-link-menu-refresh = Odśwież kod
discord-link-menu-copy = Kopiuj kod
discord-link-menu-regenerate = Wygeneruj nowy kod
discord-link-menu-status = Wróć do menu Discord
welcome =
    [accent]Witaj na { $serverName }!
    [lightgray]Wpisz [accent]/help[lightgray], aby zobaczyć listę komend
    [lightgray]Wpisz [accent]/vote [gray]<y/n>[lightgray], aby zagłosować za wyrzuceniem gracza
    [lightgray]Wpisz [accent]/votekick [gray]<ID/nick> <powód…>[lightgray], aby rozpocząć głosowanie nad wyrzuceniem
    [lightgray]Wpisz [accent]/t [gray]<wiadomość…>[lightgray], aby napisać do drużyny
    [lightgray]Wpisz [accent]/g [gray]<wiadomość…>[lightgray], aby napisać do wszystkich serwerów
    [lightgray]Wpisz [accent]/tr [gray]<język/auto>[lightgray], aby włączyć tłumacza
    [lightgray]Wpisz [accent]/discord[lightgray], aby otworzyć menu Discord i połączyć konto
# ==============================================================================
# Chat & Social
# ==============================================================================
commands-t-description = Wysyła wiadomość tylko do twojej drużyny.
commands-t-chat = [{ "#" }{ $color }][Drużyna] [coral]> { $badge }[accent]{ $name }[lightgray]: [white]{ $message }
commands-g-description = Wysyła wiadomość na wszystkie serwery.
commands-a-description = Wysyła wiadomość tylko do adminów.
commands-msg-description = Wysyła prywatną wiadomość do gracza.
commands-msg-id-description = ID gracza.
commands-msg-message-description = Treść prywatnej wiadomości.
commands-reply-description = Odpowiada ostatniemu graczowi w prywatnych wiadomościach.
commands-reply-message-description = Treść odpowiedzi.
commands-inbox-description = Otwiera menu prywatnych wiadomości.
commands-inbox-id-description = ID gracza.
commands-tr-description = Ustawia język tłumacza.
commands-badge-description = Otwiera menu odznak i pozwala zarządzać aktywną odznaką.
commands-tr-success = [accent]Język tłumacza zmieniono na [grey]{ $translatorLanguage }[]!
commands-tr-off = [accent]Tłumacz jest [scarlet]wyłączony[]!
commands-tr-not-found = [scarlet]⚠ Nie ma takiego języka.
discord-chat-format = [#5865F2][DISCORD][] [lightgray]| [accent]{ $author }[lightgray] >> [white]{ $message }
global-chat-format = [royal][[[orange]GLOBALNY [lightgray](z [accent]{ $server }[])[] { $author }[]]: [white]{ $message }
private-message-received = [sky][PW][] [lightgray]od [accent]{ $author } [gray]#{ $pid }[lightgray]: [white]{ $message }
private-message-sent = [sky][PW][] [lightgray]do [accent]{ $target } [gray]#{ $pid }[lightgray]: [white]{ $message }
private-message-unread-count =
    [accent]Masz [white]{ $count }[accent] { $count ->
        [one] nieprzeczytaną prywatną wiadomość
        [few] nieprzeczytane prywatne wiadomości
        [many] nieprzeczytanych prywatnych wiadomości
       *[other] nieprzeczytanej prywatnej wiadomości
    }.
private-message-join-notification =
    [accent]Masz [white]{ $count }[accent] { $count ->
        [one] nieprzeczytaną prywatną wiadomość
        [few] nieprzeczytane prywatne wiadomości
        [many] nieprzeczytanych prywatnych wiadomości
       *[other] nieprzeczytanej prywatnej wiadomości
    }. Użyj [white]/inbox[accent], aby { $count ->
        [one] ją przeczytać
       *[other] je przeczytać
    }.
private-message-block-success = [accent]Prywatne wiadomości od [white]{ $target } [gray]#{ $pid }[accent] są teraz zablokowane.
private-message-block-already = [lightgray]Prywatne wiadomości od [white]{ $target } [gray]#{ $pid }[lightgray] są już zablokowane.
private-message-unblock-success = [accent]Prywatne wiadomości od [white]{ $target } [gray]#{ $pid }[accent] nie są już zablokowane.
private-message-unblock-missing = [lightgray][white]{ $target } [gray]#{ $pid }[lightgray] nie jest zablokowany.
private-message-menu-title = [orange]{ -xcore } — Prywatne wiadomości
private-message-menu-content =
    [white]Strona [green]{ $page }[] z [green]{ $total }[]
    [white]Nieprzeczytane: [accent]{ $unread }[]
private-message-menu-empty = [lightgray]Twoja skrzynka jest pusta.
private-message-menu-entry-unread = [accent]Nieprzeczytana[] [white]{ $author } [gray]#{ $pid }[] [lightgray]({ $time })[]: [white]{ $message }
private-message-menu-entry-read = [gray]Przeczytana[] [white]{ $author } [gray]#{ $pid }[] [lightgray]({ $time })[]: [white]{ $message }
private-message-details-title = [orange]{ -xcore } — Wiadomość
private-message-details-content =
    [white]Od: [accent]{ $author } [gray]#{ $pid }[]
    [white]Czas: [accent]{ $time }[]
    [white]Status: [accent]{ $status }[]
    { "" }
    [white]{ $message }
private-message-status-unread = nieprzeczytana
private-message-status-read = przeczytana
private-message-blocked-title = [orange]{ -xcore } — Zablokowani gracze
private-message-blocked-content =
    [white]Strona [green]{ $page }[] z [green]{ $total }[]
    [white]Zablokowani: [accent]{ $count }[]
private-message-blocked-empty = [lightgray]Nie masz zablokowanych graczy.
private-message-blocked-entry = [white]{ $target } [gray]#{ $pid }[]
private-message-compose = Nowa wiadomość
private-message-blocked = Zablokowani
private-message-block = Zablokuj nadawcę
private-message-unblock = Odblokuj nadawcę
private-message-reply-title = Odpowiedz
private-message-reply-message = Wpisz wiadomość dla [accent]#{ $pid }[]
private-message-compose-target-title = Nowa wiadomość
private-message-compose-target-message = Wpisz ID gracza w formacie [accent]#123[]
private-message-compose-body-title = Treść wiadomości
private-message-compose-body-message = Wpisz prywatną wiadomość dla [accent]{ $pid }[]
# ==============================================================================
# Authentication & Admin Access
# ==============================================================================
commands-login-description = Włącza uprawnienia admina, jeśli twoje połączone konto Discord ma już dostęp.
commands-login-incorrect-password = [scarlet]⚠ Nieprawidłowe hasło!
commands-login-success = [green]Przyznano uprawnienia admina.
commands-login-confirmed = [green]Potwierdzono dostęp admina na Discordzie.
commands-login-admin-password-created =
    [green]Utworzono hasło admina.
    [red]Nie zapomnij hasła! Jeśli je zapomnisz, musisz poprosić głównego administratora o jego zresetowanie.
commands-login-request-approval-discord = [accent]Twoje konto nie ma uprawnień administratora Discorda. Uzyskaj rolę administratora na Discordzie i spróbuj ponownie.
commands-login-verifying = [lightgray]Weryfikacja hasła administratora...
commands-login-already-processing = [scarlet]⚠ Żądanie logowania jest już przetwarzane. Proszę czekać.
commands-login-rate-limited = [scarlet]⚠ Zbyt wiele nieudanych prób logowania. Proszę poczekać przed kolejną próbą.
commands-discord-link-created =
    [green]Utworzono kod połączenia z Discordem: [accent]{ $code }[]
    [lightgray]Na naszym serwerze Discord użyj komendy bota [accent]/link { $code }[] w ciągu [accent]{ $expireMinutes }[] min.
    [cyan]{ $discordUrl }
commands-discord-link-confirmed = [green]Połączono konto Discord: [accent]{ $discordUsername }[]
commands-discord-link-already-linked = [lightgray]To konto Mindustry jest już połączone. Użyj [accent]/discord status[] lub [accent]/discord unlink[].
commands-discord-link-error = [scarlet]Nie udało się utworzyć kodu połączenia z Discordem. Spróbuj później.
commands-discord-status-not-linked = [lightgray]Twoje konto nie jest połączone z Discordem.
commands-discord-status-linked = [green]Połączony Discord: [accent]{ $discordUsername }[] [gray]({ $discordId })[]
commands-discord-unlink-not-linked = [lightgray]Twoje konto nie jest połączone z Discordem.
commands-discord-unlink-success = [green]Usunięto połączenie z Discordem.
commands-logout-description = Wyloguj się. To [scarlet]odbierze ci uprawnienia admina.
commands-logout-successful = [green]Odebrano uprawnienia admina.
# ==============================================================================
# Moderation (Ban, Mute, Kick)
# ==============================================================================
commands-ban-description = Banuje gracza.
commands-ban-success = { $nickname } [scarlet]zbanowany
commands-unban-description = Odbanowuje gracza.
commands-unban-success = { $nickname }[accent] #{ $pid } [green]został odbanowany.
commands-mute-description = Wycisza gracza.
commands-mute-success = [accent]Wyciszono { $nickname }
commands-unmute-description = Zdejmuje wyciszenie z gracza.
commands-unmute-success = [green]Zdjęto wyciszenie z []{ $nickname }
commands-alert-description = Wyświetla wybranym lub wszystkim graczom wyraźny baner z ogłoszeniem.
commands-toast-description = Wyświetla wybranym graczom powiadomienie z ostrzeżeniem.
commands-announcement-description = Nadaje cykliczne ogłoszenie po kluczu albo następne w kolejce.
commands-audit-description = Pokazuje historię i działania administracji oraz moderacji.
ban-content = [scarlet]⚠ Zbanowany[]
    [accent]{ $nickname }[white] — masz stały ban na tym serwerze.
    [lightgray]Aby się odwołać, odwiedź kanał Discord [gray]{ support-channel }[]:
    [cyan]{ $discordUrl }
ban-cancelled = [accent]Ban gracza [scarlet]{ $nickname }[accent] został anulowany
tempban-content = [scarlet]⚠ Zbanowany[]
    [accent]{ $nickname }[white] — masz tymczasowy ban na tym serwerze.
    { "" }
    [orange]» [accent]Admin: [white]{ $adminName }
    [orange]» [accent]Powód: [gold]{ $reason }
    [orange]» [accent]Pozostało: { DURATION($duration, style: "full", colored: "true", maxUnits: 2) }
    [orange]» [accent]Wygasa: [white]{ DATETIME($expireDate, dateStyle: "medium", timeStyle: "short") }
    { "" }
    [lightgray]Aby się odwołać, odwiedź kanał Discord [gray]{ support-channel }[]:
    [cyan]{ $discordUrl }
tempban-player-banned = [scarlet] Admin { $adminName }[scarlet] zbanował gracza [gray]'[]{ $playerName }[gray]'
you-are-muted-by =
    [orange]⚠ Czat ograniczony[]
    [lightgray]Zostałeś wyciszony przez administratora [accent]{ $adminName }[lightgray].
    [orange]» [accent]Powód: [gold]{ $reason }
    [orange]» [accent]Pozostało: { DURATION($duration, style: "full", colored: "true", maxUnits: 2) }
you-are-muted =
    [orange]⚠ Czat ograniczony[]
    [lightgray]Nie możesz wysyłać wiadomości, dopóki wyciszenie jest aktywne.
    [orange]» [accent]Admin: [white]{ $adminName }
    [orange]» [accent]Powód: [gold]{ $reason }
    [orange]» [accent]Pozostało: { DURATION($duration, style: "full", colored: "true", maxUnits: 2) }
kick-pirated-game = [accent]Wykryto nieautoryzowanego klienta. [scarlet]Odmowa dostępu[]. Graj w [lime]oficjalną[] wersję ze [blue]Steam[], [blue]Google Play[] lub [blue]itch.io[].
kick-recently-kicked =
    [accent]Niedawno zostałeś wyrzucony z tego serwera.
    Poczekaj [cyan]{ DURATION($remaining, style: "timer") }[accent], zanim dołączysz ponownie.
kick-admintools-outdated =
    [green]Wymagana wersja AdminTools: [grey]{ $requiredVersion }[]
    [scarlet]Twoja wersja AdminTools: [grey]{ $version }[]
    { "" }
    [cyan]Zaktualizuj AdminTools, aby dołączyć do tego serwera.
support-channel = #reports-appeals
# ==============================================================================
# Voting (VoteKick)
# ==============================================================================
commands-votekick-description = Głosowanie nad wyrzuceniem gracza z serwera.
commands-vote-description = Oddaj głos w trwającym głosowaniu.
commands-vote-vote-with = [scarlet]⚠ Głosuj za pomocą [orange]/vote <y/n/c>
votekick-vote =
    { $starter } [grey]#[white]{ $starterId }[lightgray] zagłosował za wyrzuceniem { $target } [grey]#[white]{ $targetId }[lightgray] z powodu: [orange]{ $reason }[lightgray]. ([accent]{ $votes }[]/[accent]{ $required }[])
    [lightgray]Wpisz [orange]/vote <y/n>[], aby zagłosować.
votekick-left = { $player }[lightgray] wyszedł. Jego głos został anulowany. ([accent]{ $votes }[]/[accent]{ $required }[])
votekick-fail = [lightgray]Głosowanie nieudane. Za mało głosów, aby wyrzucić { $target }[lightgray].
votekick-cancelled = [scarlet]Głosowanie nad wyrzuceniem { $target }[scarlet] zostało anulowane przez { $admin }.
votekick-success =
    [orange]Głosowanie przyjęte. { $target }[orange] wyrzucony na [scarlet]{ $minutes }[] { $minutes ->
        [one] minutę
        [few] minuty
        [many] minut
       *[other] minuty
    }.
# ==============================================================================
# Maps & RTV
# ==============================================================================
commands-map-description = Statystyki wybranej mapy.
commands-map-title = [orange]{ -xcore } — Mapa
commands-map-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $name }[] [gray]autor: [sky]{ $author }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Przegląd[]
    [gray]Rozmiar: [white]{ $width }x{ $height } [darkgray]|[gray] Głosy: [lime]+{ $like } [darkgray]/[scarlet] -{ $dislike }[]
    { "" }
    [accent]■ Aktywność[]
    [gray]Rozegrano łącznie: [white]{ $played } [darkgray]|[gray] W tym roku: [white]{ $playedYear }[]
    [gray]Ostatnio grana: [white]{ $lastPlayed }[]
    [gray]Popularność: [white]{ $popularity } [darkgray]|[gray] Zainteresowanie: [white]{ $interest } [darkgray]|[gray] Reputacja: [white]{ $reputation }[]
    { "" }
    [accent]■ Czas trwania gry[]
    [gray]Min.: [white]{ $min } [darkgray]|[gray] Śr.: [white]{ $avg } [darkgray]|[gray] Maks.: [white]{ $max }[]
commands-maps-description = Lista wszystkich map na tym serwerze.
commands-maps-title = [orange]{ -xcore } — Mapy
commands-maps-content =
    [gray]Obecna mapa: [accent]{ $current }[]
    [white]Strona [green]{ $page }[] z [green]{ $total }[]
commands-maps-current-row = { $name } ★
commands-maps-text-description = Lista wszystkich map na tym serwerze.
commands-maps-text-start-content =
    [accent]Obecna mapa: []{ $name }[white]
    [orange][gold]Lista map [lightgray]{ $page }[gray]/[lightgray]{ $total }
commands-maps-text-content =
    { "" }
    { $index }. [orange] - [white]{ $name }[orange] | [green]{ $reputation }[orange] | [white]{ $width }x{ $height }[orange] | [white]{ $lastPlayed }[orange] | Autor: [sky]{ $author }
commands-artv-description = Natychmiast zmienia mapę.
commands-artv-map-skipped = { $nickname }[accent] pominął mapę. Następna mapa: { $name }.
commands-artv-event-skipped = { $nickname }[accent] pominął wydarzenie. Następne wydarzenie: { $name }.
commands-rtv-description = Głosowanie nad zmianą mapy.
commands-vnw-description = Głosowanie nad wcześniejszym rozpoczęciem następnej fali.
commands-avnw-description = Natychmiast rozpoczyna następną falę.
commands-like-description = Zagłosuj za obecną mapą (zwiększa jej reputację).
commands-dislike-description = Zagłosuj przeciw obecnej mapie.
map-vote-title = [orange]{ -xcore } — [scarlet]KONIEC GRY!
map-vote-content =
    { "" }
    Następna mapa: [accent]{ $mapName }[], autor: [accent]{ $author }[white].
    Nowa gra zacznie się za [accent]{ $seconds }[white] { $seconds ->
        [one] sekundę
        [few] sekundy
        [many] sekund
       *[other] sekundy
    }.
    { "" }
    [cyan]Podobała ci się ta mapa?
map-vote-like = [green]👍 Lubię
map-vote-dislike = [red]👎 Nie lubię
map-vote-like-selected = [gray]Podobała ci się
map-vote-dislike-selected = [gray]Nie podobała ci się
map-rtv = [orange]Głosowanie
map-artv = [red]Natychmiastowa zmiana
map-maps = Mapy
map-maps-back = Wróć do listy map
current-map = Obecna mapa
next-map = Następna mapa

# Map UI Modernized
map-ui-search-hint = Szukaj mapy lub autora...
map-ui-total = [lightgray]Mapy: [white]{ $count }[]
map-ui-about-title = O mapie
map-ui-rtv-title = Zmiana mapy
map-ui-no-maps-found = [lightgray]Nie znaleziono pasujących map.[]
map-ui-by = autor: [lightgray]{ $author }[]
map-ui-mode = Tryb: [white]{ $mode }[]
map-ui-loading = [gray]Ładowanie...[]
map-ui-no-preview = [gray]Brak podglądu[]
map-ui-dimensions = [gray]Wymiary: [white]{ $width } x { $height }[]
map-ui-total-plays = [gray]Rozegrano: [white]{ $played } [lightgray]({ $playedYear } w tym roku)[]
map-ui-last-played = [gray]Ostatnio grana: [white]{ $lastPlayed }[]
map-ui-description = [gray]Opis: [lightgray]{ $description }[]
map-ui-no-description = [gray]Opis: [lightgray]Brak opisu.[]

map-ui-col-duration = Czas trwania
map-ui-duration-min = [gray]Min.: [white]{ $value }[]
map-ui-duration-avg = [gray]Śr.: [white]{ $value }[]
map-ui-duration-max = [gray]Maks.: [white]{ $value }[]

map-ui-col-popularity = Popularność
map-ui-popularity-score = [gray]Wynik: [white]{ $value }[]
map-ui-popularity-pop = [gray]Popularność: [white]{ $value }[]
map-ui-popularity-interest = [gray]Zainteresowanie: [white]{ $value }[]

map-ui-col-community = Społeczność
map-ui-community-approval = [gray]Aprobata: [green]{ $rate }%[]

map-ui-btn-like = Lubię ({ $count })
map-ui-btn-dislike = Nie lubię ({ $count })

map-ui-rtv-active-status = [accent]● Trwa głosowanie: [white]{ $votes }/{ $required }[] [gray](koniec za [white]{ $seconds } s[gray])[]
map-ui-rtv-vote-yes = Głosuj za tą mapą
map-ui-rtv-start = Rozpocznij głosowanie za tą mapą
map-ui-admin-rtv = Zmień mapę teraz
map-ui-admin-rtv-confirm = Naciśnij ponownie, aby potwierdzić

gamemode-survival = Przetrwanie
gamemode-attack = Atak
gamemode-pvp = PvP
gamemode-sandbox = Piaskownica
gamemode-editor = Edytor
rtv-vote =
    { $nickname }[lightgray] zagłosował za zmianą mapy na [orange]{ $mapName }[lightgray]. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Wpisz [orange]y[] lub [orange]n[], aby zagłosować.
rtv-left = { $nickname }[lightgray] wyszedł. Jego głos za zmianą mapy został anulowany. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
rtv-fail = [lightgray]Głosowanie nieudane. Za mało głosów, aby zmienić mapę na [orange]{ $mapName }[].
rtv-success = [orange]Głosowanie przyjęte. Mapa [accent]{ $mapName }[] zostanie wczytana za [accent]{ $mapLoadDelay }[] { $mapLoadDelay ->
    [one] sekundę
    [few] sekundy
    [many] sekund
   *[other] sekundy
}…
rtv-cancelled = [lightgray]Głosowanie nad zmianą mapy na [orange]{ $mapName }[lightgray] zostało anulowane przez { $admin }.
vnw-vote =
    { $nickname }[lightgray] zagłosował za wcześniejszym rozpoczęciem fali [orange]{ $wave }[lightgray]. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Wpisz [orange]y[] lub [orange]n[], aby zagłosować.
vnw-left = { $nickname }[lightgray] wyszedł. Jego głos za wcześniejszym rozpoczęciem fali [orange]{ $wave }[lightgray] został anulowany. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
vnw-fail = [lightgray]Głosowanie nieudane. Za mało głosów, aby wcześniej rozpocząć falę [orange]{ $wave }[].
vnw-success = [orange]Głosowanie przyjęte. Fala [accent]{ $wave }[] zaczyna się teraz.
vnw-cancelled = [lightgray]Głosowanie nad wcześniejszym rozpoczęciem fali [orange]{ $wave }[lightgray] zostało anulowane przez { $admin }.
vnw-obsolete = [lightgray]Fala [orange]{ $wave }[lightgray] już się zaczęła, więc wynik głosowania nie jest potrzebny.
# ==============================================================================
# Statistics & Ranks & Players
# ==============================================================================
commands-player-description = Pokazuje statystyki gracza.
commands-settings-description = Otwiera twoje ustawienia gracza.
player-menu-player = Gracz
player-menu-player-title = [orange]{ -xcore } — Statystyki gracza
player-menu-player-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $customNickname }[] [gray]#{ $pid }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Profil[]
    [gray]Nick: [white]{ $nickname } [darkgray]|[gray] Admin: [lime]{ $admin }[]
    [gray]Odznaka: [white]{ $activeBadge } [darkgray]|[gray] Systemowa: [coral]{ $systemBadge }[]
    [gray]Dołączył: [white]{ $accountCreated }[]
    { "" }
    [accent]■ Rankingi[]
    [gray]Czas gry: [white]{ $totalPlayTime }[]
    [gray]MiniPvP: [sky]{ $pvpRating } [darkgray]|[gray] Stary Hexed: [sky]{ $hexedRankName } [gray]({ $hexedPoints } pkt) [darkgray]|[gray] Top: [accent]{ $hexedTopRank }[]
    { "" }
    [accent]■ Mecze: [white]{ $gamesPlayed } [gray]gier [darkgray]|[lime] { $gamesWon } [gray]wygranych [darkgray]|[sky] { $winRate }% [gray]wygranych[]
    [gray]• [white]PvP: { $pvpSummary }[]
    [gray]• [white]Przetrwanie: { $survivalSummary }[]
    [gray]• [white]Stary Hexed: { $hexedSummary }[]
    { "" }
    [accent]■ Skuteczność bojowa[]
    [gray]Bloki (Zbudowane/Rozebrane/Zniszczone): [lime]{ $blocksBuilt } [darkgray]/ [orange]{ $blocksDeconstructed } [darkgray]/ [scarlet]{ $blocksDestroyed }[]

# Modern Player Stats UI
player-stats-title = [orange]{ -xcore } — Profil gracza
player-stats-tab-overview = Przegląd
player-stats-tab-stats = Statystyki
player-stats-tab-matches = Mecze
player-stats-tab-blocks = Bloki
player-stats-tab-players = Online ({ $count })

player-stats-status-online = [lime]● Online[]
player-stats-status-offline = [gray]○ Offline[]
player-stats-no-bio = [gray]Brak opisu.[]
player-stats-loading = [lightgray]Ładowanie danych...[]
player-stats-no-stats = [gray]Brak zapisanych danych z meczów.[]
player-stats-filter-all = Filtr: Wszyscy
player-stats-filter-admins = Filtr: Tylko admini
player-stats-filter-non-admins = Filtr: Bez adminów
player-stats-refresh = Odśwież
player-stats-combat-efficiency = Skuteczność walki i budowy
player-stats-waves-summary = [gray]fale: maks. [lime]{ $best }[], śr. [white]{ $avg }[]
player-stats-hexed-top-placement = [gray]najlepsze [accent]#{ $best }[], top 3: [sky]{ $top3 }[]
player-stats-max-rank = [gold]★ OSIĄGNIĘTO NAJWYŻSZĄ RANGĘ ★[]
player-stats-max-league = [gold]★ OSIĄGNIĘTO NAJWYŻSZĄ LIGĘ ★[]
player-stats-hexed-wins-left = [lightgray]Wygrane do [white]{ $rank }[]: { $wins }
player-stats-league-elo-left = [lightgray]ELO do [white]{ $league }[]: { $elo }
player-stats-ratio-legend = [lightgray]Proporcje bloków[]

player-stats-account-created = [gray]Dołączył:[]
player-stats-play-time = [gray]Czas gry:[]
player-stats-legacy-pvp-rating = [gray]Stary PvP:[]
player-stats-hexed-rank = [gray]Stary Hexed:[]
player-stats-hexed-leaderboard = [gray]Top Hexed:[]

player-stats-total-games = [gray]Wszystkie gry:[]
player-stats-victories = [gray]Wygrane:[]
player-stats-survival-summary = [gray]Przetrwanie:[]
player-stats-hexed-summary = [gray]Stary Hexed:[]

player-stats-blocks-built = [gray]Zbudowane bloki:[]
player-stats-blocks-deconstructed = [gray]Rozebrane:[]
player-stats-blocks-destroyed = [gray]Zniszczone:[]
player-stats-units-produced = [gray]Wyprodukowane jednostki:[]
player-stats-units-lost = [gray]Stracone jednostki:[]

player-stats-games-played-value = [gray]Gry:[] [white]{ $count }[]
player-stats-victories-value = [gray]Wygrane:[] [lime]{ $wins }[]  [darkgray]|[]  [sky]{ $winRate }%[] [gray]wygranych[]
player-stats-hexed-points = [gray]({ $points } pkt)[]

player-stats-btn-settings = Ustawienia
player-stats-btn-audit = Historia
player-stats-btn-players = Online
player-stats-btn-close = Zamknij
player-stats-admin-tag = [coral]<Admin>[]
player-menu-players = Gracze online
player-menu-players-title = [orange]{ -xcore } — Gracze online
player-menu-players-content = [white]Strona [green]{ $page }[] z [green]{ $total }[]
player-menu-players-empty = Brak graczy online
player-menu-players-row = [white]{ $nickname } [gray](PID: { $pid })[]
player-menu-settings = Ustawienia
player-menu-settings-title = [orange]{ -xcore } — Ustawienia gracza
player-menu-settings-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $displayNickname }[] [gray]#{ $pid }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Profil[]
    [gray]Nick: [white]{ $nickname } [darkgray]|[gray] Wyświetlany: [lime]{ $customNickname }[]
    [gray]Odznaka: [white]{ $activeBadge } [darkgray]|[gray] Systemowa: [coral]{ $systemBadge }[]
    { "" }
    [accent]■ Widoczność[]
    [gray]Ranking: [white]{ $leaderboard }[]
    { "" }
    [accent]■ Czat[]
    [gray]Globalny: [white]{ $globalChat } [darkgray]|[gray] Discord: [white]{ $discordRelay }[]
    [gray]Tłumacz: [white]{ $translatorLanguage }[]
    { "" }
    [accent]■ Język[]
    [gray]Język: [white]{ $language }[]
player-menu-settings-chat = Ustawienia czatu
player-menu-settings-chat-title = [orange]{ -xcore } — Ustawienia czatu

# Modern Player Settings Form
player-settings-tab-profile = Profil
player-settings-tab-chat = Czat
player-settings-tab-badges = Odznaki
player-settings-chat-preview = Podgląd czatu
player-settings-chat-preview-sample = Przykład
player-settings-chat-preview-message = Witaj, świecie!
player-settings-symbol-color-mode = Kolor symbolu
player-settings-badges-my = Moje odznaki
player-settings-badges-all = Wszystkie odznaki
player-settings-badge-equip = Załóż
player-settings-badge-unequip = Zdejmij
player-settings-badge-preview = Podgląd
player-settings-badge-previewing = W podglądzie
player-settings-translator-lang = Tłumacz czatu
player-settings-translator-off = Wyłączony
player-settings-badges-empty = Nie odblokowałeś jeszcze żadnej odznaki.
player-settings-manage-badges = Zarządzaj odznakami
player-settings-global-chat = Czat globalny
player-settings-discord-relay = Przekaz z Discorda
player-settings-leaderboard = Pokazuj ranking
player-settings-language = Język
player-settings-saved = [lime]Zapisano ustawienia![]
player-settings-reset-feedback = [lightgray]Zresetowano własny nick.[]
player-settings-edit-badges = [accent]Edytuj[]
player-settings-tab-language = Język
player-settings-identity = Nick i opis
player-settings-interface = Ekran
player-settings-leaderboard-hint = Lista najlepszych graczy nad grą. Działa w Mini-PvP.
player-settings-global-chat-hint = Wiadomości graczy z innych serwerów XCore.
player-settings-discord-relay-hint = Wiadomości z kanału Discord serwera w czacie gry.
player-settings-translator-hint = Wiadomości innych graczy są tłumaczone na wybrany język.
player-settings-language-hint = Język menu i wiadomości serwera. Auto podąża za językiem twojej gry.
player-menu-settings-chat-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [accent]■ Widoczność czatu[]
    [gray]Czat globalny: [white]{ $globalChat }[]
    [gray]Przekaz z Discorda: [white]{ $discordRelay }[]
    { "" }
    [accent]■ Tłumaczenie[]
    [gray]Język tłumacza: [white]{ $translatorLanguage }[]
player-menu-settings-translator-title = [orange]{ -xcore } — Wybór języka tłumacza
player-menu-settings-language-title = [orange]{ -xcore } — Wybór języka
player-menu-settings-customNickname = Zmień nick
player-menu-settings-customNickname-title = [orange]{ -xcore } — Zmień nick
player-menu-settings-customNickname-message = [lightgray]Zostaw puste, aby zresetować
player-menu-settings-customNickname-reset = [scarlet]Resetuj nick
player-menu-settings-description = Zmień opis
player-menu-settings-description-title = [orange]{ -xcore } — Zmień opis
player-menu-settings-badges = Odznaki
player-menu-settings-global-chat-on = [green]Czat globalny
player-menu-settings-global-chat-off = [red]Czat globalny
player-menu-settings-discord-relay-on = [green]Przekaz z Discorda
player-menu-settings-discord-relay-off = [red]Przekaz z Discorda
audit-menu-open = Historia
audit-menu-actions-open = Działania
audit-menu-history-title = [orange]{ -xcore } — Historia
audit-menu-history-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $player }[] [gray]#{ $pid }[]
    [gray]Pokazane wpisy: [accent]{ $entriesShown }[]
    [gray]{ $pageState } [darkgray]|[] { $nextState }
    [lightgray]{ $hint }[]
audit-menu-history-page-first = Najnowsze wpisy
audit-menu-history-page-older = Starsze wpisy
audit-menu-tab-sanctions = Otrzymane kary
audit-menu-tab-actions = Wykonane działania
audit-menu-filter-all = Wszystkie
audit-menu-filter-bans = Bany
audit-menu-filter-mutes = Wyciszenia
audit-menu-filter-warns = Ostrzeżenia
audit-menu-filter-other = Inne
audit-menu-btn-back = Wróć do historii
audit-menu-btn-copy-id = Kopiuj ID
audit-menu-copy-id-success = ID wpisu wysłano na czat
audit-menu-field-id = ID wpisu
audit-menu-page = Strona { $page }
audit-menu-details-unavailable = Wpis jest niedostępny.
audit-menu-field-target = Gracz
audit-menu-field-actor = Wykonał
audit-menu-field-server = Serwer
audit-menu-field-reason = Powód
audit-menu-section-time = Czas
audit-menu-field-occurred = Kiedy
audit-menu-field-duration = Czas trwania
audit-menu-field-expires = Wygasa
audit-menu-btn-revoke = Cofnij karę
audit-menu-revoke-success = Kara została cofnięta.
audit-menu-status-active = AKTYWNA
audit-menu-status-expired = WYGASŁA
audit-menu-status-permanent = STAŁA
audit-menu-history-more = Są kolejne wpisy
audit-menu-history-end = Koniec historii
audit-menu-history-empty = Ten gracz nie ma jeszcze żadnych wpisów.
audit-menu-history-hint = Wybierz wpis poniżej, aby zobaczyć szczegóły.
audit-menu-actions-title = [orange]{ -xcore } — Działania
audit-menu-actions-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $player }[] [gray]#{ $pid }[]
    [gray]Pokazane wpisy: [accent]{ $entriesShown }[]
    [gray]{ $pageState } [darkgray]|[] { $nextState }
    [lightgray]{ $hint }[]
audit-menu-actions-empty = Ten gracz nie ma jeszcze żadnych działań.
audit-menu-actions-hint = Wybierz wpis poniżej, aby zobaczyć, co zrobił ten gracz.
audit-menu-summary-row = [accent]{ $action }[] [darkgray]•[] [white]{ $actor }[] [gray]— { $reason }
audit-menu-action-summary-row = [accent]{ $action }[] [darkgray]•[] [white]{ $target }[] [gray]— { $reason }
audit-menu-details-title = [orange]{ -xcore } — Szczegóły wpisu
audit-menu-details-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $player }[] [gray]#{ $pid }[]
    { "" }
    [accent]■ Zdarzenie[]
    [gray]Działanie: [white]{ $action }[]
    [gray]Wykonał: [white]{ $actor }[]
    [gray]Powód: [white]{ $reason }[]
    { "" }
    [accent]■ Czas[]
    [gray]Kiedy: [white]{ $occurredAt }[]
    [gray]Czas trwania: [white]{ $duration }[]
    [gray]Wygasa: [white]{ $expiresAt }[]
    { "" }
    [accent]■ Metadane[]
    [gray]ID wpisu: [white]{ $auditId }[]
audit-menu-unknown-actor = Nieznany
audit-menu-unknown-target = Nieznany
audit-menu-reason-unspecified = Nie podano
audit-menu-duration-permanent = Na stałe
audit-menu-action-ban = Ban
audit-menu-action-unban = Odbanowanie
audit-menu-action-mute = Wyciszenie
audit-menu-action-unmute = Zdjęcie wyciszenia
audit-menu-action-warn = Ostrzeżenie
audit-menu-action-kick = Wyrzucenie
audit-menu-action-note = Notatka
audit-menu-action-quarantine = Kwarantanna
audit-menu-action-unquarantine = Zdjęcie kwarantanny
player-menu-player-max-rank = Osiągnięto najwyższą rangę
player-menu-player-hexed-progress = [gray]Wymagane wygrane do [white]{ $nextRankName }[gray]: [accent]{ $requiredPoints }[]
player-menu-player-no-mode-stats = [gray]brak danych[]
player-menu-player-pvp-summary = [gray]gry [white]{ $gamesPlayed }[], wygrane [lime]{ $gamesWon }[], [sky]{ $winRate }%[]
player-menu-player-survival-summary = [gray]fale: maks. [lime]{ $bestWave }[], śr. [white]{ $averageWave }[] [gray](gry: { $gamesPlayed })[]
player-menu-player-hexed-summary = [gray]mecze [white]{ $gamesPlayed }[], 1. miejsce [lime]{ $gamesWon }[], najlepsze miejsce [accent]#{ $bestPlacement }[]
player-menu-time-days = { $value } d
player-menu-time-hours = { $value } godz.
player-menu-time-minutes = { $value } min
settings-language-label = Język: [green]{ $lang }[]
settings-translator-label = Tłumacz: [green]{ $lang }[]
badge-menu-title = [orange]{ -xcore } — Odznaki
badge-menu-content =
    [white]Odznaka systemowa: [green]{ $systemBadge }[]
    [white]Aktywna odznaka: [green]{ $activeBadge }[]
    [white]Kolor symbolu: [green]{ $symbolColorMode }[]
badge-menu-empty = [lightgray]Nie odblokowałeś jeszcze żadnej odznaki.
badge-menu-row = [white]{ $badge }[] [gray]-[] { $description }
badge-menu-symbol-color-button = Kolor symbolu: [green]{ $mode }[]
badge-menu-symbol-color-title = [orange]{ -xcore } — Kolor symbolu odznaki
badge-menu-symbol-color-content =
    [white]Obecny tryb: [green]{ $mode }[]
    [lightgray]Wybierz, jak ma być pokolorowany symbol odznaki.
badge-menu-symbol-color-default = Domyślny kolor odznaki
badge-menu-symbol-color-player-color = Kolor gracza
badge-menu-view-all = Wszystkie odznaki
badge-menu-all-title = [orange]{ -xcore } — Wszystkie odznaki
badge-menu-all-content = [lightgray]Wszystkie odznaki z ich statusem i opisem.
badge-menu-all-row = [white]{ $badge }[] [gray]-[] [accent]{ $state }[] [gray]-[] { $description }
badge-clear-button = Zdejmij aktywną odznakę
badge-state-system = Systemowa
badge-state-system-active = Systemowa aktywna
badge-state-active = Aktywna
badge-state-unlocked = Odblokowana
badge-state-locked = Zablokowana
badge-set-success = [accent]Aktywna odznaka: [green]{ $badge }[].
badge-clear-success = [accent]Zdjęto aktywną odznakę.
badge-grant-success = [accent]Przyznano [green]{ $badge }[] graczowi [green]{ $nickname }[][gray]#{ $pid }[].
badge-revoke-success = [accent]Odebrano [green]{ $badge }[] graczowi [green]{ $nickname }[][gray]#{ $pid }[].
badge-already-unlocked = [scarlet]⚠ Odznaka [accent]{ $badge }[scarlet] jest już odblokowana.
badge-not-owned = [scarlet]⚠ Gracz nie ma odznaki [accent]{ $badge }[scarlet].
error-badge-not-found = [scarlet]⚠ Nie znaleziono odznaki [accent]{ $badge }[scarlet].
error-badge-not-unlocked = [scarlet]⚠ Odznaka [accent]{ $badge }[scarlet] nie jest odblokowana.
error-badge-not-selectable = [scarlet]⚠ Odznaki [accent]{ $badge }[scarlet] nie można wybrać ręcznie.
badge-admin-name = Admin
badge-admin-description = Automatyczna odznaka administratorów.
badge-developer-name = Deweloper
badge-developer-description = Przyznawana deweloperom XCore.
badge-translator-name = Tłumacz
badge-translator-description = Przyznawana osobom tłumaczącym XCore.
badge-map-maker-name = Twórca map
badge-map-maker-description = Przyznawana twórcom map używanych na serwerze.
badge-contributor-name = Współtwórca
badge-contributor-description = Przyznawana za wkład w XCore lub jego społeczność.
badge-bug-finder-name = Łowca błędów
badge-bug-finder-description = Przyznawana za regularne, rzetelne zgłoszenia błędów.
badge-event-winner-name = Zwycięzca wydarzenia
badge-event-winner-description = Przyznawana zwycięzcom specjalnych wydarzeń na serwerze.
badge-veteran-name = Weteran
badge-veteran-description = Przyznawana długoletnim, szanowanym graczom.
badge-season-champion-name = Mistrz sezonu
badge-season-champion-description = Przyznawana za miejsce na podium sezonu rankingowego.
commands-lb-description = Włącza/wyłącza ranking.
commands-lb-success =
    { $leaderboardEnabled ->
        [true] [accent]Ranking [green]włączony.
       *[other] [accent]Ranking [scarlet]wyłączony.
    }
leaderboard = [blue]Ranking
commands-observer-description = Przełącza w tryb obserwatora. Twoja obecna jednostka zostanie usunięta, a ty trafisz do drużyny widzów.
commands-rank-description = Pokazuje twoją rangę lub rangę innego gracza.
commands-rank-content =
    { $nickname }
    { $rankTag } [accent]{ $rankName }
    [gold]Wygrane: { $points }/{ $requiredPoints }
commands-ranks-description = Pokazuje informacje o rangach.
commands-ranks-content =
    { $rankTag } [accent]{ $rankName }
    [gold]Wymagania: [grey]{ $requiredPoints } [accent]wygranych[]
commands-ranks-footer = Wygrane liczą się tylko po pokonaniu gracza o twojej randze lub wyższej.
commands-top-description = Najlepsi gracze.
commands-season-description = Sezon rankingowy: pozostały czas, twoja pozycja i zwycięzcy poprzedniego sezonu.
commands-top-hexed-content = [orange]{ $index }. { $nickname }[accent]: [blue]{ $rankName } [cyan]{ $points } []wygranych
commands-top-pvp-content = [orange]{ $index }. { $nickname }[accent]: [cyan]{ $rating }
top-menu-title = [orange]{ -xcore } — Najlepsi gracze: [accent]{ $category }
top-menu-content =
    [lightgray]Wybierz gracza, aby otworzyć jego profil.[]
    [lightgray]Strona [green]{ $page }[]/[green]{ $totalPages }[] [gold]•[] [lightgray]Gracze: [green]{ $totalEntries }[]
    { $selfRankLine }
top-menu-empty =
    [accent]Kategoria: [green]{ $category }[]
    [gray]Na razie brak graczy.
top-menu-categories-title = [orange]{ -xcore } — Kategoria rankingu
top-menu-categories-content =
    [lightgray]Wybierz, który ranking pokazać.[]
    [lightgray]Obecny: [green]{ $category }[]
top-menu-category-button = [accent]Kategoria: [green]{ $category }[]
top-menu-category-mini-pvp = MiniPvP
top-menu-category-playtime = Czas gry
top-menu-category-hexed = Hexed
top-menu-self-rank-known = [lightgray]Twoja pozycja: [accent]#{ $rank }[]
top-menu-self-rank-unknown = [lightgray]Twoja pozycja: [gray]nie znaleziono[]
top-menu-entry-mini-pvp = { $rankLabel } { $leagueIcon } [accent]{ $nickname }[] [gray]—[] [sky]{ $value }[]
top-menu-entry-playtime = { $rankLabel } [accent]{ $nickname }[] [gray]—[] [green]{ $value }[]
top-menu-entry-hexed = { $rankLabel } [accent]{ $nickname }[] [gray]—[] [violet]{ $rankName }[] [gold]•[] [cyan]{ $value }[]
top-menu-total-count = Gracze: { $count }
top-menu-btn-find-self = Znajdź mnie
top-menu-on-this-page = na tej stronie
top-menu-unranked = Nie masz jeszcze miejsca w tej kategorii
top-menu-self-rank-line = Twoje miejsce: { $rank }
top-menu-score-points = { $points } pkt
top-menu-score-minutes = { $time }
# ==============================================================================
# Game Modes (Hexed, PvP, Surrender, AI)
# ==============================================================================
commands-surrender-description = Poddaj się w Hexed. Twoja obecna drużyna zostanie zniszczona, jednostka usunięta, a ty trafisz do drużyny widzów.
commands-surrender-success = [green]Poddałeś się i teraz obserwujesz grę
commands-observer-success = [green]Teraz obserwujesz grę
commands-observer-exit-success = [green]Już nie obserwujesz gry
commands-ai-description = Steruje SI.
commands-ai-usage = [red]attack(i) []lub [accent]idle(i)
hexed-popup = [blue]{ DURATION($remaining, style: "timer") }[] do końca gry.
hexed-eliminated = { $nickname } [gold]został [scarlet]wyeliminowany[]!
hexed-leaderboard-content = [orange]{ $index }. { $nickname }[accent]: [cyan]{ $hexes } [accent]heksów
hexed-ranks-newbie = Nowicjusz
hexed-ranks-regular = Bywalec
hexed-ranks-advanced = Zaawansowany
hexed-ranks-veteran = Weteran
hexed-ranks-davastator = Niszczyciel
hexed-ranks-the_legend = Legenda
hexed-game-over-header = Koniec gry. Zwycięzcy:
hexed-game-over-winner-row =
    [orange]{ $index }. { $name }[][accent]: [cyan]{ $cores } { $cores ->
        [one] heks
        [few] heksy
        [many] heksów
       *[other] heksu
    }
hexed-game-over-no-winners = Koniec gry. Niestety nie udało się ustalić zwycięzców.
hexed-game-over-restart = Nowa gra za 10 sekund…
rating_league_scrap = Złom
rating_league_copper = Miedź
rating_league_lead = Ołów
rating_league_graphite = Grafit
rating_league_silicon = Krzem
rating_league_titanium = Tytan
rating_league_thorium = Tor
rating_league_plastanium = Plastan
rating_league_phase_fabric = Tkanina fazowa
rating_league_surge_alloy = Stop energetyczny
pvp-team-won = Twoja drużyna wygrała. Twój ranking wzrósł o { $increased }
pvp-team-lose = Twoja drużyna przegrała. Twój ranking spadł o { $reduced }
pvp-match-settlement-win = [accent]■ Wynik meczu: [green]Zwycięstwo![] Twój ranking: [stat]{ $oldRating }[] -> [stat]{ $newRating }[] [green](+{ $delta })[] { $leagueIcon } { $leagueName }
pvp-match-settlement-loss = [accent]■ Wynik meczu: [scarlet]Porażka![] Twój ranking: [stat]{ $oldRating }[] -> [stat]{ $newRating }[] [scarlet]({ $delta })[] { $leagueIcon } { $leagueName }
pvp-match-settlement-draw = [accent]■ Wynik meczu: [yellow]Remis![] Twój ranking: [stat]{ $oldRating }[] -> [stat]{ $newRating }[] [yellow]({ $delta })[] { $leagueIcon } { $leagueName }
pvp-match-settlement-exempt = [accent]■ Wynik meczu: [lightgray]Bez zmiany rankingu (za mało aktywnej gry).[]
season-ending-soon = [accent]■ [white]{ $ladder }[]: sezon { $number } kończy się za [stat]{ $remaining }[]. Wykorzystaj ostatnie mecze!
season-started = [accent]■ [white]{ $ladder }[]: sezon { $previous } dobiegł końca. Rozpoczął się sezon { $number }!
season-reset-soft = [lightgray]Rankingi przesunięto w stronę wartości startowej, a liczniki meczów liczą od zera.[]
season-reset-hard = [lightgray]Wszyscy zaczynają od nowa z rankingiem startowym.[]
season-reset-none = [lightgray]Rankingi zostają, liczniki meczów liczą od zera.[]
season-title = Sezon { $number }
top-menu-scope-current = { $season } [lightgray]· koniec za { $remaining }[]
top-menu-scope-past = { $season } [gray]· { $from } – { $to }[]
ladder-profile-standing = { $leagueIcon } [white]{ $league }[] [accent]{ $rating } ELO[]
ladder-profile-headline = [accent]{ $icon }[] [lightgray]{ $ladder }:[] { $standing }
ladder-profile-unplaced = [gray]brak meczów rankingowych w tym sezonie[]
ladder-profile-matches = [lightgray]Mecze:[] [white]{ $matches }[]
ladder-profile-wins = [lightgray]Wygrane:[] [white]{ $wins }[] [gray]({ $rate }%)[]
ladder-profile-rank = [lightgray]Miejsce:[] [accent]#{ $rank }[]
ladder-profile-peak = [lightgray]Szczyt:[] [white]{ $rating }[]
ladder-profile-season = [lightgray]{ $season } · koniec za [white]{ $remaining }[][]
ladder-profile-season-closing = [lightgray]{ $season } · trwa liczenie wyników[]
ladder-profile-history = [gray]{ $season } — { $rank }, { $league }, { $rating } ELO[]
season-menu-title = Sezony rankingowe
season-menu-card-title = { $ladder } — { $season }
season-menu-ends = [lightgray]Kończy się za [white]{ $remaining }[] ({ $date })[]
season-menu-closing = [lightgray]Sezon dobiegł końca, trwa liczenie wyników.[]
season-menu-participants = [lightgray]Gracze w tym sezonie: [white]{ $count }[][]
season-menu-you = [lightgray]Ty:[] { $standing }
season-menu-previous = [lightgray]{ $season } — zwycięzcy:[]
season-menu-podium-entry = [gold]{ $place }.[] [white]{ $name }[] [gray]—[] { $league } [accent]{ $rating }[]
season-menu-open-top = Ranking
season-menu-prizes = [lightgray]Nagrody w tym sezonie:[]
season-menu-prize-entry = [gold]{ $places }.[] [white]{ $prize }[]
season-menu-podium-prizes = [gray] · [gold]{ $prizes }[]
prize-grant-line = [lightgray]{ $season } · nagroda:[] [gold]{ $prize }[] [gray]({ $status })[]
prize-status-pending = czeka na przekazanie
prize-status-granted = otrzymana
prize-status-delivered = dostarczona
prize-status-failed = admin się tym zajmie
season-menu-empty = Nie ma jeszcze sezonów rankingowych.
pvp-hud-status = [accent]MiniPvP[] | [stat]Żywi:[] { $teams } | [gray]{ $time }[]
pvp-leaderboard-content = [orange]{ $index }. { $nickname }[accent]:[cyan] { $rating } [accent]pkt rankingu
pvp-you-spectator = [scarlet]Zostałeś wyeliminowany. Poczekaj na następną grę.
# ==============================================================================
# Events & Notifications
# ==============================================================================
player-joined-pid = { $nickname } [grey]#[white]{ $pid }[grey] [accent]dołączył.
player-joined-username = { $nickname } [grey]@[white]{ $username }[grey] [accent]dołączył.
player-joined-both = { $nickname } [accent]@{ $username } [grey]#[white]{ $pid }[grey] [accent]dołączył.
player-joined-none = { $nickname } [accent]dołączył.

player-left-pid = { $nickname } [grey]#[white]{ $pid }[grey] [accent]wyszedł.
player-left-username = { $nickname } [grey]@[white]{ $username }[grey] [accent]wyszedł.
player-left-both = { $nickname } [accent]@{ $username } [grey]#[white]{ $pid }[grey] [accent]wyszedł.
player-left-none = { $nickname } [accent]wyszedł.

player-settings-identity-mode = [accent]Identyfikator w ogłoszeniach:[]
player-settings-identity-mode-pid = Tylko ID (#12)
player-settings-identity-mode-username = Tylko nazwa użytkownika (@Steve)
player-settings-identity-mode-both = Oba (@Steve #12)
player-settings-identity-mode-none = Ukryty

notification-votekick-playtime =
    [accent]Gratulacje! Grasz już [lightgray]{ $votekickPlayTime }[] { $votekickPlayTime ->
        [one] minutę
        [few] minuty
        [many] minut
       *[other] minuty
    } i możesz rozpoczynać głosowania nad wyrzuceniem.
notification-global-chat-playtime =
    [accent]Gratulacje! Grasz już [lightgray]{ $globalChatPlayTime }[] { $globalChatPlayTime ->
        [one] minutę
        [few] minuty
        [many] minut
       *[other] minuty
    } i możesz pisać na czacie globalnym.
    [lightgray]Wpisz [accent]/g [gray]<wiadomość…>[lightgray], aby wysłać wiadomość.
notification-admin-kick = { $admin }[accent] wyrzucił { $target }[].
notification-admin-wave-skip = { $admin }[accent] pominął falę.
server-restart-countdown =
    Restart za { $seconds ->
        [one] { $seconds } sekundę
        [few] { $seconds } sekundy
        [many] { $seconds } sekund
       *[other] { $seconds } sekundy
    }
like-map-success = [green]Podoba ci się ta mapa!
like-map-changed = [green]Zmieniłeś zdanie na „lubię”!
dislike-map-success = [orange]Nie podoba ci się ta mapa.
dislike-map-changed = [orange]Zmieniłeś zdanie na „nie lubię”.
like-event-success = [green]Podoba ci się to wydarzenie!
like-event-changed = [green]Zmieniłeś zdanie na „lubię”!
dislike-event-success = [orange]Nie podoba ci się to wydarzenie.
dislike-event-changed = [orange]Zmieniłeś zdanie na „nie lubię”.

# ==============================================================================
# Events (Server)
# ==============================================================================

commands-event-description = Menu zarządzania wydarzeniami.
commands-events-description = Lista wszystkich wydarzeń na serwerach.
event-events = Wydarzenia
event-menu-main = Główne wydarzenia
event-menu-main-title = [orange]{ -xcore } — Wydarzenia
event-menu-main-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]Centrum wydarzeń[]
    [lightgray]Wszystkie aktywne i zaplanowane wydarzenia serwera w jednym miejscu.[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Obecne wydarzenie[]
    [gray]Status: [white]{ $currentEventState }[]
    [gray]Wybrane: [white]{ $currentEventName }[]
    { "" }
    [accent]■ Głosowanie[]
    [gray]Sesja głosowania: [white]{ $voteStatus }[]
    { "" }
    [accent]■ Działania[]
    [gray]Otwórz katalog, sprawdź obecne wydarzenie lub przygotuj nowe.[]
event-menu-event = Wydarzenie
event-menu-event-title = [orange]{ -xcore } — Wydarzenie
event-menu-event-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $name }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Przegląd[]
    [gray]Autor: [white]{ $author }[]
    [gray]Mapa: [white]{ $mapName }[]
    [gray]Typ: [white]{ $eventType }[] [darkgray]|[gray] Stan: [white]{ $eventState }[]
    [gray]Tymczasowe: [white]{ $isTemporary }[]
    { "" }
    [accent]■ Harmonogram[]
    [gray]Utworzone: [white]{ $createdEventTime }[]
    [gray]Planowany start: [white]{ $plannedStartTime }[]
    [gray]Planowany koniec: [white]{ $plannedEndTime }[]
    { "" }
    [accent]■ Reputacja[]
    [gray]Polubienia: [white]{ $like }[] [darkgray]|[gray] Niepolubienia: [white]{ $dislike }[]
event-menu-event-map = Zobacz mapę
event-menu-events = Lista wydarzeń
event-menu-events-title = [orange]{ -xcore } — Lista wydarzeń
event-menu-events-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]Katalog wydarzeń[]
    [lightgray]Strona [green]{ $page }[]/[green]{ $total }[] [gold]•[] [lightgray]Wydarzenia: [green]{ $count }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Filtry[]
    [gray]Zakończone: [white]{ $finished }[]
    [gray]Duże: [white]{ $major }[] [darkgray]|[gray] Aktywne: [white]{ $active }[]
    { "" }
    [accent]■ Lista[]
    [gray]Wybierz wydarzenie poniżej, aby zobaczyć jego kartę.[]
event-menu-events-empty =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]Katalog wydarzeń[]
    [lightgray]Brak wydarzeń pasujących do obecnych filtrów.[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Filtry[]
    [gray]Zakończone: [white]{ $finished }[]
    [gray]Duże: [white]{ $major }[] [darkgray]|[gray] Aktywne: [white]{ $active }[]
event-menu-events-row = [accent]{ $state }[] [darkgray]•[] [white]{ $type }[] [darkgray]—[] { $name }
event-menu-events-selected = [green]●[] [accent]{ $state }[] [darkgray]•[] [white]{ $type }[] [darkgray]—[] { $name }
event-menu-create-start = Utwórz
event-menu-create-start-title = [orange]{ -xcore } — Tworzenie wydarzenia
event-menu-create-start-message = Wpisz nazwę nowego wydarzenia
event-menu-create-start-default = Wydarzenie gracza { $playerName }
event-menu-create-start-map = Utwórz wydarzenie dla tej mapy
event-menu-edit = Edytuj
event-menu-edit-title = [orange]{ -xcore } — Edycja wydarzenia
event-menu-edit-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $name }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Dane[]
    [gray]Autor: [white]{ $author }[]
    [gray]Typ: [white]{ $eventType }[]
    { "" }
    [accent]■ Mapa[]
    [gray]Wybrana mapa: [white]{ $mapName }[]
    { "" }
    [accent]■ Harmonogram[]
    [gray]Planowany start: [white]{ $plannedStartTime }[]
    [gray]Planowany koniec: [white]{ $plannedEndTime }[]
    { "" }
    [accent]■ Opcje[]
    [gray]Tymczasowe: [white]{ $isTemporary }[]
event-menu-edit-name = Nazwa
event-menu-edit-name-reset = [scarlet]Resetuj nazwę
event-menu-edit-name-title = [orange]{ -xcore } — Edycja wydarzenia
event-menu-edit-name-message = Zmień nazwę:
event-menu-edit-description = Opis
event-menu-edit-description-title = [orange]{ -xcore } — Edycja wydarzenia
event-menu-edit-description-message = Zmień opis:
event-menu-edit-map = Zmień mapę
event-menu-edit-temporary-active = [green]Tymczasowe
event-menu-edit-temporary-inactive = [gray]Tymczasowe
event-menu-edit-major-active = [green]Duże
event-menu-edit-major-inactive = [gray]Duże
event-menu-edit-planned-start = Początek wydarzenia
event-menu-edit-planned-start-title = [orange]{ -xcore } — Edycja wydarzenia
event-menu-edit-planned-start-message = Wpisz czas rozpoczęcia w ms lub z m/h/d:
event-menu-edit-planned-end = Koniec wydarzenia
event-menu-edit-planned-end-title = [orange]{ -xcore } — Edycja wydarzenia
event-menu-edit-planned-end-message = Wpisz czas zakończenia w ms lub z m/h/d:
event-menu-maps = Mapy
event-menu-maps-title = [orange]{ -xcore } — Wybierz mapę
event-menu-maps-content = [white]Strona [green]{ $page }[] z [green]{ $total }[]
vote-event-vote =
    { $nickname }[lightgray] zagłosował za zmianą wydarzenia na [orange]{ $name }[lightgray]. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Wpisz [orange]y[] lub [orange]n[], aby zagłosować.
vote-event-left = { $nickname }[lightgray] wyszedł. Jego głos za zmianą wydarzenia został anulowany. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
vote-event-fail = [lightgray]Głosowanie nieudane. Za mało głosów, aby zmienić wydarzenie na [orange]{ $name }[].
vote-event-success = [orange]Głosowanie przyjęte. Wydarzenie [accent]{ $name }[] zostanie wczytane przy następnej zmianie mapy.
vote-event-cancelled = [lightgray]Głosowanie nad zmianą wydarzenia na [orange]{ $name }[lightgray] zostało anulowane przez administratora { $admin }.
event-vote = [orange]Głosowanie
event-avote = [red]Natychmiastowa zmiana
event-menu-vote-stop = Zatrzymaj głosowanie
event-menu-stop = Zatrzymaj wydarzenie
event-menu-this-event = [orange]Obecne wydarzenie
event-menu-type-major = Duże wydarzenie
event-menu-type-regular = Zwykłe wydarzenie
event-menu-state-none = Brak aktywnego wydarzenia
event-menu-state-planned = Zaplanowane
event-menu-state-active = Trwa teraz
event-menu-state-finished = Zakończone
event-menu-vote-status-running = Trwa
event-menu-vote-status-idle = Nie trwa
date-time-picker-title = [orange]{ -xcore } — Data i godzina
date-time-picker-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $field }[]
    [lightgray]Obecna wartość: [white]{ $value }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Data[]
    [gray]Najpierw wybierz dzień, a potem dopasuj godzinę poniżej.[]
    { "" }
    [accent]■ Godzina[]
    [gray]Użyj gotowych ustawień lub drobnych korekt do dokładnego planowania.[]
    { "" }
    [accent]■ Ręczne wpisanie[]
    [gray]Tylko gdy potrzebujesz dokładnych milisekund lub wartości względnej +m/+h/+d.[]
date-time-picker-field-generic = Planowany czas
date-time-picker-today = Dziś
date-time-picker-tomorrow = Jutro
date-time-picker-plus-2d = +2 dni
date-time-picker-plus-7d = +7 dni
date-time-picker-now = Teraz
date-time-picker-time-0000 = 00:00
date-time-picker-time-0600 = 06:00
date-time-picker-time-1200 = 12:00
date-time-picker-time-1800 = 18:00
date-time-picker-minus-1d = -1 d
date-time-picker-plus-1d = +1 d
date-time-picker-minus-1h = -1 godz.
date-time-picker-plus-1h = +1 godz.
date-time-picker-minus-15m = -15 min
date-time-picker-plus-15m = +15 min
date-time-picker-reset = Resetuj
date-time-picker-manual = Ręczne wpisanie
date-time-picker-manual-title = [orange]{ -xcore } — Ręczne wpisanie czasu
date-time-picker-manual-message = Wpisz bezwzględne milisekundy lub czas względny, np. +30m, +2h, +1d.
event-end = Wydarzenie [green]{ $name }[] dobiegło końca!
# ==============================================================================
# Errors
# ==============================================================================
error-access-denied = [scarlet]⚠ Odmowa dostępu.
error-ip-changed = [scarlet]⚠ Twój adres IP się zmienił. Uprawnienia admina zostały odebrane.
error-not-enough-params = [scarlet]⚠ Za mało parametrów pozycji.
error-player-not-found = [scarlet]Nie znaleziono gracza.
error-player-not-teammate = [scarlet]⚠ Ten gracz nie jest w twojej drużynie.
error-player-admin = [scarlet]⚠ Nie próbuj wyrzucać admina. ⚠
error-already-voted = [scarlet]⚠ Już zagłosowałeś. Spokojnie.
error-playtime-requirement =
    [scarlet]⚠ Musisz grać co najmniej { $time } { $time ->
        [one] minutę
        [few] minuty
        [many] minut
       *[other] minuty
    }, aby użyć tej funkcji.
error-globalchat-total-playtime =
    [scarlet]⚠ Aby pisać na czacie globalnym, musisz grać { $globalChatPlayTime } { $globalChatPlayTime ->
        [one] minutę
        [few] minuty
        [many] minut
       *[other] minuty
    }.
error-votekick-total-playtime =
    [scarlet]⚠ Aby rozpocząć głosowanie nad wyrzuceniem, musisz grać { $votekickPlayTime } { $votekickPlayTime ->
        [one] minutę
        [few] minuty
        [many] minut
       *[other] minuty
    }.
error-vote-yourself = [scarlet]⚠ Nie możesz głosować we własnym głosowaniu.
error-vote-in-progress = [scarlet]⚠ Głosowanie już trwa.
error-no-voting = [scarlet]⚠ W tej chwili nie ma żadnego głosowania.
error-wave-vote-unavailable = [scarlet]⚠ Wcześniejsze rozpoczęcie fali jest dostępne tylko w trybach z falami.
error-no-map = [scarlet]⚠ Nie ustawiono mapy.
error-map-not-event = [scarlet]⚠ Mapa nie należy do obecnego wydarzenia.
error-map-not-found = [scarlet]⚠ Nie znaleziono mapy! [accent]Użyj [cyan]/maps[], aby zobaczyć wszystkie dostępne mapy.
error-maps-empty = [scarlet]⚠ Lista map jest pusta.
error-event-not-found = [scarlet]⚠ Nie znaleziono wydarzenia! [accent]Użyj [cyan]/events[], aby zobaczyć dostępne wydarzenia.
error-page-between = [scarlet]⚠ 'page' musi być liczbą od[orange] 1[] do [orange]{ $totalPages }[].
error-page-number = [scarlet]'page' musi być liczbą.
error-wrong-number = [scarlet]⚠ Nieprawidłowy format liczby.
error-wrong-period-format = [scarlet]⚠ Nieprawidłowy format czasu. Przykład: 1h 30m, 30 ({ hours })
error-invalid-id = [scarlet]⚠ Nieprawidłowe ID gracza.
error-spectator = [scarlet]⚠ Jako widz nie możesz używać tej komendy.
error-admin-password-too-short = [scarlet]⚠ Hasło administratora musi mieć co najmniej 8 znaków.
error-wrong-admin-password = [scarlet]⚠ Nieprawidłowe hasło admina.
error-internal = [scarlet]Błąd wewnętrzny.
error-processing-request = [scarlet]Wystąpił błąd podczas przetwarzania żądania.
error-no-access = [scarlet]⚠ Brak dostępu.
error-nickname-too-long = [scarlet]⚠ Nick jest za długi. Maksymalnie { $max } widocznych znaków.
error-private-message-invalid-pid = [scarlet]⚠ Nieprawidłowy PID wiadomości prywatnej. Format: [lightgray]#123[].
error-private-message-self = [scarlet]⚠ Nie możesz wysłać prywatnej wiadomości do siebie.
error-private-message-empty = [scarlet]⚠ Wiadomość nie może być pusta.
error-private-message-too-long = [scarlet]⚠ Wiadomość jest za długa. Maksymalnie { $max } znaków.
error-private-message-cooldown = [scarlet]⚠ Poczekaj { DURATION($seconds) } przed wysłaniem kolejnej prywatnej wiadomości.
error-private-message-target-unavailable = [scarlet]⚠ Ten gracz nie może teraz odbierać prywatnych wiadomości.
error-private-message-no-reply-target = [scarlet]⚠ Brak ostatniego kontaktu, któremu można odpowiedzieć.
error-private-message-not-found = [scarlet]⚠ Nie znaleziono wiadomości.
error-private-message-block-self = [scarlet]⚠ Nie możesz zablokować samego siebie.
error-private-message-block-limit = [scarlet]⚠ Osiągnięto limit listy blokowanych ({ $limit }).
ban-menu-duration-title = [orange]{ -xcore } - Czas bana
ban-menu-duration-message = Wpisz czas bana dla { $nickname }. Przykład: 1d, 12h, 30m
ban-menu-reason-title = [orange]{ -xcore } - Powód bana
ban-menu-reason-message = Wpisz powód bana dla { $nickname }. Zostaw puste, aby użyć domyślnego powodu.
ban-menu-confirm-title = [orange]{ -xcore } - Potwierdź bana
ban-menu-confirm-content =
    [white]Gracz: { $nickname }[]
    [white]Czas: [accent]{ $duration }[]
    [white]Powód: [accent]{ $reason }[]
ban-menu-confirm-action = [scarlet]Zbanuj gracza
error-invalid-syntax = [scarlet]⚠ Nieprawidłowa składnia komendy. Użycie: [lightgray]/'{ $syntax }'.
error-invalid-sender = [scarlet]⚠ Nieprawidłowy nadawca komendy. Ta komenda wymaga: '[lightgray]{ $type }[]'.
error-argument-parse-generic = [scarlet]⚠ Nieprawidłowy argument: '{ $error }'.
exception-unexpected = [scarlet]⚠ Podczas wykonywania tej komendy wystąpił błąd wewnętrzny.
exception-invalid-argument = [scarlet]⚠ Nieprawidłowy argument komendy: '{ $cause }'.
exception-no-such-command = [scarlet]⚠ Nieznana komenda.
exception-no-permission = [scarlet]⚠ Odmowa dostępu.
exception-invalid-sender = [scarlet]⚠ '{ $actual }' nie może wykonać tej komendy. Wymagany nadawca: [lightgray]{ $expected }[].
exception-invalid-sender-list = [scarlet]⚠ '{ $actual }' nie może wykonać tej komendy. Dozwoleni nadawcy: [lightgray]{ $expected }[].
exception-invalid-syntax = [scarlet]⚠ Nieprawidłowa składnia komendy. Użycie: [lightgray]/'{ $syntax }'.
argument-parse-failure-boolean = [scarlet]⚠ Nie udało się odczytać wartości logicznej z '{ $input }'.
argument-parse-failure-number = [scarlet]⚠ '{ $input }' nie jest prawidłową liczbą z zakresu [{ $min }, { $max }].
argument-parse-failure-char = [scarlet]⚠ '{ $input }' nie jest prawidłowym znakiem.
argument-parse-failure-enum = [scarlet]⚠ '{ $input }' nie jest prawidłową opcją. Dozwolone: [lightgray]{ $acceptableValues }
argument-parse-failure-string = [scarlet]⚠ Nieprawidłowy format tekstu dla '{ $input }'.
argument-parse-failure-uuid = [scarlet]⚠ Nieprawidłowy format UUID: '{ $input }'.
argument-parse-failure-regex = [scarlet]⚠ Wpis '{ $input }' nie pasuje do wzorca '{ $pattern }'.
argument-parse-failure-color = [scarlet]⚠ '{ $input }' nie jest prawidłowym kolorem.
argument-parse-failure-duration = [scarlet]⚠ '{ $input }' nie jest prawidłowym formatem czasu.
argument-parse-failure-aggregate-missing = [scarlet]⚠ Brak składnika '{ $component }'.
argument-parse-failure-aggregate-failure = [scarlet]⚠ Nieprawidłowy składnik '{ $component }': '{ $failure }'.
argument-parse-failure-either = [scarlet]⚠ Nie udało się odczytać { $primary } ani { $fallback } z '{ $input }'.
argument-parse-failure-flag-unknown = [scarlet]⚠ Nieznana flaga: '{ $flag }'.
argument-parse-failure-flag-duplicate = [scarlet]⚠ Powtórzona flaga: '{ $flag }'.
argument-parse-failure-flag-duplicate-flag = [scarlet]⚠ Powtórzona flaga: '{ $flag }'.
argument-parse-failure-flag-no-flag-started = [scarlet]⚠ Nie rozpoczęto flagi. Nie wiadomo, co zrobić z '{ $input }'.
argument-parse-failure-flag-missing-argument = [scarlet]⚠ Brak argumentu dla flagi: '{ $flag }'.
argument-parse-failure-flag-no-permission = [scarlet]⚠ Nie masz uprawnień do flagi '{ $flag }'.
argument-parse-failure-selector-syntax = [scarlet]⚠ Nieprawidłowy selektor '{ $input }': [lightgray]{ $reason }
argument-parse-failure-selector-no-such-target = [scarlet]⚠ Nic nie pasuje do '{ $input }'.
argument-parse-failure-selector-too-many-targets = [scarlet]⚠ '{ $input }' pasuje do kilku celów, a ta komenda potrzebuje jednego.
argument-parse-failure-selector-denied = [scarlet]⚠ Selektory są tu niedozwolone: [lightgray]{ $reason }
argument-parse-failure-selector-kind-not-allowed = [scarlet]⚠ Selektor '{ $kind }' jest tu niedozwolony.
argument-parse-failure-selector-sender-required = [scarlet]⚠ Selektora '{ $kind }' można użyć tylko w grze.
argument-parse-failure-selector-limit-exceeded = [scarlet]⚠ Selektor pasuje do { $count } celów, limit to { $limit }.
argument-parse-failure-team = [scarlet]⚠ Nie znaleziono drużyny '{ $input }'.
argument-parse-failure-content = [scarlet]⚠ '{ $input }' nie jest prawidłową wartością typu { $type }.
# ==============================================================================
# Button Status
# ==============================================================================
finished = zakończone
finished-neutral = [orange]Zakończone
finished-active = [green]Zakończone
finished-inactive = [red]Zakończone
major = Duże
major-neutral = [orange]Duże
major-active = [green]Duże
major-inactive = [red]Duże
active = Aktywne
active-neutral = [orange]Aktywne
active-active = [green]Aktywne
active-inactive = [red]Aktywne
admin = Admin
admin-neutral = [orange]Admin
admin-active = [green]Admin
admin-inactive = [red]Admin
player-leaderboard-active = [green]Ranking: włączony[]
player-leaderboard-inactive = [red]Ranking: wyłączony[]
# ==============================================================================
# Miscellaneous
# ==============================================================================
hours = godziny
days = dni
success = [green]Sukces
empty = [accent]Pusto
never = Nigdy
save = Zapisz
close = [scarlet]Zamknij
previous = [accent]« Poprzednia
next = [accent]Następna »
cancel = Anuluj
back = Wstecz
yes = Tak
no = Nie
test = Test
no-description = Brak opisu
discord = Discord
github = Github
donatello = Donatello
weblate = Weblate
discord-red-vs-blue = RedVSBlue
auto = Auto
on = Włączone
off = Wyłączone
error-command-disabled = [scarlet]⚠ Komenda [accent]/{ $command }[scarlet] jest wyłączona na tym serwerze.
error-feature-disabled = [scarlet]⚠ Ta funkcja jest wyłączona na tym serwerze.
none = Brak
unknown = Nieznane
error-nickname-badge-glyph = [scarlet]⚠ Własny nick nie może zawierać zarezerwowanych ikon odznak.

# Server browser (/servers)
player-servers-title = SIEĆ SERWERÓW XCORE
player-servers-cat-all = Wszystkie
player-servers-cat-pvp = PvP
player-servers-cat-survival = Przetrwanie
player-servers-cat-special = Specjalne
player-servers-hint = Kliknij kartę serwera, aby się połączyć
player-servers-refresh = Odśwież
player-servers-already-connected = [gold]● Jesteś już połączony z tym serwerem!
player-servers-transferring = [accent]Przenoszenie na serwer [white]{ $server }[]...
player-servers-full = [scarlet]Serwer { $server } jest pełny! Poczekaj na wolne miejsce.
player-servers-offline = [scarlet]Serwer { $server } jest obecnie offline.
player-servers-not-found = [scarlet]Nie znaleziono serwera '{ $server }'.
player-servers-empty-category = [lightgray]Brak dostępnych serwerów w tej kategorii.[]
player-servers-online-summary = [green]● { $players } [gray]gra[] [darkgray]|[] [sky]{ $servers } [gray]online[]
player-servers-badge-current = [gold]● JESTEŚ TUTAJ[]
player-servers-offline-badge = [darkgray]● OFFLINE[]
player-servers-capacity-full = [scarlet]● { $players }/{ $max } PEŁNY[]
player-servers-capacity-normal = [green]● { $players }/{ $max } { $bar }
player-servers-card-current = [lightgray]Jesteś połączony z tym serwerem[]
player-servers-card-wave = [darkgray]|[] [accent]Fala { $wave }[]
player-servers-card-empty = [sky]Bądź pierwszy! Rozpocznij grę[]
player-servers-card-map = [gray]Mapa:[] [white]{ $map }[]
player-servers-card-mode = [gray]Tryb:[] [white]{ $mode }[]

announcement-hub =
    [gold]★ [accent]Serwery XCore [lightgray]» [white]Nudzi cię ten mecz?
    [lightgray]Odwiedź inne serwery w każdej chwili za pomocą [accent]/hub[lightgray]!
announcement-discord =
    [gold]★ [accent]Społeczność XCore [lightgray]» [white]Szukasz drużyny i nowości?
    [lightgray]Dołącz do naszego serwera Discord za pomocą [accent]/discord[lightgray]!
announcement-help =
    [gold]★ [accent]Pomoc XCore [lightgray]» [white]Potrzebujesz pomocy lub listy komend?
    [lightgray]Wpisz [accent]/help[lightgray], aby zobaczyć wszystkie dostępne komendy!


error-only-players = [scarlet]⚠ To polecenie może użyć tylko gracz.

player-settings-username-editable = [gold]★ Nazwa użytkownika (nagroda za wydarzenie):[]
player-settings-username-hint = Wpisz unikalną nazwę użytkownika (4–32 znaki)...
player-settings-username-locked = [gray]Nazwa użytkownika:[] [accent]@{ $username }[] [darkgray](Zablokowana)[]
player-settings-username-none = [gray]Nazwa użytkownika: [darkgray]Nie ustawiono (wygraj wydarzenie, aby odblokować)[]

error-username-empty = Nazwa użytkownika nie może być pusta!
error-username-length = Nazwa użytkownika musi mieć od 4 do 32 znaków!
error-username-invalid-chars = Nazwa użytkownika może zawierać tylko litery łacińskie, cyfry i podkreślniki!
error-username-taken = Tę nazwę użytkownika zajął już inny gracz!

# ==============================================================================
# Permission nodes
# ==============================================================================
permission-mindustry-admin = Korzystać z wbudowanego menu admina w grze i pomijać fale
permission-xcore-moderation-mute = Wyciszać graczy
permission-xcore-moderation-unmute = Zdejmować wyciszenie z graczy
permission-xcore-moderation-kick = Wyrzucać graczy
permission-xcore-moderation-ban = Banować graczy
permission-xcore-moderation-unban = Odbanowywać graczy
permission-xcore-moderation-audit-others = Przeglądać historię moderacji innych graczy
permission-xcore-moderation-votekick-immune = Nie może zostać wyrzucony głosowaniem
permission-xcore-admin-tp = Teleportować graczy
permission-xcore-admin-broadcast = Wysyłać ogłoszenia do wszystkich
permission-xcore-admin-kill = Zabijać jednostki i graczy
permission-xcore-admin-heal = Leczyć jednostki i graczy
permission-xcore-admin-set-team = Zmieniać drużynę gracza
permission-xcore-maps-force-rtv = Zmieniać mapę bez głosowania
permission-xcore-maps-force-vnw = Pomijać falę bez głosowania
permission-xcore-votes-cancel = Anulować trwające głosowanie
permission-xcore-events-create-major = Tworzyć duże wydarzenia
permission-xcore-events-edit-others = Edytować wydarzenia innych graczy
permission-xcore-events-force-vote = Uruchamiać wydarzenie bez głosowania
permission-xcore-events-stop = Zatrzymywać trwające wydarzenie
permission-xcore-players-settings-others = Zmieniać ustawienia innych graczy
permission-xcore-players-private-info = Widzieć prywatne dane graczy, np. ich adresy IP
permission-xcore-bypass-playtime = Pomijać wymagania czasu gry dla komend
permission-xcore-permissions-inspect = Widzieć, kto ma jakie uprawnienia
permission-xcore-permissions-manage = Zmieniać role i uprawnienia

error-target-outranks = [scarlet]⚠ Nie możesz tego zrobić graczowi, którego rola nie jest niższa od twojej.
perm-me-legacy = [accent]Role są wyłączone na tym serwerze. Admin: [white]{ $admin }
perm-me-header = [accent]Twoje role tutaj (waga [white]{ $weight }[accent]):
perm-me-none = [lightgray] - brak
perm-me-logged-in = [green]Jesteś zalogowany jako członek administracji.
perm-me-not-logged-in = [yellow]Nie jesteś zalogowany jako członek administracji. Użyj [white]/login <hasło>[yellow].
perm-me-stale = [scarlet]Twoje uprawnienia administracji są wstrzymane: serwer nie mógł ich odświeżyć. Wrócą same.

help-ui-search-hint = Szukaj poleceń, aliasów lub opisów
help-ui-search-empty = [lightgray]Nie znaleziono pasujących poleceń.[]
