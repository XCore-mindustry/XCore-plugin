# ==============================================================================
# Terms
# ==============================================================================
-xcore = XCore-Server
# ==============================================================================
# General & Help
# ==============================================================================
menu-main = Hauptmenü
commands-main-description = Öffnet das interaktive Hauptmenü.
menu-main-title = [orange]{ -xcore } — Hauptmenü
menu-main-content = Hauptmenü des Servers
help-menu = Hilfemenü
commands-help-description = Öffnet das interaktive Hilfemenü.
help-menu-title = [orange]{ -xcore } — Befehle
help-menu-content =
    [gray]Seite [white]{ $page }[gray]/[white]{ $total }
    [lightgray]Wähle einen Befehl, um die genaue Verwendung zu sehen:
help-menu-button = [accent]/{ $command } [gray]» Beschreibung: [white]{ $description }
help-command-with-overload-count = { $name } von ({ $count })
help-command-title = [orange]» Name: [white]/{ $name }
help-command-header =
    [orange]» [accent]Syntax: [white]{ $syntax }
    [orange]» [accent]Info: [lightgray]{ $description }
help-aliases = [orange]» [accent]Aliase: [white]{ $aliases }
help-args-title = [orange]» [accent]Argumente:
help-usages-title = [orange]» [accent]Verwendung:
help-usage-entry = [gray]• [white]{ $syntax }
help-usage-args-title = [orange]» [accent]Für [white]{ $syntax }[accent]:
help-arg-entry = [gray]• [white]{ $arg } [lightgray]- { $description }
help-no-arguments = [gray]Keine weiteren Argumente erforderlich.
help-no-arg-description = Keine Beschreibung.
help-no-description = Für diesen Befehl gibt es keine Beschreibung.
help-legacy-command-content =
    [orange]» [accent]Befehl: [white]/{ $name }
    [orange]» [accent]Parameter: [white]{ $params }
    [orange]» [accent]Info: [lightgray]{ $description }
    { "" }
    [gray](Das ist ein älterer Befehl mit eingeschränkten Infos)
help-legacy-command-content-no-params =
    [orange]» [accent]Befehl: [white]/{ $name }
    [orange]» [accent]Info: [lightgray]{ $description }
    { "" }
    [gray](Das ist ein älterer Befehl mit eingeschränkten Infos)
help-back = [lightgray]« Zurück

# ==============================================================================
# Modern Reactive Help & Commands Guide (xcore-ui)
# ==============================================================================
help-ui-title = BEFEHLSÜBERSICHT
help-ui-summary = [gray]{ $count } Befehle verfügbar
help-ui-empty-category = [lightgray]In dieser Kategorie gibt es keine Befehle.[]
help-ui-overloads = ({ $count } Varianten)
help-ui-aliases = [lightgray]Aliase:[] { $aliases }
help-ui-syntax-title = Syntax
help-ui-args-title = Parameter
help-ui-arg-required = [scarlet]Erforderlich
help-ui-arg-optional = [sky]Optional
help-ui-btn-run = Ausführen
help-ui-btn-copy = In den Chat
help-ui-btn-back = Zurück
help-ui-copied = [accent]Befehl: [white]/{ $syntax }
help-ui-executed = [accent]Befehl wird ausgeführt: [white]/{ $syntax }

# Categories
help-cat-all = Alle
help-cat-general = Allgemein
help-cat-game = Spiel
help-cat-social = Chat
help-cat-votes = Abstimmungen
help-cat-admin = Admin
# ==============================================================================
# Command Argument Descriptions
# ==============================================================================
# help
commands-help-page-description = Anzuzeigende Seitenzahl.
# login
commands-login-password-description = Dein Admin-Passwort.
# ban
commands-ban-id-description = ID des zu sperrenden Spielers.
commands-ban-period-description = Dauer der Sperre (z. B. 1d, 2h, 30m).
commands-ban-reason-description = Grund für die Sperre.
# unban
commands-unban-id-description = ID des zu entsperrenden Spielers.
# mute
commands-mute-id-description = ID des stummzuschaltenden Spielers.
commands-mute-period-description = Dauer der Stummschaltung (z. B. 1h, 30m).
commands-mute-reason-description = Grund für die Stummschaltung.
# unmute
commands-unmute-id-description = ID des Spielers, dessen Stummschaltung aufgehoben wird.
# votekick
commands-votekick-target-description = Zu kickender Spieler (ID oder Name).
commands-votekick-reason-description = Grund für den Kick.
# vote
commands-vote-choice-description = Deine Stimme: y (ja), n (nein) oder c (abbrechen, nur Admins).
# t (team chat)
commands-t-message-description = Nachricht an deine Teammitglieder.
# g (global chat)
commands-g-message-description = Nachricht an alle Server.
# tr (translator)
commands-tr-language-description = Sprachcode, 'auto' oder 'off'.
# stats
commands-stats-id-description = ID des Spielers, dessen Statistik angezeigt wird
# rank
commands-rank-player-description = Spieler, dessen Rang angezeigt wird
# map
commands-map-map-description = Kartenname oder -index.
# maps / maps-text
commands-maps-page-description = Seitenzahl.
commands-maps-text-page-description = Seitenzahl.
# rtv / artv
commands-rtv-map-description = Karte, für die abgestimmt wird (optional).
commands-artv-map-description = Karte, zu der sofort gewechselt wird.
# ai
commands-ai-state-description = KI-Zustand: attack (a) oder idle (i).
# event / events
commands-events-page-description = Seitenzahl.
# ==============================================================================
# General & Help (continued)
# ==============================================================================
commands-information-description = Zeigt Informationen über den Server.
commands-info = Information
commands-info-title = [orange]{ -xcore } — Servername: [orange]{ $server-name }
commands-info-text =
    [accent]XCore[white] ist ein [cyan]kostenloser[white] Server zum Spielen von [accent]Mindustry[white].
    { "" }
    XCore-Version — [accent]{ $version }[white]
commands-sync-description = Synchronisiert dein Spiel mit dem Server. Hilft bei Fehlern wie Geistereinheiten.
commands-discord-description = Leitet dich zum Discord-Server weiter.
discord-menu-title = [orange]{ -xcore } — Discord
discord-menu-content =
    [white]Hier verwaltest du deine Discord-Verknüpfung.
    { "" }
    [white]Status: { $status }
    [white]Server: [accent]{ $discordUrl }[]
discord-menu-open = Discord öffnen
discord-menu-link = Konto verknüpfen
discord-menu-status = Status aktualisieren
discord-menu-unlink = Verknüpfung aufheben
discord-menu-status-not-linked = [lightgray]nicht verknüpft[]
discord-menu-status-linked = [green]{ $discordUsername }[] [gray]({ $discordId })[]
discord-link-menu-title = [orange]{ -xcore } — Discord-Konto verknüpfen
discord-link-menu-content =
    [white]Führe auf unserem Discord-Server diesen Slash-Befehl des Bots aus:
    { "" }
    [accent]/link { $code }[]
    { "" }
    [white]Läuft ab in: [accent]{ $expireMinutes }[] Min.
    [white]Discord: [accent]{ $discordUrl }[]
discord-link-menu-refresh = Code aktualisieren
discord-link-menu-copy = Code kopieren
discord-link-menu-regenerate = Neuen Code erzeugen
discord-link-menu-status = Zurück zum Discord-Menü
welcome =
    [accent]Willkommen auf { $serverName }!
    [lightgray]Gib [accent]/help[lightgray] ein, um eine Liste der Befehle zu sehen
    [lightgray]Gib [accent]/vote [gray]<y/n>[lightgray] ein, um über einen Kick abzustimmen
    [lightgray]Gib [accent]/votekick [gray]<ID/Name> <Grund…>[lightgray] ein, um eine Kick-Abstimmung zu starten
    [lightgray]Gib [accent]/t [gray]<Nachricht…>[lightgray] ein, um deinem Team zu schreiben
    [lightgray]Gib [accent]/g [gray]<Nachricht…>[lightgray] ein, um allen Servern zu schreiben
    [lightgray]Gib [accent]/tr [gray]<Sprache/auto>[lightgray] ein, um den Übersetzer einzuschalten
    [lightgray]Gib [accent]/discord[lightgray] ein, um das Discord-Menü zu öffnen und dein Konto zu verknüpfen
# ==============================================================================
# Chat & Social
# ==============================================================================
commands-t-description = Sendet eine Nachricht nur an dein Team.
commands-t-chat = [{ "#" }{ $color }][Team] [coral]> { $badge }[accent]{ $name }[lightgray]: [white]{ $message }
commands-g-description = Sendet eine Nachricht an alle Server.
commands-a-description = Sendet eine Nachricht nur an Admins.
commands-msg-description = Sendet einem Spieler eine private Nachricht.
commands-msg-id-description = Spieler-ID.
commands-msg-message-description = Text der privaten Nachricht.
commands-reply-description = Antwortet dem letzten Spieler in den privaten Nachrichten.
commands-reply-message-description = Text der Antwort.
commands-inbox-description = Öffnet das Menü der privaten Nachrichten.
commands-inbox-id-description = Spieler-ID.
commands-tr-description = Legt die Sprache des Übersetzers fest.
commands-badge-description = Öffnet das Abzeichenmenü und verwaltet dein aktives Abzeichen.
commands-tr-success = [accent]Die Sprache des Übersetzers wurde auf [grey]{ $translatorLanguage }[] geändert!
commands-tr-off = [accent]Der Übersetzer ist [scarlet]aus[]!
commands-tr-not-found = [scarlet]⚠ Diese Sprache gibt es nicht.
discord-chat-format = [#5865F2][DISCORD][] [lightgray]| [accent]{ $author }[lightgray] >> [white]{ $message }
global-chat-format = [royal][[[orange]GLOBAL [lightgray](von [accent]{ $server }[])[] { $author }[]]: [white]{ $message }
private-message-received = [sky][PN][] [lightgray]von [accent]{ $author } [gray]#{ $pid }[lightgray]: [white]{ $message }
private-message-sent = [sky][PN][] [lightgray]an [accent]{ $target } [gray]#{ $pid }[lightgray]: [white]{ $message }
private-message-unread-count =
    [accent]Du hast [white]{ $count }[accent] { $count ->
        [one] ungelesene private Nachricht
       *[other] ungelesene private Nachrichten
    }.
private-message-join-notification =
    [accent]Du hast [white]{ $count }[accent] { $count ->
        [one] ungelesene private Nachricht
       *[other] ungelesene private Nachrichten
    }. Öffne sie mit [white]/inbox[accent].
private-message-block-success = [accent]Private Nachrichten von [white]{ $target } [gray]#{ $pid }[accent] sind jetzt blockiert.
private-message-block-already = [lightgray]Private Nachrichten von [white]{ $target } [gray]#{ $pid }[lightgray] sind bereits blockiert.
private-message-unblock-success = [accent]Private Nachrichten von [white]{ $target } [gray]#{ $pid }[accent] sind nicht mehr blockiert.
private-message-unblock-missing = [lightgray][white]{ $target } [gray]#{ $pid }[lightgray] ist nicht blockiert.
private-message-menu-title = [orange]{ -xcore } — Private Nachrichten
private-message-menu-content =
    [white]Seite [green]{ $page }[] von [green]{ $total }[]
    [white]Ungelesen: [accent]{ $unread }[]
private-message-menu-empty = [lightgray]Dein Posteingang ist leer.
private-message-menu-entry-unread = [accent]Ungelesen[] [white]{ $author } [gray]#{ $pid }[] [lightgray]({ $time })[]: [white]{ $message }
private-message-menu-entry-read = [gray]Gelesen[] [white]{ $author } [gray]#{ $pid }[] [lightgray]({ $time })[]: [white]{ $message }
private-message-details-title = [orange]{ -xcore } — Nachricht
private-message-details-content =
    [white]Von: [accent]{ $author } [gray]#{ $pid }[]
    [white]Zeit: [accent]{ $time }[]
    [white]Status: [accent]{ $status }[]
    { "" }
    [white]{ $message }
private-message-status-unread = ungelesen
private-message-status-read = gelesen
private-message-blocked-title = [orange]{ -xcore } — Blockierte Spieler
private-message-blocked-content =
    [white]Seite [green]{ $page }[] von [green]{ $total }[]
    [white]Blockiert: [accent]{ $count }[]
private-message-blocked-empty = [lightgray]Du hast keine blockierten Spieler.
private-message-blocked-entry = [white]{ $target } [gray]#{ $pid }[]
private-message-compose = Neue Nachricht
private-message-blocked = Blockiert
private-message-block = Absender blockieren
private-message-unblock = Absender entblocken
private-message-reply-title = Antworten
private-message-reply-message = Gib eine Nachricht für [accent]#{ $pid }[] ein
private-message-compose-target-title = Neue Nachricht
private-message-compose-target-message = Gib die Spieler-ID im Format [accent]#123[] ein
private-message-compose-body-title = Nachrichtentext
private-message-compose-body-message = Gib eine private Nachricht für [accent]{ $pid }[] ein
# ==============================================================================
# Authentication & Admin Access
# ==============================================================================
commands-login-description = Aktiviert Admin-Rechte, wenn dein verknüpftes Discord-Konto bereits Zugriff hat.
commands-login-incorrect-password = [scarlet]⚠ Falsches Passwort!
commands-login-success = [green]Admin-Rechte erteilt.
commands-login-confirmed = [green]Discord-Adminzugriff bestätigt.
commands-login-admin-password-created =
    [green]Admin-Passwort erstellt.
    [red]Vergiss dein Passwort nicht! Sonst musst du einen Hauptadministrator bitten, es zurückzusetzen.
commands-login-request-approval-discord = [accent]Dein Konto hat keinen Discord-Adminzugriff. Hol dir die Admin-Rolle auf Discord und versuche es erneut.
commands-login-verifying = [lightgray]Admin-Passwort wird überprüft...
commands-login-already-processing = [scarlet]⚠ Eine Anmeldeanforderung wird bereits verarbeitet. Bitte warten.
commands-login-rate-limited = [scarlet]⚠ Zu viele fehlgeschlagene Anmeldeversuche. Bitte warten Sie, bevor Sie es erneut versuchen.
commands-discord-link-created =
    [green]Discord-Verknüpfungscode erstellt: [accent]{ $code }[]
    [lightgray]Führe auf unserem Discord-Server innerhalb von [accent]{ $expireMinutes }[] Min. den Bot-Befehl [accent]/link { $code }[] aus.
    [cyan]{ $discordUrl }
commands-discord-link-confirmed = [green]Discord-Konto verknüpft: [accent]{ $discordUsername }[]
commands-discord-link-already-linked = [lightgray]Dieses Mindustry-Konto ist bereits verknüpft. Nutze [accent]/discord status[] oder [accent]/discord unlink[].
commands-discord-link-error = [scarlet]Discord-Verknüpfungscode konnte nicht erstellt werden. Versuche es später erneut.
commands-discord-status-not-linked = [lightgray]Dein Konto ist nicht mit Discord verknüpft.
commands-discord-status-linked = [green]Verknüpftes Discord: [accent]{ $discordUsername }[] [gray]({ $discordId })[]
commands-discord-unlink-not-linked = [lightgray]Dein Konto ist nicht mit Discord verknüpft.
commands-discord-unlink-success = [green]Discord-Verknüpfung entfernt.
commands-logout-description = Abmelden. Dadurch werden deine [scarlet]Admin-Rechte entzogen.
commands-logout-successful = [green]Admin-Rechte entzogen.
# ==============================================================================
# Moderation (Ban, Mute, Kick)
# ==============================================================================
commands-ban-description = Sperrt einen Spieler.
commands-ban-success = { $nickname } [scarlet]gesperrt
commands-unban-description = Hebt die Sperre eines Spielers auf.
commands-unban-success = { $nickname }[accent] #{ $pid } [green]wurde entsperrt.
commands-mute-description = Schaltet einen Spieler stumm.
commands-mute-success = [accent]{ $nickname } wurde stummgeschaltet
commands-unmute-description = Hebt die Stummschaltung eines Spielers auf.
commands-unmute-success = [green]Stummschaltung aufgehoben für []{ $nickname }
commands-alert-description = Zeigt ausgewählten oder allen Spielern ein auffälliges Ankündigungsbanner.
commands-toast-description = Zeigt ausgewählten Spielern eine Warnmeldung als Toast.
commands-announcement-description = Sendet eine regelmäßige Ankündigung nach Schlüssel oder die nächste in der Reihenfolge.
commands-audit-description = Zeigt den Verlauf und die Aktionen von Team und Moderation.
ban-content = [scarlet]⚠ Gesperrt[]
    [accent]{ $nickname }[white] — du bist dauerhaft von diesem Server gesperrt.
    [lightgray]Für einen Einspruch besuche den Discord-Kanal [gray]{ support-channel }[]:
    [cyan]{ $discordUrl }
ban-cancelled = [accent]Die Sperre von [scarlet]{ $nickname }[accent] wurde aufgehoben
tempban-content = [scarlet]⚠ Gesperrt[]
    [accent]{ $nickname }[white] — du bist vorübergehend von diesem Server gesperrt.
    { "" }
    [orange]» [accent]Admin: [white]{ $adminName }
    [orange]» [accent]Grund: [gold]{ $reason }
    [orange]» [accent]Verbleibend: { DURATION($duration, style: "full", colored: "true", maxUnits: 2) }
    [orange]» [accent]Endet: [white]{ DATETIME($expireDate, dateStyle: "medium", timeStyle: "short") }
    { "" }
    [lightgray]Für einen Einspruch besuche den Discord-Kanal [gray]{ support-channel }[]:
    [cyan]{ $discordUrl }
tempban-player-banned = [scarlet] Admin { $adminName }[scarlet] hat den Spieler [gray]'[]{ $playerName }[gray]' gesperrt
you-are-muted-by =
    [orange]⚠ Chat eingeschränkt[]
    [lightgray]Du wurdest von Administrator [accent]{ $adminName }[lightgray] stummgeschaltet.
    [orange]» [accent]Grund: [gold]{ $reason }
    [orange]» [accent]Verbleibend: { DURATION($duration, style: "full", colored: "true", maxUnits: 2) }
you-are-muted =
    [orange]⚠ Chat eingeschränkt[]
    [lightgray]Solange die Stummschaltung aktiv ist, kannst du keine Nachrichten senden.
    [orange]» [accent]Admin: [white]{ $adminName }
    [orange]» [accent]Grund: [gold]{ $reason }
    [orange]» [accent]Verbleibend: { DURATION($duration, style: "full", colored: "true", maxUnits: 2) }
kick-pirated-game = [accent]Nicht autorisierter Client erkannt. [scarlet]Zugriff verweigert[]. Bitte spiele die [lime]offizielle[] Version von [blue]Steam[], [blue]Google Play[] oder [blue]itch.io[].
kick-recently-kicked =
    [accent]Du wurdest kürzlich von diesem Server gekickt.
    Warte [cyan]{ DURATION($remaining, style: "timer") }[accent], bevor du wieder beitrittst.
kick-admintools-outdated =
    [green]Benötigte AdminTools-Version: [grey]{ $requiredVersion }[]
    [scarlet]Deine AdminTools-Version: [grey]{ $version }[]
    { "" }
    [cyan]Bitte aktualisiere AdminTools, um diesem Server beizutreten.
support-channel = #reports-appeals
# ==============================================================================
# Voting (VoteKick)
# ==============================================================================
commands-votekick-description = Abstimmung, um einen Spieler vom Server zu kicken.
commands-vote-description = Stimme in der aktuellen Abstimmung ab.
commands-vote-vote-with = [scarlet]⚠ Stimme mit [orange]/vote <y/n/c>[scarlet] ab
votekick-vote =
    { $starter } [grey]#[white]{ $starterId }[lightgray] hat dafür gestimmt, { $target } [grey]#[white]{ $targetId }[lightgray] wegen [orange]{ $reason }[lightgray] zu kicken. ([accent]{ $votes }[]/[accent]{ $required }[])
    [lightgray]Gib [orange]/vote <y/n>[] ein, um abzustimmen.
votekick-left = { $player }[lightgray] ist gegangen. Die Stimme wurde zurückgezogen. ([accent]{ $votes }[]/[accent]{ $required }[])
votekick-fail = [lightgray]Abstimmung gescheitert. Nicht genug Stimmen, um { $target }[lightgray] zu kicken.
votekick-cancelled = [scarlet]Die Abstimmung zum Kick von { $target }[scarlet] wurde von { $admin } abgebrochen.
votekick-success =
    [orange]Abstimmung erfolgreich. { $target }[orange] wurde für [scarlet]{ $minutes }[] { $minutes ->
        [one] Minute
       *[other] Minuten
    } gekickt.
# ==============================================================================
# Maps & RTV
# ==============================================================================
commands-map-description = Statistik einer bestimmten Karte.
commands-map-title = [orange]{ -xcore } — Karte
commands-map-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $name }[] [gray]von [sky]{ $author }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Übersicht[]
    [gray]Größe: [white]{ $width }x{ $height } [darkgray]|[gray] Bewertungen: [lime]+{ $like } [darkgray]/[scarlet] -{ $dislike }[]
    { "" }
    [accent]■ Aktivität[]
    [gray]Insgesamt gespielt: [white]{ $played } [darkgray]|[gray] Dieses Jahr: [white]{ $playedYear }[]
    [gray]Zuletzt gespielt: [white]{ $lastPlayed }[]
    [gray]Beliebtheit: [white]{ $popularity } [darkgray]|[gray] Interesse: [white]{ $interest } [darkgray]|[gray] Ansehen: [white]{ $reputation }[]
    { "" }
    [accent]■ Spieldauer[]
    [gray]Min.: [white]{ $min } [darkgray]|[gray] Schnitt: [white]{ $avg } [darkgray]|[gray] Max.: [white]{ $max }[]
commands-maps-description = Liste aller Karten auf diesem Server.
commands-maps-title = [orange]{ -xcore } — Karten
commands-maps-content =
    [gray]Aktuelle Karte: [accent]{ $current }[]
    [white]Seite [green]{ $page }[] von [green]{ $total }[]
commands-maps-current-row = { $name } ★
commands-maps-text-description = Liste aller Karten auf diesem Server.
commands-maps-text-start-content =
    [accent]Aktuelle Karte: []{ $name }[white]
    [orange][gold]Kartenliste [lightgray]{ $page }[gray]/[lightgray]{ $total }
commands-maps-text-content =
    { "" }
    { $index }. [orange] - [white]{ $name }[orange] | [green]{ $reputation }[orange] | [white]{ $width }x{ $height }[orange] | [white]{ $lastPlayed }[orange] | Von: [sky]{ $author }
commands-artv-description = Wechselt die Karte sofort.
commands-artv-map-skipped = { $nickname }[accent] hat die Karte übersprungen. Nächste Karte: { $name }.
commands-artv-event-skipped = { $nickname }[accent] hat das Event übersprungen. Nächstes Event: { $name }.
commands-rtv-description = Abstimmung über einen Kartenwechsel.
commands-vnw-description = Abstimmung, um die nächste Welle früher zu starten.
commands-avnw-description = Startet die nächste Welle sofort.
commands-like-description = Stimme für die aktuelle Karte (erhöht ihr Ansehen).
commands-dislike-description = Stimme gegen die aktuelle Karte.
map-vote-title = [orange]{ -xcore } — [scarlet]SPIEL VORBEI!
map-vote-content =
    { "" }
    Nächste Karte: [accent]{ $mapName }[] von [accent]{ $author }[white].
    Das neue Spiel beginnt in [accent]{ $seconds }[white] { $seconds ->
        [one] Sekunde
       *[other] Sekunden
    }.
    { "" }
    [cyan]Hat dir diese Karte gefallen?
map-vote-like = [green]👍 Gefällt mir
map-vote-dislike = [red]👎 Gefällt mir nicht
map-vote-like-selected = [gray]Dir hat sie gefallen
map-vote-dislike-selected = [gray]Dir hat sie nicht gefallen
map-rtv = [orange]Abstimmung
map-artv = [red]Sofortiger Wechsel
map-maps = Karten
map-maps-back = Zurück zur Kartenliste
current-map = Aktuelle Karte
next-map = Nächste Karte

# Map UI Modernized
map-ui-search-hint = Karte oder Autor suchen...
map-ui-total = [lightgray]Karten: [white]{ $count }[]
map-ui-about-title = Über die Karte
map-ui-rtv-title = Kartenwechsel
map-ui-no-maps-found = [lightgray]Keine passenden Karten gefunden.[]
map-ui-by = von [lightgray]{ $author }[]
map-ui-mode = Modus: [white]{ $mode }[]
map-ui-loading = [gray]Wird geladen...[]
map-ui-no-preview = [gray]Keine Vorschau[]
map-ui-dimensions = [gray]Größe: [white]{ $width } x { $height }[]
map-ui-total-plays = [gray]Gespielt: [white]{ $played } [lightgray]({ $playedYear } dieses Jahr)[]
map-ui-last-played = [gray]Zuletzt gespielt: [white]{ $lastPlayed }[]
map-ui-description = [gray]Beschreibung: [lightgray]{ $description }[]
map-ui-no-description = [gray]Beschreibung: [lightgray]Keine Beschreibung vorhanden.[]

map-ui-col-duration = Dauer
map-ui-duration-min = [gray]Min.: [white]{ $value }[]
map-ui-duration-avg = [gray]Schnitt: [white]{ $value }[]
map-ui-duration-max = [gray]Max.: [white]{ $value }[]

map-ui-col-popularity = Beliebtheit
map-ui-popularity-score = [gray]Wertung: [white]{ $value }[]
map-ui-popularity-pop = [gray]Beliebtheit: [white]{ $value }[]
map-ui-popularity-interest = [gray]Interesse: [white]{ $value }[]

map-ui-col-community = Community
map-ui-community-approval = [gray]Zustimmung: [green]{ $rate }%[]

map-ui-btn-like = Gefällt mir ({ $count })
map-ui-btn-dislike = Gefällt mir nicht ({ $count })

map-ui-rtv-active-status = [accent]● Abstimmung läuft: [white]{ $votes }/{ $required }[] [gray](endet in [white]{ $seconds } s[gray])[]
map-ui-rtv-vote-yes = Für diese Karte stimmen
map-ui-rtv-start = Abstimmung für diese Karte starten
map-ui-admin-rtv = Karte jetzt wechseln
map-ui-admin-rtv-confirm = Zum Bestätigen erneut drücken

gamemode-survival = Überleben
gamemode-attack = Angriff
gamemode-pvp = PvP
gamemode-sandbox = Sandbox
gamemode-editor = Editor
rtv-vote =
    { $nickname }[lightgray] hat dafür gestimmt, die aktuelle Karte zu [orange]{ $mapName }[lightgray] zu wechseln. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Gib [orange]y[] oder [orange]n[] ein, um abzustimmen.
rtv-left = { $nickname }[lightgray] ist gegangen. Die Stimme für den Kartenwechsel wurde zurückgezogen. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
rtv-fail = [lightgray]Abstimmung gescheitert. Nicht genug Stimmen für den Wechsel zu [orange]{ $mapName }[].
rtv-success = [orange]Abstimmung erfolgreich. Die Karte [accent]{ $mapName }[] wird in [accent]{ $mapLoadDelay }[] { $mapLoadDelay ->
    [one] Sekunde
   *[other] Sekunden
} geladen…
rtv-cancelled = [lightgray]Die Abstimmung für den Wechsel zu [orange]{ $mapName }[lightgray] wurde von { $admin } abgebrochen.
vnw-vote =
    { $nickname }[lightgray] hat dafür gestimmt, Welle [orange]{ $wave }[lightgray] früher zu starten. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Gib [orange]y[] oder [orange]n[] ein, um abzustimmen.
vnw-left = { $nickname }[lightgray] ist gegangen. Die Stimme für den früheren Start von Welle [orange]{ $wave }[lightgray] wurde zurückgezogen. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
vnw-fail = [lightgray]Abstimmung gescheitert. Nicht genug Stimmen, um Welle [orange]{ $wave }[] früher zu starten.
vnw-success = [orange]Abstimmung erfolgreich. Welle [accent]{ $wave }[] startet jetzt.
vnw-cancelled = [lightgray]Die Abstimmung über den früheren Start von Welle [orange]{ $wave }[lightgray] wurde von { $admin } abgebrochen.
vnw-obsolete = [lightgray]Welle [orange]{ $wave }[lightgray] hat bereits begonnen, das Abstimmungsergebnis wird nicht mehr gebraucht.
# ==============================================================================
# Statistics & Ranks & Players
# ==============================================================================
commands-player-description = Zeigt die Statistik eines Spielers.
commands-settings-description = Öffnet deine Spielereinstellungen.
player-menu-player = Spieler
player-menu-player-title = [orange]{ -xcore } — Spielerstatistik
player-menu-player-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $customNickname }[] [gray]#{ $pid }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Profil[]
    [gray]Name: [white]{ $nickname } [darkgray]|[gray] Admin: [lime]{ $admin }[]
    [gray]Abzeichen: [white]{ $activeBadge } [darkgray]|[gray] System: [coral]{ $systemBadge }[]
    [gray]Dabei seit: [white]{ $accountCreated }[]
    { "" }
    [accent]■ Spielwertungen[]
    [gray]Spielzeit: [white]{ $totalPlayTime }[]
    [gray]MiniPvP: [sky]{ $pvpRating } [darkgray]|[gray] Altes Hexed: [sky]{ $hexedRankName } [gray]({ $hexedPoints } Pkt.) [darkgray]|[gray] Top: [accent]{ $hexedTopRank }[]
    { "" }
    [accent]■ Partien: [white]{ $gamesPlayed } [gray]Spiele [darkgray]|[lime] { $gamesWon } [gray]Siege [darkgray]|[sky] { $winRate }% [gray]Siegquote[]
    [gray]• [white]PvP: { $pvpSummary }[]
    [gray]• [white]Überleben: { $survivalSummary }[]
    [gray]• [white]Altes Hexed: { $hexedSummary }[]
    { "" }
    [accent]■ Kampfeffizienz[]
    [gray]Blöcke (Gebaut/Abgebaut/Zerstört): [lime]{ $blocksBuilt } [darkgray]/ [orange]{ $blocksDeconstructed } [darkgray]/ [scarlet]{ $blocksDestroyed }[]

# Modern Player Stats UI
player-stats-title = [orange]{ -xcore } — Spielerprofil
player-stats-tab-overview = Übersicht
player-stats-tab-stats = Statistik
player-stats-tab-matches = Partien
player-stats-tab-blocks = Blöcke
player-stats-tab-players = Online ({ $count })

player-stats-status-online = [lime]● Online[]
player-stats-status-offline = [gray]○ Offline[]
player-stats-no-bio = [gray]Noch keine Beschreibung.[]
player-stats-loading = [lightgray]Daten werden geladen...[]
player-stats-no-stats = [gray]Noch keine Spieldaten erfasst.[]
player-stats-filter-all = Filter: Alle
player-stats-filter-admins = Filter: Nur Admins
player-stats-filter-non-admins = Filter: Keine Admins
player-stats-refresh = Aktualisieren
player-stats-combat-efficiency = Kampf- und Bauleistung
player-stats-waves-summary = [gray]Wellen: max. [lime]{ $best }[], Schnitt [white]{ $avg }[]
player-stats-hexed-top-placement = [gray]beste [accent]#{ $best }[], Top 3: [sky]{ $top3 }[]
player-stats-max-rank = [gold]★ HÖCHSTER RANG ERREICHT ★[]
player-stats-max-league = [gold]★ HÖCHSTE LIGA ERREICHT ★[]
player-stats-hexed-wins-left = [lightgray]Noch { $wins } Siege bis [white]{ $rank }[]
player-stats-league-elo-left = [lightgray]Noch { $elo } ELO bis [white]{ $league }[]
player-stats-ratio-legend = [lightgray]Blockverhältnis[]

player-stats-account-created = [gray]Dabei seit:[]
player-stats-play-time = [gray]Spielzeit:[]
player-stats-legacy-pvp-rating = [gray]Altes PvP:[]
player-stats-hexed-rank = [gray]Altes Hexed:[]
player-stats-hexed-leaderboard = [gray]Hexed-Top:[]

player-stats-total-games = [gray]Spiele gesamt:[]
player-stats-victories = [gray]Siege:[]
player-stats-survival-summary = [gray]Überleben:[]
player-stats-hexed-summary = [gray]Altes Hexed:[]

player-stats-blocks-built = [gray]Gebaute Blöcke:[]
player-stats-blocks-deconstructed = [gray]Abgebaut:[]
player-stats-blocks-destroyed = [gray]Zerstört:[]
player-stats-units-produced = [gray]Gebaute Einheiten:[]
player-stats-units-lost = [gray]Verlorene Einheiten:[]

player-stats-games-played-value = [white]{ $count }[] [gray]Spiele[]
player-stats-victories-value = [lime]{ $wins }[] [gray]Siege[]  [darkgray]|[]  [sky]{ $winRate }%[] [gray]Siegquote[]
player-stats-hexed-points = [gray]({ $points } Pkt.)[]

player-stats-btn-settings = Einstellungen
player-stats-btn-matches = Matches
player-stats-btn-audit = Verlauf
player-stats-btn-players = Online
player-stats-btn-close = Schließen
player-stats-admin-tag = [coral]<Admin>[]
player-menu-players = Spieler online
player-menu-players-title = [orange]{ -xcore } — Spieler online
player-menu-players-content = [white]Seite [green]{ $page }[] von [green]{ $total }[]
player-menu-players-empty = Keine Spieler online
player-menu-players-row = [white]{ $nickname } [gray](PID: { $pid })[]
player-menu-settings = Einstellungen
player-menu-settings-title = [orange]{ -xcore } — Spielereinstellungen
player-menu-settings-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $displayNickname }[] [gray]#{ $pid }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Profil[]
    [gray]Name: [white]{ $nickname } [darkgray]|[gray] Angezeigt: [lime]{ $customNickname }[]
    [gray]Abzeichen: [white]{ $activeBadge } [darkgray]|[gray] System: [coral]{ $systemBadge }[]
    { "" }
    [accent]■ Sichtbarkeit[]
    [gray]Bestenliste: [white]{ $leaderboard }[]
    { "" }
    [accent]■ Chat[]
    [gray]Global: [white]{ $globalChat } [darkgray]|[gray] Discord: [white]{ $discordRelay }[]
    [gray]Übersetzer: [white]{ $translatorLanguage }[]
    { "" }
    [accent]■ Sprache[]
    [gray]Sprache: [white]{ $language }[]
player-menu-settings-chat = Chat-Einstellungen
player-menu-settings-chat-title = [orange]{ -xcore } — Chat-Einstellungen

# Modern Player Settings Form
player-settings-tab-profile = Profil
player-settings-tab-chat = Chat
player-settings-tab-badges = Abzeichen
player-settings-chat-preview = Chat-Vorschau
player-settings-chat-preview-sample = Beispiel
player-settings-chat-preview-message = Hallo Welt!
player-settings-symbol-color-mode = Symbolfarbe
player-settings-badges-my = Meine Abzeichen
player-settings-badges-all = Alle Abzeichen
player-settings-badge-equip = Anlegen
player-settings-badge-unequip = Ablegen
player-settings-badge-preview = Vorschau
player-settings-badge-previewing = In der Vorschau
player-settings-translator-lang = Chat-Übersetzer
player-settings-translator-off = Aus
player-settings-badges-empty = Du hast noch keine Abzeichen freigeschaltet.
player-settings-manage-badges = Abzeichen verwalten
player-settings-global-chat = Globaler Chat
player-settings-discord-relay = Discord-Weiterleitung
player-settings-leaderboard = Bestenliste anzeigen
player-settings-language = Sprache
player-settings-saved = [lime]Einstellungen gespeichert![]
player-settings-reset-feedback = [lightgray]Eigener Name zurückgesetzt.[]
player-settings-edit-badges = [accent]Bearbeiten[]
player-settings-tab-language = Sprache
player-settings-identity = Name und Beschreibung
player-settings-interface = Bildschirm
player-settings-leaderboard-hint = Die Liste der besten Spieler über dem Spiel. Funktioniert in Mini-PvP.
player-settings-global-chat-hint = Nachrichten von Spielern auf den anderen XCore-Servern.
player-settings-discord-relay-hint = Nachrichten aus dem Discord-Kanal des Servers im Spielchat.
player-settings-translator-hint = Nachrichten anderer Spieler werden in die gewählte Sprache übersetzt.
player-settings-language-hint = Die Sprache der Menüs und Servernachrichten. Auto folgt der Sprache deines Spiels.
player-menu-settings-chat-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [accent]■ Chat-Sichtbarkeit[]
    [gray]Globaler Chat: [white]{ $globalChat }[]
    [gray]Discord-Weiterleitung: [white]{ $discordRelay }[]
    { "" }
    [accent]■ Übersetzung[]
    [gray]Sprache des Übersetzers: [white]{ $translatorLanguage }[]
player-menu-settings-translator-title = [orange]{ -xcore } — Sprache des Übersetzers wählen
player-menu-settings-language-title = [orange]{ -xcore } — Sprache wählen
player-menu-settings-customNickname = Name bearbeiten
player-menu-settings-customNickname-title = [orange]{ -xcore } — Name bearbeiten
player-menu-settings-customNickname-message = [lightgray]Leer lassen zum Zurücksetzen
player-menu-settings-customNickname-reset = [scarlet]Name zurücksetzen
player-menu-settings-description = Beschreibung bearbeiten
player-menu-settings-description-title = [orange]{ -xcore } — Beschreibung bearbeiten
player-menu-settings-badges = Abzeichen
player-menu-settings-global-chat-on = [green]Globaler Chat
player-menu-settings-global-chat-off = [red]Globaler Chat
player-menu-settings-discord-relay-on = [green]Discord-Weiterleitung
player-menu-settings-discord-relay-off = [red]Discord-Weiterleitung
audit-menu-open = Verlauf
audit-menu-actions-open = Aktionen
audit-menu-history-title = [orange]{ -xcore } — Verlauf
audit-menu-history-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $player }[] [gray]#{ $pid }[]
    [gray]Angezeigte Einträge: [accent]{ $entriesShown }[]
    [gray]{ $pageState } [darkgray]|[] { $nextState }
    [lightgray]{ $hint }[]
audit-menu-history-page-first = Neueste Einträge
audit-menu-history-page-older = Ältere Einträge
audit-menu-tab-sanctions = Erhaltene Strafen
audit-menu-tab-actions = Durchgeführte Aktionen
audit-menu-filter-all = Alle
audit-menu-filter-bans = Sperren
audit-menu-filter-mutes = Stummschaltungen
audit-menu-filter-warns = Verwarnungen
audit-menu-filter-other = Sonstiges
audit-menu-btn-back = Zurück zum Verlauf
audit-menu-btn-copy-id = ID kopieren
audit-menu-copy-id-success = Die ID des Eintrags wurde in den Chat gesendet
audit-menu-field-id = Eintrags-ID
audit-menu-page = Seite { $page }
audit-menu-details-unavailable = Der Eintrag ist nicht verfügbar.
audit-menu-field-target = Spieler
audit-menu-field-actor = Durchgeführt von
audit-menu-field-server = Server
audit-menu-field-reason = Grund
audit-menu-section-time = Zeit
audit-menu-field-occurred = Wann
audit-menu-field-duration = Dauer
audit-menu-field-expires = Endet
audit-menu-btn-revoke = Strafe aufheben
audit-menu-revoke-success = Strafe aufgehoben.
audit-menu-status-active = AKTIV
audit-menu-status-expired = ABGELAUFEN
audit-menu-status-permanent = DAUERHAFT
audit-menu-history-more = Weitere Einträge vorhanden
audit-menu-history-end = Ende des Verlaufs
audit-menu-history-empty = Für diesen Spieler gibt es noch keine Einträge.
audit-menu-history-hint = Wähle unten einen Eintrag, um Details zu sehen.
audit-menu-actions-title = [orange]{ -xcore } — Aktionen
audit-menu-actions-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $player }[] [gray]#{ $pid }[]
    [gray]Angezeigte Einträge: [accent]{ $entriesShown }[]
    [gray]{ $pageState } [darkgray]|[] { $nextState }
    [lightgray]{ $hint }[]
audit-menu-actions-empty = Für diesen Spieler gibt es noch keine Aktionen.
audit-menu-actions-hint = Wähle unten einen Eintrag, um zu sehen, was dieser Spieler getan hat.
audit-menu-summary-row = [accent]{ $action }[] [darkgray]•[] [white]{ $actor }[] [gray]— { $reason }
audit-menu-action-summary-row = [accent]{ $action }[] [darkgray]•[] [white]{ $target }[] [gray]— { $reason }
audit-menu-details-title = [orange]{ -xcore } — Eintragsdetails
audit-menu-details-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $player }[] [gray]#{ $pid }[]
    { "" }
    [accent]■ Ereignis[]
    [gray]Aktion: [white]{ $action }[]
    [gray]Ausgeführt von: [white]{ $actor }[]
    [gray]Grund: [white]{ $reason }[]
    { "" }
    [accent]■ Zeit[]
    [gray]Zeitpunkt: [white]{ $occurredAt }[]
    [gray]Dauer: [white]{ $duration }[]
    [gray]Endet: [white]{ $expiresAt }[]
    { "" }
    [accent]■ Metadaten[]
    [gray]Eintrags-ID: [white]{ $auditId }[]
audit-menu-unknown-actor = Unbekannt
audit-menu-unknown-target = Unbekannt
audit-menu-reason-unspecified = Nicht angegeben
audit-menu-duration-permanent = Dauerhaft
audit-menu-action-ban = Sperre
audit-menu-action-unban = Entsperrung
audit-menu-action-mute = Stummschaltung
audit-menu-action-unmute = Aufhebung der Stummschaltung
audit-menu-action-warn = Verwarnung
audit-menu-action-kick = Kick
audit-menu-action-note = Notiz
audit-menu-action-quarantine = Quarantäne
audit-menu-action-unquarantine = Aufhebung der Quarantäne
player-menu-player-max-rank = Höchster Rang erreicht
player-menu-player-hexed-progress = [gray]Benötigte Siege für [white]{ $nextRankName }[gray]: [accent]{ $requiredPoints }[]
player-menu-player-no-mode-stats = [gray]keine Daten[]
player-menu-player-pvp-summary = [gray]Spiele [white]{ $gamesPlayed }[], Siege [lime]{ $gamesWon }[], [sky]{ $winRate }%[]
player-menu-player-survival-summary = [gray]Wellen: max. [lime]{ $bestWave }[], Schnitt [white]{ $averageWave }[] [gray](Runden: { $gamesPlayed })[]
player-menu-player-hexed-summary = [gray]Partien [white]{ $gamesPlayed }[], Platz 1 [lime]{ $gamesWon }[], bester Platz [accent]#{ $bestPlacement }[]
player-menu-time-days = { $value } T.
player-menu-time-hours = { $value } Std.
player-menu-time-minutes = { $value } Min.
settings-language-label = Sprache: [green]{ $lang }[]
settings-translator-label = Übersetzer: [green]{ $lang }[]
badge-menu-title = [orange]{ -xcore } — Abzeichen
badge-menu-content =
    [white]System-Abzeichen: [green]{ $systemBadge }[]
    [white]Aktives Abzeichen: [green]{ $activeBadge }[]
    [white]Symbolfarbe: [green]{ $symbolColorMode }[]
badge-menu-empty = [lightgray]Du hast noch keine Abzeichen freigeschaltet.
badge-menu-row = [white]{ $badge }[] [gray]-[] { $description }
badge-menu-symbol-color-button = Symbolfarbe: [green]{ $mode }[]
badge-menu-symbol-color-title = [orange]{ -xcore } — Symbolfarbe des Abzeichens
badge-menu-symbol-color-content =
    [white]Aktueller Modus: [green]{ $mode }[]
    [lightgray]Wähle, wie das Symbol des Abzeichens gefärbt wird.
badge-menu-symbol-color-default = Standardfarbe des Abzeichens
badge-menu-symbol-color-player-color = Spielerfarbe übernehmen
badge-menu-view-all = Alle Abzeichen anzeigen
badge-menu-all-title = [orange]{ -xcore } — Alle Abzeichen
badge-menu-all-content = [lightgray]Alle Abzeichen mit Status und Beschreibung.
badge-menu-all-row = [white]{ $badge }[] [gray]-[] [accent]{ $state }[] [gray]-[] { $description }
badge-clear-button = Aktives Abzeichen entfernen
badge-state-system = System
badge-state-system-active = System, aktiv
badge-state-active = Aktiv
badge-state-unlocked = Freigeschaltet
badge-state-locked = Gesperrt
badge-set-success = [accent]Aktives Abzeichen: [green]{ $badge }[].
badge-clear-success = [accent]Aktives Abzeichen entfernt.
badge-grant-success = [accent][green]{ $badge }[] an [green]{ $nickname }[][gray]#{ $pid }[] vergeben.
badge-revoke-success = [accent][green]{ $badge }[] von [green]{ $nickname }[][gray]#{ $pid }[] entzogen.
badge-already-unlocked = [scarlet]⚠ Das Abzeichen [accent]{ $badge }[scarlet] ist bereits freigeschaltet.
badge-not-owned = [scarlet]⚠ Der Spieler besitzt das Abzeichen [accent]{ $badge }[scarlet] nicht.
error-badge-not-found = [scarlet]⚠ Das Abzeichen [accent]{ $badge }[scarlet] wurde nicht gefunden.
error-badge-not-unlocked = [scarlet]⚠ Das Abzeichen [accent]{ $badge }[scarlet] ist nicht freigeschaltet.
error-badge-not-selectable = [scarlet]⚠ Das Abzeichen [accent]{ $badge }[scarlet] kann nicht manuell gewählt werden.
badge-admin-name = Admin
badge-admin-description = Automatisches Abzeichen für Administratoren.
badge-developer-name = Entwickler
badge-developer-description = Für die Entwickler von XCore.
badge-translator-name = Übersetzer
badge-translator-description = Für alle, die XCore übersetzen.
badge-map-maker-name = Kartenbauer
badge-map-maker-description = Für die Ersteller von Karten, die auf dem Server laufen.
badge-contributor-name = Unterstützer
badge-contributor-description = Für Beiträge zu XCore oder seiner Community.
badge-bug-finder-name = Fehlerfinder
badge-bug-finder-description = Für regelmäßige, hochwertige Fehlerberichte.
badge-event-winner-name = Event-Sieger
badge-event-winner-description = Für die Gewinner besonderer Server-Events.
badge-veteran-name = Veteran
badge-veteran-description = Für langjährige, geschätzte Spieler.
badge-season-champion-name = Saisonmeister
badge-season-champion-description = Für eine Platzierung auf dem Podest einer Wertungssaison.
commands-lb-description = Bestenliste ein-/ausschalten.
commands-lb-success =
    { $leaderboardEnabled ->
        [true] [accent]Bestenliste [green]eingeschaltet.
       *[other] [accent]Bestenliste [scarlet]ausgeschaltet.
    }
leaderboard = [blue]Bestenliste
commands-observer-description = Wechselt in den Zuschauermodus. Deine aktuelle Einheit wird entfernt und du kommst ins Zuschauerteam.
commands-rank-description = Zeigt deinen Rang oder den eines anderen Spielers.
commands-rank-content =
    { $nickname }
    { $rankTag } [accent]{ $rankName }
    [gold]Siege: { $points }/{ $requiredPoints }
commands-ranks-description = Zeigt Informationen über die Ränge.
commands-ranks-content =
    { $rankTag } [accent]{ $rankName }
    [gold]Voraussetzung: [grey]{ $requiredPoints } [accent]Siege[]
commands-ranks-footer = Siege zählen nur, wenn du einen Spieler deines Rangs oder höher besiegst.
commands-top-description = Die besten Spieler.
commands-season-description = Wertungssaison: verbleibende Zeit, deine Platzierung und die Sieger der letzten Saison.
commands-matches-description = Deine Wertungsmatches: Ergebnisse, Wertungsänderungen und mit wem du gespielt hast.
commands-top-hexed-content = [orange]{ $index }. { $nickname }[accent]: [blue]{ $rankName } [cyan]{ $points } []Siege
commands-top-pvp-content = [orange]{ $index }. { $nickname }[accent]: [cyan]{ $rating }
top-menu-title = [orange]{ -xcore } — Beste Spieler: [accent]{ $category }
top-menu-content =
    [lightgray]Wähle einen Spieler, um sein Profil zu öffnen.[]
    [lightgray]Seite [green]{ $page }[]/[green]{ $totalPages }[] [gold]•[] [lightgray]Spieler: [green]{ $totalEntries }[]
    { $selfRankLine }
top-menu-empty =
    [accent]Kategorie: [green]{ $category }[]
    [gray]Noch keine Spieler.
top-menu-categories-title = [orange]{ -xcore } — Bestenlisten-Kategorie
top-menu-categories-content =
    [lightgray]Wähle, welche Wertung angezeigt wird.[]
    [lightgray]Aktuell: [green]{ $category }[]
top-menu-category-button = [accent]Kategorie: [green]{ $category }[]
top-menu-category-mini-pvp = MiniPvP
top-menu-category-playtime = Spielzeit
top-menu-category-hexed = Hexed
top-menu-self-rank-known = [lightgray]Deine Platzierung: [accent]#{ $rank }[]
top-menu-self-rank-unknown = [lightgray]Deine Platzierung: [gray]nicht gefunden[]
top-menu-entry-mini-pvp = { $rankLabel } { $leagueIcon } [accent]{ $nickname }[] [gray]—[] [sky]{ $value }[]
top-menu-entry-playtime = { $rankLabel } [accent]{ $nickname }[] [gray]—[] [green]{ $value }[]
top-menu-entry-hexed = { $rankLabel } [accent]{ $nickname }[] [gray]—[] [violet]{ $rankName }[] [gold]•[] [cyan]{ $value }[]
top-menu-total-count = { $count } Spieler
top-menu-btn-find-self = Mich finden
top-menu-on-this-page = auf dieser Seite
top-menu-unranked = Du bist in dieser Kategorie noch nicht platziert
top-menu-self-rank-line = Dein Platz: { $rank }
top-menu-score-points = { $points } Pkt.
top-menu-score-minutes = { $time }
# ==============================================================================
# Game Modes (Hexed, PvP, Surrender, AI)
# ==============================================================================
commands-surrender-description = Aufgeben in Hexed. Dein aktuelles Team wird zerstört, deine Einheit entfernt und du kommst ins Zuschauerteam.
commands-surrender-success = [green]Du hast aufgegeben und schaust jetzt zu
commands-observer-success = [green]Du schaust jetzt zu
commands-observer-exit-success = [green]Du schaust nicht mehr zu
commands-ai-description = KI steuern.
commands-ai-usage = [red]attack(a) []oder [accent]idle(i)
hexed-popup = [blue]{ DURATION($remaining, style: "timer") }[] bis zum Spielende.
hexed-eliminated = { $nickname } [gold]wurde [scarlet]ausgeschaltet[]!
hexed-leaderboard-content = [orange]{ $index }. { $nickname }[accent]: [cyan]{ $hexes } [accent]Hexes
hexed-ranks-newbie = Neuling
hexed-ranks-regular = Stammspieler
hexed-ranks-advanced = Fortgeschritten
hexed-ranks-veteran = Veteran
hexed-ranks-davastator = Verwüster
hexed-ranks-the_legend = Die Legende
hexed-game-over-header = Spiel vorbei. Sieger:
hexed-game-over-winner-row =
    [orange]{ $index }. { $name }[][accent]: [cyan]{ $cores } { $cores ->
        [one] Hex
       *[other] Hexes
    }
hexed-game-over-no-winners = Spiel vorbei. Leider konnten keine Sieger ermittelt werden.
hexed-game-over-restart = Neues Spiel in 10 Sekunden…
rating_league_scrap = Schrott
rating_league_copper = Kupfer
rating_league_lead = Blei
rating_league_graphite = Graphit
rating_league_silicon = Silizium
rating_league_titanium = Titan
rating_league_thorium = Thorium
rating_league_plastanium = Plastanium
rating_league_phase_fabric = Phasengewebe
rating_league_surge_alloy = Spannungslegierung
pvp-team-won = Dein Team hat gewonnen. Deine Wertung steigt um { $increased }
pvp-team-lose = Dein Team hat verloren. Deine Wertung sinkt um { $reduced }
pvp-match-settlement-win = [accent]■ Ergebnis: [green]Sieg![] Deine Wertung: [stat]{ $oldRating }[] -> [stat]{ $newRating }[] [green](+{ $delta })[] { $leagueIcon } { $leagueName }
pvp-match-settlement-loss = [accent]■ Ergebnis: [scarlet]Niederlage![] Deine Wertung: [stat]{ $oldRating }[] -> [stat]{ $newRating }[] [scarlet]({ $delta })[] { $leagueIcon } { $leagueName }
pvp-match-settlement-draw = [accent]■ Ergebnis: [yellow]Unentschieden![] Deine Wertung: [stat]{ $oldRating }[] -> [stat]{ $newRating }[] [yellow]({ $delta })[] { $leagueIcon } { $leagueName }
pvp-match-settlement-exempt = [accent]■ Ergebnis: [lightgray]Keine Wertungsänderung (zu wenig aktive Spielzeit).[]
pvp-match-settlement-details = [lightgray]Matchdetails: [white]/matches[][]
season-ending-soon = [accent]■ [white]{ $ladder }[]: Saison { $number } endet in [stat]{ $remaining }[]. Nutze deine letzten Partien!
season-started = [accent]■ [white]{ $ladder }[]: Saison { $previous } ist vorbei. Saison { $number } hat begonnen!
season-reset-soft = [lightgray]Die Wertungen wurden Richtung Startwert gezogen, die Partienzähler beginnen bei null.[]
season-reset-hard = [lightgray]Alle beginnen wieder mit der Startwertung.[]
season-reset-none = [lightgray]Die Wertungen bleiben, die Partienzähler beginnen bei null.[]
season-title = Saison { $number }
top-menu-scope-current = { $season } [lightgray]· endet in { $remaining }[]
top-menu-scope-past = { $season } [gray]· { $from } – { $to }[]
ladder-profile-standing = { $leagueIcon } [white]{ $league }[] [accent]{ $rating } ELO[]
ladder-profile-headline = [accent]{ $icon }[] [lightgray]{ $ladder }:[] { $standing }
ladder-profile-unplaced = [gray]in dieser Saison keine gewerteten Partien[]
ladder-profile-matches = [lightgray]Partien:[] [white]{ $matches }[]
ladder-profile-wins = [lightgray]Siege:[] [white]{ $wins }[] [gray]({ $rate }%)[]
ladder-profile-rank = [lightgray]Platz:[] [accent]#{ $rank }[]
ladder-profile-peak = [lightgray]Höchstwert:[] [white]{ $rating }[]
ladder-profile-season = [lightgray]{ $season } · endet in [white]{ $remaining }[][]
ladder-profile-season-closing = [lightgray]{ $season } · Ergebnisse werden ausgewertet[]
ladder-profile-history = [gray]{ $season } — { $rank }, { $league }, { $rating } ELO[]
season-menu-title = Wertungssaisons
season-menu-card-title = { $ladder } — { $season }
season-menu-ends = [lightgray]Endet in [white]{ $remaining }[] ({ $date })[]
season-menu-closing = [lightgray]Die Saison ist vorbei, die Ergebnisse werden ausgewertet.[]
season-menu-participants = [lightgray]Spieler in dieser Saison: [white]{ $count }[][]
season-menu-you = [lightgray]Du:[] { $standing }
season-menu-previous = [lightgray]{ $season } — Sieger:[]
season-menu-podium-entry = [gold]{ $place }.[] [white]{ $name }[] [gray]—[] { $league } [accent]{ $rating }[]
season-menu-open-top = Bestenliste
season-menu-prizes = [lightgray]Preise dieser Saison:[]
season-menu-prize-entry = [gold]{ $places }.[] [white]{ $prize }[]
season-menu-podium-prizes = [gray] · [gold]{ $prizes }[]
prize-grant-line = [lightgray]{ $season } · Preis:[] [gold]{ $prize }[] [gray]({ $status })[]
prize-status-pending = wartet auf Übergabe
prize-status-granted = erhalten
prize-status-delivered = zugestellt
prize-status-failed = ein Admin kümmert sich darum
season-menu-empty = Es gibt noch keine Wertungssaisons.
pvp-hud-status = [accent]MiniPvP[] | [stat]Am Leben:[] { $teams } | [gray]{ $time }[]
pvp-leaderboard-content = [orange]{ $index }. { $nickname }[accent]:[cyan] { $rating } [accent]Wertung
pvp-you-spectator = [scarlet]Du wurdest ausgeschaltet. Bitte warte auf das nächste Spiel.
# ==============================================================================
# Events & Notifications
# ==============================================================================
player-joined-pid = { $nickname } [grey]#[white]{ $pid }[grey] [accent]ist beigetreten.
player-joined-username = { $nickname } [grey]@[white]{ $username }[grey] [accent]ist beigetreten.
player-joined-both = { $nickname } [accent]@{ $username } [grey]#[white]{ $pid }[grey] [accent]ist beigetreten.
player-joined-none = { $nickname } [accent]ist beigetreten.

player-left-pid = { $nickname } [grey]#[white]{ $pid }[grey] [accent]ist gegangen.
player-left-username = { $nickname } [grey]@[white]{ $username }[grey] [accent]ist gegangen.
player-left-both = { $nickname } [accent]@{ $username } [grey]#[white]{ $pid }[grey] [accent]ist gegangen.
player-left-none = { $nickname } [accent]ist gegangen.

player-settings-identity-mode = [accent]Kennung in Meldungen:[]
player-settings-identity-mode-pid = Nur ID (#12)
player-settings-identity-mode-username = Nur Benutzername (@Steve)
player-settings-identity-mode-both = Beides (@Steve #12)
player-settings-identity-mode-none = Ausgeblendet

notification-votekick-playtime =
    [accent]Glückwunsch! Du hast [lightgray]{ $votekickPlayTime }[] { $votekickPlayTime ->
        [one] Minute
       *[other] Minuten
    } gespielt und kannst jetzt Kick-Abstimmungen starten.
notification-global-chat-playtime =
    [accent]Glückwunsch! Du hast [lightgray]{ $globalChatPlayTime }[] { $globalChatPlayTime ->
        [one] Minute
       *[other] Minuten
    } gespielt und kannst jetzt im globalen Chat schreiben.
    [lightgray]Gib [accent]/g [gray]<Nachricht…>[lightgray] ein, um eine Nachricht zu senden.
notification-admin-kick = { $admin }[accent] hat { $target }[] gekickt.
notification-admin-wave-skip = { $admin }[accent] hat die Welle übersprungen.
server-restart-countdown =
    Neustart in { $seconds ->
        [one] { $seconds } Sekunde
       *[other] { $seconds } Sekunden
    }
like-map-success = [green]Dir gefällt diese Karte!
like-map-changed = [green]Du hast deine Meinung zu „Gefällt mir“ geändert!
dislike-map-success = [orange]Dir gefällt diese Karte nicht.
dislike-map-changed = [orange]Du hast deine Meinung zu „Gefällt mir nicht“ geändert.
like-event-success = [green]Dir gefällt dieses Event!
like-event-changed = [green]Du hast deine Meinung zu „Gefällt mir“ geändert!
dislike-event-success = [orange]Dir gefällt dieses Event nicht.
dislike-event-changed = [orange]Du hast deine Meinung zu „Gefällt mir nicht“ geändert.

# ==============================================================================
# Events (Server)
# ==============================================================================

commands-event-description = Menü zur Event-Verwaltung.
commands-events-description = Liste aller Events auf den Servern.
event-events = Events
event-menu-main = Haupt-Events
event-menu-main-title = [orange]{ -xcore } — Events
event-menu-main-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]Event-Zentrale[]
    [lightgray]Alle aktiven und geplanten Server-Events an einem Ort.[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Aktuelles Event[]
    [gray]Status: [white]{ $currentEventState }[]
    [gray]Ausgewählt: [white]{ $currentEventName }[]
    { "" }
    [accent]■ Abstimmung[]
    [gray]Abstimmung: [white]{ $voteStatus }[]
    { "" }
    [accent]■ Aktionen[]
    [gray]Öffne den Katalog, sieh dir das aktuelle Event an oder bereite ein neues vor.[]
event-menu-event = Event
event-menu-event-title = [orange]{ -xcore } — Event
event-menu-event-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $name }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Übersicht[]
    [gray]Autor: [white]{ $author }[]
    [gray]Karte: [white]{ $mapName }[]
    [gray]Typ: [white]{ $eventType }[] [darkgray]|[gray] Status: [white]{ $eventState }[]
    [gray]Vorübergehend: [white]{ $isTemporary }[]
    { "" }
    [accent]■ Zeitplan[]
    [gray]Erstellt: [white]{ $createdEventTime }[]
    [gray]Geplanter Start: [white]{ $plannedStartTime }[]
    [gray]Geplantes Ende: [white]{ $plannedEndTime }[]
    { "" }
    [accent]■ Ansehen[]
    [gray]Gefällt mir: [white]{ $like }[] [darkgray]|[gray] Gefällt mir nicht: [white]{ $dislike }[]
event-menu-event-map = Karte ansehen
event-menu-events = Event-Liste
event-menu-events-title = [orange]{ -xcore } — Event-Liste
event-menu-events-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]Event-Katalog[]
    [lightgray]Seite [green]{ $page }[]/[green]{ $total }[] [gold]•[] [lightgray]Events: [green]{ $count }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Filter[]
    [gray]Beendet: [white]{ $finished }[]
    [gray]Groß: [white]{ $major }[] [darkgray]|[gray] Aktiv: [white]{ $active }[]
    { "" }
    [accent]■ Liste[]
    [gray]Wähle unten ein Event, um seine Karte zu sehen.[]
event-menu-events-empty =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]Event-Katalog[]
    [lightgray]Zu den aktuellen Filtern gibt es noch keine Events.[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Filter[]
    [gray]Beendet: [white]{ $finished }[]
    [gray]Groß: [white]{ $major }[] [darkgray]|[gray] Aktiv: [white]{ $active }[]
event-menu-events-row = [accent]{ $state }[] [darkgray]•[] [white]{ $type }[] [darkgray]—[] { $name }
event-menu-events-selected = [green]●[] [accent]{ $state }[] [darkgray]•[] [white]{ $type }[] [darkgray]—[] { $name }
event-menu-create-start = Erstellen
event-menu-create-start-title = [orange]{ -xcore } — Event erstellen
event-menu-create-start-message = Gib den Namen des neuen Events ein
event-menu-create-start-default = Event von { $playerName }
event-menu-create-start-map = Event für diese Karte erstellen
event-menu-edit = Bearbeiten
event-menu-edit-title = [orange]{ -xcore } — Event bearbeiten
event-menu-edit-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $name }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Angaben[]
    [gray]Autor: [white]{ $author }[]
    [gray]Typ: [white]{ $eventType }[]
    { "" }
    [accent]■ Karte[]
    [gray]Gewählte Karte: [white]{ $mapName }[]
    { "" }
    [accent]■ Zeitplan[]
    [gray]Geplanter Start: [white]{ $plannedStartTime }[]
    [gray]Geplantes Ende: [white]{ $plannedEndTime }[]
    { "" }
    [accent]■ Optionen[]
    [gray]Vorübergehend: [white]{ $isTemporary }[]
event-menu-edit-name = Name
event-menu-edit-name-reset = [scarlet]Name zurücksetzen
event-menu-edit-name-title = [orange]{ -xcore } — Event bearbeiten
event-menu-edit-name-message = Name bearbeiten:
event-menu-edit-description = Beschreibung
event-menu-edit-description-title = [orange]{ -xcore } — Event bearbeiten
event-menu-edit-description-message = Beschreibung bearbeiten:
event-menu-edit-map = Karte ändern
event-menu-edit-temporary-active = [green]Vorübergehend
event-menu-edit-temporary-inactive = [gray]Vorübergehend
event-menu-edit-major-active = [green]Groß
event-menu-edit-major-inactive = [gray]Groß
event-menu-edit-planned-start = Event-Start
event-menu-edit-planned-start-title = [orange]{ -xcore } — Event bearbeiten
event-menu-edit-planned-start-message = Gib die Startzeit in ms oder mit m/h/d ein:
event-menu-edit-planned-end = Event-Ende
event-menu-edit-planned-end-title = [orange]{ -xcore } — Event bearbeiten
event-menu-edit-planned-end-message = Gib die Endzeit in ms oder mit m/h/d ein:
event-menu-maps = Karten
event-menu-maps-title = [orange]{ -xcore } — Karte wählen
event-menu-maps-content = [white]Seite [green]{ $page }[] von [green]{ $total }[]
vote-event-vote =
    { $nickname }[lightgray] hat dafür gestimmt, das aktuelle Event zu [orange]{ $name }[lightgray] zu wechseln. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Gib [orange]y[] oder [orange]n[] ein, um abzustimmen.
vote-event-left = { $nickname }[lightgray] ist gegangen. Die Stimme für den Event-Wechsel wurde zurückgezogen. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
vote-event-fail = [lightgray]Abstimmung gescheitert. Nicht genug Stimmen für den Wechsel zu [orange]{ $name }[].
vote-event-success = [orange]Abstimmung erfolgreich. Das Event [accent]{ $name }[] wird beim nächsten Kartenwechsel geladen.
vote-event-cancelled = [lightgray]Die Abstimmung für den Wechsel zu [orange]{ $name }[lightgray] wurde von Administrator { $admin } abgebrochen.
event-vote = [orange]Abstimmung
event-avote = [red]Sofortiger Wechsel
event-menu-vote-stop = Abstimmung beenden
event-menu-stop = Event beenden
event-menu-this-event = [orange]Aktuelles Event
event-menu-type-major = Großes Event
event-menu-type-regular = Normales Event
event-menu-state-none = Kein aktives Event
event-menu-state-planned = Geplant
event-menu-state-active = Läuft gerade
event-menu-state-finished = Beendet
event-menu-vote-status-running = Läuft
event-menu-vote-status-idle = Läuft nicht
date-time-picker-title = [orange]{ -xcore } — Datum und Uhrzeit
date-time-picker-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $field }[]
    [lightgray]Aktueller Wert: [white]{ $value }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Datum[]
    [gray]Wähle zuerst einen Tag und passe dann unten die Uhrzeit an.[]
    { "" }
    [accent]■ Uhrzeit[]
    [gray]Nutze Vorgaben oder Feinanpassungen für eine genaue Planung.[]
    { "" }
    [accent]■ Manuelle Eingabe[]
    [gray]Nur nötig, wenn du exakte Millisekunden oder einen relativen Wert wie +m/+h/+d brauchst.[]
date-time-picker-field-generic = Geplante Zeit
date-time-picker-today = Heute
date-time-picker-tomorrow = Morgen
date-time-picker-plus-2d = +2 Tage
date-time-picker-plus-7d = +7 Tage
date-time-picker-now = Jetzt
date-time-picker-time-0000 = 00:00
date-time-picker-time-0600 = 06:00
date-time-picker-time-1200 = 12:00
date-time-picker-time-1800 = 18:00
date-time-picker-minus-1d = -1 T.
date-time-picker-plus-1d = +1 T.
date-time-picker-minus-1h = -1 Std.
date-time-picker-plus-1h = +1 Std.
date-time-picker-minus-15m = -15 Min.
date-time-picker-plus-15m = +15 Min.
date-time-picker-reset = Zurücksetzen
date-time-picker-manual = Manuelle Eingabe
date-time-picker-manual-title = [orange]{ -xcore } — Manuelle Zeiteingabe
date-time-picker-manual-message = Gib absolute Millisekunden oder eine relative Zeit wie +30m, +2h, +1d ein.
event-end = Das Event [green]{ $name }[] ist beendet!
# ==============================================================================
# Errors
# ==============================================================================
error-access-denied = [scarlet]⚠ Zugriff verweigert.
error-ip-changed = [scarlet]⚠ Deine IP-Adresse hat sich geändert. Die Admin-Rechte wurden entzogen.
error-not-enough-params = [scarlet]⚠ Nicht genug Positionsparameter.
error-player-not-found = [scarlet]Spieler nicht gefunden.
error-player-not-teammate = [scarlet]⚠ Der Spieler ist nicht in deinem Team.
error-player-admin = [scarlet]⚠ Versuch nicht, einen Admin zu kicken. ⚠
error-already-voted = [scarlet]⚠ Du hast bereits abgestimmt. Immer mit der Ruhe.
error-playtime-requirement =
    [scarlet]⚠ Du musst mindestens { $time } { $time ->
        [one] Minute
       *[other] Minuten
    } gespielt haben, um diese Funktion zu nutzen.
error-globalchat-total-playtime =
    [scarlet]⚠ Um im globalen Chat zu schreiben, musst du { $globalChatPlayTime } { $globalChatPlayTime ->
        [one] Minute
       *[other] Minuten
    } gespielt haben.
error-votekick-total-playtime =
    [scarlet]⚠ Um eine Kick-Abstimmung zu starten, musst du { $votekickPlayTime } { $votekickPlayTime ->
        [one] Minute
       *[other] Minuten
    } gespielt haben.
error-vote-yourself = [scarlet]⚠ Du kannst nicht in deiner eigenen Abstimmung abstimmen.
error-vote-in-progress = [scarlet]⚠ Es läuft bereits eine Abstimmung.
error-no-voting = [scarlet]⚠ Im Moment läuft keine Abstimmung.
error-wave-vote-unavailable = [scarlet]⚠ Eine Welle früher zu starten geht nur in wellenbasierten Modi.
error-no-map = [scarlet]⚠ Keine Karte festgelegt.
error-map-not-event = [scarlet]⚠ Die Karte gehört nicht zum aktuellen Event.
error-map-not-found = [scarlet]⚠ Karte nicht gefunden! [accent]Mit [cyan]/maps[] siehst du alle verfügbaren Karten.
error-maps-empty = [scarlet]⚠ Die Kartenliste ist leer.
error-event-not-found = [scarlet]⚠ Event nicht gefunden! [accent]Mit [cyan]/events[] siehst du alle verfügbaren Events.
error-page-between = [scarlet]⚠ 'page' muss eine Zahl zwischen[orange] 1[] und [orange]{ $totalPages }[] sein.
error-page-number = [scarlet]'page' muss eine Zahl sein.
error-wrong-number = [scarlet]⚠ Falsches Zahlenformat.
error-wrong-period-format = [scarlet]⚠ Falsches Zeitformat. Beispiel: 1h 30m, 30 ({ hours })
error-invalid-id = [scarlet]⚠ Ungültige Spieler-ID.
error-spectator = [scarlet]⚠ Als Zuschauer kannst du diesen Befehl nicht nutzen.
error-admin-password-too-short = [scarlet]⚠ Das Admin-Passwort muss mindestens 8 Zeichen lang sein.
error-wrong-admin-password = [scarlet]⚠ Falsches Admin-Passwort.
error-internal = [scarlet]Interner Fehler.
error-processing-request = [scarlet]Bei der Verarbeitung der Anfrage ist ein Fehler aufgetreten.
error-no-access = [scarlet]⚠ Kein Zugriff.
error-nickname-too-long = [scarlet]⚠ Der Name ist zu lang. Höchstens { $max } sichtbare Zeichen.
error-private-message-invalid-pid = [scarlet]⚠ Ungültige PID für private Nachrichten. Format: [lightgray]#123[].
error-private-message-self = [scarlet]⚠ Du kannst dir selbst keine private Nachricht senden.
error-private-message-empty = [scarlet]⚠ Die Nachricht darf nicht leer sein.
error-private-message-too-long = [scarlet]⚠ Die Nachricht ist zu lang. Höchstens { $max } Zeichen.
error-private-message-cooldown = [scarlet]⚠ Warte { DURATION($seconds) }, bevor du die nächste private Nachricht sendest.
error-private-message-target-unavailable = [scarlet]⚠ Dieser Spieler kann gerade keine privaten Nachrichten empfangen.
error-private-message-no-reply-target = [scarlet]⚠ Kein aktueller Kontakt, dem du antworten kannst.
error-private-message-not-found = [scarlet]⚠ Nachricht nicht gefunden.
error-private-message-block-self = [scarlet]⚠ Du kannst dich nicht selbst blockieren.
error-private-message-block-limit = [scarlet]⚠ Grenze der Blockliste erreicht ({ $limit }).
ban-menu-duration-title = [orange]{ -xcore } - Dauer der Sperre
ban-menu-duration-message = Gib die Dauer der Sperre für { $nickname } ein. Beispiel: 1d, 12h, 30m
ban-menu-reason-title = [orange]{ -xcore } - Grund der Sperre
ban-menu-reason-message = Gib den Grund der Sperre für { $nickname } ein. Leer lassen für den Standardgrund.
ban-menu-confirm-title = [orange]{ -xcore } - Sperre bestätigen
ban-menu-confirm-content =
    [white]Spieler: { $nickname }[]
    [white]Dauer: [accent]{ $duration }[]
    [white]Grund: [accent]{ $reason }[]
ban-menu-confirm-action = [scarlet]Spieler sperren
error-invalid-syntax = [scarlet]⚠ Ungültige Befehlssyntax. Verwendung: [lightgray]/'{ $syntax }'.
error-invalid-sender = [scarlet]⚠ Ungültiger Absender. Dieser Befehl erfordert: '[lightgray]{ $type }[]'.
error-argument-parse-generic = [scarlet]⚠ Ungültiges Argument: '{ $error }'.
exception-unexpected = [scarlet]⚠ Beim Ausführen dieses Befehls ist ein interner Fehler aufgetreten.
exception-invalid-argument = [scarlet]⚠ Ungültiges Befehlsargument: '{ $cause }'.
exception-no-such-command = [scarlet]⚠ Unbekannter Befehl.
exception-no-permission = [scarlet]⚠ Zugriff verweigert.
exception-invalid-sender = [scarlet]⚠ '{ $actual }' kann diesen Befehl nicht ausführen. Benötigter Absender: [lightgray]{ $expected }[].
exception-invalid-sender-list = [scarlet]⚠ '{ $actual }' kann diesen Befehl nicht ausführen. Erlaubte Absender: [lightgray]{ $expected }[].
exception-invalid-syntax = [scarlet]⚠ Ungültige Befehlssyntax. Verwendung: [lightgray]/'{ $syntax }'.
argument-parse-failure-boolean = [scarlet]⚠ '{ $input }' ist kein gültiger Wahrheitswert.
argument-parse-failure-number = [scarlet]⚠ '{ $input }' ist keine gültige Zahl im Bereich [{ $min }, { $max }].
argument-parse-failure-char = [scarlet]⚠ '{ $input }' ist kein gültiges Zeichen.
argument-parse-failure-enum = [scarlet]⚠ '{ $input }' ist keine gültige Option. Erlaubt: [lightgray]{ $acceptableValues }
argument-parse-failure-string = [scarlet]⚠ Ungültiges Textformat für '{ $input }'.
argument-parse-failure-uuid = [scarlet]⚠ Ungültiges UUID-Format: '{ $input }'.
argument-parse-failure-regex = [scarlet]⚠ Die Eingabe '{ $input }' passt nicht zum Muster '{ $pattern }'.
argument-parse-failure-color = [scarlet]⚠ '{ $input }' ist keine gültige Farbe.
argument-parse-failure-duration = [scarlet]⚠ '{ $input }' ist kein gültiges Zeitformat.
argument-parse-failure-aggregate-missing = [scarlet]⚠ Fehlender Bestandteil '{ $component }'.
argument-parse-failure-aggregate-failure = [scarlet]⚠ Ungültiger Bestandteil '{ $component }': '{ $failure }'.
argument-parse-failure-either = [scarlet]⚠ Aus '{ $input }' konnte weder { $primary } noch { $fallback } ermittelt werden.
argument-parse-failure-flag-unknown = [scarlet]⚠ Unbekanntes Flag: '{ $flag }'.
argument-parse-failure-flag-duplicate = [scarlet]⚠ Doppeltes Flag: '{ $flag }'.
argument-parse-failure-flag-duplicate-flag = [scarlet]⚠ Doppeltes Flag: '{ $flag }'.
argument-parse-failure-flag-no-flag-started = [scarlet]⚠ Kein Flag begonnen. Unklar, was mit '{ $input }' passieren soll.
argument-parse-failure-flag-missing-argument = [scarlet]⚠ Fehlendes Argument für Flag: '{ $flag }'.
argument-parse-failure-flag-no-permission = [scarlet]⚠ Du darfst das Flag '{ $flag }' nicht verwenden.
argument-parse-failure-selector-syntax = [scarlet]⚠ Ungültiger Selektor '{ $input }': [lightgray]{ $reason }
argument-parse-failure-selector-no-such-target = [scarlet]⚠ Nichts passt zu '{ $input }'.
argument-parse-failure-selector-too-many-targets = [scarlet]⚠ '{ $input }' passt zu mehreren Zielen, dieser Befehl braucht aber genau eines.
argument-parse-failure-selector-denied = [scarlet]⚠ Selektoren sind hier nicht erlaubt: [lightgray]{ $reason }
argument-parse-failure-selector-kind-not-allowed = [scarlet]⚠ Der Selektor '{ $kind }' ist hier nicht erlaubt.
argument-parse-failure-selector-sender-required = [scarlet]⚠ Der Selektor '{ $kind }' geht nur im Spiel.
argument-parse-failure-selector-limit-exceeded = [scarlet]⚠ Der Selektor passt zu { $count } Zielen, erlaubt sind { $limit }.
argument-parse-failure-team = [scarlet]⚠ Team '{ $input }' nicht gefunden.
argument-parse-failure-content = [scarlet]⚠ '{ $input }' ist kein gültiger Wert für { $type }.
# ==============================================================================
# Button Status
# ==============================================================================
finished = beendet
finished-neutral = [orange]Beendet
finished-active = [green]Beendet
finished-inactive = [red]Beendet
major = Groß
major-neutral = [orange]Groß
major-active = [green]Groß
major-inactive = [red]Groß
active = Aktiv
active-neutral = [orange]Aktiv
active-active = [green]Aktiv
active-inactive = [red]Aktiv
admin = Admin
admin-neutral = [orange]Admin
admin-active = [green]Admin
admin-inactive = [red]Admin
player-leaderboard-active = [green]Bestenliste: an[]
player-leaderboard-inactive = [red]Bestenliste: aus[]
# ==============================================================================
# Miscellaneous
# ==============================================================================
hours = Stunden
days = Tage
success = [green]Erfolgreich
empty = [accent]Leer
never = Nie
save = Speichern
close = [scarlet]Schließen
previous = [accent]« Zurück
next = [accent]Weiter »
cancel = Abbrechen
back = Zurück
yes = Ja
no = Nein
test = Test
no-description = Keine Beschreibung
discord = Discord
github = Github
donatello = Donatello
weblate = Weblate
discord-red-vs-blue = RedVSBlue
auto = Auto
on = An
off = Aus
error-command-disabled = [scarlet]⚠ Der Befehl [accent]/{ $command }[scarlet] ist auf diesem Server deaktiviert.
error-feature-disabled = [scarlet]⚠ Diese Funktion ist auf diesem Server deaktiviert.
none = Keine
unknown = Unbekannt
error-nickname-badge-glyph = [scarlet]⚠ Der eigene Name darf keine reservierten Abzeichen-Symbole enthalten.

# Server browser (/servers)
player-servers-title = XCORE-SERVERNETZWERK
player-servers-cat-all = Alle
player-servers-cat-pvp = PvP
player-servers-cat-survival = Überleben
player-servers-cat-special = Spezial
player-servers-hint = Tippe auf eine Serverkarte, um dich zu verbinden
player-servers-refresh = Aktualisieren
player-servers-already-connected = [gold]● Du bist bereits mit diesem Server verbunden!
player-servers-transferring = [accent]Wechsel zum Server [white]{ $server }[]...
player-servers-full = [scarlet]Server { $server } ist voll! Bitte warte auf einen freien Platz.
player-servers-offline = [scarlet]Server { $server } ist gerade offline.
player-servers-not-found = [scarlet]Server '{ $server }' nicht gefunden.
player-servers-empty-category = [lightgray]In dieser Kategorie sind keine Server verfügbar.[]
player-servers-online-summary = [green]● { $players } [gray]spielen[] [darkgray]|[] [sky]{ $servers } [gray]online[]
player-servers-badge-current = [gold]● DU BIST HIER[]
player-servers-offline-badge = [darkgray]● OFFLINE[]
player-servers-capacity-full = [scarlet]● { $players }/{ $max } VOLL[]
player-servers-capacity-normal = [green]● { $players }/{ $max } { $bar }
player-servers-card-current = [lightgray]Du bist mit diesem Server verbunden[]
player-servers-card-wave = [darkgray]|[] [accent]Welle { $wave }[]
player-servers-card-empty = [sky]Sei der Erste! Starte eine Runde[]
player-servers-card-map = [gray]Karte:[] [white]{ $map }[]
player-servers-card-mode = [gray]Modus:[] [white]{ $mode }[]

announcement-hub =
    [gold]★ [accent]XCore-Server [lightgray]» [white]Langweilt dich diese Partie?
    [lightgray]Mit [accent]/hub[lightgray] kannst du jederzeit andere Server besuchen!
announcement-discord =
    [gold]★ [accent]XCore-Community [lightgray]» [white]Suchst du Mitspieler und Neuigkeiten?
    [lightgray]Tritt mit [accent]/discord[lightgray] unserem Discord-Server bei!
announcement-help =
    [gold]★ [accent]XCore-Hilfe [lightgray]» [white]Brauchst du Hilfe oder die Befehlsliste?
    [lightgray]Gib [accent]/help[lightgray] ein, um alle verfügbaren Befehle zu sehen!


error-only-players = [scarlet]⚠ Dieser Befehl kann nur von Spielern verwendet werden.

player-settings-username-editable = [gold]★ Benutzername (Event-Belohnung):[]
player-settings-username-hint = Eindeutigen Benutzernamen eingeben (4–32 Zeichen)...
player-settings-username-locked = [gray]Benutzername:[] [accent]@{ $username }[] [darkgray](Gesperrt)[]
player-settings-username-none = [gray]Benutzername: [darkgray]Nicht gesetzt (gewinne ein Event zum Freischalten)[]

error-username-empty = Der Benutzername darf nicht leer sein!
error-username-length = Der Benutzername muss 4 bis 32 Zeichen lang sein!
error-username-invalid-chars = Der Benutzername darf nur lateinische Buchstaben, Ziffern und Unterstriche enthalten!
error-username-taken = Dieser Benutzername ist bereits von einem anderen Spieler vergeben!

# ==============================================================================
# Permission nodes
# ==============================================================================
permission-mindustry-admin = Das eingebaute Admin-Menü des Spiels nutzen und Wellen überspringen
permission-xcore-moderation-mute = Spieler stummschalten
permission-xcore-moderation-unmute = Stummschaltung aufheben
permission-xcore-moderation-kick = Spieler kicken
permission-xcore-moderation-ban = Spieler sperren
permission-xcore-moderation-unban = Spieler entsperren
permission-xcore-moderation-audit-others = Den Moderationsverlauf anderer Spieler ansehen
permission-xcore-moderation-votekick-immune = Kann nicht per Abstimmung gekickt werden
permission-xcore-admin-tp = Spieler teleportieren
permission-xcore-admin-broadcast = Ankündigungen an alle senden
permission-xcore-admin-kill = Einheiten und Spieler töten
permission-xcore-admin-heal = Einheiten und Spieler heilen
permission-xcore-admin-set-team = Das Team eines Spielers ändern
permission-xcore-maps-force-rtv = Die Karte ohne Abstimmung wechseln
permission-xcore-maps-force-vnw = Eine Welle ohne Abstimmung überspringen
permission-xcore-votes-cancel = Eine laufende Abstimmung abbrechen
permission-xcore-events-create-major = Große Events erstellen
permission-xcore-events-edit-others = Events anderer Spieler bearbeiten
permission-xcore-events-force-vote = Ein Event ohne Abstimmung starten
permission-xcore-events-stop = Das laufende Event beenden
permission-xcore-players-settings-others = Die Einstellungen anderer Spieler ändern
permission-xcore-players-private-info = Private Daten von Spielern sehen, etwa ihre IP-Adressen
permission-xcore-bypass-playtime = Spielzeit-Voraussetzungen von Befehlen umgehen
permission-xcore-permissions-inspect = Sehen, wer welche Berechtigungen hat
permission-xcore-permissions-manage = Rollen und Berechtigungen ändern

error-target-outranks = [scarlet]⚠ Das geht nicht bei einem Spieler, dessen Rolle nicht unter deiner liegt.
perm-me-legacy = [accent]Rollen sind auf diesem Server deaktiviert. Admin: [white]{ $admin }
perm-me-header = [accent]Deine Rollen hier (Gewicht [white]{ $weight }[accent]):
perm-me-none = [lightgray] - keine
perm-me-logged-in = [green]Du bist als Teammitglied angemeldet.
perm-me-not-logged-in = [yellow]Du bist nicht als Teammitglied angemeldet. Nutze [white]/login <Passwort>[yellow].
perm-me-stale = [scarlet]Deine Team-Rechte sind pausiert: Der Server konnte sie nicht aktualisieren. Sie kommen von selbst zurück.

help-ui-search-hint = Befehle, Aliase oder Beschreibungen suchen
help-ui-search-empty = [lightgray]Keine Befehle für diese Suche gefunden.[]

match-history-title = Matchverlauf
match-history-rank = #{ $rank } in dieser Saison
match-history-form = Letzte { $count }:
match-history-empty = Hier erscheinen deine Wertungsmatches.
match-history-since = Der Verlauf wird seit { $date } geführt.
match-history-load-failed = Der Matchverlauf konnte nicht geladen werden. Versuche es gleich noch einmal.
match-history-back-to-list = Zur Liste
match-history-your-result = Dein Ergebnis
match-history-places = Platzierungen
match-history-team-title = #{ $place } · { $team } · Schnitt { $average }
match-history-time-now = gerade eben
match-history-time-minutes = vor { $count } Min.
match-history-time-hours = vor { $count } Std.
match-history-time-yesterday = gestern
match-history-outcome-win = [lime]Sieg[]
match-history-outcome-loss = [scarlet]Niederlage[]
match-history-outcome-unrated = [lightgray]Ohne Wertung[]
match-history-outcome-uncounted = [lightgray]Nicht gewertet[]
match-history-lineup = { $own } gegen { $other }
match-history-team-place = #{ $place } von { $teams } Teams
match-history-place = #{ $place } von { $players }
match-history-counted = Gewertet: { $reason }
match-history-not-counted = Nicht gewertet: { $reason }
match-history-counted-share = Zu { $percent } % des Matches gewertet
match-history-reason-winner = gewonnen
match-history-reason-defeated = verloren
match-history-reason-short-play = weniger als die Hälfte des Matches gespielt
match-history-reason-late-join = ganz am Ende beigetreten
match-history-reason-match-unrated = das Match war ohne Wertung
match-history-reason-unknown = kein Grund gespeichert
match-history-skip-not-enough-players = zu wenige Spieler
match-history-skip-admin-stop = von einem Admin gestoppt
match-history-skip-technical-error = technischer Fehler
match-history-skip-unknown = ohne Wertung
match-history-finish-surrender = durch Aufgabe
match-history-finish-timeout = durch Zeitablauf
match-history-finish-admin-stop = von einem Admin gestoppt
match-history-finish-technical-error = technischer Fehler
