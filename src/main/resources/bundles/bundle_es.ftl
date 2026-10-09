# ==============================================================================
# Terms
# ==============================================================================
-xcore = Servidor XCore
# ==============================================================================
# General & Help
# ==============================================================================
menu-main = Menú principal
commands-main-description = Abre el menú principal interactivo.
menu-main-title = [orange]{ -xcore } — Menú principal
menu-main-content = Menú principal del servidor
help-menu = Menú de ayuda
commands-help-description = Abre el menú de ayuda interactivo.
help-menu-title = [orange]{ -xcore } — Comandos
help-menu-content =
    [gray]Página [white]{ $page }[gray]/[white]{ $total }
    [lightgray]Elige un comando para ver cómo se usa:
help-menu-button = [accent]/{ $command } [gray]» Descripción: [white]{ $description }
help-command-with-overload-count = { $name } de ({ $count })
help-command-title = [orange]» Nombre: [white]/{ $name }
help-command-header =
    [orange]» [accent]Sintaxis: [white]{ $syntax }
    [orange]» [accent]Info: [lightgray]{ $description }
help-aliases = [orange]» [accent]Alias: [white]{ $aliases }
help-args-title = [orange]» [accent]Argumentos:
help-usages-title = [orange]» [accent]Uso:
help-usage-entry = [gray]• [white]{ $syntax }
help-usage-args-title = [orange]» [accent]Para [white]{ $syntax }[accent]:
help-arg-entry = [gray]• [white]{ $arg } [lightgray]- { $description }
help-no-arguments = [gray]No se necesitan argumentos adicionales.
help-no-arg-description = Sin descripción.
help-no-description = Este comando no tiene descripción.
help-legacy-command-content =
    [orange]» [accent]Comando: [white]/{ $name }
    [orange]» [accent]Parámetros: [white]{ $params }
    [orange]» [accent]Info: [lightgray]{ $description }
    { "" }
    [gray](Comando antiguo con información limitada)
help-legacy-command-content-no-params =
    [orange]» [accent]Comando: [white]/{ $name }
    [orange]» [accent]Info: [lightgray]{ $description }
    { "" }
    [gray](Comando antiguo con información limitada)
help-back = [lightgray]« Atrás

# ==============================================================================
# Modern Reactive Help & Commands Guide (xcore-ui)
# ==============================================================================
help-ui-title = GUÍA DE COMANDOS
help-ui-summary = [gray]{ $count } comandos disponibles
help-ui-empty-category = [lightgray]No hay comandos en esta categoría.[]
help-ui-overloads = ({ $count } variantes)
help-ui-aliases = [lightgray]Alias:[] { $aliases }
help-ui-syntax-title = Sintaxis
help-ui-args-title = Parámetros
help-ui-arg-required = [scarlet]Obligatorio
help-ui-arg-optional = [sky]Opcional
help-ui-btn-run = Ejecutar
help-ui-btn-copy = Al chat
help-ui-btn-back = Atrás
help-ui-copied = [accent]Comando: [white]/{ $syntax }
help-ui-executed = [accent]Ejecutando comando: [white]/{ $syntax }

# Categories
help-cat-all = Todos
help-cat-general = General
help-cat-game = Juego
help-cat-social = Chat
help-cat-votes = Votaciones
help-cat-admin = Admin
# ==============================================================================
# Command Argument Descriptions
# ==============================================================================
# help
commands-help-page-description = Número de página que se mostrará.
# login
commands-login-password-description = Tu contraseña de administrador.
# ban
commands-ban-id-description = ID del jugador que se va a banear.
commands-ban-period-description = Duración del baneo (p. ej. 1d, 2h, 30m).
commands-ban-reason-description = Motivo del baneo.
# unban
commands-unban-id-description = ID del jugador al que se quitará el baneo.
# mute
commands-mute-id-description = ID del jugador que se va a silenciar.
commands-mute-period-description = Duración del silencio (p. ej. 1h, 30m).
commands-mute-reason-description = Motivo del silencio.
# unmute
commands-unmute-id-description = ID del jugador al que se quitará el silencio.
# votekick
commands-votekick-target-description = Jugador que se va a expulsar (ID o nombre).
commands-votekick-reason-description = Motivo de la expulsión.
# vote
commands-vote-choice-description = Tu voto: y (sí), n (no) o c (cancelar, solo admins).
# t (team chat)
commands-t-message-description = Mensaje para tus compañeros de equipo.
# g (global chat)
commands-g-message-description = Mensaje para todos los servidores.
# tr (translator)
commands-tr-language-description = Código de idioma, 'auto' u 'off'.
# stats
commands-stats-id-description = ID del jugador cuyas estadísticas quieres ver
# rank
commands-rank-player-description = Jugador cuyo rango quieres ver
# map
commands-map-map-description = Nombre o número del mapa.
# maps / maps-text
commands-maps-page-description = Número de página.
commands-maps-text-page-description = Número de página.
# rtv / artv
commands-rtv-map-description = Mapa por el que votar (opcional).
commands-artv-map-description = Mapa al que cambiar de inmediato.
# ai
commands-ai-state-description = Estado de la IA: attack (a) o idle (i).
# event / events
commands-events-page-description = Número de página.
# ==============================================================================
# General & Help (continued)
# ==============================================================================
commands-information-description = Muestra información sobre el servidor.
commands-info = Información
commands-info-title = [orange]{ -xcore } — Nombre del servidor: [orange]{ $server-name }
commands-info-text =
    [accent]XCore[white] es un servidor [cyan]gratuito[white] para jugar a [accent]Mindustry[white].
    { "" }
    Versión de XCore — [accent]{ $version }[white]
commands-sync-description = Sincroniza tu partida con el servidor. Sirve para corregir errores como unidades fantasma.
commands-discord-description = Te lleva al servidor de Discord.
discord-menu-title = [orange]{ -xcore } — Discord
discord-menu-content =
    [white]Gestiona aquí tu vinculación con Discord.
    { "" }
    [white]Estado: { $status }
    [white]Servidor: [accent]{ $discordUrl }[]
discord-menu-open = Abrir Discord
discord-menu-link = Vincular cuenta
discord-menu-status = Actualizar estado
discord-menu-unlink = Desvincular cuenta
discord-menu-status-not-linked = [lightgray]sin vincular[]
discord-menu-status-linked = [green]{ $discordUsername }[] [gray]({ $discordId })[]
discord-link-menu-title = [orange]{ -xcore } — Vincular cuenta de Discord
discord-link-menu-content =
    [white]En nuestro servidor de Discord, ejecuta el comando del bot:
    { "" }
    [accent]/link { $code }[]
    { "" }
    [white]Caduca en: [accent]{ $expireMinutes }[] min
    [white]Discord: [accent]{ $discordUrl }[]
discord-link-menu-refresh = Actualizar código
discord-link-menu-copy = Copiar código
discord-link-menu-regenerate = Generar código nuevo
discord-link-menu-status = Volver al menú de Discord
welcome =
    [accent]¡Bienvenido a { $serverName }!
    [lightgray]Escribe [accent]/help[lightgray] para ver la lista de comandos
    [lightgray]Escribe [accent]/vote [gray]<y/n>[lightgray] para votar la expulsión de un jugador
    [lightgray]Escribe [accent]/votekick [gray]<ID/nombre> <motivo…>[lightgray] para iniciar una votación de expulsión
    [lightgray]Escribe [accent]/t [gray]<mensaje…>[lightgray] para escribir a tu equipo
    [lightgray]Escribe [accent]/g [gray]<mensaje…>[lightgray] para escribir a todos los servidores
    [lightgray]Escribe [accent]/tr [gray]<idioma/auto>[lightgray] para activar el traductor
    [lightgray]Escribe [accent]/discord[lightgray] para abrir el menú de Discord y vincular tu cuenta
# ==============================================================================
# Chat & Social
# ==============================================================================
commands-t-description = Envía un mensaje solo a tus compañeros de equipo.
commands-t-chat = [{ "#" }{ $color }][Equipo] [coral]> { $badge }[accent]{ $name }[lightgray]: [white]{ $message }
commands-g-description = Envía un mensaje a todos los servidores.
commands-a-description = Envía un mensaje solo a los admins.
commands-msg-description = Envía un mensaje privado a un jugador.
commands-msg-id-description = ID del jugador.
commands-msg-message-description = Texto del mensaje privado.
commands-reply-description = Responde al último jugador en los mensajes privados.
commands-reply-message-description = Texto de la respuesta.
commands-inbox-description = Abre el menú de mensajes privados.
commands-inbox-id-description = ID del jugador.
commands-tr-description = Establece el idioma del traductor.
commands-badge-description = Abre el menú de insignias y gestiona tu insignia activa.
commands-tr-success = [accent]¡El idioma del traductor ahora es [grey]{ $translatorLanguage }[]!
commands-tr-off = [accent]¡El traductor está [scarlet]desactivado[]!
commands-tr-not-found = [scarlet]⚠ Ese idioma no existe.
discord-chat-format = [#5865F2][DISCORD][] [lightgray]| [accent]{ $author }[lightgray] >> [white]{ $message }
global-chat-format = [royal][[[orange]GLOBAL [lightgray](desde [accent]{ $server }[])[] { $author }[]]: [white]{ $message }
private-message-received = [sky][MP][] [lightgray]de [accent]{ $author } [gray]#{ $pid }[lightgray]: [white]{ $message }
private-message-sent = [sky][MP][] [lightgray]para [accent]{ $target } [gray]#{ $pid }[lightgray]: [white]{ $message }
private-message-unread-count =
    [accent]Tienes [white]{ $count }[accent] { $count ->
        [one] mensaje privado sin leer
       *[other] mensajes privados sin leer
    }.
private-message-join-notification =
    [accent]Tienes [white]{ $count }[accent] { $count ->
        [one] mensaje privado sin leer
       *[other] mensajes privados sin leer
    }. Usa [white]/inbox[accent] para { $count ->
        [one] leerlo
       *[other] leerlos
    }.
private-message-block-success = [accent]Los mensajes privados de [white]{ $target } [gray]#{ $pid }[accent] ahora están bloqueados.
private-message-block-already = [lightgray]Los mensajes privados de [white]{ $target } [gray]#{ $pid }[lightgray] ya están bloqueados.
private-message-unblock-success = [accent]Los mensajes privados de [white]{ $target } [gray]#{ $pid }[accent] ya no están bloqueados.
private-message-unblock-missing = [lightgray][white]{ $target } [gray]#{ $pid }[lightgray] no está bloqueado.
private-message-menu-title = [orange]{ -xcore } — Mensajes privados
private-message-menu-content =
    [white]Página [green]{ $page }[] de [green]{ $total }[]
    [white]Sin leer: [accent]{ $unread }[]
private-message-menu-empty = [lightgray]Tu bandeja de entrada está vacía.
private-message-menu-entry-unread = [accent]Sin leer[] [white]{ $author } [gray]#{ $pid }[] [lightgray]({ $time })[]: [white]{ $message }
private-message-menu-entry-read = [gray]Leído[] [white]{ $author } [gray]#{ $pid }[] [lightgray]({ $time })[]: [white]{ $message }
private-message-details-title = [orange]{ -xcore } — Mensaje
private-message-details-content =
    [white]De: [accent]{ $author } [gray]#{ $pid }[]
    [white]Hora: [accent]{ $time }[]
    [white]Estado: [accent]{ $status }[]
    { "" }
    [white]{ $message }
private-message-status-unread = sin leer
private-message-status-read = leído
private-message-blocked-title = [orange]{ -xcore } — Jugadores bloqueados
private-message-blocked-content =
    [white]Página [green]{ $page }[] de [green]{ $total }[]
    [white]Bloqueados: [accent]{ $count }[]
private-message-blocked-empty = [lightgray]No tienes jugadores bloqueados.
private-message-blocked-entry = [white]{ $target } [gray]#{ $pid }[]
private-message-compose = Nuevo mensaje
private-message-blocked = Bloqueados
private-message-block = Bloquear remitente
private-message-unblock = Desbloquear remitente
private-message-reply-title = Responder
private-message-reply-message = Escribe un mensaje para [accent]#{ $pid }[]
private-message-compose-target-title = Nuevo mensaje
private-message-compose-target-message = Escribe el ID del jugador con el formato [accent]#123[]
private-message-compose-body-title = Texto del mensaje
private-message-compose-body-message = Escribe un mensaje privado para [accent]{ $pid }[]
# ==============================================================================
# Authentication & Admin Access
# ==============================================================================
commands-login-description = Activa los permisos de admin si tu cuenta de Discord vinculada ya tiene acceso.
commands-login-incorrect-password = [scarlet]⚠ ¡Contraseña incorrecta!
commands-login-success = [green]Permisos de admin concedidos.
commands-login-confirmed = [green]Acceso de admin en Discord confirmado.
commands-login-admin-password-created =
    [green]Contraseña de admin creada.
    [red]¡No olvides tu contraseña! Si la olvidas, tendrás que pedir a un administrador general que la restablezca.
commands-login-request-approval-discord = [accent]Tu cuenta no tiene acceso de administrador en Discord. Obtén el rol de administrador en Discord e inténtalo de nuevo.
commands-login-verifying = [lightgray]Verificando contraseña de administrador...
commands-login-already-processing = [scarlet]⚠ Ya se está procesando una solicitud de inicio de sesión. Por favor, espere.
commands-login-rate-limited = [scarlet]⚠ Demasiados intentos fallidos de inicio de sesión. Por favor, espere antes de intentarlo de nuevo.
commands-discord-link-created =
    [green]Código de vinculación de Discord creado: [accent]{ $code }[]
    [lightgray]En nuestro servidor de Discord, ejecuta el comando del bot [accent]/link { $code }[] en menos de [accent]{ $expireMinutes }[] min.
    [cyan]{ $discordUrl }
commands-discord-link-confirmed = [green]Cuenta de Discord vinculada: [accent]{ $discordUsername }[]
commands-discord-link-already-linked = [lightgray]Esta cuenta de Mindustry ya está vinculada. Usa [accent]/discord status[] o [accent]/discord unlink[].
commands-discord-link-error = [scarlet]No se pudo crear el código de vinculación de Discord. Inténtalo más tarde.
commands-discord-status-not-linked = [lightgray]Tu cuenta no está vinculada a Discord.
commands-discord-status-linked = [green]Discord vinculado: [accent]{ $discordUsername }[] [gray]({ $discordId })[]
commands-discord-unlink-not-linked = [lightgray]Tu cuenta no está vinculada a Discord.
commands-discord-unlink-success = [green]Vinculación con Discord eliminada.
commands-logout-description = Cerrar sesión. Esto [scarlet]retira tus permisos de admin.
commands-logout-successful = [green]Permisos de admin retirados.
# ==============================================================================
# Moderation (Ban, Mute, Kick)
# ==============================================================================
commands-ban-description = Banea a un jugador.
commands-ban-success = { $nickname } [scarlet]baneado
commands-unban-description = Quita el baneo a un jugador.
commands-unban-success = { $nickname }[accent] #{ $pid } [green]ya no está baneado.
commands-mute-description = Silencia a un jugador.
commands-mute-success = [accent]{ $nickname } ha sido silenciado
commands-unmute-description = Quita el silencio a un jugador.
commands-unmute-success = [green]Silencio retirado a []{ $nickname }
commands-alert-description = Muestra un anuncio destacado a los jugadores elegidos o a todos.
commands-toast-description = Muestra una notificación de aviso a los jugadores elegidos.
commands-announcement-description = Emite un anuncio periódico por clave o el siguiente de la rotación.
commands-audit-description = Muestra el historial y las acciones del staff y la moderación.
ban-content = [scarlet]⚠ Baneado[]
    [accent]{ $nickname }[white] — estás baneado de forma permanente en este servidor.
    [lightgray]Para apelar, visita el canal de Discord [gray]{ support-channel }[]:
    [cyan]{ $discordUrl }
ban-cancelled = [accent]Se ha cancelado el baneo de [scarlet]{ $nickname }[accent]
tempban-content = [scarlet]⚠ Baneado[]
    [accent]{ $nickname }[white] — estás baneado temporalmente en este servidor.
    { "" }
    [orange]» [accent]Admin: [white]{ $adminName }
    [orange]» [accent]Motivo: [gold]{ $reason }
    [orange]» [accent]Tiempo restante: { DURATION($duration, style: "full", colored: "true", maxUnits: 2) }
    [orange]» [accent]Termina: [white]{ DATETIME($expireDate, dateStyle: "medium", timeStyle: "short") }
    { "" }
    [lightgray]Para apelar, visita el canal de Discord [gray]{ support-channel }[]:
    [cyan]{ $discordUrl }
tempban-player-banned = [scarlet] El admin { $adminName }[scarlet] ha baneado al jugador [gray]'[]{ $playerName }[gray]'
you-are-muted-by =
    [orange]⚠ Chat restringido[]
    [lightgray]El administrador [accent]{ $adminName }[lightgray] te ha silenciado.
    [orange]» [accent]Motivo: [gold]{ $reason }
    [orange]» [accent]Tiempo restante: { DURATION($duration, style: "full", colored: "true", maxUnits: 2) }
you-are-muted =
    [orange]⚠ Chat restringido[]
    [lightgray]No puedes enviar mensajes mientras este silencio esté activo.
    [orange]» [accent]Admin: [white]{ $adminName }
    [orange]» [accent]Motivo: [gold]{ $reason }
    [orange]» [accent]Tiempo restante: { DURATION($duration, style: "full", colored: "true", maxUnits: 2) }
kick-pirated-game = [accent]Cliente no autorizado detectado. [scarlet]Acceso denegado[]. Juega con la versión [lime]oficial[] de [blue]Steam[], [blue]Google Play[] o [blue]itch.io[].
kick-recently-kicked =
    [accent]Te expulsaron de este servidor hace poco.
    Espera [cyan]{ DURATION($remaining, style: "timer") }[accent] antes de volver a entrar.
kick-admintools-outdated =
    [green]Versión de AdminTools requerida: [grey]{ $requiredVersion }[]
    [scarlet]Tu versión de AdminTools: [grey]{ $version }[]
    { "" }
    [cyan]Actualiza AdminTools para entrar en este servidor.
support-channel = #reports-appeals
# ==============================================================================
# Voting (VoteKick)
# ==============================================================================
commands-votekick-description = Vota para expulsar a un jugador del servidor.
commands-vote-description = Vota en la votación activa.
commands-vote-vote-with = [scarlet]⚠ Vota con [orange]/vote <y/n/c>
votekick-vote =
    { $starter } [grey]#[white]{ $starterId }[lightgray] ha votado expulsar a { $target } [grey]#[white]{ $targetId }[lightgray] por [orange]{ $reason }[lightgray]. ([accent]{ $votes }[]/[accent]{ $required }[])
    [lightgray]Escribe [orange]/vote <y/n>[] para votar.
votekick-left = { $player }[lightgray] se ha ido. Su voto se ha cancelado. ([accent]{ $votes }[]/[accent]{ $required }[])
votekick-fail = [lightgray]La votación ha fallado. No hay votos suficientes para expulsar a { $target }[lightgray].
votekick-cancelled = [scarlet]{ $admin } ha cancelado la votación para expulsar a { $target }[scarlet].
votekick-success =
    [orange]Votación aprobada. { $target }[orange] expulsado durante [scarlet]{ $minutes }[] { $minutes ->
        [one] minuto
       *[other] minutos
    }.
# ==============================================================================
# Maps & RTV
# ==============================================================================
commands-map-description = Estadísticas de un mapa concreto.
commands-map-title = [orange]{ -xcore } — Mapa
commands-map-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $name }[] [gray]por [sky]{ $author }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Resumen[]
    [gray]Tamaño: [white]{ $width }x{ $height } [darkgray]|[gray] Votos: [lime]+{ $like } [darkgray]/[scarlet] -{ $dislike }[]
    { "" }
    [accent]■ Actividad[]
    [gray]Partidas totales: [white]{ $played } [darkgray]|[gray] Este año: [white]{ $playedYear }[]
    [gray]Última partida: [white]{ $lastPlayed }[]
    [gray]Popularidad: [white]{ $popularity } [darkgray]|[gray] Interés: [white]{ $interest } [darkgray]|[gray] Reputación: [white]{ $reputation }[]
    { "" }
    [accent]■ Duración de las partidas[]
    [gray]Mín.: [white]{ $min } [darkgray]|[gray] Media: [white]{ $avg } [darkgray]|[gray] Máx.: [white]{ $max }[]
commands-maps-description = Lista de todos los mapas de este servidor.
commands-maps-title = [orange]{ -xcore } — Mapas
commands-maps-content =
    [gray]Mapa actual: [accent]{ $current }[]
    [white]Página [green]{ $page }[] de [green]{ $total }[]
commands-maps-current-row = { $name } ★
commands-maps-text-description = Lista de todos los mapas de este servidor.
commands-maps-text-start-content =
    [accent]Mapa actual: []{ $name }[white]
    [orange][gold]Lista de mapas [lightgray]{ $page }[gray]/[lightgray]{ $total }
commands-maps-text-content =
    { "" }
    { $index }. [orange] - [white]{ $name }[orange] | [green]{ $reputation }[orange] | [white]{ $width }x{ $height }[orange] | [white]{ $lastPlayed }[orange] | Por: [sky]{ $author }
commands-artv-description = Cambia el mapa de inmediato.
commands-artv-map-skipped = { $nickname }[accent] ha saltado el mapa. Siguiente mapa: { $name }.
commands-artv-event-skipped = { $nickname }[accent] ha saltado el evento. Siguiente evento: { $name }.
commands-rtv-description = Vota para cambiar de mapa.
commands-vnw-description = Vota para adelantar la siguiente oleada.
commands-avnw-description = Inicia ya la siguiente oleada.
commands-like-description = Vota a favor del mapa actual (aumenta su reputación).
commands-dislike-description = Vota en contra del mapa actual.
map-vote-title = [orange]{ -xcore } — [scarlet]¡FIN DE LA PARTIDA!
map-vote-content =
    { "" }
    Siguiente mapa: [accent]{ $mapName }[] de [accent]{ $author }[white].
    La nueva partida empieza en [accent]{ $seconds }[white] { $seconds ->
        [one] segundo
       *[other] segundos
    }.
    { "" }
    [cyan]¿Te ha gustado este mapa?
map-vote-like = [green]👍 Me gusta
map-vote-dislike = [red]👎 No me gusta
map-vote-like-selected = [gray]Te ha gustado
map-vote-dislike-selected = [gray]No te ha gustado
map-rtv = [orange]Votación
map-artv = [red]Cambio inmediato
map-maps = Mapas
map-maps-back = Volver a la lista de mapas
current-map = Mapa actual
next-map = Siguiente mapa

# Map UI Modernized
map-ui-search-hint = Buscar mapa o autor...
map-ui-total = [lightgray]Mapas: [white]{ $count }[]
map-ui-about-title = Sobre el mapa
map-ui-rtv-title = Cambio de mapa
map-ui-no-maps-found = [lightgray]No se encontraron mapas para esta búsqueda.[]
map-ui-by = de [lightgray]{ $author }[]
map-ui-mode = Modo: [white]{ $mode }[]
map-ui-loading = [gray]Cargando...[]
map-ui-no-preview = [gray]Sin vista previa[]
map-ui-dimensions = [gray]Tamaño: [white]{ $width } x { $height }[]
map-ui-total-plays = [gray]Partidas: [white]{ $played } [lightgray]({ $playedYear } este año)[]
map-ui-last-played = [gray]Última partida: [white]{ $lastPlayed }[]
map-ui-description = [gray]Descripción: [lightgray]{ $description }[]
map-ui-no-description = [gray]Descripción: [lightgray]Sin descripción.[]

map-ui-col-duration = Duración
map-ui-duration-min = [gray]Mín.: [white]{ $value }[]
map-ui-duration-avg = [gray]Media: [white]{ $value }[]
map-ui-duration-max = [gray]Máx.: [white]{ $value }[]

map-ui-col-popularity = Popularidad
map-ui-popularity-score = [gray]Puntuación: [white]{ $value }[]
map-ui-popularity-pop = [gray]Popularidad: [white]{ $value }[]
map-ui-popularity-interest = [gray]Interés: [white]{ $value }[]

map-ui-col-community = Comunidad
map-ui-community-approval = [gray]Aprobación: [green]{ $rate }%[]

map-ui-btn-like = Me gusta ({ $count })
map-ui-btn-dislike = No me gusta ({ $count })

map-ui-rtv-active-status = [accent]● Votación en curso: [white]{ $votes }/{ $required }[] [gray](termina en [white]{ $seconds } s[gray])[]
map-ui-rtv-vote-yes = Votar por este mapa
map-ui-rtv-start = Iniciar votación por este mapa
map-ui-admin-rtv = Cambiar el mapa ahora
map-ui-admin-rtv-confirm = Pulsa otra vez para confirmar

gamemode-survival = Supervivencia
gamemode-attack = Ataque
gamemode-pvp = PvP
gamemode-sandbox = Sandbox
gamemode-editor = Editor
rtv-vote =
    { $nickname }[lightgray] ha votado cambiar el mapa actual a [orange]{ $mapName }[lightgray]. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Escribe [orange]y[] o [orange]n[] para votar.
rtv-left = { $nickname }[lightgray] se ha ido. Su voto para cambiar el mapa se ha cancelado. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
rtv-fail = [lightgray]La votación ha fallado. No hay votos suficientes para cambiar el mapa a [orange]{ $mapName }[].
rtv-success = [orange]Votación aprobada. El mapa [accent]{ $mapName }[] se cargará en [accent]{ $mapLoadDelay }[] { $mapLoadDelay ->
    [one] segundo
   *[other] segundos
}…
rtv-cancelled = [lightgray]{ $admin } ha cancelado la votación para cambiar el mapa a [orange]{ $mapName }[lightgray].
vnw-vote =
    { $nickname }[lightgray] ha votado adelantar la oleada [orange]{ $wave }[lightgray]. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Escribe [orange]y[] o [orange]n[] para votar.
vnw-left = { $nickname }[lightgray] se ha ido. Su voto para adelantar la oleada [orange]{ $wave }[lightgray] se ha cancelado. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
vnw-fail = [lightgray]La votación ha fallado. No hay votos suficientes para adelantar la oleada [orange]{ $wave }[].
vnw-success = [orange]Votación aprobada. La oleada [accent]{ $wave }[] empieza ya.
vnw-cancelled = [lightgray]{ $admin } ha cancelado la votación para adelantar la oleada [orange]{ $wave }[lightgray].
vnw-obsolete = [lightgray]La oleada [orange]{ $wave }[lightgray] ya ha empezado, así que el resultado de la votación ya no hace falta.
# ==============================================================================
# Statistics & Ranks & Players
# ==============================================================================
commands-player-description = Muestra las estadísticas de un jugador.
commands-settings-description = Abre tus ajustes de jugador.
player-menu-player = Jugador
player-menu-player-title = [orange]{ -xcore } — Estadísticas del jugador
player-menu-player-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $customNickname }[] [gray]#{ $pid }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Perfil[]
    [gray]Nombre: [white]{ $nickname } [darkgray]|[gray] Admin: [lime]{ $admin }[]
    [gray]Insignia: [white]{ $activeBadge } [darkgray]|[gray] Sistema: [coral]{ $systemBadge }[]
    [gray]Se unió: [white]{ $accountCreated }[]
    { "" }
    [accent]■ Clasificaciones[]
    [gray]Tiempo de juego: [white]{ $totalPlayTime }[]
    [gray]MiniPvP: [sky]{ $pvpRating } [darkgray]|[gray] Hexed antiguo: [sky]{ $hexedRankName } [gray]({ $hexedPoints } pts) [darkgray]|[gray] Top: [accent]{ $hexedTopRank }[]
    { "" }
    [accent]■ Partidas: [white]{ $gamesPlayed } [gray]jugadas [darkgray]|[lime] { $gamesWon } [gray]victorias [darkgray]|[sky] { $winRate }% [gray]de victorias[]
    [gray]• [white]PvP: { $pvpSummary }[]
    [gray]• [white]Superv.: { $survivalSummary }[]
    [gray]• [white]Hexed antiguo: { $hexedSummary }[]
    { "" }
    [accent]■ Eficacia en combate[]
    [gray]Bloques (Construidos/Desmontados/Destruidos): [lime]{ $blocksBuilt } [darkgray]/ [orange]{ $blocksDeconstructed } [darkgray]/ [scarlet]{ $blocksDestroyed }[]

# Modern Player Stats UI
player-stats-title = [orange]{ -xcore } — Perfil del jugador
player-stats-tab-overview = Resumen
player-stats-tab-stats = Estadísticas
player-stats-tab-matches = Partidas
player-stats-tab-blocks = Bloques
player-stats-tab-players = En línea ({ $count })

player-stats-status-online = [lime]● En línea[]
player-stats-status-offline = [gray]○ Desconectado[]
player-stats-no-bio = [gray]Todavía no hay biografía.[]
player-stats-loading = [lightgray]Cargando datos...[]
player-stats-no-stats = [gray]Todavía no hay datos de partidas.[]
player-stats-filter-all = Filtro: Todos
player-stats-filter-admins = Filtro: Solo admins
player-stats-filter-non-admins = Filtro: Sin admins
player-stats-refresh = Actualizar
player-stats-combat-efficiency = Eficacia en combate y construcción
player-stats-waves-summary = [gray]oleadas: máx. [lime]{ $best }[], media [white]{ $avg }[]
player-stats-hexed-top-placement = [gray]mejor [accent]#{ $best }[], top 3: [sky]{ $top3 }[]
player-stats-max-rank = [gold]★ RANGO MÁXIMO ALCANZADO ★[]
player-stats-max-league = [gold]★ LIGA MÁXIMA ALCANZADA ★[]
player-stats-hexed-wins-left = [lightgray]{ $wins } victorias para [white]{ $rank }[]
player-stats-league-elo-left = [lightgray]{ $elo } ELO para [white]{ $league }[]
player-stats-ratio-legend = [lightgray]Proporción de bloques[]

player-stats-account-created = [gray]Se unió:[]
player-stats-play-time = [gray]Tiempo de juego:[]
player-stats-legacy-pvp-rating = [gray]PvP antiguo:[]
player-stats-hexed-rank = [gray]Hexed antiguo:[]
player-stats-hexed-leaderboard = [gray]Top de Hexed:[]

player-stats-total-games = [gray]Partidas totales:[]
player-stats-victories = [gray]Victorias:[]
player-stats-survival-summary = [gray]Supervivencia:[]
player-stats-hexed-summary = [gray]Hexed antiguo:[]

player-stats-blocks-built = [gray]Bloques construidos:[]
player-stats-blocks-deconstructed = [gray]Desmontados:[]
player-stats-blocks-destroyed = [gray]Destruidos:[]
player-stats-units-produced = [gray]Unidades producidas:[]
player-stats-units-lost = [gray]Unidades perdidas:[]

player-stats-games-played-value = [white]{ $count }[] [gray]partidas[]
player-stats-victories-value = [lime]{ $wins }[] [gray]victorias[]  [darkgray]|[]  [sky]{ $winRate }%[] [gray]de victorias[]
player-stats-hexed-points = [gray]({ $points } pts)[]

player-stats-btn-settings = Ajustes
player-stats-btn-matches = Partidas
player-stats-btn-audit = Historial
player-stats-btn-players = En línea
player-stats-btn-close = Cerrar
player-stats-admin-tag = [coral]<Admin>[]
player-menu-players = Jugadores en línea
player-menu-players-title = [orange]{ -xcore } — Jugadores en línea
player-menu-players-content = [white]Página [green]{ $page }[] de [green]{ $total }[]
player-menu-players-empty = No hay jugadores en línea
player-menu-players-row = [white]{ $nickname } [gray](PID: { $pid })[]
player-menu-settings = Ajustes
player-menu-settings-title = [orange]{ -xcore } — Ajustes del jugador
player-menu-settings-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $displayNickname }[] [gray]#{ $pid }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Perfil[]
    [gray]Nombre: [white]{ $nickname } [darkgray]|[gray] Visible: [lime]{ $customNickname }[]
    [gray]Insignia: [white]{ $activeBadge } [darkgray]|[gray] Sistema: [coral]{ $systemBadge }[]
    { "" }
    [accent]■ Visibilidad[]
    [gray]Clasificación: [white]{ $leaderboard }[]
    { "" }
    [accent]■ Chat[]
    [gray]Global: [white]{ $globalChat } [darkgray]|[gray] Discord: [white]{ $discordRelay }[]
    [gray]Traductor: [white]{ $translatorLanguage }[]
    { "" }
    [accent]■ Idioma[]
    [gray]Idioma: [white]{ $language }[]
player-menu-settings-chat = Ajustes del chat
player-menu-settings-chat-title = [orange]{ -xcore } — Ajustes del chat

# Modern Player Settings Form
player-settings-tab-profile = Perfil
player-settings-tab-chat = Chat
player-settings-tab-badges = Insignias
player-settings-chat-preview = Vista previa del chat
player-settings-chat-preview-sample = Ejemplo
player-settings-chat-preview-message = ¡Hola, mundo!
player-settings-symbol-color-mode = Color del símbolo
player-settings-badges-my = Mis insignias
player-settings-badges-all = Todas las insignias
player-settings-badge-equip = Equipar
player-settings-badge-unequip = Quitar
player-settings-badge-preview = Vista previa
player-settings-badge-previewing = En vista previa
player-settings-translator-lang = Traductor del chat
player-settings-translator-off = Desactivado
player-settings-badges-empty = Todavía no has desbloqueado ninguna insignia.
player-settings-manage-badges = Gestionar insignias
player-settings-global-chat = Chat global
player-settings-discord-relay = Retransmisión de Discord
player-settings-leaderboard = Mostrar la clasificación
player-settings-language = Idioma
player-settings-saved = [lime]¡Ajustes guardados![]
player-settings-reset-feedback = [lightgray]Nombre personalizado restablecido.[]
player-settings-edit-badges = [accent]Editar[]
player-settings-tab-language = Idioma
player-settings-identity = Nombre y descripción
player-settings-interface = Pantalla
player-settings-leaderboard-hint = La lista de los mejores jugadores sobre la partida. Funciona en Mini-PvP.
player-settings-global-chat-hint = Mensajes de jugadores de los demás servidores XCore.
player-settings-discord-relay-hint = Mensajes del canal de Discord del servidor en el chat del juego.
player-settings-translator-hint = Los mensajes de otros jugadores se traducen al idioma que elijas.
player-settings-language-hint = El idioma de los menús y mensajes del servidor. Auto sigue el idioma de tu juego.
player-menu-settings-chat-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [accent]■ Visibilidad del chat[]
    [gray]Chat global: [white]{ $globalChat }[]
    [gray]Retransmisión de Discord: [white]{ $discordRelay }[]
    { "" }
    [accent]■ Traducción[]
    [gray]Idioma del traductor: [white]{ $translatorLanguage }[]
player-menu-settings-translator-title = [orange]{ -xcore } — Elegir idioma del traductor
player-menu-settings-language-title = [orange]{ -xcore } — Elegir idioma
player-menu-settings-customNickname = Editar nombre
player-menu-settings-customNickname-title = [orange]{ -xcore } — Editar nombre
player-menu-settings-customNickname-message = [lightgray]Déjalo vacío para restablecer
player-menu-settings-customNickname-reset = [scarlet]Restablecer nombre
player-menu-settings-description = Editar descripción
player-menu-settings-description-title = [orange]{ -xcore } — Editar descripción
player-menu-settings-badges = Insignias
player-menu-settings-global-chat-on = [green]Chat global
player-menu-settings-global-chat-off = [red]Chat global
player-menu-settings-discord-relay-on = [green]Retransmisión de Discord
player-menu-settings-discord-relay-off = [red]Retransmisión de Discord
audit-menu-open = Historial
audit-menu-actions-open = Acciones
audit-menu-history-title = [orange]{ -xcore } — Historial
audit-menu-history-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $player }[] [gray]#{ $pid }[]
    [gray]Entradas mostradas: [accent]{ $entriesShown }[]
    [gray]{ $pageState } [darkgray]|[] { $nextState }
    [lightgray]{ $hint }[]
audit-menu-history-page-first = Entradas más recientes
audit-menu-history-page-older = Entradas anteriores
audit-menu-tab-sanctions = Sanciones recibidas
audit-menu-tab-actions = Acciones realizadas
audit-menu-filter-all = Todas
audit-menu-filter-bans = Baneos
audit-menu-filter-mutes = Silencios
audit-menu-filter-warns = Avisos
audit-menu-filter-other = Otras
audit-menu-btn-back = Volver al historial
audit-menu-btn-copy-id = Copiar ID
audit-menu-copy-id-success = El ID de la entrada se ha enviado al chat
audit-menu-field-id = ID de la entrada
audit-menu-page = Página { $page }
audit-menu-details-unavailable = La entrada no está disponible.
audit-menu-field-target = Jugador
audit-menu-field-actor = Realizado por
audit-menu-field-server = Servidor
audit-menu-field-reason = Motivo
audit-menu-section-time = Tiempo
audit-menu-field-occurred = Cuándo
audit-menu-field-duration = Duración
audit-menu-field-expires = Termina
audit-menu-btn-revoke = Revocar sanción
audit-menu-revoke-success = Sanción revocada.
audit-menu-status-active = ACTIVA
audit-menu-status-expired = CADUCADA
audit-menu-status-permanent = PERMANENTE
audit-menu-history-more = Hay más entradas
audit-menu-history-end = Fin del historial
audit-menu-history-empty = Todavía no hay entradas para este jugador.
audit-menu-history-hint = Elige una entrada para ver los detalles.
audit-menu-actions-title = [orange]{ -xcore } — Acciones
audit-menu-actions-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $player }[] [gray]#{ $pid }[]
    [gray]Entradas mostradas: [accent]{ $entriesShown }[]
    [gray]{ $pageState } [darkgray]|[] { $nextState }
    [lightgray]{ $hint }[]
audit-menu-actions-empty = Todavía no hay acciones de este jugador.
audit-menu-actions-hint = Elige una entrada para ver qué hizo este jugador.
audit-menu-summary-row = [accent]{ $action }[] [darkgray]•[] [white]{ $actor }[] [gray]— { $reason }
audit-menu-action-summary-row = [accent]{ $action }[] [darkgray]•[] [white]{ $target }[] [gray]— { $reason }
audit-menu-details-title = [orange]{ -xcore } — Detalles de la entrada
audit-menu-details-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $player }[] [gray]#{ $pid }[]
    { "" }
    [accent]■ Evento[]
    [gray]Acción: [white]{ $action }[]
    [gray]Autor: [white]{ $actor }[]
    [gray]Motivo: [white]{ $reason }[]
    { "" }
    [accent]■ Tiempo[]
    [gray]Ocurrió: [white]{ $occurredAt }[]
    [gray]Duración: [white]{ $duration }[]
    [gray]Termina: [white]{ $expiresAt }[]
    { "" }
    [accent]■ Metadatos[]
    [gray]ID de la entrada: [white]{ $auditId }[]
audit-menu-unknown-actor = Desconocido
audit-menu-unknown-target = Desconocido
audit-menu-reason-unspecified = Sin especificar
audit-menu-duration-permanent = Permanente
audit-menu-action-ban = Baneo
audit-menu-action-unban = Desbaneo
audit-menu-action-mute = Silencio
audit-menu-action-unmute = Fin del silencio
audit-menu-action-warn = Aviso
audit-menu-action-kick = Expulsión
audit-menu-action-note = Nota
audit-menu-action-quarantine = Cuarentena
audit-menu-action-unquarantine = Fin de la cuarentena
player-menu-player-max-rank = Rango máximo alcanzado
player-menu-player-hexed-progress = [gray]Victorias necesarias para [white]{ $nextRankName }[gray]: [accent]{ $requiredPoints }[]
player-menu-player-no-mode-stats = [gray]sin datos[]
player-menu-player-pvp-summary = [gray]partidas [white]{ $gamesPlayed }[], victorias [lime]{ $gamesWon }[], [sky]{ $winRate }%[]
player-menu-player-survival-summary = [gray]oleadas: máx. [lime]{ $bestWave }[], media [white]{ $averageWave }[] [gray](partidas: { $gamesPlayed })[]
player-menu-player-hexed-summary = [gray]partidas [white]{ $gamesPlayed }[], 1.er puesto [lime]{ $gamesWon }[], mejor puesto [accent]#{ $bestPlacement }[]
player-menu-time-days = { $value } d
player-menu-time-hours = { $value } h
player-menu-time-minutes = { $value } min
settings-language-label = Idioma: [green]{ $lang }[]
settings-translator-label = Traductor: [green]{ $lang }[]
badge-menu-title = [orange]{ -xcore } — Insignias
badge-menu-content =
    [white]Insignia del sistema: [green]{ $systemBadge }[]
    [white]Insignia activa: [green]{ $activeBadge }[]
    [white]Color del símbolo: [green]{ $symbolColorMode }[]
badge-menu-empty = [lightgray]Todavía no has desbloqueado ninguna insignia.
badge-menu-row = [white]{ $badge }[] [gray]-[] { $description }
badge-menu-symbol-color-button = Color del símbolo: [green]{ $mode }[]
badge-menu-symbol-color-title = [orange]{ -xcore } — Color del símbolo de la insignia
badge-menu-symbol-color-content =
    [white]Modo actual: [green]{ $mode }[]
    [lightgray]Elige cómo se colorea el símbolo de la insignia.
badge-menu-symbol-color-default = Color por defecto de la insignia
badge-menu-symbol-color-player-color = Color del jugador
badge-menu-view-all = Ver todas las insignias
badge-menu-all-title = [orange]{ -xcore } — Todas las insignias
badge-menu-all-content = [lightgray]Todas las insignias con su estado y descripción.
badge-menu-all-row = [white]{ $badge }[] [gray]-[] [accent]{ $state }[] [gray]-[] { $description }
badge-clear-button = Quitar insignia activa
badge-state-system = Sistema
badge-state-system-active = Sistema activa
badge-state-active = Activa
badge-state-unlocked = Desbloqueada
badge-state-locked = Bloqueada
badge-set-success = [accent]Insignia activa: [green]{ $badge }[].
badge-clear-success = [accent]Insignia activa quitada.
badge-grant-success = [accent]Se ha dado [green]{ $badge }[] a [green]{ $nickname }[][gray]#{ $pid }[].
badge-revoke-success = [accent]Se ha quitado [green]{ $badge }[] a [green]{ $nickname }[][gray]#{ $pid }[].
badge-already-unlocked = [scarlet]⚠ La insignia [accent]{ $badge }[scarlet] ya está desbloqueada.
badge-not-owned = [scarlet]⚠ El jugador no tiene la insignia [accent]{ $badge }[scarlet].
error-badge-not-found = [scarlet]⚠ No se encontró la insignia [accent]{ $badge }[scarlet].
error-badge-not-unlocked = [scarlet]⚠ La insignia [accent]{ $badge }[scarlet] no está desbloqueada.
error-badge-not-selectable = [scarlet]⚠ La insignia [accent]{ $badge }[scarlet] no se puede elegir a mano.
badge-admin-name = Admin
badge-admin-description = Insignia automática de los administradores.
badge-developer-name = Desarrollador
badge-developer-description = Para los desarrolladores de XCore.
badge-translator-name = Traductor
badge-translator-description = Para quienes traducen XCore.
badge-map-maker-name = Creador de mapas
badge-map-maker-description = Para los creadores de mapas que se usan en el servidor.
badge-contributor-name = Colaborador
badge-contributor-description = Por contribuir a XCore o a su comunidad.
badge-bug-finder-name = Cazador de errores
badge-bug-finder-description = Por informar de errores con regularidad y calidad.
badge-event-winner-name = Ganador de eventos
badge-event-winner-description = Para los ganadores de eventos especiales del servidor.
badge-veteran-name = Veterano
badge-veteran-description = Para jugadores veteranos y respetados.
badge-season-champion-name = Campeón de temporada
badge-season-champion-description = Por quedar en el podio de una temporada clasificatoria.
commands-lb-description = Activa/desactiva la clasificación.
commands-lb-success =
    { $leaderboardEnabled ->
        [true] [accent]Clasificación [green]activada.
       *[other] [accent]Clasificación [scarlet]desactivada.
    }
leaderboard = [blue]Clasificación
commands-observer-description = Cambia al modo espectador. Se elimina tu unidad actual y pasas al equipo de espectadores.
commands-rank-description = Muestra tu rango o el de otro jugador.
commands-rank-content =
    { $nickname }
    { $rankTag } [accent]{ $rankName }
    [gold]Victorias: { $points }/{ $requiredPoints }
commands-ranks-description = Muestra información sobre los rangos.
commands-ranks-content =
    { $rankTag } [accent]{ $rankName }
    [gold]Requisito: [grey]{ $requiredPoints } [accent]victorias[]
commands-ranks-footer = Las victorias solo cuentan al derrotar a un jugador de tu rango o superior.
commands-top-description = Mejores jugadores.
commands-season-description = Temporada clasificatoria: tiempo restante, tu posición y los ganadores de la temporada anterior.
commands-matches-description = Tus partidas clasificatorias: resultados, cambios de puntuación y con quién jugaste.
commands-top-hexed-content = [orange]{ $index }. { $nickname }[accent]: [blue]{ $rankName } [cyan]{ $points } []victorias
commands-top-pvp-content = [orange]{ $index }. { $nickname }[accent]: [cyan]{ $rating }
top-menu-title = [orange]{ -xcore } — Mejores jugadores: [accent]{ $category }
top-menu-content =
    [lightgray]Elige un jugador para abrir su perfil.[]
    [lightgray]Página [green]{ $page }[]/[green]{ $totalPages }[] [gold]•[] [lightgray]Jugadores: [green]{ $totalEntries }[]
    { $selfRankLine }
top-menu-empty =
    [accent]Categoría: [green]{ $category }[]
    [gray]Todavía no hay jugadores.
top-menu-categories-title = [orange]{ -xcore } — Categoría de la clasificación
top-menu-categories-content =
    [lightgray]Elige qué clasificación mostrar.[]
    [lightgray]Actual: [green]{ $category }[]
top-menu-category-button = [accent]Categoría: [green]{ $category }[]
top-menu-category-mini-pvp = MiniPvP
top-menu-category-playtime = Tiempo de juego
top-menu-category-hexed = Hexed
top-menu-self-rank-known = [lightgray]Tu posición: [accent]#{ $rank }[]
top-menu-self-rank-unknown = [lightgray]Tu posición: [gray]no encontrada[]
top-menu-entry-mini-pvp = { $rankLabel } { $leagueIcon } [accent]{ $nickname }[] [gray]—[] [sky]{ $value }[]
top-menu-entry-playtime = { $rankLabel } [accent]{ $nickname }[] [gray]—[] [green]{ $value }[]
top-menu-entry-hexed = { $rankLabel } [accent]{ $nickname }[] [gray]—[] [violet]{ $rankName }[] [gold]•[] [cyan]{ $value }[]
top-menu-total-count = { $count } jugadores
top-menu-btn-find-self = Buscarme
top-menu-on-this-page = en esta página
top-menu-unranked = Todavía no estás clasificado en esta categoría
top-menu-self-rank-line = Tu puesto: { $rank }
top-menu-score-points = { $points } pts
top-menu-score-minutes = { $time }
# ==============================================================================
# Game Modes (Hexed, PvP, Surrender, AI)
# ==============================================================================
commands-surrender-description = Rendirse en Hexed. Tu equipo actual se destruye, tu unidad desaparece y pasas al equipo de espectadores.
commands-surrender-success = [green]Te has rendido y ahora eres espectador
commands-observer-success = [green]Ahora eres espectador
commands-observer-exit-success = [green]Ya no eres espectador
commands-ai-description = Controla la IA.
commands-ai-usage = [red]attack(a) []o [accent]idle(i)
hexed-popup = [blue]{ DURATION($remaining, style: "timer") }[] para que termine la partida.
hexed-eliminated = ¡{ $nickname } [gold]ha sido [scarlet]eliminado[]!
hexed-leaderboard-content = [orange]{ $index }. { $nickname }[accent]: [cyan]{ $hexes } [accent]hexágonos
hexed-ranks-newbie = Novato
hexed-ranks-regular = Habitual
hexed-ranks-advanced = Avanzado
hexed-ranks-veteran = Veterano
hexed-ranks-davastator = Devastador
hexed-ranks-the_legend = La Leyenda
hexed-game-over-header = Fin de la partida. Ganadores:
hexed-game-over-winner-row =
    [orange]{ $index }. { $name }[][accent]: [cyan]{ $cores } { $cores ->
        [one] hexágono
       *[other] hexágonos
    }
hexed-game-over-no-winners = Fin de la partida. Por desgracia, no se encontraron ganadores.
hexed-game-over-restart = Nueva partida en 10 segundos…
rating_league_scrap = Chatarra
rating_league_copper = Cobre
rating_league_lead = Plomo
rating_league_graphite = Grafito
rating_league_silicon = Silicio
rating_league_titanium = Titanio
rating_league_thorium = Torio
rating_league_plastanium = Plastanio
rating_league_phase_fabric = Tejido de fase
rating_league_surge_alloy = Aleación eléctrica
pvp-team-won = Tu equipo ha ganado. Tu puntuación sube { $increased }
pvp-team-lose = Tu equipo ha perdido. Tu puntuación baja { $reduced }
pvp-match-settlement-win = [accent]■ Resultado: [green]¡Victoria![] Tu puntuación: [stat]{ $oldRating }[] -> [stat]{ $newRating }[] [green](+{ $delta })[] { $leagueIcon } { $leagueName }
pvp-match-settlement-loss = [accent]■ Resultado: [scarlet]¡Derrota![] Tu puntuación: [stat]{ $oldRating }[] -> [stat]{ $newRating }[] [scarlet]({ $delta })[] { $leagueIcon } { $leagueName }
pvp-match-settlement-draw = [accent]■ Resultado: [yellow]¡Empate![] Tu puntuación: [stat]{ $oldRating }[] -> [stat]{ $newRating }[] [yellow]({ $delta })[] { $leagueIcon } { $leagueName }
pvp-match-settlement-exempt = [accent]■ Resultado: [lightgray]Sin cambio de puntuación (tiempo de juego activo insuficiente).[]
pvp-match-settlement-details = [lightgray]Detalles de la partida: [white]/matches[][]
season-ending-soon = [accent]■ [white]{ $ladder }[]: la temporada { $number } termina en [stat]{ $remaining }[]. ¡Aprovecha tus últimas partidas!
season-started = [accent]■ [white]{ $ladder }[]: la temporada { $previous } ha terminado. ¡Empieza la temporada { $number }!
season-reset-soft = [lightgray]Las puntuaciones se han acercado al valor inicial y los contadores de partidas empiezan de cero.[]
season-reset-hard = [lightgray]Todos empiezan de nuevo con la puntuación inicial.[]
season-reset-none = [lightgray]Las puntuaciones se mantienen; los contadores de partidas empiezan de cero.[]
season-title = Temporada { $number }
top-menu-scope-current = { $season } [lightgray]· termina en { $remaining }[]
top-menu-scope-past = { $season } [gray]· { $from } – { $to }[]
ladder-profile-standing = { $leagueIcon } [white]{ $league }[] [accent]{ $rating } ELO[]
ladder-profile-headline = [accent]{ $icon }[] [lightgray]{ $ladder }:[] { $standing }
ladder-profile-unplaced = [gray]sin partidas clasificatorias esta temporada[]
ladder-profile-matches = [lightgray]Partidas:[] [white]{ $matches }[]
ladder-profile-wins = [lightgray]Victorias:[] [white]{ $wins }[] [gray]({ $rate }%)[]
ladder-profile-rank = [lightgray]Puesto:[] [accent]#{ $rank }[]
ladder-profile-peak = [lightgray]Máximo:[] [white]{ $rating }[]
ladder-profile-season = [lightgray]{ $season } · termina en [white]{ $remaining }[][]
ladder-profile-season-closing = [lightgray]{ $season } · se están contando los resultados[]
ladder-profile-history = [gray]{ $season } — { $rank }, { $league }, { $rating } ELO[]
season-menu-title = Temporadas clasificatorias
season-menu-card-title = { $ladder } — { $season }
season-menu-ends = [lightgray]Termina en [white]{ $remaining }[] ({ $date })[]
season-menu-closing = [lightgray]La temporada ha terminado, se están contando los resultados.[]
season-menu-participants = [lightgray]Jugadores esta temporada: [white]{ $count }[][]
season-menu-you = [lightgray]Tú:[] { $standing }
season-menu-previous = [lightgray]{ $season } — ganadores:[]
season-menu-podium-entry = [gold]{ $place }.[] [white]{ $name }[] [gray]—[] { $league } [accent]{ $rating }[]
season-menu-open-top = Clasificación
season-menu-prizes = [lightgray]Premios de esta temporada:[]
season-menu-prize-entry = [gold]{ $places }.[] [white]{ $prize }[]
season-menu-podium-prizes = [gray] · [gold]{ $prizes }[]
prize-grant-line = [lightgray]{ $season } · premio:[] [gold]{ $prize }[] [gray]({ $status })[]
prize-status-pending = pendiente de entrega
prize-status-granted = recibido
prize-status-delivered = entregado
prize-status-failed = un admin se encargará
season-menu-empty = Todavía no hay temporadas clasificatorias.
pvp-hud-status = [accent]MiniPvP[] | [stat]Vivos:[] { $teams } | [gray]{ $time }[]
pvp-leaderboard-content = [orange]{ $index }. { $nickname }[accent]:[cyan] { $rating } [accent]puntos
pvp-you-spectator = [scarlet]Has sido eliminado. Espera a la siguiente partida.
# ==============================================================================
# Events & Notifications
# ==============================================================================
player-joined-pid = { $nickname } [grey]#[white]{ $pid }[grey] [accent]se ha unido.
player-joined-username = { $nickname } [grey]@[white]{ $username }[grey] [accent]se ha unido.
player-joined-both = { $nickname } [accent]@{ $username } [grey]#[white]{ $pid }[grey] [accent]se ha unido.
player-joined-none = { $nickname } [accent]se ha unido.

player-left-pid = { $nickname } [grey]#[white]{ $pid }[grey] [accent]se ha ido.
player-left-username = { $nickname } [grey]@[white]{ $username }[grey] [accent]se ha ido.
player-left-both = { $nickname } [accent]@{ $username } [grey]#[white]{ $pid }[grey] [accent]se ha ido.
player-left-none = { $nickname } [accent]se ha ido.

player-settings-identity-mode = [accent]Identificador en los anuncios:[]
player-settings-identity-mode-pid = Solo ID (#12)
player-settings-identity-mode-username = Solo nombre de usuario (@Steve)
player-settings-identity-mode-both = Ambos (@Steve #12)
player-settings-identity-mode-none = Oculto

notification-votekick-playtime =
    [accent]¡Enhorabuena! Has jugado [lightgray]{ $votekickPlayTime }[] { $votekickPlayTime ->
        [one] minuto
       *[other] minutos
    } y ya puedes iniciar votaciones de expulsión.
notification-global-chat-playtime =
    [accent]¡Enhorabuena! Has jugado [lightgray]{ $globalChatPlayTime }[] { $globalChatPlayTime ->
        [one] minuto
       *[other] minutos
    } y ya puedes escribir en el chat global.
    [lightgray]Escribe [accent]/g [gray]<mensaje…>[lightgray] para enviar un mensaje.
notification-admin-kick = { $admin }[accent] ha expulsado a { $target }[].
notification-admin-wave-skip = { $admin }[accent] ha saltado la oleada.
server-restart-countdown =
    Reinicio en { $seconds ->
        [one] { $seconds } segundo
       *[other] { $seconds } segundos
    }
like-map-success = [green]¡Te gusta este mapa!
like-map-changed = [green]¡Has cambiado de opinión: ahora te gusta!
dislike-map-success = [orange]No te gusta este mapa.
dislike-map-changed = [orange]Has cambiado de opinión: ahora no te gusta.
like-event-success = [green]¡Te gusta este evento!
like-event-changed = [green]¡Has cambiado de opinión: ahora te gusta!
dislike-event-success = [orange]No te gusta este evento.
dislike-event-changed = [orange]Has cambiado de opinión: ahora no te gusta.

# ==============================================================================
# Events (Server)
# ==============================================================================

commands-event-description = Menú de gestión de eventos.
commands-events-description = Lista de todos los eventos de los servidores.
event-events = Eventos
event-menu-main = Eventos principales
event-menu-main-title = [orange]{ -xcore } — Eventos
event-menu-main-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]Centro de eventos[]
    [lightgray]Todos los eventos activos y planificados del servidor en un solo lugar.[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Evento actual[]
    [gray]Estado: [white]{ $currentEventState }[]
    [gray]Seleccionado: [white]{ $currentEventName }[]
    { "" }
    [accent]■ Votación[]
    [gray]Sesión de votación: [white]{ $voteStatus }[]
    { "" }
    [accent]■ Acciones[]
    [gray]Abre el catálogo, consulta el evento actual o prepara uno nuevo.[]
event-menu-event = Evento
event-menu-event-title = [orange]{ -xcore } — Evento
event-menu-event-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $name }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Resumen[]
    [gray]Autor: [white]{ $author }[]
    [gray]Mapa: [white]{ $mapName }[]
    [gray]Tipo: [white]{ $eventType }[] [darkgray]|[gray] Estado: [white]{ $eventState }[]
    [gray]Temporal: [white]{ $isTemporary }[]
    { "" }
    [accent]■ Calendario[]
    [gray]Creado: [white]{ $createdEventTime }[]
    [gray]Inicio previsto: [white]{ $plannedStartTime }[]
    [gray]Fin previsto: [white]{ $plannedEndTime }[]
    { "" }
    [accent]■ Reputación[]
    [gray]Me gusta: [white]{ $like }[] [darkgray]|[gray] No me gusta: [white]{ $dislike }[]
event-menu-event-map = Ver mapa
event-menu-events = Lista de eventos
event-menu-events-title = [orange]{ -xcore } — Lista de eventos
event-menu-events-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]Catálogo de eventos[]
    [lightgray]Página [green]{ $page }[]/[green]{ $total }[] [gold]•[] [lightgray]Eventos: [green]{ $count }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Filtros[]
    [gray]Terminados: [white]{ $finished }[]
    [gray]Principales: [white]{ $major }[] [darkgray]|[gray] Activos: [white]{ $active }[]
    { "" }
    [accent]■ Lista[]
    [gray]Elige un evento para ver su ficha.[]
event-menu-events-empty =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]Catálogo de eventos[]
    [lightgray]Todavía no hay eventos que coincidan con los filtros actuales.[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Filtros[]
    [gray]Terminados: [white]{ $finished }[]
    [gray]Principales: [white]{ $major }[] [darkgray]|[gray] Activos: [white]{ $active }[]
event-menu-events-row = [accent]{ $state }[] [darkgray]•[] [white]{ $type }[] [darkgray]—[] { $name }
event-menu-events-selected = [green]●[] [accent]{ $state }[] [darkgray]•[] [white]{ $type }[] [darkgray]—[] { $name }
event-menu-create-start = Crear
event-menu-create-start-title = [orange]{ -xcore } — Crear evento
event-menu-create-start-message = Escribe el nombre del nuevo evento
event-menu-create-start-default = Evento de { $playerName }
event-menu-create-start-map = Crear evento para este mapa
event-menu-edit = Editar
event-menu-edit-title = [orange]{ -xcore } — Editar evento
event-menu-edit-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $name }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Identidad[]
    [gray]Autor: [white]{ $author }[]
    [gray]Tipo: [white]{ $eventType }[]
    { "" }
    [accent]■ Mapa[]
    [gray]Mapa elegido: [white]{ $mapName }[]
    { "" }
    [accent]■ Calendario[]
    [gray]Inicio previsto: [white]{ $plannedStartTime }[]
    [gray]Fin previsto: [white]{ $plannedEndTime }[]
    { "" }
    [accent]■ Opciones[]
    [gray]Temporal: [white]{ $isTemporary }[]
event-menu-edit-name = Nombre
event-menu-edit-name-reset = [scarlet]Restablecer nombre
event-menu-edit-name-title = [orange]{ -xcore } — Editar evento
event-menu-edit-name-message = Editar nombre:
event-menu-edit-description = Descripción
event-menu-edit-description-title = [orange]{ -xcore } — Editar evento
event-menu-edit-description-message = Editar descripción:
event-menu-edit-map = Cambiar mapa
event-menu-edit-temporary-active = [green]Temporal
event-menu-edit-temporary-inactive = [gray]Temporal
event-menu-edit-major-active = [green]Principal
event-menu-edit-major-inactive = [gray]Principal
event-menu-edit-planned-start = Inicio del evento
event-menu-edit-planned-start-title = [orange]{ -xcore } — Editar evento
event-menu-edit-planned-start-message = Escribe la hora de inicio en ms o con m/h/d:
event-menu-edit-planned-end = Fin del evento
event-menu-edit-planned-end-title = [orange]{ -xcore } — Editar evento
event-menu-edit-planned-end-message = Escribe la hora de fin en ms o con m/h/d:
event-menu-maps = Mapas
event-menu-maps-title = [orange]{ -xcore } — Elegir mapa
event-menu-maps-content = [white]Página [green]{ $page }[] de [green]{ $total }[]
vote-event-vote =
    { $nickname }[lightgray] ha votado cambiar el evento actual a [orange]{ $name }[lightgray]. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Escribe [orange]y[] o [orange]n[] para votar.
vote-event-left = { $nickname }[lightgray] se ha ido. Su voto para cambiar el evento se ha cancelado. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
vote-event-fail = [lightgray]La votación ha fallado. No hay votos suficientes para cambiar el evento a [orange]{ $name }[].
vote-event-success = [orange]Votación aprobada. El evento [accent]{ $name }[] se cargará en el próximo cambio de mapa.
vote-event-cancelled = [lightgray]El administrador { $admin } ha cancelado la votación para cambiar el evento a [orange]{ $name }[lightgray].
event-vote = [orange]Votación
event-avote = [red]Cambio inmediato
event-menu-vote-stop = Detener votación
event-menu-stop = Detener evento
event-menu-this-event = [orange]Evento actual
event-menu-type-major = Evento principal
event-menu-type-regular = Evento normal
event-menu-state-none = Sin evento activo
event-menu-state-planned = Planificado
event-menu-state-active = En curso
event-menu-state-finished = Terminado
event-menu-vote-status-running = En curso
event-menu-vote-status-idle = Inactiva
date-time-picker-title = [orange]{ -xcore } — Fecha y hora
date-time-picker-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $field }[]
    [lightgray]Valor actual: [white]{ $value }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Fecha[]
    [gray]Elige primero un día y luego ajusta la hora abajo.[]
    { "" }
    [accent]■ Hora[]
    [gray]Usa los valores predefinidos o los ajustes finos para planificar con precisión.[]
    { "" }
    [accent]■ Entrada manual[]
    [gray]Solo si necesitas milisegundos exactos o un valor relativo +m/+h/+d.[]
date-time-picker-field-generic = Hora prevista
date-time-picker-today = Hoy
date-time-picker-tomorrow = Mañana
date-time-picker-plus-2d = +2 días
date-time-picker-plus-7d = +7 días
date-time-picker-now = Ahora
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
date-time-picker-reset = Restablecer
date-time-picker-manual = Entrada manual
date-time-picker-manual-title = [orange]{ -xcore } — Entrada manual de la hora
date-time-picker-manual-message = Escribe milisegundos absolutos o un tiempo relativo como +30m, +2h, +1d.
event-end = ¡El evento [green]{ $name }[] ha terminado!
# ==============================================================================
# Errors
# ==============================================================================
error-access-denied = [scarlet]⚠ Acceso denegado.
error-ip-changed = [scarlet]⚠ Tu dirección IP ha cambiado. Se han retirado los permisos de admin.
error-not-enough-params = [scarlet]⚠ Faltan parámetros de posición.
error-player-not-found = [scarlet]Jugador no encontrado.
error-player-not-teammate = [scarlet]⚠ Ese jugador no está en tu equipo.
error-player-admin = [scarlet]⚠ No intentes expulsar a un admin. ⚠
error-already-voted = [scarlet]⚠ Ya has votado. Tranquilo.
error-playtime-requirement =
    [scarlet]⚠ Necesitas haber jugado al menos { $time } { $time ->
        [one] minuto
       *[other] minutos
    } para usar esta función.
error-globalchat-total-playtime =
    [scarlet]⚠ Para escribir en el chat global necesitas haber jugado { $globalChatPlayTime } { $globalChatPlayTime ->
        [one] minuto
       *[other] minutos
    }.
error-votekick-total-playtime =
    [scarlet]⚠ Para iniciar una votación de expulsión necesitas haber jugado { $votekickPlayTime } { $votekickPlayTime ->
        [one] minuto
       *[other] minutos
    }.
error-vote-yourself = [scarlet]⚠ No puedes votar en tu propia votación.
error-vote-in-progress = [scarlet]⚠ Ya hay una votación en curso.
error-no-voting = [scarlet]⚠ No hay ninguna votación en este momento.
error-wave-vote-unavailable = [scarlet]⚠ Adelantar una oleada solo es posible en modos con oleadas.
error-no-map = [scarlet]⚠ No hay mapa definido.
error-map-not-event = [scarlet]⚠ El mapa no forma parte del evento actual.
error-map-not-found = [scarlet]⚠ ¡Mapa no encontrado! [accent]Usa [cyan]/maps[] para ver todos los mapas disponibles.
error-maps-empty = [scarlet]⚠ La lista de mapas está vacía.
error-event-not-found = [scarlet]⚠ ¡Evento no encontrado! [accent]Usa [cyan]/events[] para ver los eventos disponibles.
error-page-between = [scarlet]⚠ 'page' debe ser un número entre[orange] 1[] y [orange]{ $totalPages }[].
error-page-number = [scarlet]'page' debe ser un número.
error-wrong-number = [scarlet]⚠ Formato de número incorrecto.
error-wrong-period-format = [scarlet]⚠ Formato de duración incorrecto. Ejemplo: 1h 30m, 30 ({ hours })
error-invalid-id = [scarlet]⚠ ID de jugador no válido.
error-spectator = [scarlet]⚠ Eres espectador y no puedes usar este comando.
error-admin-password-too-short = [scarlet]⚠ La contraseña de administrador debe tener al menos 8 caracteres.
error-wrong-admin-password = [scarlet]⚠ Contraseña de admin incorrecta.
error-internal = [scarlet]Error interno.
error-processing-request = [scarlet]Se produjo un error al procesar la solicitud.
error-no-access = [scarlet]⚠ Sin acceso.
error-nickname-too-long = [scarlet]⚠ El nombre es demasiado largo. Máximo { $max } caracteres visibles.
error-private-message-invalid-pid = [scarlet]⚠ PID de mensaje privado no válido. Usa el formato [lightgray]#123[].
error-private-message-self = [scarlet]⚠ No puedes enviarte un mensaje privado a ti mismo.
error-private-message-empty = [scarlet]⚠ El mensaje no puede estar vacío.
error-private-message-too-long = [scarlet]⚠ El mensaje es demasiado largo. Máximo { $max } caracteres.
error-private-message-cooldown = [scarlet]⚠ Espera { DURATION($seconds) } antes de enviar otro mensaje privado.
error-private-message-target-unavailable = [scarlet]⚠ Este jugador no puede recibir mensajes privados ahora.
error-private-message-no-reply-target = [scarlet]⚠ No hay ningún contacto reciente al que responder.
error-private-message-not-found = [scarlet]⚠ Mensaje no encontrado.
error-private-message-block-self = [scarlet]⚠ No puedes bloquearte a ti mismo.
error-private-message-block-limit = [scarlet]⚠ Se ha alcanzado el límite de la lista de bloqueados ({ $limit }).
ban-menu-duration-title = [orange]{ -xcore } - Duración del baneo
ban-menu-duration-message = Escribe la duración del baneo de { $nickname }. Ejemplo: 1d, 12h, 30m
ban-menu-reason-title = [orange]{ -xcore } - Motivo del baneo
ban-menu-reason-message = Escribe el motivo del baneo de { $nickname }. Déjalo vacío para usar el motivo por defecto.
ban-menu-confirm-title = [orange]{ -xcore } - Confirmar baneo
ban-menu-confirm-content =
    [white]Jugador: { $nickname }[]
    [white]Duración: [accent]{ $duration }[]
    [white]Motivo: [accent]{ $reason }[]
ban-menu-confirm-action = [scarlet]Banear jugador
error-invalid-syntax = [scarlet]⚠ Sintaxis del comando no válida. Uso: [lightgray]/'{ $syntax }'.
error-invalid-sender = [scarlet]⚠ Emisor del comando no válido. Este comando requiere: '[lightgray]{ $type }[]'.
error-argument-parse-generic = [scarlet]⚠ Argumento no válido: '{ $error }'.
exception-unexpected = [scarlet]⚠ Se produjo un error interno al ejecutar este comando.
exception-invalid-argument = [scarlet]⚠ Argumento del comando no válido: '{ $cause }'.
exception-no-such-command = [scarlet]⚠ Comando desconocido.
exception-no-permission = [scarlet]⚠ Acceso denegado.
exception-invalid-sender = [scarlet]⚠ '{ $actual }' no puede ejecutar este comando. Emisor necesario: [lightgray]{ $expected }[].
exception-invalid-sender-list = [scarlet]⚠ '{ $actual }' no puede ejecutar este comando. Emisores permitidos: [lightgray]{ $expected }[].
exception-invalid-syntax = [scarlet]⚠ Sintaxis del comando no válida. Uso: [lightgray]/'{ $syntax }'.
argument-parse-failure-boolean = [scarlet]⚠ No se pudo leer un valor booleano de '{ $input }'.
argument-parse-failure-number = [scarlet]⚠ '{ $input }' no es un número válido en el rango [{ $min }, { $max }].
argument-parse-failure-char = [scarlet]⚠ '{ $input }' no es un carácter válido.
argument-parse-failure-enum = [scarlet]⚠ '{ $input }' no es una opción válida. Permitido: [lightgray]{ $acceptableValues }
argument-parse-failure-string = [scarlet]⚠ Formato de texto no válido para '{ $input }'.
argument-parse-failure-uuid = [scarlet]⚠ Formato de UUID no válido: '{ $input }'.
argument-parse-failure-regex = [scarlet]⚠ La entrada '{ $input }' no coincide con el patrón '{ $pattern }'.
argument-parse-failure-color = [scarlet]⚠ '{ $input }' no es un color válido.
argument-parse-failure-duration = [scarlet]⚠ '{ $input }' no es un formato de duración válido.
argument-parse-failure-aggregate-missing = [scarlet]⚠ Falta el componente '{ $component }'.
argument-parse-failure-aggregate-failure = [scarlet]⚠ Componente no válido '{ $component }': '{ $failure }'.
argument-parse-failure-either = [scarlet]⚠ No se pudo obtener { $primary } ni { $fallback } de '{ $input }'.
argument-parse-failure-flag-unknown = [scarlet]⚠ Opción desconocida: '{ $flag }'.
argument-parse-failure-flag-duplicate = [scarlet]⚠ Opción duplicada: '{ $flag }'.
argument-parse-failure-flag-duplicate-flag = [scarlet]⚠ Opción duplicada: '{ $flag }'.
argument-parse-failure-flag-no-flag-started = [scarlet]⚠ No se ha iniciado ninguna opción. No se sabe qué hacer con '{ $input }'.
argument-parse-failure-flag-missing-argument = [scarlet]⚠ Falta el argumento de la opción: '{ $flag }'.
argument-parse-failure-flag-no-permission = [scarlet]⚠ No tienes permiso para usar la opción '{ $flag }'.
argument-parse-failure-selector-syntax = [scarlet]⚠ Selector no válido '{ $input }': [lightgray]{ $reason }
argument-parse-failure-selector-no-such-target = [scarlet]⚠ Nada coincide con '{ $input }'.
argument-parse-failure-selector-too-many-targets = [scarlet]⚠ '{ $input }' coincide con varios objetivos, pero este comando necesita uno.
argument-parse-failure-selector-denied = [scarlet]⚠ Aquí no se permiten selectores: [lightgray]{ $reason }
argument-parse-failure-selector-kind-not-allowed = [scarlet]⚠ El selector '{ $kind }' no está permitido aquí.
argument-parse-failure-selector-sender-required = [scarlet]⚠ El selector '{ $kind }' solo se puede usar en el juego.
argument-parse-failure-selector-limit-exceeded = [scarlet]⚠ El selector coincide con { $count } objetivos, el límite es { $limit }.
argument-parse-failure-team = [scarlet]⚠ Equipo '{ $input }' no encontrado.
argument-parse-failure-content = [scarlet]⚠ '{ $input }' no es un { $type } válido.
# ==============================================================================
# Button Status
# ==============================================================================
finished = terminado
finished-neutral = [orange]Terminados
finished-active = [green]Terminados
finished-inactive = [red]Terminados
major = Principal
major-neutral = [orange]Principales
major-active = [green]Principales
major-inactive = [red]Principales
active = Activo
active-neutral = [orange]Activos
active-active = [green]Activos
active-inactive = [red]Activos
admin = Admin
admin-neutral = [orange]Admin
admin-active = [green]Admin
admin-inactive = [red]Admin
player-leaderboard-active = [green]Clasificación: activada[]
player-leaderboard-inactive = [red]Clasificación: desactivada[]
# ==============================================================================
# Miscellaneous
# ==============================================================================
hours = horas
days = días
success = [green]Correcto
empty = [accent]Vacío
never = Nunca
save = Guardar
close = [scarlet]Cerrar
previous = [accent]« Anterior
next = [accent]Siguiente »
cancel = Cancelar
back = Atrás
yes = Sí
no = No
test = Prueba
no-description = Sin descripción
discord = Discord
github = Github
donatello = Donatello
weblate = Weblate
discord-red-vs-blue = RedVSBlue
auto = Auto
on = Activado
off = Desactivado
error-command-disabled = [scarlet]⚠ El comando [accent]/{ $command }[scarlet] está desactivado en este servidor.
error-feature-disabled = [scarlet]⚠ Esta función está desactivada en este servidor.
none = Ninguno
unknown = Desconocido
error-nickname-badge-glyph = [scarlet]⚠ El nombre personalizado no puede contener iconos de insignias reservados.

# Server browser (/servers)
player-servers-title = RED DE SERVIDORES XCORE
player-servers-cat-all = Todos
player-servers-cat-pvp = PvP
player-servers-cat-survival = Supervivencia
player-servers-cat-special = Especial
player-servers-hint = Toca la tarjeta de un servidor para conectarte
player-servers-refresh = Actualizar
player-servers-already-connected = [gold]● ¡Ya estás conectado a este servidor!
player-servers-transferring = [accent]Transfiriendo al servidor [white]{ $server }[]...
player-servers-full = [scarlet]¡El servidor { $server } está lleno! Espera a que haya una plaza libre.
player-servers-offline = [scarlet]El servidor { $server } está desconectado ahora mismo.
player-servers-not-found = [scarlet]Servidor '{ $server }' no encontrado.
player-servers-empty-category = [lightgray]No hay servidores disponibles en esta categoría.[]
player-servers-online-summary = [green]● { $players } [gray]jugando[] [darkgray]|[] [sky]{ $servers } [gray]en línea[]
player-servers-badge-current = [gold]● ESTÁS AQUÍ[]
player-servers-offline-badge = [darkgray]● DESCONECTADO[]
player-servers-capacity-full = [scarlet]● { $players }/{ $max } LLENO[]
player-servers-capacity-normal = [green]● { $players }/{ $max } { $bar }
player-servers-card-current = [lightgray]Estás conectado a este servidor[]
player-servers-card-wave = [darkgray]|[] [accent]Oleada { $wave }[]
player-servers-card-empty = [sky]¡Sé el primero! Empieza una partida[]
player-servers-card-map = [gray]Mapa:[] [white]{ $map }[]
player-servers-card-mode = [gray]Modo:[] [white]{ $mode }[]

announcement-hub =
    [gold]★ [accent]Servidores XCore [lightgray]» [white]¿Te aburre esta partida?
    [lightgray]¡Explora otros servidores cuando quieras con [accent]/hub[lightgray]!
announcement-discord =
    [gold]★ [accent]Comunidad XCore [lightgray]» [white]¿Buscas compañeros y novedades?
    [lightgray]¡Únete a nuestro servidor de Discord con [accent]/discord[lightgray]!
announcement-help =
    [gold]★ [accent]Ayuda de XCore [lightgray]» [white]¿Necesitas ayuda o la lista de comandos?
    [lightgray]¡Escribe [accent]/help[lightgray] para ver todos los comandos disponibles!


error-only-players = [scarlet]⚠ Este comando solo puede usarse por jugadores.

player-settings-username-editable = [gold]★ Nombre de usuario (recompensa de evento):[]
player-settings-username-hint = Escribe un nombre de usuario único (4-32 caracteres)...
player-settings-username-locked = [gray]Nombre de usuario:[] [accent]@{ $username }[] [darkgray](Bloqueado)[]
player-settings-username-none = [gray]Nombre de usuario: [darkgray]Sin definir (gana un evento para desbloquearlo)[]

error-username-empty = ¡El nombre de usuario no puede estar vacío!
error-username-length = ¡El nombre de usuario debe tener entre 4 y 32 caracteres!
error-username-invalid-chars = ¡El nombre de usuario solo puede contener letras latinas, números y guiones bajos!
error-username-taken = ¡Otro jugador ya usa este nombre de usuario!

# ==============================================================================
# Permission nodes
# ==============================================================================
permission-mindustry-admin = Usar el menú de admin integrado del juego y saltar oleadas
permission-xcore-moderation-mute = Silenciar jugadores
permission-xcore-moderation-unmute = Quitar el silencio a jugadores
permission-xcore-moderation-kick = Expulsar jugadores
permission-xcore-moderation-ban = Banear jugadores
permission-xcore-moderation-unban = Quitar el baneo a jugadores
permission-xcore-moderation-audit-others = Ver el historial de moderación de otros jugadores
permission-xcore-moderation-votekick-immune = No puede ser expulsado por votación
permission-xcore-admin-tp = Teletransportar jugadores
permission-xcore-admin-broadcast = Enviar anuncios a todos
permission-xcore-admin-kill = Matar unidades y jugadores
permission-xcore-admin-heal = Curar unidades y jugadores
permission-xcore-admin-set-team = Cambiar el equipo de un jugador
permission-xcore-maps-force-rtv = Cambiar el mapa sin votación
permission-xcore-maps-force-vnw = Saltar una oleada sin votación
permission-xcore-votes-cancel = Cancelar una votación en curso
permission-xcore-events-create-major = Crear eventos principales
permission-xcore-events-edit-others = Editar eventos de otros jugadores
permission-xcore-events-force-vote = Iniciar un evento sin votación
permission-xcore-events-stop = Detener el evento en curso
permission-xcore-players-settings-others = Cambiar los ajustes de otros jugadores
permission-xcore-players-private-info = Ver datos privados de los jugadores, como su dirección IP
permission-xcore-bypass-playtime = Saltarse los requisitos de tiempo de juego de los comandos
permission-xcore-permissions-inspect = Ver quién tiene qué permisos
permission-xcore-permissions-manage = Cambiar roles y permisos

error-target-outranks = [scarlet]⚠ No puedes hacer eso a un jugador cuyo rol no es inferior al tuyo.
perm-me-legacy = [accent]Los roles están desactivados en este servidor. Admin: [white]{ $admin }
perm-me-header = [accent]Tus roles aquí (peso [white]{ $weight }[accent]):
perm-me-none = [lightgray] - ninguno
perm-me-logged-in = [green]Has iniciado sesión como staff.
perm-me-not-logged-in = [yellow]No has iniciado sesión como staff. Usa [white]/login <contraseña>[yellow].
perm-me-stale = [scarlet]Tus permisos de staff están en pausa: el servidor no pudo actualizarlos. Volverán solos.

help-ui-search-hint = Buscar comandos, alias o descripciones
help-ui-search-empty = [lightgray]No se encontraron comandos para tu búsqueda.[]

match-history-title = Historial de partidas
match-history-rank = #{ $rank } esta temporada
match-history-form = Últimas { $count }:
match-history-empty = Aquí aparecerán tus partidas clasificatorias.
match-history-since = El historial se guarda desde el { $date }.
match-history-load-failed = No se pudo cargar el historial de partidas. Inténtalo de nuevo en un momento.
match-history-back-to-list = Volver a la lista
match-history-your-result = Tu resultado
match-history-places = Posiciones
match-history-team-title = #{ $place } · { $team } · media { $average }
match-history-time-now = ahora mismo
match-history-time-minutes = hace { $count } min
match-history-time-hours = hace { $count } h
match-history-time-yesterday = ayer
match-history-outcome-win = [lime]Victoria[]
match-history-outcome-loss = [scarlet]Derrota[]
match-history-outcome-unrated = [lightgray]Sin puntuación[]
match-history-outcome-uncounted = [lightgray]No contada[]
match-history-lineup = { $own } contra { $other }
match-history-team-place = #{ $place } de { $teams } equipos
match-history-place = #{ $place } de { $players }
match-history-counted = Contada: { $reason }
match-history-not-counted = No contada: { $reason }
match-history-counted-share = Contado el { $percent } % de la partida
match-history-reason-winner = victoria
match-history-reason-defeated = derrota
match-history-reason-short-play = jugó menos de la mitad de la partida
match-history-reason-late-join = entró al final
match-history-reason-match-unrated = la partida no puntuaba
match-history-reason-unknown = sin motivo registrado
match-history-skip-not-enough-players = pocos jugadores
match-history-skip-admin-stop = detenida por un admin
match-history-skip-technical-error = error técnico
match-history-skip-unknown = sin puntuación
match-history-finish-surrender = por rendición
match-history-finish-timeout = por tiempo
match-history-finish-admin-stop = detenida por un admin
match-history-finish-technical-error = error técnico
