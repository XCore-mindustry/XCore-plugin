# ==============================================================================
# Terms
# ==============================================================================
-xcore = Server XCore

# ==============================================================================
# General & Help
# ==============================================================================
menu-main = Menu principale
commands-main-description = Apre il menu principale interattivo.
menu-main-title = [orange]{ -xcore } — Menu principale
menu-main-content = Menu principale del server
help-menu = Menu di aiuto
commands-help-description = Apre il menu di aiuto interattivo.
help-menu-title = [orange]{ -xcore } — Comandi
help-menu-content =
    [gray]Pagina [white]{ $page }[gray]/[white]{ $total }
    [lightgray]Seleziona un comando per vedere i dettagli d'uso:
help-menu-button = [accent]/{ $command } [gray]» Descrizione: [white]{ $description }
help-command-with-overload-count = { $name } di ({ $count })
help-command-title = [orange]» Nome: [white]/{ $name }
help-command-header =
    [orange]» [accent]Sintassi: [white]{ $syntax }
    [orange]» [accent]Info: [lightgray]{ $description }
help-aliases = [orange]» [accent]Alias: [white]{ $aliases }
help-args-title = [orange]» [accent]Argomenti:
help-usages-title = [orange]» [accent]Uso:
help-usage-entry = [gray]• [white]{ $syntax }
help-usage-args-title = [orange]» [accent]Per [white]{ $syntax }[accent]:
help-arg-entry = [gray]• [white]{ $arg } [lightgray]- { $description }
help-no-arguments = [gray]Nessun argomento aggiuntivo richiesto.
help-no-arg-description = Nessuna descrizione.
help-no-description = Nessuna descrizione fornita per questo comando.
help-legacy-command-content =
    [orange]» [accent]Comando: [white]/{ $name }
    [orange]» [accent]Parametri: [white]{ $params }
    [orange]» [accent]Info: [lightgray]{ $description }
    { "" }
    [gray](Questo è un comando legacy con informazioni limitate)
help-legacy-command-content-no-params =
    [orange]» [accent]Comando: [white]/{ $name }
    [orange]» [accent]Info: [lightgray]{ $description }
    { "" }
    [gray](Questo è un comando legacy con informazioni limitate)
help-back = [lightgray]« Indietro

# ==============================================================================
# Modern Reactive Help & Commands Guide (xcore-ui)
# ==============================================================================
help-ui-title = GUIDA AI COMANDI
help-ui-summary = [gray]{ $count } comandi disponibili
help-ui-empty-category = [lightgray]Nessun comando disponibile in questa categoria.[]
help-ui-overloads = ({ $count } varianti)
help-ui-aliases = [lightgray]Alias:[] { $aliases }
help-ui-syntax-title = Sintassi
help-ui-args-title = Parametri
help-ui-arg-required = [scarlet]Obbligatorio
help-ui-arg-optional = [sky]Opzionale
help-ui-btn-run = Esegui
help-ui-btn-copy = In chat
help-ui-btn-back = Indietro
help-ui-copied = [accent]Comando: [white]/{ $syntax }
help-ui-executed = [accent]Esecuzione comando: [white]/{ $syntax }

# Categories
help-cat-all = Tutti
help-cat-general = Generale
help-cat-game = Gioco
help-cat-social = Chat
help-cat-votes = Votazioni
help-cat-admin = Admin

# ==============================================================================
# Command Argument Descriptions
# ==============================================================================
# help
commands-help-page-description = Numero di pagina da visualizzare.
# login
commands-login-password-description = La tua password da amministratore.
# ban
commands-ban-id-description = ID del giocatore da bannare.
commands-ban-period-description = Durata del ban (es. 1d, 2h, 30m).
commands-ban-reason-description = Motivo del ban.
# unban
commands-unban-id-description = ID del giocatore da sbannare.
# mute
commands-mute-id-description = ID del giocatore da silenziare.
commands-mute-period-description = Durata del silenziamento (es. 1h, 30m).
commands-mute-reason-description = Motivo del silenziamento.
# unmute
commands-unmute-id-description = ID del giocatore a cui rimuovere il silenziamento.
# votekick
commands-votekick-target-description = Giocatore da espellere (ID o nome).
commands-votekick-reason-description = Motivo dell'espulsione.
# vote
commands-vote-choice-description = Il tuo voto: y (sì), n (no) o c (annulla, solo admin).
# t (team chat)
commands-t-message-description = Messaggio da inviare ai compagni di squadra.
# g (global chat)
commands-g-message-description = Messaggio da inviare a tutti i server.
# tr (translator)
commands-tr-language-description = Codice lingua, 'auto' o 'off'.
# stats
commands-stats-id-description = ID del giocatore per visualizzare le statistiche
# rank
commands-rank-player-description = Giocatore per visualizzare il grado
# map
commands-map-map-description = Nome o numero della mappa.
# maps / maps-text
commands-maps-page-description = Numero di pagina.
commands-maps-text-page-description = Numero di pagina.
# rtv / artv
commands-rtv-map-description = Mappa da votare (opzionale).
commands-artv-map-description = Mappa su cui forzare il cambio immediato.
# ai
commands-ai-state-description = Stato dell'IA: attack (a) o idle (i).
# event / events
commands-events-page-description = Numero di pagina.

# ==============================================================================
# General & Help (continued)
# ==============================================================================
commands-information-description = Mostra informazioni sul server.
commands-info = Informazioni
commands-info-title = [orange]{ -xcore } — Nome server: [orange]{ $server-name }
commands-info-text =
    [accent]XCore[white] è un server [cyan]gratuito[white] per giocare a [accent]Mindustry[white].
    { "" }
    Versione di XCore — [accent]{ $version }[white]
commands-sync-description = Sincronizza la tua partita con il server. Usalo per risolvere errori come unità fantasma.
commands-discord-description = Ti reindirizza al server Discord.
discord-menu-title = [orange]{ -xcore } — Discord
discord-menu-content =
    [white]Gestisci il tuo collegamento Discord qui.
    { "" }
    [white]Stato: { $status }
    [white]Server: [accent]{ $discordUrl }[]
discord-menu-open = Apri Discord
discord-menu-link = Collega account
discord-menu-status = Aggiorna stato
discord-menu-unlink = Scollega account
discord-menu-status-not-linked = [lightgray]non collegato[]
discord-menu-status-linked = [green]{ $discordUsername }[] [gray]({ $discordId })[]
discord-link-menu-title = [orange]{ -xcore } — Collega account Discord
discord-link-menu-content =
    [white]Sul nostro server Discord, esegui il comando slash del bot:
    { "" }
    [accent]/link { $code }[]
    { "" }
    [white]Scade tra: [accent]{ $expireMinutes }[] min
    [white]Discord: [accent]{ $discordUrl }[]
discord-link-menu-refresh = Aggiorna codice
discord-link-menu-copy = Copia codice
discord-link-menu-regenerate = Genera nuovo codice
discord-link-menu-status = Torna al menu Discord
welcome =
    [accent]Benvenuto su { $serverName }!
    [lightgray]Digita [accent]/help[lightgray] per vedere la lista dei comandi
    [lightgray]Digita [accent]/vote [gray]<y/n>[lightgray] per votare l'espulsione di un giocatore
    [lightgray]Digita [accent]/votekick [gray]<ID/nome> <motivo…>[lightgray] per avviare una votazione di espulsione
    [lightgray]Digita [accent]/t [gray]<messaggio…>[lightgray] per inviare un messaggio alla tua squadra
    [lightgray]Digita [accent]/g [gray]<messaggio…>[lightgray] per inviare un messaggio a tutti i server
    [lightgray]Digita [accent]/tr [gray]<lingua/auto>[lightgray] per attivare il traduttore
    [lightgray]Digita [accent]/discord[lightgray] per aprire il menu Discord e collegare il tuo account

# ==============================================================================
# Chat & Social
# ==============================================================================
commands-t-description = Invia un messaggio solo ai compagni di squadra.
commands-t-chat = [{ "#" }{ $color }][Squadra] [coral]> { $badge }[accent]{ $name }[lightgray]: [white]{ $message }
commands-g-description = Invia un messaggio a tutti i server.
commands-a-description = Invia un messaggio solo agli admin.
commands-msg-description = Invia un messaggio privato a un giocatore.
commands-msg-id-description = ID del giocatore.
commands-msg-message-description = Testo del messaggio privato.
commands-reply-description = Risponde all'ultimo giocatore nei messaggi privati.
commands-reply-message-description = Testo della risposta privata.
commands-inbox-description = Apre il menu dei messaggi privati.
commands-inbox-id-description = ID del giocatore.
commands-tr-description = Imposta la lingua del traduttore.
commands-badge-description = Apre il menu dei distintivi e gestisce il tuo distintivo attivo.
commands-tr-success = [accent]La lingua del traduttore è stata impostata su [grey]{ $translatorLanguage }[]!
commands-tr-off = [accent]Il traduttore è [scarlet]disattivato[]!
commands-tr-not-found = [scarlet]⚠ Questa lingua non esiste.
discord-chat-format = [#5865F2][DISCORD][] [lightgray]| [accent]{ $author }[lightgray] >> [white]{ $message }
global-chat-format = [royal][[[orange]GLOBALE [lightgray](da [accent]{ $server }[])[] { $author }[]]: [white]{ $message }
private-message-received = [sky][MP][] [lightgray]da [accent]{ $author } [gray]#{ $pid }[lightgray]: [white]{ $message }
private-message-sent = [sky][MP][] [lightgray]a [accent]{ $target } [gray]#{ $pid }[lightgray]: [white]{ $message }
private-message-unread-count =
    [accent]Hai [white]{ $count }[accent] { $count ->
    [one] messaggio privato non letto
    *[other] messaggi privati non letti
    }.
private-message-join-notification =
    [accent]Hai [white]{ $count }[accent] { $count ->
    [one] messaggio privato non letto
    *[other] messaggi privati non letti
    }. Usa [white]/inbox[accent] per { $count ->
    [one] leggerlo
    *[other] leggerli
    }.
private-message-block-success = [accent]I messaggi privati da [white]{ $target } [gray]#{ $pid }[accent] sono ora bloccati.
private-message-block-already = [lightgray]I messaggi privati da [white]{ $target } [gray]#{ $pid }[lightgray] sono già bloccati.
private-message-unblock-success = [accent]I messaggi privati da [white]{ $target } [gray]#{ $pid }[accent] non sono più bloccati.
private-message-unblock-missing = [lightgray][white]{ $target } [gray]#{ $pid }[lightgray] non è bloccato.
private-message-menu-title = [orange]{ -xcore } — Messaggi privati
private-message-menu-content =
    [white]Pagina [green]{ $page }[] di [green]{ $total }[]
    [white]Non letti: [accent]{ $unread }[]
private-message-menu-empty = [lightgray]La tua casella dei messaggi è vuota.
private-message-menu-entry-unread = [accent]Non letto[] [white]{ $author } [gray]#{ $pid }[] [lightgray]({ $time })[]: [white]{ $message }
private-message-menu-entry-read = [gray]Letto[] [white]{ $author } [gray]#{ $pid }[] [lightgray]({ $time })[]: [white]{ $message }
private-message-details-title = [orange]{ -xcore } — Messaggio
private-message-details-content =
    [white]Da: [accent]{ $author } [gray]#{ $pid }[]
    [white]Ora: [accent]{ $time }[]
    [white]Stato: [accent]{ $status }[]
    { "" }
    [white]{ $message }
private-message-status-unread = non letto
private-message-status-read = letto
private-message-blocked-title = [orange]{ -xcore } — Giocatori bloccati
private-message-blocked-content =
    [white]Pagina [green]{ $page }[] di [green]{ $total }[]
    [white]Bloccati: [accent]{ $count }[]
private-message-blocked-empty = [lightgray]Non hai giocatori bloccati.
private-message-blocked-entry = [white]{ $target } [gray]#{ $pid }[]
private-message-compose = Nuovo messaggio
private-message-blocked = Bloccati
private-message-block = Blocca mittente
private-message-unblock = Sblocca mittente
private-message-reply-title = Rispondi
private-message-reply-message = Inserisci un messaggio per [accent]#{ $pid }[]
private-message-compose-target-title = Nuovo messaggio
private-message-compose-target-message = Inserisci l'ID del giocatore nel formato [accent]#123[]
private-message-compose-body-title = Testo del messaggio
private-message-compose-body-message = Inserisci un messaggio privato per [accent]{ $pid }[]

# ==============================================================================
# Authentication & Admin Access
# ==============================================================================
commands-login-description = Attiva i permessi da admin se uno dei tuoi ruoli su questo server consente l'accesso come staff.
commands-login-incorrect-password = [scarlet]⚠ Password non corretta!
commands-login-success = [green]Permessi da admin concessi.
commands-login-confirmed = [green]Accesso admin da Discord confermato.
commands-login-admin-password-created =
    [green]Password admin creata.
    [red]Non dimenticare la password! Se la dimentichi, dovrai chiedere a un amministratore generale di reimpostarla.
commands-login-request-approval-discord = [accent]Nessuno dei tuoi ruoli su questo server consente l'accesso come staff. Chiedi un ruolo allo staff e riprova.
commands-login-verifying = [lightgray]Verifica della password da amministratore...
commands-login-already-processing = [scarlet]⚠ Una richiesta di accesso è già in elaborazione. Attendi.
commands-login-rate-limited = [scarlet]⚠ Troppi tentativi di accesso falliti. Attendi prima di riprovare.
commands-discord-link-created =
    [green]Codice di collegamento Discord creato: [accent]{ $code }[]
    [lightgray]Sul nostro server Discord, esegui il comando slash del bot [accent]/link { $code }[] entro [accent]{ $expireMinutes }[] min.
    [cyan]{ $discordUrl }
commands-discord-link-confirmed = [green]Account Discord collegato: [accent]{ $discordUsername }[]
commands-discord-link-already-linked = [lightgray]Questo account Mindustry è già collegato. Usa [accent]/discord status[] o [accent]/discord unlink[].
commands-discord-link-error = [scarlet]Impossibile creare il codice di collegamento Discord. Riprova più tardi.
commands-discord-status-not-linked = [lightgray]Il tuo account non è collegato a Discord.
commands-discord-status-linked = [green]Discord collegato: [accent]{ $discordUsername }[] [gray]({ $discordId })[]
commands-discord-unlink-not-linked = [lightgray]Il tuo account non è collegato a Discord.
commands-discord-unlink-success = [green]Collegamento Discord rimosso.
commands-logout-description = Esci. Questo [scarlet]revocherà i tuoi permessi da admin.
commands-logout-successful = [green]Permessi da admin revocati.

# ==============================================================================
# Moderation (Ban, Mute, Kick)
# ==============================================================================
commands-ban-description = Banna un giocatore.
commands-ban-success = { $nickname } [scarlet]bannato
commands-unban-description = Sbanna un giocatore.
commands-unban-success = { $nickname }[accent] #{ $pid } [green]sbannato con successo.
commands-mute-description = Silenzia un giocatore.
commands-mute-success = [accent]{ $nickname } silenziato con successo
commands-unmute-description = Rimuove il silenziamento a un giocatore.
commands-unmute-success = [green]Silenziamento rimosso per []{ $nickname }
commands-alert-description = Mostra un banner di annuncio in evidenza ai giocatori selezionati o a tutti.
commands-toast-description = Mostra una notifica di avviso rapida ai giocatori selezionati.
commands-announcement-description = Trasmette un annuncio periodico tramite chiave o il successivo nella rotazione.
commands-audit-description = Mostra il registro delle azioni dello staff e della moderazione.
ban-content = [scarlet]⚠ Bannato[]
    [accent]{ $nickname }[white] — sei stato bannato permanentemente da questo server.
    [orange]» [accent]ID giocatore: [white]{ $pid }
    [lightgray]Per fare ricorso, visita il canale Discord [gray]{ support-channel }[]:
    [cyan]{ $discordUrl }
ban-cancelled = [accent]Il ban del giocatore [scarlet]{ $nickname }[accent] è stato annullato
tempban-content = [scarlet]⚠ Bannato[]
    { $permanent ->
         [true] [accent]{ $nickname }[white] — sei stato bannato permanentemente da questo server.
        *[false] [accent]{ $nickname }[white] — sei stato bannato temporaneamente da questo server.
    }
    [orange]» [accent]Ban: [white]{ $banId }
    [orange]» [accent]ID giocatore: [white]{ $pid }
    { "" }
    [orange]» [accent]Admin: [white]{ $adminName }
    [orange]» [accent]Motivo: [gold]{ $reason }
    { $permanent ->
         [true] [orange]» [accent]{ audit-menu-duration-permanent }[]
        *[false] [orange]» [accent]Tempo rimasto: { DURATION($duration, style: "full", colored: "true", maxUnits: 2) }
            { "" }[orange]» [accent]Scadenza: [white]{ DATETIME($expireDate, dateStyle: "medium", timeStyle: "short") }
    }
    { "" }
    [lightgray]Per fare ricorso, visita il canale Discord [gray]{ support-channel }[]:
    [lightgray]Indica { $banId } nel tuo ricorso.
    [cyan]{ $discordUrl }
tempban-player-banned = [scarlet] L'admin { $adminName }[scarlet] ha bannato il giocatore [gray]'[]{ $playerName }[gray]'
you-are-muted-by =
    [orange]⚠ Chat limitata[]
    [lightgray]Sei stato silenziato dall'amministratore [accent]{ $adminName }[lightgray].
    [orange]» [accent]Motivo: [gold]{ $reason }
    [orange]» [accent]Tempo rimasto: { DURATION($duration, style: "full", colored: "true", maxUnits: 2) }
you-are-muted =
    [orange]⚠ Chat limitata[]
    [lightgray]Non puoi inviare messaggi finché questo silenziamento è attivo.
    [orange]» [accent]Admin: [white]{ $adminName }
    [orange]» [accent]Motivo: [gold]{ $reason }
    [orange]» [accent]Tempo rimasto: { DURATION($duration, style: "full", colored: "true", maxUnits: 2) }
kick-pirated-game = [accent]Rilevato client non autorizzato. [scarlet]Accesso negato[]. Gioca usando la versione [lime]ufficiale[] da [blue]Steam[], [blue]Google Play[] o [blue]itch.io[].
kick-recently-kicked =
    [accent]Sei stato espulso di recente da questo server.
    Attendi [cyan]{ DURATION($remaining, style: "timer") }[accent] prima di rientrare.
kick-admintools-outdated =
    [green]Versione richiesta di AdminTools: [grey]{ $requiredVersion }[]
    [scarlet]La tua versione di AdminTools: [grey]{ $version }[]
    { "" }
    [cyan]Aggiorna AdminTools per accedere a questo server.
support-channel = #tickets

# ==============================================================================
# Cases: player reports, in-game notices and the /report menu
# ==============================================================================
report-menu-title = [orange]{ -xcore } — Segnala un giocatore
report-menu-pick-player = Scegli un giocatore
report-menu-left-recently = Usciti di recente
report-menu-left-ago = { $minutes } min fa
report-menu-empty = Nessun giocatore da segnalare
report-menu-staff-hint = [lightgray]I reclami sullo staff vanno inviati tramite Discord.
report-menu-pick-reason = Scegli un motivo
report-menu-comment = Commento (opzionale)
report-menu-comment-required = È richiesto un commento per il motivo "Altro".
report-menu-confirm-title = Conferma segnalazione
report-menu-confirm-note = [lightgray]La chat recente e la cronologia di costruzione saranno allegate. Le false segnalazioni vengono punite.
report-menu-next = Avanti
report-menu-back = Indietro
report-menu-send = Invia
report-menu-close = Chiudi
report-result-sent = [green]Segnalazione inviata. Grazie!
report-result-cooldown = [scarlet]Attendi [accent]{ $seconds } s[scarlet] prima della prossima segnalazione.
report-result-same-target = [scarlet]Hai già segnalato questo giocatore di recente.
report-result-daily-limit = [scarlet]Limite giornaliero di segnalazioni raggiunto. Riprova domani.
report-result-invalid-target = [scarlet]Questo giocatore non può essere segnalato.
report-result-playtime = [scarlet]Gioca per altri [accent]{ $minutes } min[scarlet] prima di poter inviare segnalazioni.
report-result-disabled = [scarlet]Le segnalazioni sono disattivate su questo server.
report-result-failed = [scarlet]Impossibile inviare la segnalazione. Riprova.
report-reason-griefing = Griefing
report-reason-toxicity = Tossicità
report-reason-cheating = Trucchi / Cheat
report-reason-nsfw = NSFW
report-reason-evasion = Evasione del ban
report-reason-other = Altro
case-notice-report-received = [lightgray]Segnalazione registrata con il numero [accent]#{ $number }[lightgray]. Grazie!
case-notice-report-resolved = [lightgray]Segnalazione [accent]#{ $number }[lightgray] esaminata: [green]sono stati presi provvedimenti[lightgray]. Grazie.
case-notice-report-rejected =
    { $reason ->
    [no_violation] [lightgray]Segnalazione [accent]#{ $number }[lightgray] esaminata: nessuna violazione rilevata. Grazie.
    [no_evidence] [lightgray]Segnalazione [accent]#{ $number }[lightgray] esaminata: prove insufficienti. Grazie.
    [already_handled] [lightgray]Segnalazione [accent]#{ $number }[lightgray] chiusa: il caso è già stato gestito. Grazie.
    [duplicate] [lightgray]Segnalazione [accent]#{ $number }[lightgray] chiusa: era già stato segnalato in precedenza. Grazie.
    [abuse] [lightgray]Segnalazione [accent]#{ $number }[lightgray] respinta: [scarlet]la segnalazione stessa viola le regole del server.
    *[other] [lightgray]Segnalazione [accent]#{ $number }[lightgray] esaminata: nessun provvedimento preso. Grazie.
    }
case-notice-report-not-accepted = [scarlet]La tua segnalazione non è stata accettata.
case-notice-appeal-accepted = [lightgray]Ricorso [accent]#{ $number }[lightgray] accettato: il ban è stato revocato.
case-notice-appeal-shortened = [lightgray]Ricorso [accent]#{ $number }[lightgray] accettato: la durata del ban è stata ridotta.
case-notice-badge-granted = [lightgray]Richiesta distintivo [accent]#{ $number }[lightgray] approvata: distintivo [green]{ $badge }[lightgray] assegnato.
case-notice-badge-rejected = [lightgray]Richiesta distintivo [accent]#{ $number }[lightgray] rifiutata.
case-notice-map-accepted = [lightgray]Revisione mappa [accent]#{ $number }[lightgray]: mappa [green]{ $map }[lightgray] accettata.
case-notice-map-rejected = [lightgray]Revisione mappa [accent]#{ $number }[lightgray]: mappa [scarlet]{ $map }[lightgray] rifiutata.
case-notice-complaint-closed = [lightgray]La richiesta [accent]#{ $number }[lightgray] è stata chiusa. Grazie.

# ==============================================================================
# Voting (VoteKick)
# ==============================================================================
commands-votekick-description = Vota per espellere un giocatore dal server.
commands-vote-description = Vota nella votazione attiva.
commands-vote-vote-with = [scarlet]⚠ Vota con [orange]/vote <y/n/c>
votekick-vote =
    { $starter } [grey]#[white]{ $starterId }[lightgray] ha votato per espellere { $target } [grey]#[white]{ $targetId }[lightgray] per [orange]{ $reason }[lightgray]. ([accent]{ $votes }[]/[accent]{ $required }[])
    [lightgray]Digita [orange]/vote <y/n>[] per votare.
votekick-left = { $player }[lightgray] è uscito. Il suo voto è stato annullato. ([accent]{ $votes }[]/[accent]{ $required }[])
votekick-fail = [lightgray]Votazione fallita. Voti insufficienti per espellere { $target }[lightgray].
votekick-cancelled = [scarlet]La votazione per espellere { $target }[scarlet] è stata annullata da { $admin }.
votekick-success =
    [orange]Votazione approvata. { $target }[orange] espulso per [scarlet]{ $minutes }[] { $minutes ->
    [one] minuto
    *[other] minuti
    }.

# ==============================================================================
# Maps & RTV
# ==============================================================================
commands-map-description = Statistiche di una mappa specifica.
commands-map-title = [orange]{ -xcore } — Mappa
commands-map-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $name }[] [sky]{ $authorLabel }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Panoramica[]
    [gray]Dimensioni: [white]{ $width }x{ $height } [darkgray]|[gray] Voti: [lime]+{ $like } [darkgray]/[scarlet] -{ $dislike }[]
    { "" }
    [accent]■ Attività[]
    [gray]Partite totali: [white]{ $played } [darkgray]|[gray] Quest'anno: [white]{ $playedYear }[]
    [gray]Ultima partita: [white]{ $lastPlayed }[]
    [gray]Popolarità: [white]{ $popularity } [darkgray]|[gray] Interesse: [white]{ $interest } [darkgray]|[gray] Reputazione: [white]{ $reputation }[]
    { "" }
    [accent]■ Durata delle partite[]
    [gray]Min: [white]{ $min } [darkgray]|[gray] Media: [white]{ $avg } [darkgray]|[gray] Max: [white]{ $max }[]
commands-maps-description = Lista di tutte le mappe su questo server.
commands-maps-title = [orange]{ -xcore } — Mappe
commands-maps-content =
    [gray]Mappa attuale: [accent]{ $current }[]
    [white]Pagina [green]{ $page }[] di [green]{ $total }[]
commands-maps-current-row = { $name } ★
commands-maps-text-description = Lista di tutte le mappe su questo server.
commands-maps-text-start-content =
    [accent]Mappa attuale: []{ $name }[white]
    [orange][gold]Lista mappe [lightgray]{ $page }[gray]/[lightgray]{ $total }
commands-maps-text-content =
    { "" }
    { $index }. [orange] - [white]{ $name }[orange] | [green]{ $reputation }[orange] | [white]{ $width }x{ $height }[orange] | [white]{ $lastPlayed }[orange] | Da: [sky]{ $author }
commands-artv-description = Cambia mappa immediatamente.
commands-artv-map-skipped = { $nickname }[accent] ha saltato la mappa. Mappa successiva: { $name }.
commands-artv-event-skipped = { $nickname }[accent] ha saltato l'evento. Evento successivo: { $name }.
commands-rtv-description = Votazione per cambiare mappa.
commands-vnw-description = Votazione per avviare la prossima ondata in anticipo.
commands-avnw-description = Avvia immediatamente la prossima ondata.
commands-like-description = Vota a favore della mappa attuale (aumenta la reputazione).
commands-dislike-description = Vota contro la mappa attuale.
map-vote-title = [orange]{ -xcore } — [scarlet]PARTITA TERMINATA!
map-vote-content =
    { "" }
    Mappa successiva: [accent]{ $mapName }[] di [accent]{ $author }[white].
    La nuova partita inizia tra [accent]{ $seconds }[white] { $seconds ->
    [one] secondo
    *[other] secondi
    }.
    { "" }
    [cyan]Ti è piaciuta questa mappa?
map-vote-like = [green]👍 Mi piace
map-vote-dislike = [red]👎 Non mi piace
map-vote-like-selected = [gray]Ti è piaciuta
map-vote-dislike-selected = [gray]Non ti è piaciuta
map-rtv = [orange]Votazione
map-artv = [red]Cambio Immediato
map-maps = Mappe
map-maps-back = Torna alla lista mappe
current-map = Mappa attuale
next-map = Mappa successiva

# Map UI Modernized
map-ui-search-hint = Cerca mappa o autore...
map-ui-total = [lightgray]Mappe: [white]{ $count }[]
map-ui-about-title = Informazioni sulla mappa
map-ui-rtv-title = Cambio mappa
map-ui-no-maps-found = [lightgray]Nessuna mappa trovata per questa ricerca.[]
map-ui-by = di [lightgray]{ $author }[]
map-ui-mode = Modalità: [white]{ $mode }[]
map-ui-loading = [gray]Caricamento...[]
map-ui-no-preview = [gray]Nessuna anteprima[]
map-ui-dimensions = [gray]Dimensioni: [white]{ $width } x { $height }[]
map-ui-total-plays = [gray]Partite totali: [white]{ $played } [lightgray]({ $playedYear } quest'anno)[]
map-ui-last-played = [gray]Ultima partita: [white]{ $lastPlayed }[]
map-ui-description = [gray]Descrizione: [lightgray]{ $description }[]
map-ui-no-description = [gray]Descrizione: [lightgray]Nessuna descrizione disponibile.[]

map-ui-col-duration = Durata
map-ui-duration-min = [gray]Min: [white]{ $value }[]
map-ui-duration-avg = [gray]Media: [white]{ $value }[]
map-ui-duration-max = [gray]Max: [white]{ $value }[]

map-ui-col-popularity = Popolarità
map-ui-popularity-score = [gray]Punteggio: [white]{ $value }[]
map-ui-popularity-pop = [gray]Popolarità: [white]{ $value }[]
map-ui-popularity-interest = [gray]Interesse: [white]{ $value }[]

map-ui-col-community = Comunità
map-ui-community-approval = [gray]Approvazione: [green]{ $rate }%[]

map-ui-btn-like = Mi piace ({ $count })
map-ui-btn-dislike = Non mi piace ({ $count })

map-ui-rtv-active-status = [accent]● Votazione in corso: [white]{ $votes }/{ $required }[] [gray](termina tra [white]{ $seconds } s[gray])[]
map-ui-rtv-vote-yes = Vota per questa mappa
map-ui-rtv-start = Avvia votazione per questa mappa
map-ui-admin-rtv = Cambia mappa adesso
map-ui-admin-rtv-confirm = Premi di nuovo per confermare

gamemode-survival = Sopravvivenza
gamemode-attack = Attacco
gamemode-pvp = PvP
gamemode-sandbox = Sandbox
gamemode-editor = Editor
rtv-vote =
    { $nickname }[lightgray] ha votato per cambiare la mappa attuale in [orange]{ $mapName }[lightgray]. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Digita [orange]y[] o [orange]n[] per votare.
rtv-left = { $nickname }[lightgray] è uscito. Il suo voto per cambiare mappa è stato annullato. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
rtv-fail = [lightgray]Votazione fallita. Voti insufficienti per cambiare mappa in [orange]{ $mapName }[].
rtv-success = [orange]Votazione approvata. La mappa [accent]{ $mapName }[] sarà caricata tra [accent]{ $mapLoadDelay }[] { $mapLoadDelay ->
[one] secondo
*[other] secondi
}…
rtv-cancelled = [lightgray]La votazione per cambiare mappa in [orange]{ $mapName }[lightgray] è stata annullata da { $admin }.
vnw-vote =
    { $nickname }[lightgray] ha votato per avviare l'ondata [orange]{ $wave }[lightgray] in anticipo. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Digita [orange]y[] o [orange]n[] per votare.
vnw-left = { $nickname }[lightgray] è uscito. Il suo voto per avviare l'ondata [orange]{ $wave }[lightgray] in anticipo è stato annullato. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
vnw-fail = [lightgray]Votazione fallita. Voti insufficienti per avviare l'ondata [orange]{ $wave }[] in anticipo.
vnw-success = [orange]Votazione approvata. L'ondata [accent]{ $wave }[] sta iniziando ora.
vnw-cancelled = [lightgray]La votazione per avviare l'ondata [orange]{ $wave }[lightgray] in anticipo è stata annullata da { $admin }.
vnw-obsolete = [lightgray]L'ondata [orange]{ $wave }[lightgray] è già iniziata, il risultato della votazione non serve più.

# ==============================================================================
# Statistics & Ranks & Players
# ==============================================================================
commands-player-description = Mostra le statistiche di un giocatore.
commands-settings-description = Apre le tue impostazioni giocatore.
player-menu-player = Giocatore
player-menu-player-title = [orange]{ -xcore } — Statistiche giocatore
player-menu-player-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $customNickname }[] [gray]#{ $pid }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Profilo[]
    [gray]Nome: [white]{ $nickname } [darkgray]|[gray] Admin: [lime]{ $admin }[]
    [gray]Distintivo: [white]{ $activeBadge } [darkgray]|[gray] Sistema: [coral]{ $systemBadge }[]
    [gray]Registrato: [white]{ $accountCreated }[]
    { "" }
    [accent]■ Classifiche di gioco[]
    [gray]Tempo di gioco: [white]{ $totalPlayTime }[]
    [gray]MiniPvP: [sky]{ $pvpRating } [darkgray]|[gray] Vecchio Hexed: [sky]{ $hexedRankName } [gray]({ $hexedPoints } pt) [darkgray]|[gray] Top: [accent]{ $hexedTopRank }[]
    { "" }
    [accent]■ Partite: [white]{ $gamesPlayed } [gray]giocate [darkgray]|[lime] { $gamesWon } [gray]vittorie [darkgray]|[sky] { $winRate }% [gray]tasso di vittoria[]
    [gray]• [white]PvP: { $pvpSummary }[]
    [gray]• [white]Sopravv.: { $survivalSummary }[]
    [gray]• [white]Vecchio Hexed: { $hexedSummary }[]
    { "" }
    [accent]■ Efficienza in combattimento[]
    [gray]Blocchi (Costruiti/Smantellati/Distrutti): [lime]{ $blocksBuilt } [darkgray]/ [orange]{ $blocksDeconstructed } [darkgray]/ [scarlet]{ $blocksDestroyed }[]

# Modern Player Stats UI
player-stats-title = [orange]{ -xcore } — Profilo giocatore
player-stats-tab-overview = Panoramica
player-stats-tab-stats = Statistiche
player-stats-tab-matches = Partite
player-stats-tab-blocks = Blocchi
player-stats-tab-players = Online ({ $count })

player-stats-status-online = [lime]● Online[]
player-stats-status-offline = [gray]○ Offline[]
player-stats-no-bio = [gray]Nessuna biografia inserita.[]
player-stats-loading = [lightgray]Caricamento dati...[]
player-stats-no-stats = [gray]Nessun dato di partita registrato.[]
player-stats-filter-all = Filtro: Tutti
player-stats-filter-admins = Filtro: Solo admin
player-stats-filter-non-admins = Filtro: Senza admin
player-stats-refresh = Aggiorna
player-stats-combat-efficiency = Efficienza di combattimento e costruzione
player-stats-waves-summary = [gray]ondate: max [lime]{ $best }[], media [white]{ $avg }[]
player-stats-hexed-top-placement = [gray]miglior [accent]#{ $best }[], top 3: [sky]{ $top3 }[]
player-stats-max-rank = [gold]★ GRADO MASSIMO RAGGIUNTO ★[]
player-stats-max-league = [gold]★ LEGA MASSIMA RAGGIUNTA ★[]
player-stats-hexed-wins-left = [lightgray]{ $wins } vittorie per [white]{ $rank }[]
player-stats-league-elo-left = [lightgray]{ $elo } ELO per [white]{ $league }[]
player-stats-ratio-legend = [lightgray]Rapporto blocchi[]

player-stats-account-created = [gray]Registrato:[]
player-stats-play-time = [gray]Tempo di gioco:[]
player-stats-legacy-pvp-rating = [gray]Vecchio PvP:[]
player-stats-hexed-rank = [gray]Vecchio Hexed:[]
player-stats-hexed-leaderboard = [gray]Top Hexed:[]

player-stats-total-games = [gray]Partite totali:[]
player-stats-victories = [gray]Vittorie:[]
player-stats-survival-summary = [gray]Sopravvivenza:[]
player-stats-hexed-summary = [gray]Vecchio Hexed:[]

player-stats-blocks-built = [gray]Blocchi costruiti:[]
player-stats-blocks-deconstructed = [gray]Smantellati:[]
player-stats-blocks-destroyed = [gray]Distrutti:[]
player-stats-units-produced = [gray]Unità prodotte:[]
player-stats-units-lost = [gray]Unità perse:[]

player-stats-games-played-value = [white]{ $count }[] [gray]partite[]
player-stats-victories-value = [lime]{ $wins }[] [gray]vittorie[]  [darkgray]|[]  [sky]{ $winRate }%[] [gray]tasso di vittoria[]
player-stats-hexed-points = [gray]({ $points } pt)[]

player-stats-btn-settings = Impostazioni
player-stats-btn-matches = Partite
player-stats-btn-audit = Cronologia
player-stats-btn-players = Online
player-stats-btn-close = Chiudi
player-stats-admin-tag = [coral]<Admin>[]
player-menu-players = Giocatori online
player-menu-players-title = [orange]{ -xcore } — Giocatori online
player-menu-players-content = [white]Pagina [green]{ $page }[] di [green]{ $total }[]
player-menu-players-empty = Nessun giocatore online trovato
player-menu-players-row = [white]{ $nickname } [gray](PID: { $pid })[]
player-menu-settings = Impostazioni
player-menu-settings-title = [orange]{ -xcore } — Impostazioni giocatore
player-menu-settings-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $displayNickname }[] [gray]#{ $pid }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Profilo[]
    [gray]Nome: [white]{ $nickname } [darkgray]|[gray] Visibile: [lime]{ $customNickname }[]
    [gray]Distintivo: [white]{ $activeBadge } [darkgray]|[gray] Sistema: [coral]{ $systemBadge }[]
    { "" }
    [accent]■ Visibilità[]
    [gray]Classifica: [white]{ $leaderboard }[]
    { "" }
    [accent]■ Chat[]
    [gray]Globale: [white]{ $globalChat } [darkgray]|[gray] Discord: [white]{ $discordRelay }[]
    [gray]Traduttore: [white]{ $translatorLanguage }[]
    { "" }
    [accent]■ Lingua[]
    [gray]Lingua: [white]{ $language }[]
player-menu-settings-chat = Impostazioni chat
player-menu-settings-chat-title = [orange]{ -xcore } — Impostazioni chat

# Modern Player Settings Form
player-settings-tab-profile = Profilo
player-settings-tab-chat = Chat
player-settings-tab-badges = Distintivi
player-settings-chat-preview = Anteprima chat
player-settings-chat-preview-sample = Esempio
player-settings-chat-preview-message = Ciao a tutti!
player-settings-symbol-color-mode = Colore del distintivo
player-settings-badges-my = I miei distintivi
player-settings-badges-all = Tutti i distintivi
player-settings-badge-equip = Equipaggia
player-settings-badge-unequip = Rimuovi
player-settings-badge-preview = Anteprima
player-settings-badge-previewing = In anteprima
player-settings-translator-lang = Traduttore chat
player-settings-translator-off = Disattivato
player-settings-badges-empty = Non hai ancora sbloccato alcun distintivo.
player-settings-manage-badges = Gestisci distintivi
player-settings-global-chat = Chat globale
player-settings-discord-relay = Inoltro Discord
player-settings-leaderboard = Mostra classifica
player-settings-language = Lingua
player-settings-saved = [lime]Impostazioni salvate![]
player-settings-reset-feedback = [lightgray]Nome personalizzato ripristinato.[]
player-settings-edit-badges = [accent]Modifica[]
player-settings-tab-language = Lingua
player-settings-identity = Nome e descrizione
player-settings-interface = Schermo
player-settings-leaderboard-hint = La lista dei migliori giocatori sovrapposta alla partita. Funziona in Mini-PvP.
player-settings-global-chat-hint = Messaggi dei giocatori dagli altri server XCore.
player-settings-discord-relay-hint = Messaggi dal canale Discord del server nella chat di gioco.
player-settings-translator-hint = I messaggi degli altri giocatori vengono tradotti nella lingua scelta.
player-settings-language-hint = La lingua dei menu e dei messaggi del server. Auto segue la lingua del gioco.
player-menu-settings-chat-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [accent]■ Visibilità chat[]
    [gray]Chat globale: [white]{ $globalChat }[]
    [gray]Inoltro Discord: [white]{ $discordRelay }[]
    { "" }
    [accent]■ Traduzione[]
    [gray]Lingua del traduttore: [white]{ $translatorLanguage }[]
player-menu-settings-translator-title = [orange]{ -xcore } — Scelta lingua traduttore
player-menu-settings-language-title = [orange]{ -xcore } — Scelta lingua
player-menu-settings-customNickname = Modifica nome
player-menu-settings-customNickname-title = [orange]{ -xcore } — Modifica nome
player-menu-settings-customNickname-message = [lightgray]Lascia vuoto per ripristinare
player-menu-settings-customNickname-reset = [scarlet]Ripristina nome
player-menu-settings-description = Modifica descrizione
player-menu-settings-description-title = [orange]{ -xcore } — Modifica descrizione
player-menu-settings-badges = Distintivi
player-menu-settings-global-chat-on = [green]Chat globale
player-menu-settings-global-chat-off = [red]Chat globale
player-menu-settings-discord-relay-on = [green]Inoltro Discord
player-menu-settings-discord-relay-off = [red]Inoltro Discord
audit-menu-open = Cronologia
audit-menu-actions-open = Azioni
audit-menu-history-title = [orange]{ -xcore } — Cronologia di audit
audit-menu-history-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $player }[] [gray]#{ $pid }[]
    [gray]Voci mostrate: [accent]{ $entriesShown }[]
    [gray]{ $pageState } [darkgray]|[] { $nextState }
    [lightgray]{ $hint }[]
audit-menu-history-page-first = Voci più recenti
audit-menu-history-page-older = Voci più vecchie
audit-menu-tab-sanctions = Sanzioni ricevute
audit-menu-tab-actions = Azioni dello staff
audit-menu-filter-all = Tutte
audit-menu-filter-bans = Ban
audit-menu-filter-mutes = Silenziamenti
audit-menu-filter-warns = Avvisi
audit-menu-filter-other = Altro
audit-menu-btn-back = Torna alla cronologia
audit-menu-btn-copy-id = Copia ID
audit-menu-copy-id-success = L'ID della voce è stato inviato in chat
audit-menu-field-id = ID voce
audit-menu-page = Pagina { $page }
audit-menu-details-unavailable = La voce non è disponibile.
audit-menu-field-target = Giocatore
audit-menu-field-actor = Eseguito da
audit-menu-field-server = Server
audit-menu-field-reason = Motivo
audit-menu-section-time = Tempo
audit-menu-field-occurred = Quando
audit-menu-field-duration = Durata
audit-menu-field-expires = Scade il
audit-menu-btn-revoke = Revoca sanzione
audit-menu-revoke-success = Sanzione revocata con successo.
audit-menu-status-active = ATTIVO
audit-menu-status-expired = SCADUTO
audit-menu-status-permanent = PERMANENTE
audit-menu-history-more = Altre voci disponibili
audit-menu-history-end = Fine della cronologia
audit-menu-history-empty = Nessuna voce di audit per questo giocatore.
audit-menu-history-hint = Seleziona una voce sotto per vedere i dettagli.
audit-menu-actions-title = [orange]{ -xcore } — Azioni di audit
audit-menu-actions-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $player }[] [gray]#{ $pid }[]
    [gray]Voci mostrate: [accent]{ $entriesShown }[]
    [gray]{ $pageState } [darkgray]|[] { $nextState }
    [lightgray]{ $hint }[]
audit-menu-actions-empty = Nessuna azione registrata per questo giocatore.
audit-menu-actions-hint = Seleziona una voce sotto per vedere cosa ha fatto questo giocatore.
audit-menu-summary-row = [accent]{ $action }[] [darkgray]•[] [white]{ $actor }[] [gray]— { $reason }
audit-menu-action-summary-row = [accent]{ $action }[] [darkgray]•[] [white]{ $target }[] [gray]— { $reason }
audit-menu-details-title = [orange]{ -xcore } — Dettagli audit
audit-menu-details-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $player }[] [gray]#{ $pid }[]
    { "" }
    [accent]■ Evento di audit[]
    [gray]Azione: [white]{ $action }[]
    [gray]Autore: [white]{ $actor }[]
    [gray]Motivo: [white]{ $reason }[]
    { "" }
    [accent]■ Tempo[]
    [gray]Avvenuto: [white]{ $occurredAt }[]
    [gray]Durata: [white]{ $duration }[]
    [gray]Scade il: [white]{ $expiresAt }[]
    { "" }
    [accent]■ Metadati[]
    [gray]Audit ID: [white]{ $auditId }[]
audit-menu-unknown-actor = Sconosciuto
audit-menu-unknown-target = Sconosciuto
audit-menu-reason-unspecified = Non specificato
audit-menu-duration-permanent = Permanente
audit-menu-action-ban = Ban
audit-menu-action-unban = Revoca ban
audit-menu-action-mute = Silenziamento
audit-menu-action-unmute = Revoca silenziamento
audit-menu-action-warn = Avviso
audit-menu-action-kick = Espulsione
audit-menu-action-note = Nota
audit-menu-action-quarantine = Quarantena
audit-menu-action-unquarantine = Fine quarantena
player-menu-player-max-rank = Grado massimo raggiunto
player-menu-player-hexed-progress = [gray]Vittorie necessarie per [white]{ $nextRankName }[gray]: [accent]{ $requiredPoints }[]
player-menu-player-no-mode-stats = [gray]nessun dato[]
player-menu-player-pvp-summary = [gray]partite [white]{ $gamesPlayed }[], vittorie [lime]{ $gamesWon }[], [sky]{ $winRate }%[]
player-menu-player-survival-summary = [gray]ondate: max [lime]{ $bestWave }[], media [white]{ $averageWave }[] [gray](partite: { $gamesPlayed })[]
player-menu-player-hexed-summary = [gray]partite [white]{ $gamesPlayed }[], 1º posto [lime]{ $gamesWon }[], miglior posto [accent]#{ $bestPlacement }[]
player-menu-time-days = { $value }g
player-menu-time-hours = { $value }h
player-menu-time-minutes = { $value }m
settings-language-label = Lingua: [green]{ $lang }[]
settings-translator-label = Traduttore: [green]{ $lang }[]

badge-menu-title = [orange]{ -xcore } — Distintivi
badge-menu-content =
    [white]Distintivo di sistema: [green]{ $systemBadge }[]
    [white]Distintivo attivo: [green]{ $activeBadge }[]
    [white]Colore del simbolo: [green]{ $symbolColorMode }[]
badge-menu-empty = Non hai ancora sbloccato alcun distintivo.
badge-menu-row = [white]{ $badge }[] [gray]-[] { $description }
badge-menu-symbol-color-button = Colore del simbolo: [green]{ $mode }[]
badge-menu-symbol-color-title = [orange]{ -xcore } — Colore del simbolo del distintivo
badge-menu-symbol-color-content =
    [white]Modalità attuale: [green]{ $mode }[]
    [lightgray]Scegli come colorare il simbolo del distintivo.
badge-menu-symbol-color-default = Colore predefinito del distintivo
badge-menu-symbol-color-player-color = Simbolo del colore del giocatore
badge-menu-symbol-color-player-color-full = Distintivo intero del colore del giocatore
badge-menu-view-all = Mostra tutti i distintivi
badge-menu-all-title = [orange]{ -xcore } — Tutti i distintivi
badge-menu-all-content = [lightgray]Consulta tutti i distintivi con stato e descrizione.
badge-menu-all-row = [white]{ $badge }[] [gray]-[] [accent]{ $state }[] [gray]-[] { $description }
badge-clear-button = Rimuovi distintivo attivo
badge-state-system = Sistema
badge-state-system-active = Sistema attivo
badge-state-active = Attivo
badge-state-unlocked = Sbloccato
badge-state-locked = Bloccato
badge-set-success = [accent]Distintivo attivo impostato su [green]{ $badge }[].
badge-clear-success = [accent]Distintivo attivo rimosso.
badge-grant-success = [accent]Distintivo [green]{ $badge }[] assegnato a [green]{ $nickname }[][gray]#{ $pid }[].
badge-revoke-success = [accent]Distintivo [green]{ $badge }[] revocato a [green]{ $nickname }[][gray]#{ $pid }[].
badge-already-unlocked = [scarlet]⚠ Il distintivo [accent]{ $badge }[scarlet] è già sbloccato.
badge-not-owned = [scarlet]⚠ Il giocatore non possiede il distintivo [accent]{ $badge }[scarlet].
error-badge-not-found = [scarlet]⚠ Il distintivo [accent]{ $badge }[scarlet] non è stato trovato.
error-badge-not-unlocked = [scarlet]⚠ Il distintivo [accent]{ $badge }[scarlet] non è sbloccato.
error-badge-not-selectable = [scarlet]⚠ Il distintivo [accent]{ $badge }[scarlet] non può essere selezionato a mano.
badge-admin-name = Admin
badge-admin-description = Distintivo automatico per gli amministratori.
badge-developer-name = Sviluppatore
badge-developer-description = Assegnato agli sviluppatori di XCore.
badge-translator-name = Traduttore
badge-translator-description = Assegnato a coloro che aiutano a tradurre XCore.
badge-map-maker-name = Creatore di mappe
badge-map-maker-description = Assegnato agli autori delle mappe utilizzate sul server.
badge-contributor-name = Collaboratore
badge-contributor-description = Assegnato per contributi a XCore o alla comunità.
badge-bug-finder-name = Cacciatore di bug
badge-bug-finder-description = Assegnato per segnalazioni di bug regolari e di qualità.
badge-event-winner-name = Vincitore di eventi
badge-event-winner-description = Assegnato ai vincitori di eventi speciali del server.
badge-veteran-name = Veterano
badge-veteran-description = Assegnato a giocatori di lunga data e rispettati.
badge-season-champion-name = Campione di stagione
badge-season-champion-description = Assegnato per un piazzamento sul podio di una stagione classificata.

commands-lb-description = Attiva/disattiva la classifica.
commands-lb-success =
    { $leaderboardEnabled ->
    [true] [accent]Classifica [green]attivata.
    *[other] [accent]Classifica [scarlet]disattivata.
    }
leaderboard = [blue]Classifica
commands-observer-description = Passa alla modalità spettatore. Rimuove la tua unità attuale e ti sposta nella squadra degli spettatori.
commands-rank-description = Mostra il tuo grado o quello di un altro giocatore.
commands-rank-content =
    { $nickname }
    { $rankTag } [accent]{ $rankName }
    [gold]Vittorie: { $points }/{ $requiredPoints }
commands-ranks-description = Mostra informazioni sul sistema dei gradi.
commands-ranks-content =
    { $rankTag } [accent]{ $rankName }
    [gold]Requisiti: [grey]{ $requiredPoints } [accent]vittorie[]
commands-ranks-footer = Il conteggio delle vittorie aumenta solo sconfiggendo un giocatore del tuo stesso grado o superiore.
commands-top-description = Migliori giocatori.
commands-season-description = Stagione classificata: tempo rimasto, la tua posizione e i vincitori della stagione passata.
commands-matches-description = Le tue partite classificate: risultati, variazioni di punteggio e avversari.
commands-pvp-description = Guida al MiniPvP: il round, le regole del server e come viene calcolato il punteggio.
commands-top-hexed-content = [orange]{ $index }. { $nickname }[accent]: [blue]{ $rankName } [cyan]{ $points } []vittorie
commands-top-pvp-content = [orange]{ $index }. { $nickname }[accent]: [cyan]{ $rating }
top-menu-title = [orange]{ -xcore } — Migliori giocatori: [accent]{ $category }
top-menu-content =
    [lightgray]Seleziona un giocatore per aprire il suo profilo.[]
    [lightgray]Pagina [green]{ $page }[]/[green]{ $totalPages }[] [gold]•[] [lightgray]Giocatori: [green]{ $totalEntries }[]
    { $selfRankLine }
top-menu-empty =
    [accent]Categoria: [green]{ $category }[]
    [gray]Nessun giocatore trovato finora.
top-menu-categories-title = [orange]{ -xcore } — Categoria della classifica
top-menu-categories-content =
    [lightgray]Scegli quale classifica mostrare.[]
    [lightgray]Attuale: [green]{ $category }[]
top-menu-category-button = [accent]Categoria: [green]{ $category }[]
top-menu-category-mini-pvp = MiniPvP
top-menu-category-playtime = Tempo di gioco
top-menu-category-hexed = Hexed
top-menu-self-rank-known = [lightgray]La tua posizione: [accent]#{ $rank }[]
top-menu-self-rank-unknown = [lightgray]La tua posizione: [gray]non trovata[]
top-menu-entry-mini-pvp = { $rankLabel } { $leagueIcon } [accent]{ $nickname }[] [gray]—[] [sky]{ $value }[]
top-menu-entry-playtime = { $rankLabel } [accent]{ $nickname }[] [gray]—[] [green]{ $value }[]
top-menu-entry-hexed = { $rankLabel } [accent]{ $nickname }[] [gray]—[] [violet]{ $rankName }[] [gold]•[] [cyan]{ $value }[]
top-menu-total-count = { $count } giocatori
top-menu-btn-find-self = Trovami
top-menu-on-this-page = in questa pagina
top-menu-unranked = Non sei ancora classificato in questa categoria
top-menu-self-rank-line = La tua posizione: { $rank }
top-menu-score-points = { $points } pt
top-menu-score-minutes = { $time }

# ==============================================================================
# Game Modes (Hexed, PvP, Surrender, AI)
# ==============================================================================
commands-surrender-description = Arrenditi in Hexed. Distrugge la tua squadra attuale, rimuove la tua unità e ti sposta tra gli spettatori.
commands-surrender-success = [green]Ti sei arreso e ora stai assistendo alla partita
commands-observer-success = [green]Ora stai assistendo alla partita
commands-observer-exit-success = [green]Non stai più assistendo alla partita
commands-ai-description = Controlla l'IA.
commands-ai-usage = [red]attack(a) []o [accent]idle(i)
hexed-popup = [blue]{ DURATION($remaining, style: "timer") }[] al termine della partita.
hexed-eliminated = { $nickname } [gold]è stato [scarlet]eliminato[]!
hexed-leaderboard-content = [orange]{ $index }. { $nickname }[accent]: [cyan]{ $hexes } [accent]esagoni
hexed-ranks-newbie = Novellino
hexed-ranks-regular = Abituale
hexed-ranks-advanced = Avanzato
hexed-ranks-veteran = Veterano
hexed-ranks-davastator = Devastatore
hexed-ranks-the_legend = La Leggenda
hexed-game-over-header = Partita terminata. Vincitori:
hexed-game-over-winner-row =
    [orange]{ $index }. { $name }[][accent]: [cyan]{ $cores } { $cores ->
    [one] esagono
    *[other] esagoni
    }
hexed-game-over-no-winners = Partita terminata. Purtroppo non è stato trovato alcun vincitore.
hexed-game-over-restart = Nuova partita tra 10 secondi…
rating_league_scrap = Rottami
rating_league_copper = Rame
rating_league_lead = Piombo
rating_league_graphite = Grafite
rating_league_silicon = Silicio
rating_league_titanium = Titanio
rating_league_thorium = Torio
rating_league_plastanium = Plastanio
rating_league_phase_fabric = Tessuto di fase
rating_league_surge_alloy = Lega di sovratensione
pvp-team-won = La tua squadra ha vinto. Il tuo punteggio è aumentato di { $increased }
pvp-team-lose = La tua squadra ha perso. Il tuo punteggio è diminuito di { $reduced }
pvp-match-settlement-win = [accent]■ Risultato partita: [green]Vittoria![] Il tuo punteggio: [stat]{ $oldRating }[] -> [stat]{ $newRating }[] [green](+{ $delta })[] { $leagueIcon } { $leagueName }
pvp-match-settlement-loss = [accent]■ Risultato partita: [scarlet]Sconfitta![] Il tuo punteggio: [stat]{ $oldRating }[] -> [stat]{ $newRating }[] [scarlet]({ $delta })[] { $leagueIcon } { $leagueName }
pvp-match-settlement-draw = [accent]■ Risultato partita: [yellow]Pareggio![] Il tuo punteggio: [stat]{ $oldRating }[] -> [stat]{ $newRating }[] [yellow]({ $delta })[] { $leagueIcon } { $leagueName }
pvp-match-settlement-exempt = [accent]■ Risultato partita: [lightgray]Esente da variazioni di punteggio (tempo di gioco attivo insufficiente).[]
pvp-match-settlement-details = [lightgray]Dettagli della partita: [white]/matches[][]
season-ending-soon = [accent]■ [white]{ $ladder }[]: la stagione { $number } terminerà tra [stat]{ $remaining }[]. Fai valere le tue ultime partite!
season-started = [accent]■ [white]{ $ladder }[]: la stagione { $previous } è conclusa. È iniziata la stagione { $number }!
season-reset-soft = [lightgray]I punteggi sono stati riavvicinati al valore iniziale e i contatori delle partite azzerati.[]
season-reset-hard = [lightgray]Tutti ripartono dal punteggio iniziale.[]
season-reset-none = [lightgray]I punteggi rimangono invariati; contatori partite azzerati.[]
season-title = Stagione { $number }
top-menu-scope-current = { $season } [lightgray]· termina tra { $remaining }[]
top-menu-scope-past = { $season } [gray]· { $from } – { $to }[]
ladder-profile-standing = { $leagueIcon } [white]{ $league }[] [accent]{ $rating } ELO[]
ladder-profile-headline = [accent]{ $icon }[] [lightgray]{ $ladder }:[] { $standing }
ladder-profile-unplaced = [gray]nessuna partita classificata in questa stagione[]
ladder-profile-matches = [lightgray]Partite:[] [white]{ $matches }[]
ladder-profile-wins = [lightgray]Vittorie:[] [white]{ $wins }[] [gray]({ $rate }%)[]
ladder-profile-rank = [lightgray]Posizione:[] [accent]#{ $rank }[]
ladder-profile-peak = [lightgray]Picco:[] [white]{ $rating }[]
ladder-profile-season = [lightgray]{ $season } · termina tra [white]{ $remaining }[][]
ladder-profile-season-closing = [lightgray]{ $season } · conteggio dei risultati in corso[]
ladder-profile-history = [gray]{ $season } — { $rank }, { $league }, { $rating } ELO[]
season-menu-title = Stagioni classificate
season-menu-card-title = { $ladder } — { $season }
season-menu-ends = [lightgray]Termina tra [white]{ $remaining }[] ({ $date })[]
season-menu-closing = [lightgray]La stagione è finita, i risultati sono in fase di conteggio.[]
season-menu-participants = [lightgray]Giocatori in questa stagione: [white]{ $count }[][]
season-menu-you = [lightgray]Tu:[] { $standing }
season-menu-previous = [lightgray]{ $season } — vincitori:[]
season-menu-podium-entry = [gold]{ $place }.[] [white]{ $name }[] [gray]—[] { $league } [accent]{ $rating }[]
season-menu-open-top = Classifica
season-menu-prizes = [lightgray]Premi di questa stagione:[]
season-menu-prize-entry = [gold]{ $places }.[] [white]{ $prize }[]
season-menu-podium-prizes = [gray] · [gold]{ $prizes }[]
prize-grant-line = [lightgray]{ $season } · premio:[] [gold]{ $prize }[] [gray]({ $status })[]
prize-status-pending = in attesa di consegna
prize-status-granted = ricevuto
prize-status-delivered = consegnato
prize-status-failed = un admin se ne occuperà
season-menu-empty = Non ci sono ancora stagioni classificate.
pvp-hud-status = [accent]MiniPvP[] | [stat]In vita:[] { $teams } | [gray]{ $time }[]
pvp-leaderboard-content = [orange]{ $index }. { $nickname }[accent]:[cyan] { $rating } [accent]punti
pvp-you-spectator = [scarlet]Sei stato eliminato. Attendi la prossima partita.

# MiniPvP guide (/pvp info)
pvp-guide-title = Come si gioca qui
pvp-guide-subtitle = Normale PvP di Mindustry: le squadre costruiscono basi e distruggono i nuclei avversari. Ecco cosa distingue questo server e come funziona il punteggio.
pvp-guide-hint = Riapri: [accent]/pvp info[]
pvp-guide-join-hint = [lightgray]Regole e punteggio MiniPvP:[] [accent]/pvp info[]
pvp-guide-menu-button = Come giocare a MiniPvP
pvp-guide-tab-round = Round
pvp-guide-tab-rules = Regole
pvp-guide-tab-rating = Punteggio
pvp-guide-stat-minutes = minuti giocati
pvp-guide-stat-teams = squadre in gioco
pvp-guide-stat-players = giocatori nelle squadre
pvp-guide-stat-zone = blocchi attorno al nucleo
pvp-guide-stat-unitcap = limite unità
pvp-guide-stat-banned = blocchi vietati
pvp-guide-stat-start = punteggio iniziale
pvp-guide-stat-top = migliori giocatori
pvp-guide-stat-matches = cronologia partite

pvp-guide-now-title = In questo momento
pvp-guide-now-map = Mappa: [accent]{ $map }[]
pvp-guide-now-players = { $count } { $count ->
[one] giocatore
*[other] giocatori
}
pvp-guide-win-title = Come vincere
pvp-guide-win-text = Distruggi tutti i nuclei nemici. Vince l'ultima squadra a cui restano nuclei e giocatori. Una squadra senza nessuno viene eliminata.
pvp-guide-out-title = Eliminato: guarda fino alla fine
pvp-guide-out-text =
    La tua squadra ha perso l'ultimo nucleo: assisti fino al prossimo round.
    Sei uscito a metà round e sei rientrato: tornerai nella stessa squadra.
pvp-guide-commands-title = Comandi
pvp-guide-cmd-info = questa finestra
pvp-guide-cmd-top = classifica
pvp-guide-cmd-season = stagione e premi
pvp-guide-cmd-matches = le tue partite e i punteggi
pvp-guide-cmd-rtv = vota per cambiare mappa
pvp-guide-cmd-observer = guarda da fuori

pvp-guide-zone-title = Vietato costruire vicino ai nuclei nemici
pvp-guide-zone-text = Non puoi costruire a meno di [accent]{ $tiles }[] blocchi da un nucleo nemico. Per avanzare, distruggi prima quel nucleo.
pvp-guide-zone-polygon-title = Il territorio è diviso tra i nuclei
pvp-guide-zone-polygon-text = Puoi costruire solo dove il nucleo più vicino è tuo. Per prendere territorio nemico, distruggi il nucleo a cui appartiene.
pvp-guide-unitcap-title = Limite unità
pvp-guide-unitcap-text = La squadra inizia con un limite di [accent]{ $cap }[], e ogni nucleo lo aumenta.
pvp-guide-unitcap-fixed-title = Limite unità
pvp-guide-unitcap-fixed-text = La squadra ha al massimo [accent]{ $cap }[] unità, a prescindere da quanti nuclei possiede.
pvp-guide-multipliers-title = Bilanciamento differente
pvp-guide-multipliers-text = Su questa mappa questi valori differiscono dal normale:
pvp-guide-rule-unit-damage = Danno delle unità
pvp-guide-rule-unit-health = Salute delle unità
pvp-guide-rule-unit-cost = Costo delle unità
pvp-guide-rule-unit-build-speed = Velocità di produzione unità
pvp-guide-rule-block-damage = Danno degli edifici
pvp-guide-rule-block-health = Salute degli edifici
pvp-guide-rule-build-cost = Costo degli edifici
pvp-guide-rule-build-speed = Velocità di costruzione
pvp-guide-banned-blocks-title = Blocchi vietati
pvp-guide-banned-units-title = Unità vietate
pvp-guide-other-title = Inoltre
pvp-guide-other-fog = Nebbia di guerra: vedi solo ciò che vedono i tuoi edifici e le tue unità.
pvp-guide-other-capture = Un nucleo distrutto passa alla squadra che lo ha distrutto.
pvp-guide-other-reactors = I reattori non esplodono.
pvp-guide-vanilla-title = Regole standard
pvp-guide-vanilla-text = Questa mappa segue le regole del PvP standard di Mindustry.

pvp-guide-elo-title = Punteggio ELO
pvp-guide-elo-text = Tutti iniziano da [accent]{ $start }[]. Una vittoria fa salire il tuo punteggio, una sconfitta lo fa scendere. Di quanto dipende dal punteggio della tua squadra e degli avversari: battere squadre più forti dà di più.
pvp-guide-leave-title = Uscire non evita la sconfitta
pvp-guide-leave-text = Se la tua squadra perde, perdi l'intero punteggio, anche se sei uscito o passato a spettatore prima. Non perdi nulla solo se hai giocato meno di [accent]{ $seconds }[] s.
pvp-guide-unrated-title = Quando il punteggio non cambia
pvp-guide-unrated-text =
    Un round più breve di [accent]{ $seconds }[] s o con meno di [accent]{ $players }[] giocatori classificati non è valido.
    Un vincitore deve giocare almeno il [accent]{ $winner }%[] del round, altrimenti il punteggio non sale.
    Entrato tardi in una squadra sconfitta con meno del [accent]{ $late }%[] del round giocato: il tuo punteggio non scende.
pvp-guide-seasons-title = Leghe e stagioni
pvp-guide-seasons-text = Il tuo punteggio determina la tua lega. Le classifiche vanno a stagioni: alla fine i migliori ricevono premi. Risultati di ogni partita: [accent]/matches[].

# ==============================================================================
# Events & Notifications
# ==============================================================================
player-joined-pid = { $nickname } [grey]#[white]{ $pid }[grey] [accent]è entrato.
player-joined-username = { $nickname } [grey]@[white]{ $username }[grey] [accent]è entrato.
player-joined-both = { $nickname } [accent]@{ $username } [grey]#[white]{ $pid }[grey] [accent]è entrato.
player-joined-none = { $nickname } [accent]è entrato.

player-left-pid = { $nickname } [grey]#[white]{ $pid }[grey] [accent]è uscito.
player-left-username = { $nickname } [grey]@[white]{ $username }[grey] [accent]è uscito.
player-left-both = { $nickname } [accent]@{ $username } [grey]#[white]{ $pid }[grey] [accent]è uscito.
player-left-none = { $nickname } [accent]è uscito.

player-settings-identity-mode = [accent]Identificatore negli annunci:[]
player-settings-identity-mode-pid = Solo ID (#12)
player-settings-identity-mode-username = Solo nome utente (@Steve)
player-settings-identity-mode-both = Entrambi (@Steve #12)
player-settings-identity-mode-none = Nascosto

notification-votekick-playtime =
    [accent]Congratulazioni! Hai giocato per [lightgray]{ $votekickPlayTime }[] { $votekickPlayTime ->
    [one] minuto
    *[other] minuti
    } e ora puoi avviare votazioni di espulsione.
notification-global-chat-playtime =
    [accent]Congratulazioni! Hai giocato per [lightgray]{ $globalChatPlayTime }[] { $globalChatPlayTime ->
    [one] minuto
    *[other] minuti
    } e ora puoi scrivere nella chat globale.
    [lightgray]Digita [accent]/g [gray]<messaggio…>[lightgray] per inviare un messaggio.
notification-admin-kick = { $admin }[accent] ha espulso { $target }[].
notification-admin-wave-skip = { $admin }[accent] ha saltato l'ondata.
server-restart-countdown =
    Riavvio tra { $seconds ->
    [one] { $seconds } secondo
    *[other] { $seconds } secondi
    }
like-map-success = [green]Ti piace questa mappa!
like-map-changed = [green]Hai cambiato opinione: ora ti piace!
dislike-map-success = [orange]Non ti piace questa mappa.
dislike-map-changed = [orange]Hai cambiato opinione: ora non ti piace.
like-event-success = [green]Ti piace questo evento!
like-event-changed = [green]Hai cambiato opinione: ora ti piace!
dislike-event-success = [orange]Non ti piace questo evento.
dislike-event-changed = [orange]Hai cambiato opinione: ora non ti piace.

# ==============================================================================
# Events (Server)
# ==============================================================================
commands-event-description = Menu di gestione degli eventi.
commands-events-description = Lista di tutti gli eventi sui server.
event-events = Eventi
event-menu-main = Eventi principali
event-menu-event = Evento
event-menu-create-start-default = Evento di { $playerName }
event-menu-create-start-map = Crea evento per questa mappa
event-ui-title = Eventi
event-ui-server-time = Ora del server: { $zone }
event-ui-tab-upcoming = Prossimi
event-ui-tab-major = Grandi
event-ui-tab-past = Passati
event-ui-tab-mine = I miei
event-ui-now-running = In corso
event-ui-now-vote = Votazione
event-ui-empty-upcoming = Nessun evento pianificato finora. Crea il primo!
event-ui-empty-major = Nessun grande evento pianificato finora.
event-ui-empty-past = Nessun evento si è ancora concluso.
event-ui-empty-mine = Non hai ancora creato alcun evento.
event-ui-create = Crea evento
event-ui-load-failed = Impossibile caricare gli eventi. Riprova.
event-ui-state-active = In corso
event-ui-state-active-until = In corso fino alle { $time }
event-ui-state-finished = Concluso
event-ui-state-finished-at = Concluso il { $date }
event-ui-state-unscheduled = Senza data
event-ui-major = Grande
event-ui-major-hint = Contrassegnato da una stella e raggruppato in una scheda a parte
event-ui-temporary = Temporaneo
event-ui-temporary-hint = Termina automaticamente all'orario di fine
event-ui-about = Informazioni sull'evento
event-ui-no-description = Nessuna descrizione.
event-ui-author = Autore: { $author }
event-ui-created = Creato il: { $date }
event-ui-map-schedule = Mappa e orari
event-ui-map-none = Nessuna mappa
event-ui-map-pick = Scegli una mappa
event-ui-planned-start = Inizio: { $time }
event-ui-planned-end = Fine: { $time }
event-ui-started = Iniziato: { $time }
event-ui-ended = Terminato: { $time }
event-ui-not-set = non impostato
event-ui-open-map = Apri mappa
event-ui-rating = Valutazione
event-ui-launch = Avvio
event-ui-launch-hint = I giocatori avviano gli eventi tramite votazione. Inizia al prossimo cambio mappa.
event-ui-launch-again = Questo evento si è già tenuto. Può essere riavviato.
event-ui-launch-active = L'evento è in corso proprio adesso.
event-ui-launch-voting = Votazione in corso: { $votes } di { $required }. Scrivi y o n in chat.
event-ui-launch-busy = Un'altra votazione è in corso. Attendi che termini.
event-ui-vote = Avvia votazione
event-ui-vote-busy = Una votazione è già in corso.
event-ui-vote-stopped = La votazione è stata interrotta.
event-ui-force = Avvia subito
event-ui-force-confirm = Vuoi davvero avviare?
event-ui-stop = Ferma evento
event-ui-stop-confirm = Vuoi davvero fermare l'evento?
event-ui-stop-vote = Ferma votazione
event-ui-stopped = L'evento è stato interrotto.
event-ui-to-list = Alla lista
event-ui-edit = Modifica
event-ui-editor-new = Nuovo evento
event-ui-editor-edit = Modifica evento
event-ui-texts = Nome e descrizione
event-ui-name = Nome
event-ui-description = Descrizione
event-ui-description-hint = Di cosa tratta l'evento
event-ui-map = Mappa
event-ui-schedule = Orario e tipo
event-ui-start = Inizio
event-ui-end = Fine
event-ui-save = Salva
event-ui-saved = L'evento è stato salvato.
event-ui-draft-restored = La tua bozza di evento è stata ripristinata.
event-ui-maps-title = Scegli mappa
event-ui-to-editor = All'editor
event-ui-time-start = Inizio dell'evento
event-ui-time-end = Fine dell'evento
event-ui-time-unset = Non impostato
event-ui-time-unset-hint = Impostalo con le frecce o tramite un pulsante in basso
event-ui-time-hours = { $value } h
event-ui-time-minutes = { $value } min
event-ui-time-now = Adesso
event-ui-time-in-hour = Tra un'ora
event-ui-time-today-evening = Oggi alle 20:00
event-ui-time-tomorrow-noon = Domani alle 12:00
event-ui-time-plus-hour = +1 h
event-ui-time-plus-three-hours = +3 h
event-ui-time-plus-day = +1 giorno
event-ui-time-apply = Fatto
event-ui-time-clear = Cancella
event-ui-error-no-map = Scegli prima una mappa.
event-ui-error-no-end = Un evento temporaneo necessita di un orario di fine.
event-ui-error-end-before-start = La fine deve essere successiva all'inizio.
event-ui-error-not-allowed = Non puoi modificare questo evento.
event-ui-error-gone = Questo evento non esiste più.
event-ui-error-save-failed = Impossibile salvare l'evento. Riprova.
vote-event-vote =
    { $nickname }[lightgray] ha votato per cambiare l'evento attuale in [orange]{ $name }[lightgray]. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Digita [orange]y[] o [orange]n[] per votare.
vote-event-left = { $nickname }[lightgray] è uscito. Il suo voto per cambiare evento è stato annullato. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
vote-event-fail = [lightgray]Votazione fallita. Voti insufficienti per cambiare evento in [orange]{ $name }[].
vote-event-success = [orange]Votazione approvata. L'evento [accent]{ $name }[] sarà caricato al prossimo cambio mappa.
vote-event-cancelled = [lightgray]La votazione per cambiare evento in [orange]{ $name }[lightgray] è stata annullata dall'amministratore { $admin }.
event-end = L'evento [green]{ $name }[] è terminato!

# ==============================================================================
# Errors
# ==============================================================================
error-access-denied = [scarlet]⚠ Accesso negato.
error-ip-changed = [scarlet]⚠ Il tuo indirizzo IP è cambiato. I privilegi da admin sono stati revocati.
error-not-enough-params = [scarlet]⚠ Parametri di posizione insufficienti.
error-player-not-found = [scarlet]Giocatore non trovato.
error-player-not-teammate = [scarlet]⚠ Questo giocatore non fa parte della tua squadra.
error-player-admin = [scarlet]⚠ Non provare a espellere un admin. ⚠
error-already-voted = [scarlet]⚠ Hai già votato. Calmati.
error-playtime-requirement =
    [scarlet]⚠ Devi giocare per almeno { $time } { $time ->
    [one] minuto
    *[other] minuti
    } per usare questa funzione.
error-globalchat-total-playtime =
    [scarlet]⚠ Per scrivere nella chat globale devi giocare per { $globalChatPlayTime } { $globalChatPlayTime ->
    [one] minuto
    *[other] minuti
    }.
error-votekick-total-playtime =
    [scarlet]⚠ Per avviare una votazione di espulsione devi giocare per { $votekickPlayTime } { $votekickPlayTime ->
    [one] minuto
    *[other] minuti
    }.
error-vote-yourself = [scarlet]⚠ Non puoi votare nella tua stessa votazione.
error-vote-in-progress = [scarlet]⚠ Una votazione è già in corso.
error-no-voting = [scarlet]⚠ Nessuna votazione in corso al momento.
error-wave-vote-unavailable = [scarlet]⚠ Avviare una nuova ondata in anticipo è possibile solo nelle modalità a ondate.
error-no-map = [scarlet]⚠ Nessuna mappa impostata.
error-map-not-event = [scarlet]⚠ La mappa non fa parte dell'evento attuale.
error-map-not-found = [scarlet]⚠ Mappa non trovata! [accent]Usa [cyan]/maps[] per vedere tutte le mappe disponibili.
error-maps-empty = [scarlet]⚠ La lista delle mappe è vuota.
error-event-not-found = [scarlet]⚠ Evento non trovato! [accent]Usa [cyan]/events[] per vedere gli eventi disponibili.
error-page-between = [scarlet]⚠ 'page' deve essere un numero compreso tra[orange] 1[] e [orange]{ $totalPages }[].
error-page-number = [scarlet]'page' deve essere un numero.
error-wrong-number = [scarlet]⚠ Formato del numero non valido.
error-wrong-period-format = [scarlet]⚠ Formato della durata non valido. Esempio: 1h 30m, 30 ({ hours })
error-invalid-id = [scarlet]⚠ ID giocatore non valido.
error-spectator = [scarlet]⚠ Sei uno spettatore e non puoi usare questo comando.
error-admin-password-too-short = [scarlet]⚠ La password da amministratore deve contenere almeno 8 caratteri.
error-wrong-admin-password = [scarlet]⚠ Password da admin non corretta.
error-internal = [scarlet]Errore interno.
error-processing-request = [scarlet]Si è verificato un errore durante l'elaborazione della richiesta.
error-no-access = [scarlet]⚠ Nessun accesso.
error-nickname-too-long = [scarlet]⚠ Nome utente troppo lungo. Massimo { $max } caratteri visibili.
error-private-message-invalid-pid = [scarlet]⚠ PID del messaggio privato non valido. Usa il formato [lightgray]#123[].
error-private-message-self = [scarlet]⚠ Non puoi inviare un messaggio privato a te stesso.
error-private-message-empty = [scarlet]⚠ Il messaggio non può essere vuoto.
error-private-message-too-long = [scarlet]⚠ Il messaggio è troppo lungo. Massimo { $max } caratteri.
error-private-message-cooldown = [scarlet]⚠ Attendi { DURATION($seconds) } prima di inviare un altro messaggio privato.
error-private-message-target-unavailable = [scarlet]⚠ Questo giocatore non può ricevere messaggi privati al momento.
error-private-message-no-reply-target = [scarlet]⚠ Nessun contatto recente a cui rispondere.
error-private-message-not-found = [scarlet]⚠ Messaggio non trovato.
error-private-message-block-self = [scarlet]⚠ Non puoi bloccare te stesso.
error-private-message-block-limit = [scarlet]⚠ Raggiunto il limite della lista di blocco ({ $limit }).
ban-menu-duration-title = [orange]{ -xcore } - Durata del ban
ban-menu-duration-message = Inserisci la durata del ban per { $nickname }. Esempio: 1d, 12h, 30m
ban-menu-reason-title = [orange]{ -xcore } - Motivo del ban
ban-menu-reason-message = Inserisci il motivo del ban per { $nickname }. Lascia vuoto per il motivo predefinito.
ban-menu-confirm-title = [orange]{ -xcore } - Conferma ban
ban-menu-confirm-content =
    [white]Giocatore: { $nickname }[]
    [white]Durata: [accent]{ $duration }[]
    [white]Motivo: [accent]{ $reason }[]
ban-menu-confirm-action = [scarlet]Banna giocatore
error-invalid-syntax = [scarlet]⚠ Sintassi del comando non valida. Uso: [lightgray]/'{ $syntax }'.
error-invalid-sender = [scarlet]⚠ Mittente del comando non valido. Questo comando richiede: '[lightgray]{ $type }[]'.
error-argument-parse-generic = [scarlet]⚠ Argomento non valido: '{ $error }'.
exception-unexpected = [scarlet]⚠ Si è verificato un errore interno durante l'esecuzione di questo comando.
exception-invalid-argument = [scarlet]⚠ Argomento del comando non valido: '{ $cause }'.
exception-no-such-command = [scarlet]⚠ Comando sconosciuto.
exception-no-permission = [scarlet]⚠ Accesso negato.
exception-invalid-sender = [scarlet]⚠ '{ $actual }' non può eseguire questo comando. Mittente richiesto: [lightgray]{ $expected }[].
exception-invalid-sender-list = [scarlet]⚠ '{ $actual }' non può eseguire questo comando. Mittenti consentiti: [lightgray]{ $expected }[].
exception-invalid-syntax = [scarlet]⚠ Sintassi del comando non valida. Uso: [lightgray]/'{ $syntax }'.
argument-parse-failure-boolean = [scarlet]⚠ Impossibile interpretare un valore booleano da '{ $input }'.
argument-parse-failure-number = [scarlet]⚠ '{ $input }' non è un numero valido nell'intervallo [{ $min }, { $max }].
argument-parse-failure-char = [scarlet]⚠ '{ $input }' non è un carattere valido.
argument-parse-failure-enum = [scarlet]⚠ '{ $input }' non è un'opzione valida. Consentiti: [lightgray]{ $acceptableValues }
argument-parse-failure-string = [scarlet]⚠ Formato testo non valido per '{ $input }'.
argument-parse-failure-uuid = [scarlet]⚠ Formato UUID non valido: '{ $input }'.
argument-parse-failure-regex = [scarlet]⚠ Il testo '{ $input }' non corrisponde al modello '{ $pattern }'.
argument-parse-failure-color = [scarlet]⚠ '{ $input }' non è un colore valido.
argument-parse-failure-duration = [scarlet]⚠ '{ $input }' non è un formato di durata valido.
argument-parse-failure-aggregate-missing = [scarlet]⚠ Componente mancante '{ $component }'.
argument-parse-failure-aggregate-failure = [scarlet]⚠ Componente non valido '{ $component }': '{ $failure }'.
argument-parse-failure-either = [scarlet]⚠ Impossibile determinare { $primary } o { $fallback } da '{ $input }'.
argument-parse-failure-flag-unknown = [scarlet]⚠ Opzione sconosciuta: '{ $flag }'.
argument-parse-failure-flag-duplicate = [scarlet]⚠ Opzione duplicata: '{ $flag }'.
argument-parse-failure-flag-duplicate-flag = [scarlet]⚠ Opzione duplicata: '{ $flag }'.
argument-parse-failure-flag-no-flag-started = [scarlet]⚠ Nessuna opzione iniziata. Impossibile elaborare '{ $input }'.
argument-parse-failure-flag-missing-argument = [scarlet]⚠ Argomento mancante per l'opzione: '{ $flag }'.
argument-parse-failure-flag-no-permission = [scarlet]⚠ Non hai il permesso di usare l'opzione '{ $flag }'.
argument-parse-failure-selector-syntax = [scarlet]⚠ Selettore non valido '{ $input }': [lightgray]{ $reason }
argument-parse-failure-selector-no-such-target = [scarlet]⚠ Nessun bersaglio corrisponde a '{ $input }'.
argument-parse-failure-selector-too-many-targets = [scarlet]⚠ '{ $input }' corrisponde a più bersagli, ma questo comando ne richiede uno solo.
argument-parse-failure-selector-denied = [scarlet]⚠ I selettori non sono consentiti qui: [lightgray]{ $reason }
argument-parse-failure-selector-kind-not-allowed = [scarlet]⚠ Il selettore '{ $kind }' non è consentito qui.
argument-parse-failure-selector-sender-required = [scarlet]⚠ Il selettore '{ $kind }' può essere usato solo in gioco.
argument-parse-failure-selector-limit-exceeded = [scarlet]⚠ Il selettore corrisponde a { $count } bersagli, il limite è { $limit }.
argument-parse-failure-team = [scarlet]⚠ Squadra '{ $input }' non trovata.
argument-parse-failure-content = [scarlet]⚠ '{ $input }' non è un valore valido di tipo { $type }.

# ==============================================================================
# Button Status
# ==============================================================================
finished = terminato
finished-neutral = [orange]Terminati
finished-active = [green]Terminati
finished-inactive = [red]Terminati
major = Principale
major-neutral = [orange]Principali
major-active = [green]Principali
major-inactive = [red]Principali
active = Attivo
active-neutral = [orange]Attivi
active-active = [green]Attivi
active-inactive = [red]Attivi
admin = Admin
admin-neutral = [orange]Admin
admin-active = [green]Admin
admin-inactive = [red]Admin
player-leaderboard-active = [green]Classifica: attivata[]
player-leaderboard-inactive = [red]Classifica: disattivata[]

# ==============================================================================
# Miscellaneous
# ==============================================================================
hours = ore
days = giorni
success = [green]Successo
empty = [accent]Vuoto
never = Mai
save = Salva
close = [scarlet]Chiudi
previous = [accent]« Precedente
next = [accent]Successivo »
cancel = Annulla
back = Indietro
yes = Sì
no = No
test = Test
no-description = Nessuna descrizione
site = Sito web
discord = Discord
github = Github
donatello = Donatello
weblate = Weblate
discord-red-vs-blue = RedVSBlue
auto = Auto
on = Attivato
off = Disattivato
error-command-disabled = [scarlet]⚠ Il comando [accent]/{ $command }[scarlet] è disattivato su questo server.
error-feature-disabled = [scarlet]⚠ Questa funzione è disattivata su questo server.
none = Nessuno
unknown = Sconosciuto
error-nickname-badge-glyph = [scarlet]⚠ Il nome personalizzato non può contenere icone di distintivi riservate.

# Server browser (/servers)
player-servers-title = RETE SERVER XCORE
player-servers-cat-all = Tutti
player-servers-cat-attack = Attacco
player-servers-cat-pvp = PvP
player-servers-cat-survival = Sopravvivenza
player-servers-cat-special = Speciale
player-servers-hint = Tocca la scheda di un server per connetterti
player-servers-refresh = Aggiorna
player-servers-already-connected = [gold]● Sei già connesso a questo server!
player-servers-transferring = [accent]Trasferimento al server [white]{ $server }[]...
player-servers-full = [scarlet]Il server { $server } è pieno! Attendi che si liberi un posto.
player-servers-offline = [scarlet]Il server { $server } è attualmente offline.
player-servers-not-found = [scarlet]Server '{ $server }' non trovato.
player-servers-empty-category = [lightgray]Nessun server disponibile in questa categoria.[]
player-servers-online-summary = [green]● { $players } [gray]in gioco[] [darkgray]|[] [sky]{ $servers } [gray]online[]
player-servers-badge-current = [gold]● SEI QUI[]
player-servers-offline-badge = [darkgray]● OFFLINE[]
player-servers-capacity-full = [scarlet]● { $players }/{ $max } PIENO[]
player-servers-capacity-normal = [green]● { $players }/{ $max } { $bar }
player-servers-card-current = [lightgray]Sei connesso a questo server[]
player-servers-card-wave = [darkgray]|[] [accent]Ondata { $wave }[]
player-servers-card-empty = [sky]Sii il primo! Avvia una partita[]
player-servers-card-map = [gray]Mappa:[] [white]{ $map }[]
player-servers-card-mode = [gray]Modalità:[] [white]{ $mode }[]

announcement-hub =
    [gold]★ [accent]Server XCore [lightgray]» [white]Ti annoia questa partita?
    [lightgray]Esplora altri server in qualsiasi momento con [accent]/hub[lightgray]!
announcement-discord =
    [gold]★ [accent]Comunità XCore [lightgray]» [white]Cerchi compagni e novità?
    [lightgray]Unisciti al nostro server Discord con [accent]/discord[lightgray]!
announcement-help =
    [gold]★ [accent]Aiuto XCore [lightgray]» [white]Serve aiuto o la lista dei comandi?
    [lightgray]Digita [accent]/help[lightgray] per visualizzare tutti i comandi disponibili!
announcement-report =
    [gold]★ [accent]Segnalazioni XCore [lightgray]» [white]Qualcuno fa griefing o infrange le regole?
    [lightgray]Segnalalo con [accent]/report[lightgray] e lo staff controllerà!

error-only-players = [scarlet]⚠ Questo comando può essere utilizzato solo dai giocatori.

player-settings-username-editable = [gold]★ Nome utente (ricompensa evento):[]
player-settings-username-hint = Inserisci un nome utente univoco (4–32 caratteri)...
player-settings-username-locked = [gray]Nome utente:[] [accent]@{ $username }[] [darkgray](Bloccato)[]
player-settings-username-none = [gray]Nome utente: [darkgray]Non impostato (vinci un evento per sbloccarlo)[]

error-username-empty = Il nome utente non può essere vuoto!
error-username-length = Il nome utente deve contenere tra 4 e 32 caratteri!
error-username-invalid-chars = Il nome utente può contenere solo lettere latine, numeri e trattini bassi!
error-username-taken = Questo nome utente è già utilizzato da un altro giocatore!

# ==============================================================================
# Permission nodes
# ==============================================================================
permission-mindustry-admin = Usare il menu admin integrato del gioco e saltare le ondate
permission-xcore-moderation-mute = Silenziare i giocatori
permission-xcore-moderation-unmute = Rimuovere il silenziamento ai giocatori
permission-xcore-moderation-kick = Espellere i giocatori
permission-xcore-moderation-ban = Bannare i giocatori
permission-xcore-moderation-unban = Sbannare i giocatori
permission-xcore-moderation-audit-others = Visualizzare la cronologia di moderazione degli altri giocatori
permission-xcore-moderation-votekick-immune = Non può essere espulso tramite votazione
permission-xcore-admin-tp = Teletrasportare i giocatori
permission-xcore-admin-broadcast = Inviare annunci a tutti
permission-xcore-admin-kill = Uccidere unità e giocatori
permission-xcore-admin-heal = Curare unità e giocatori
permission-xcore-admin-set-team = Modificare la squadra di un giocatore
permission-xcore-maps-force-rtv = Cambiare mappa senza votazione
permission-xcore-maps-force-vnw = Saltare un'ondata senza votazione
permission-xcore-votes-cancel = Annullare una votazione in corso
permission-xcore-events-create-major = Creare grandi eventi
permission-xcore-events-edit-others = Modificare gli eventi creati da altri giocatori
permission-xcore-events-force-vote = Avviare un evento senza votazione
permission-xcore-events-stop = Interrompere l'evento in corso
permission-xcore-players-settings-others = Modificare le impostazioni degli altri giocatori
permission-xcore-players-private-info = Visualizzare dati privati dei giocatori, come gli indirizzi IP
permission-xcore-bypass-playtime = Ignorare i requisiti di tempo di gioco per i comandi
permission-xcore-permissions-inspect = Visualizzare chi possiede quali permessi
permission-xcore-permissions-manage = Modificare ruoli e permessi

error-target-outranks = [scarlet]⚠ Non puoi farlo a un giocatore con un ruolo pari o superiore al tuo.
perm-me-header = [accent]I tuoi ruoli qui (peso [white]{ $weight }[accent]):
perm-me-none = [lightgray] - nessuno
perm-me-logged-in = [green]Hai effettuato l'accesso come membro dello staff.
perm-me-not-logged-in = [yellow]Non hai effettuato l'accesso come staff. Usa [white]/login <password>[yellow].
perm-me-stale = [scarlet]I tuoi permessi staff sono sospesi: il server non è riuscito ad aggiornarli. Si riattiveranno da soli.

help-ui-search-hint = Cerca comandi, alias o descrizioni
help-ui-search-empty = [lightgray]Nessun comando corrispondente alla ricerca.[]

match-history-title = Cronologia partite
match-history-rank = #{ $rank } in questa stagione
match-history-form = Ultime { $count }:
match-history-empty = Le tue partite classificate appariranno qui.
match-history-since = La cronologia è attiva dal { $date }.
match-history-load-failed = Impossibile caricare la cronologia delle partite. Riprova tra poco.
match-history-back-to-list = Torna alla lista
match-history-your-result = Il tuo risultato
match-history-places = Posizioni
match-history-team-title = #{ $place } · { $team } · media { $average }
match-history-time-now = poco fa
match-history-time-minutes = { $count } min fa
match-history-time-hours = { $count } h fa
match-history-time-yesterday = ieri
match-history-outcome-win = [lime]Vittoria[]
match-history-outcome-loss = [scarlet]Sconfitta[]
match-history-outcome-unrated = [lightgray]Non classificata[]
match-history-outcome-uncounted = [lightgray]Non conteggiata[]
match-history-lineup = { $own } contro { $other }
match-history-team-place = #{ $place } su { $teams } squadre
match-history-place = #{ $place } su { $players }
match-history-counted = Conteggiata: { $reason }
match-history-not-counted = Non conteggiata: { $reason }
match-history-counted-share = Conteggiata per il { $percent }% della partita
match-history-reason-winner = vittoria
match-history-reason-defeated = sconfitta
match-history-reason-short-play = giocato meno di metà partita
match-history-reason-late-join = entrato solo alla fine
match-history-reason-left-at-once = uscito quasi subito
match-history-reason-match-unrated = la partita non era classificata
match-history-reason-unknown = nessun motivo registrato
match-history-skip-not-enough-players = troppi pochi giocatori
match-history-skip-admin-stop = interrotta da un admin
match-history-skip-technical-error = errore tecnico
match-history-skip-unknown = non classificata
match-history-finish-surrender = per resa
match-history-finish-timeout = allo scadere del tempo
match-history-finish-admin-stop = interrotta da un admin
match-history-finish-technical-error = errore tecnico

# Discord submission attribution; the declared creator is supplied by the uploader.
map-upload-file-author = Autore nel file: { $author }
map-upload-creator = Autore indicato: { $person }
map-upload-submitter = Aggiunta tramite Discord: { $person }
map-upload-date = Caricata il: [lightgray]{ $date }[] (UTC)

map-upload-current-creator = [accent]{ $map }[] — { $creator }