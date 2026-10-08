# ==============================================================================
# Terms
# ==============================================================================
-xcore = Serveur XCore
# ==============================================================================
# General & Help
# ==============================================================================
menu-main = Menu principal
commands-main-description = Ouvre le menu principal interactif.
menu-main-title = [orange]{ -xcore } — Menu principal
menu-main-content = Menu principal du serveur
help-menu = Menu d'aide
commands-help-description = Ouvre le menu d'aide interactif.
help-menu-title = [orange]{ -xcore } — Commandes
help-menu-content =
    [gray]Page [white]{ $page }[gray]/[white]{ $total }
    [lightgray]Choisissez une commande pour voir son utilisation détaillée :
help-menu-button = [accent]/{ $command } [gray]» Description : [white]{ $description }
help-command-with-overload-count = { $name } sur ({ $count })
help-command-title = [orange]» Nom : [white]/{ $name }
help-command-header =
    [orange]» [accent]Syntaxe : [white]{ $syntax }
    [orange]» [accent]Infos : [lightgray]{ $description }
help-aliases = [orange]» [accent]Alias : [white]{ $aliases }
help-args-title = [orange]» [accent]Arguments :
help-usages-title = [orange]» [accent]Utilisation :
help-usage-entry = [gray]• [white]{ $syntax }
help-usage-args-title = [orange]» [accent]Pour [white]{ $syntax }[accent] :
help-arg-entry = [gray]• [white]{ $arg } [lightgray]- { $description }
help-no-arguments = [gray]Aucun argument supplémentaire requis.
help-no-arg-description = Aucune description.
help-no-description = Aucune description pour cette commande.
help-legacy-command-content =
    [orange]» [accent]Commande : [white]/{ $name }
    [orange]» [accent]Paramètres : [white]{ $params }
    [orange]» [accent]Infos : [lightgray]{ $description }
    { "" }
    [gray](Ancienne commande, informations limitées)
help-legacy-command-content-no-params =
    [orange]» [accent]Commande : [white]/{ $name }
    [orange]» [accent]Infos : [lightgray]{ $description }
    { "" }
    [gray](Ancienne commande, informations limitées)
help-back = [lightgray]« Retour

# ==============================================================================
# Modern Reactive Help & Commands Guide (xcore-ui)
# ==============================================================================
help-ui-title = GUIDE DES COMMANDES
help-ui-summary = [gray]{ $count } commandes disponibles
help-ui-empty-category = [lightgray]Aucune commande dans cette catégorie.[]
help-ui-overloads = ({ $count } variantes)
help-ui-aliases = [lightgray]Alias :[] { $aliases }
help-ui-syntax-title = Syntaxe
help-ui-args-title = Paramètres
help-ui-arg-required = [scarlet]Obligatoire
help-ui-arg-optional = [sky]Facultatif
help-ui-btn-run = Exécuter
help-ui-btn-copy = Dans le chat
help-ui-btn-back = Retour
help-ui-copied = [accent]Commande : [white]/{ $syntax }
help-ui-executed = [accent]Exécution de la commande : [white]/{ $syntax }

# Categories
help-cat-all = Toutes
help-cat-general = Général
help-cat-game = Jeu
help-cat-social = Chat
help-cat-votes = Votes
help-cat-admin = Admin
# ==============================================================================
# Command Argument Descriptions
# ==============================================================================
# help
commands-help-page-description = Numéro de la page à afficher.
# login
commands-login-password-description = Votre mot de passe administrateur.
# ban
commands-ban-id-description = ID du joueur à bannir.
commands-ban-period-description = Durée du bannissement (ex. 1d, 2h, 30m).
commands-ban-reason-description = Raison du bannissement.
# unban
commands-unban-id-description = ID du joueur à débannir.
# mute
commands-mute-id-description = ID du joueur à rendre muet.
commands-mute-period-description = Durée du mute (ex. 1h, 30m).
commands-mute-reason-description = Raison du mute.
# unmute
commands-unmute-id-description = ID du joueur dont le mute est levé.
# votekick
commands-votekick-target-description = Joueur à expulser (ID ou nom).
commands-votekick-reason-description = Raison de l'expulsion.
# vote
commands-vote-choice-description = Votre vote : y (oui), n (non) ou c (annuler, admins uniquement).
# t (team chat)
commands-t-message-description = Message à envoyer à vos coéquipiers.
# g (global chat)
commands-g-message-description = Message à envoyer à tous les serveurs.
# tr (translator)
commands-tr-language-description = Code de langue, 'auto' ou 'off'.
# stats
commands-stats-id-description = ID du joueur dont afficher les statistiques
# rank
commands-rank-player-description = Joueur dont afficher le rang
# map
commands-map-map-description = Nom ou numéro de la carte.
# maps / maps-text
commands-maps-page-description = Numéro de page.
commands-maps-text-page-description = Numéro de page.
# rtv / artv
commands-rtv-map-description = Carte pour laquelle voter (facultatif).
commands-artv-map-description = Carte vers laquelle changer immédiatement.
# ai
commands-ai-state-description = État de l'IA : attack (a) ou idle (i).
# event / events
commands-events-page-description = Numéro de page.
# ==============================================================================
# General & Help (continued)
# ==============================================================================
commands-information-description = Affiche des informations sur le serveur.
commands-info = Informations
commands-info-title = [orange]{ -xcore } — Nom du serveur : [orange]{ $server-name }
commands-info-text =
    [accent]XCore[white] est un serveur [cyan]gratuit[white] pour jouer à [accent]Mindustry[white].
    { "" }
    Version de XCore — [accent]{ $version }[white]
commands-sync-description = Synchronise votre partie avec le serveur. Utile pour corriger des erreurs comme les unités fantômes.
commands-discord-description = Vous redirige vers le serveur Discord.
discord-menu-title = [orange]{ -xcore } — Discord
discord-menu-content =
    [white]Gérez ici votre liaison Discord.
    { "" }
    [white]Statut : { $status }
    [white]Serveur : [accent]{ $discordUrl }[]
discord-menu-open = Ouvrir Discord
discord-menu-link = Lier le compte
discord-menu-status = Actualiser le statut
discord-menu-unlink = Délier le compte
discord-menu-status-not-linked = [lightgray]non lié[]
discord-menu-status-linked = [green]{ $discordUsername }[] [gray]({ $discordId })[]
discord-link-menu-title = [orange]{ -xcore } — Lier le compte Discord
discord-link-menu-content =
    [white]Sur notre serveur Discord, lancez la commande slash du bot :
    { "" }
    [accent]/link { $code }[]
    { "" }
    [white]Expire dans : [accent]{ $expireMinutes }[] min
    [white]Discord : [accent]{ $discordUrl }[]
discord-link-menu-refresh = Actualiser le code
discord-link-menu-copy = Copier le code
discord-link-menu-regenerate = Générer un nouveau code
discord-link-menu-status = Retour au menu Discord
welcome =
    [accent]Bienvenue sur { $serverName } !
    [lightgray]Tapez [accent]/help[lightgray] pour voir la liste des commandes
    [lightgray]Tapez [accent]/vote [gray]<y/n>[lightgray] pour voter l'expulsion d'un joueur
    [lightgray]Tapez [accent]/votekick [gray]<ID/nom> <raison…>[lightgray] pour lancer un vote d'expulsion
    [lightgray]Tapez [accent]/t [gray]<message…>[lightgray] pour écrire à vos coéquipiers
    [lightgray]Tapez [accent]/g [gray]<message…>[lightgray] pour écrire à tous les serveurs
    [lightgray]Tapez [accent]/tr [gray]<langue/auto>[lightgray] pour activer le traducteur
    [lightgray]Tapez [accent]/discord[lightgray] pour ouvrir le menu Discord et lier votre compte
# ==============================================================================
# Chat & Social
# ==============================================================================
commands-t-description = Envoie un message uniquement à vos coéquipiers.
commands-t-chat = [{ "#" }{ $color }][Équipe] [coral]> { $badge }[accent]{ $name }[lightgray]: [white]{ $message }
commands-g-description = Envoie un message sur tous les serveurs.
commands-a-description = Envoie un message uniquement aux admins.
commands-msg-description = Envoie un message privé à un joueur.
commands-msg-id-description = ID du joueur.
commands-msg-message-description = Texte du message privé.
commands-reply-description = Répond au dernier joueur dans les messages privés.
commands-reply-message-description = Texte de la réponse.
commands-inbox-description = Ouvre le menu des messages privés.
commands-inbox-id-description = ID du joueur.
commands-tr-description = Définit la langue du traducteur.
commands-badge-description = Ouvre le menu des badges et gère votre badge actif.
commands-tr-success = [accent]La langue du traducteur est maintenant [grey]{ $translatorLanguage }[] !
commands-tr-off = [accent]Le traducteur est [scarlet]désactivé[] !
commands-tr-not-found = [scarlet]⚠ Cette langue n'existe pas.
discord-chat-format = [#5865F2][DISCORD][] [lightgray]| [accent]{ $author }[lightgray] >> [white]{ $message }
global-chat-format = [royal][[[orange]GLOBAL [lightgray](de [accent]{ $server }[])[] { $author }[]]: [white]{ $message }
private-message-received = [sky][MP][] [lightgray]de [accent]{ $author } [gray]#{ $pid }[lightgray]: [white]{ $message }
private-message-sent = [sky][MP][] [lightgray]à [accent]{ $target } [gray]#{ $pid }[lightgray]: [white]{ $message }
private-message-unread-count =
    [accent]Vous avez [white]{ $count }[accent] { $count ->
        [one] message privé non lu
       *[other] messages privés non lus
    }.
private-message-join-notification =
    [accent]Vous avez [white]{ $count }[accent] { $count ->
        [one] message privé non lu
       *[other] messages privés non lus
    }. Utilisez [white]/inbox[accent] pour { $count ->
        [one] le lire
       *[other] les lire
    }.
private-message-block-success = [accent]Les messages privés de [white]{ $target } [gray]#{ $pid }[accent] sont maintenant bloqués.
private-message-block-already = [lightgray]Les messages privés de [white]{ $target } [gray]#{ $pid }[lightgray] sont déjà bloqués.
private-message-unblock-success = [accent]Les messages privés de [white]{ $target } [gray]#{ $pid }[accent] ne sont plus bloqués.
private-message-unblock-missing = [lightgray][white]{ $target } [gray]#{ $pid }[lightgray] n'est pas bloqué.
private-message-menu-title = [orange]{ -xcore } — Messages privés
private-message-menu-content =
    [white]Page [green]{ $page }[] sur [green]{ $total }[]
    [white]Non lus : [accent]{ $unread }[]
private-message-menu-empty = [lightgray]Votre boîte de réception est vide.
private-message-menu-entry-unread = [accent]Non lu[] [white]{ $author } [gray]#{ $pid }[] [lightgray]({ $time })[]: [white]{ $message }
private-message-menu-entry-read = [gray]Lu[] [white]{ $author } [gray]#{ $pid }[] [lightgray]({ $time })[]: [white]{ $message }
private-message-details-title = [orange]{ -xcore } — Message
private-message-details-content =
    [white]De : [accent]{ $author } [gray]#{ $pid }[]
    [white]Heure : [accent]{ $time }[]
    [white]Statut : [accent]{ $status }[]
    { "" }
    [white]{ $message }
private-message-status-unread = non lu
private-message-status-read = lu
private-message-blocked-title = [orange]{ -xcore } — Joueurs bloqués
private-message-blocked-content =
    [white]Page [green]{ $page }[] sur [green]{ $total }[]
    [white]Bloqués : [accent]{ $count }[]
private-message-blocked-empty = [lightgray]Vous n'avez bloqué aucun joueur.
private-message-blocked-entry = [white]{ $target } [gray]#{ $pid }[]
private-message-compose = Nouveau message
private-message-blocked = Bloqués
private-message-block = Bloquer l'expéditeur
private-message-unblock = Débloquer l'expéditeur
private-message-reply-title = Répondre
private-message-reply-message = Saisissez un message pour [accent]#{ $pid }[]
private-message-compose-target-title = Nouveau message
private-message-compose-target-message = Saisissez l'ID du joueur au format [accent]#123[]
private-message-compose-body-title = Texte du message
private-message-compose-body-message = Saisissez un message privé pour [accent]{ $pid }[]
# ==============================================================================
# Authentication & Admin Access
# ==============================================================================
commands-login-description = Active les droits d'admin si votre compte Discord lié y a déjà accès.
commands-login-incorrect-password = [scarlet]⚠ Mot de passe incorrect !
commands-login-success = [green]Droits d'admin accordés.
commands-login-confirmed = [green]Accès admin Discord confirmé.
commands-login-admin-password-created =
    [green]Mot de passe admin créé.
    [red]N'oubliez pas votre mot de passe ! Sinon, il faudra demander à un administrateur principal de le réinitialiser.
commands-login-request-approval-discord = [accent]Votre compte n'a pas l'accès administrateur Discord. Obtenez le rôle d'administrateur sur Discord et réessayez.
commands-login-verifying = [lightgray]Vérification du mot de passe administrateur...
commands-login-already-processing = [scarlet]⚠ Une demande de connexion est déjà en cours de traitement. Veuillez patienter.
commands-login-rate-limited = [scarlet]⚠ Trop de tentatives de connexion échouées. Veuillez patienter avant de réessayer.
commands-discord-link-created =
    [green]Code de liaison Discord créé : [accent]{ $code }[]
    [lightgray]Sur notre serveur Discord, lancez la commande du bot [accent]/link { $code }[] dans les [accent]{ $expireMinutes }[] min.
    [cyan]{ $discordUrl }
commands-discord-link-confirmed = [green]Compte Discord lié : [accent]{ $discordUsername }[]
commands-discord-link-already-linked = [lightgray]Ce compte Mindustry est déjà lié. Utilisez [accent]/discord status[] ou [accent]/discord unlink[].
commands-discord-link-error = [scarlet]Impossible de créer le code de liaison Discord. Réessayez plus tard.
commands-discord-status-not-linked = [lightgray]Votre compte n'est pas lié à Discord.
commands-discord-status-linked = [green]Discord lié : [accent]{ $discordUsername }[] [gray]({ $discordId })[]
commands-discord-unlink-not-linked = [lightgray]Votre compte n'est pas lié à Discord.
commands-discord-unlink-success = [green]Liaison Discord supprimée.
commands-logout-description = Se déconnecter. Cela [scarlet]retire vos droits d'admin.
commands-logout-successful = [green]Droits d'admin retirés.
# ==============================================================================
# Moderation (Ban, Mute, Kick)
# ==============================================================================
commands-ban-description = Bannit un joueur.
commands-ban-success = { $nickname } [scarlet]banni
commands-unban-description = Débannit un joueur.
commands-unban-success = { $nickname }[accent] #{ $pid } [green]a été débanni.
commands-mute-description = Rend un joueur muet.
commands-mute-success = [accent]{ $nickname } a été rendu muet
commands-unmute-description = Lève le mute d'un joueur.
commands-unmute-success = [green]Mute levé pour []{ $nickname }
commands-alert-description = Affiche une bannière d'annonce bien visible aux joueurs ciblés ou à tous.
commands-toast-description = Affiche une notification d'avertissement aux joueurs ciblés.
commands-announcement-description = Diffuse une annonce périodique par clé, ou la suivante de la rotation.
commands-audit-description = Affiche l'historique et les actions de l'équipe et de la modération.
ban-content = [scarlet]⚠ Banni[]
    [accent]{ $nickname }[white] — vous êtes banni définitivement de ce serveur.
    [lightgray]Pour faire appel, rendez-vous sur le salon Discord [gray]{ support-channel }[] :
    [cyan]{ $discordUrl }
ban-cancelled = [accent]Le bannissement de [scarlet]{ $nickname }[accent] a été annulé
tempban-content = [scarlet]⚠ Banni[]
    [accent]{ $nickname }[white] — vous êtes banni temporairement de ce serveur.
    { "" }
    [orange]» [accent]Admin : [white]{ $adminName }
    [orange]» [accent]Raison : [gold]{ $reason }
    [orange]» [accent]Temps restant : { DURATION($duration, style: "full", colored: "true", maxUnits: 2) }
    [orange]» [accent]Fin : [white]{ DATETIME($expireDate, dateStyle: "medium", timeStyle: "short") }
    { "" }
    [lightgray]Pour faire appel, rendez-vous sur le salon Discord [gray]{ support-channel }[] :
    [cyan]{ $discordUrl }
tempban-player-banned = [scarlet] L'admin { $adminName }[scarlet] a banni le joueur [gray]'[]{ $playerName }[gray]'
you-are-muted-by =
    [orange]⚠ Chat restreint[]
    [lightgray]Vous avez été rendu muet par l'administrateur [accent]{ $adminName }[lightgray].
    [orange]» [accent]Raison : [gold]{ $reason }
    [orange]» [accent]Temps restant : { DURATION($duration, style: "full", colored: "true", maxUnits: 2) }
you-are-muted =
    [orange]⚠ Chat restreint[]
    [lightgray]Vous ne pouvez pas envoyer de messages tant que ce mute est actif.
    [orange]» [accent]Admin : [white]{ $adminName }
    [orange]» [accent]Raison : [gold]{ $reason }
    [orange]» [accent]Temps restant : { DURATION($duration, style: "full", colored: "true", maxUnits: 2) }
kick-pirated-game = [accent]Client non autorisé détecté. [scarlet]Accès refusé[]. Veuillez jouer avec la version [lime]officielle[] de [blue]Steam[], [blue]Google Play[] ou [blue]itch.io[].
kick-recently-kicked =
    [accent]Vous avez été expulsé de ce serveur récemment.
    Attendez [cyan]{ DURATION($remaining, style: "timer") }[accent] avant de revenir.
kick-admintools-outdated =
    [green]Version d'AdminTools requise : [grey]{ $requiredVersion }[]
    [scarlet]Votre version d'AdminTools : [grey]{ $version }[]
    { "" }
    [cyan]Mettez à jour AdminTools pour rejoindre ce serveur.
support-channel = #reports-appeals
# ==============================================================================
# Voting (VoteKick)
# ==============================================================================
commands-votekick-description = Vote pour expulser un joueur du serveur.
commands-vote-description = Votez dans le vote en cours.
commands-vote-vote-with = [scarlet]⚠ Votez avec [orange]/vote <y/n/c>
votekick-vote =
    { $starter } [grey]#[white]{ $starterId }[lightgray] a voté pour expulser { $target } [grey]#[white]{ $targetId }[lightgray] pour [orange]{ $reason }[lightgray]. ([accent]{ $votes }[]/[accent]{ $required }[])
    [lightgray]Tapez [orange]/vote <y/n>[] pour voter.
votekick-left = { $player }[lightgray] est parti. Son vote a été annulé. ([accent]{ $votes }[]/[accent]{ $required }[])
votekick-fail = [lightgray]Vote échoué. Pas assez de votes pour expulser { $target }[lightgray].
votekick-cancelled = [scarlet]Le vote pour expulser { $target }[scarlet] a été annulé par { $admin }.
votekick-success =
    [orange]Vote réussi. { $target }[orange] est expulsé pour [scarlet]{ $minutes }[] { $minutes ->
        [one] minute
       *[other] minutes
    }.
# ==============================================================================
# Maps & RTV
# ==============================================================================
commands-map-description = Statistiques d'une carte précise.
commands-map-title = [orange]{ -xcore } — Carte
commands-map-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $name }[] [gray]par [sky]{ $author }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Aperçu[]
    [gray]Taille : [white]{ $width }x{ $height } [darkgray]|[gray] Votes : [lime]+{ $like } [darkgray]/[scarlet] -{ $dislike }[]
    { "" }
    [accent]■ Activité[]
    [gray]Parties au total : [white]{ $played } [darkgray]|[gray] Cette année : [white]{ $playedYear }[]
    [gray]Dernière partie : [white]{ $lastPlayed }[]
    [gray]Popularité : [white]{ $popularity } [darkgray]|[gray] Intérêt : [white]{ $interest } [darkgray]|[gray] Réputation : [white]{ $reputation }[]
    { "" }
    [accent]■ Durée des parties[]
    [gray]Min : [white]{ $min } [darkgray]|[gray] Moy. : [white]{ $avg } [darkgray]|[gray] Max : [white]{ $max }[]
commands-maps-description = Liste de toutes les cartes de ce serveur.
commands-maps-title = [orange]{ -xcore } — Cartes
commands-maps-content =
    [gray]Carte actuelle : [accent]{ $current }[]
    [white]Page [green]{ $page }[] sur [green]{ $total }[]
commands-maps-current-row = { $name } ★
commands-maps-text-description = Liste de toutes les cartes de ce serveur.
commands-maps-text-start-content =
    [accent]Carte actuelle : []{ $name }[white]
    [orange][gold]Liste des cartes [lightgray]{ $page }[gray]/[lightgray]{ $total }
commands-maps-text-content =
    { "" }
    { $index }. [orange] - [white]{ $name }[orange] | [green]{ $reputation }[orange] | [white]{ $width }x{ $height }[orange] | [white]{ $lastPlayed }[orange] | Par : [sky]{ $author }
commands-artv-description = Change de carte immédiatement.
commands-artv-map-skipped = { $nickname }[accent] a passé la carte. Carte suivante : { $name }.
commands-artv-event-skipped = { $nickname }[accent] a passé l'événement. Événement suivant : { $name }.
commands-rtv-description = Vote pour changer de carte.
commands-vnw-description = Vote pour lancer la prochaine vague plus tôt.
commands-avnw-description = Lance immédiatement la prochaine vague.
commands-like-description = Votez pour la carte actuelle (augmente sa réputation).
commands-dislike-description = Votez contre la carte actuelle.
map-vote-title = [orange]{ -xcore } — [scarlet]PARTIE TERMINÉE !
map-vote-content =
    { "" }
    Carte suivante : [accent]{ $mapName }[] par [accent]{ $author }[white].
    La nouvelle partie commence dans [accent]{ $seconds }[white] { $seconds ->
        [one] seconde
       *[other] secondes
    }.
    { "" }
    [cyan]Cette carte vous a plu ?
map-vote-like = [green]👍 J'aime
map-vote-dislike = [red]👎 Je n'aime pas
map-vote-like-selected = [gray]Vous l'avez aimée
map-vote-dislike-selected = [gray]Vous ne l'avez pas aimée
map-rtv = [orange]Vote
map-artv = [red]Changement immédiat
map-maps = Cartes
map-maps-back = Retour à la liste des cartes
current-map = Carte actuelle
next-map = Carte suivante

# Map UI Modernized
map-ui-search-hint = Rechercher une carte ou un auteur...
map-ui-total = [lightgray]Cartes : [white]{ $count }[]
map-ui-about-title = À propos de la carte
map-ui-rtv-title = Changement de carte
map-ui-no-maps-found = [lightgray]Aucune carte ne correspond à la recherche.[]
map-ui-by = par [lightgray]{ $author }[]
map-ui-mode = Mode : [white]{ $mode }[]
map-ui-loading = [gray]Chargement...[]
map-ui-no-preview = [gray]Pas d'aperçu[]
map-ui-dimensions = [gray]Dimensions : [white]{ $width } x { $height }[]
map-ui-total-plays = [gray]Parties : [white]{ $played } [lightgray]({ $playedYear } cette année)[]
map-ui-last-played = [gray]Dernière partie : [white]{ $lastPlayed }[]
map-ui-description = [gray]Description : [lightgray]{ $description }[]
map-ui-no-description = [gray]Description : [lightgray]Aucune description.[]

map-ui-col-duration = Durée
map-ui-duration-min = [gray]Min : [white]{ $value }[]
map-ui-duration-avg = [gray]Moy. : [white]{ $value }[]
map-ui-duration-max = [gray]Max : [white]{ $value }[]

map-ui-col-popularity = Popularité
map-ui-popularity-score = [gray]Score : [white]{ $value }[]
map-ui-popularity-pop = [gray]Popularité : [white]{ $value }[]
map-ui-popularity-interest = [gray]Intérêt : [white]{ $value }[]

map-ui-col-community = Communauté
map-ui-community-approval = [gray]Approbation : [green]{ $rate }%[]

map-ui-btn-like = J'aime ({ $count })
map-ui-btn-dislike = Je n'aime pas ({ $count })

map-ui-rtv-active-status = [accent]● Vote en cours : [white]{ $votes }/{ $required }[] [gray](fin dans [white]{ $seconds } s[gray])[]
map-ui-rtv-vote-yes = Voter pour cette carte
map-ui-rtv-start = Lancer un vote pour cette carte
map-ui-admin-rtv = Changer de carte maintenant
map-ui-admin-rtv-confirm = Appuyez à nouveau pour confirmer

gamemode-survival = Survie
gamemode-attack = Attaque
gamemode-pvp = PvP
gamemode-sandbox = Bac à sable
gamemode-editor = Éditeur
rtv-vote =
    { $nickname }[lightgray] a voté pour passer à la carte [orange]{ $mapName }[lightgray]. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Tapez [orange]y[] ou [orange]n[] pour voter.
rtv-left = { $nickname }[lightgray] est parti. Son vote pour changer de carte a été annulé. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
rtv-fail = [lightgray]Vote échoué. Pas assez de votes pour passer à la carte [orange]{ $mapName }[].
rtv-success = [orange]Vote réussi. La carte [accent]{ $mapName }[] sera chargée dans [accent]{ $mapLoadDelay }[] { $mapLoadDelay ->
    [one] seconde
   *[other] secondes
}…
rtv-cancelled = [lightgray]Le vote pour passer à la carte [orange]{ $mapName }[lightgray] a été annulé par { $admin }.
vnw-vote =
    { $nickname }[lightgray] a voté pour lancer la vague [orange]{ $wave }[lightgray] plus tôt. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Tapez [orange]y[] ou [orange]n[] pour voter.
vnw-left = { $nickname }[lightgray] est parti. Son vote pour lancer la vague [orange]{ $wave }[lightgray] plus tôt a été annulé. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
vnw-fail = [lightgray]Vote échoué. Pas assez de votes pour lancer la vague [orange]{ $wave }[] plus tôt.
vnw-success = [orange]Vote réussi. La vague [accent]{ $wave }[] commence maintenant.
vnw-cancelled = [lightgray]Le vote pour lancer la vague [orange]{ $wave }[lightgray] plus tôt a été annulé par { $admin }.
vnw-obsolete = [lightgray]La vague [orange]{ $wave }[lightgray] a déjà commencé, le résultat du vote n'est plus nécessaire.
# ==============================================================================
# Statistics & Ranks & Players
# ==============================================================================
commands-player-description = Affiche les statistiques d'un joueur.
commands-settings-description = Ouvre vos paramètres de joueur.
player-menu-player = Joueur
player-menu-player-title = [orange]{ -xcore } — Statistiques du joueur
player-menu-player-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $customNickname }[] [gray]#{ $pid }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Profil[]
    [gray]Nom : [white]{ $nickname } [darkgray]|[gray] Admin : [lime]{ $admin }[]
    [gray]Badge : [white]{ $activeBadge } [darkgray]|[gray] Système : [coral]{ $systemBadge }[]
    [gray]Inscrit le : [white]{ $accountCreated }[]
    { "" }
    [accent]■ Classements[]
    [gray]Temps de jeu : [white]{ $totalPlayTime }[]
    [gray]MiniPvP : [sky]{ $pvpRating } [darkgray]|[gray] Ancien Hexed : [sky]{ $hexedRankName } [gray]({ $hexedPoints } pts) [darkgray]|[gray] Top : [accent]{ $hexedTopRank }[]
    { "" }
    [accent]■ Parties : [white]{ $gamesPlayed } [gray]jouées [darkgray]|[lime] { $gamesWon } [gray]victoires [darkgray]|[sky] { $winRate }% [gray]de victoires[]
    [gray]• [white]PvP : { $pvpSummary }[]
    [gray]• [white]Survie : { $survivalSummary }[]
    [gray]• [white]Ancien Hexed : { $hexedSummary }[]
    { "" }
    [accent]■ Efficacité au combat[]
    [gray]Blocs (Construits/Démontés/Détruits) : [lime]{ $blocksBuilt } [darkgray]/ [orange]{ $blocksDeconstructed } [darkgray]/ [scarlet]{ $blocksDestroyed }[]

# Modern Player Stats UI
player-stats-title = [orange]{ -xcore } — Profil du joueur
player-stats-tab-overview = Aperçu
player-stats-tab-stats = Stats
player-stats-tab-matches = Parties
player-stats-tab-blocks = Blocs
player-stats-tab-players = En ligne ({ $count })

player-stats-status-online = [lime]● En ligne[]
player-stats-status-offline = [gray]○ Hors ligne[]
player-stats-no-bio = [gray]Aucune bio pour l'instant.[]
player-stats-loading = [lightgray]Chargement des données...[]
player-stats-no-stats = [gray]Aucune donnée de partie enregistrée.[]
player-stats-filter-all = Filtre : Tous
player-stats-filter-admins = Filtre : Admins uniquement
player-stats-filter-non-admins = Filtre : Non-admins
player-stats-refresh = Actualiser
player-stats-combat-efficiency = Efficacité au combat et en construction
player-stats-waves-summary = [gray]vagues : max [lime]{ $best }[], moy. [white]{ $avg }[]
player-stats-hexed-top-placement = [gray]meilleur [accent]#{ $best }[], top 3 : [sky]{ $top3 }[]
player-stats-max-rank = [gold]★ RANG MAXIMAL ATTEINT ★[]
player-stats-max-league = [gold]★ LIGUE MAXIMALE ATTEINTE ★[]
player-stats-hexed-wins-left = [lightgray]{ $wins } victoires avant [white]{ $rank }[]
player-stats-league-elo-left = [lightgray]{ $elo } ELO avant [white]{ $league }[]
player-stats-ratio-legend = [lightgray]Ratio des blocs[]

player-stats-account-created = [gray]Inscrit le :[]
player-stats-play-time = [gray]Temps de jeu :[]
player-stats-legacy-pvp-rating = [gray]Ancien PvP :[]
player-stats-hexed-rank = [gray]Ancien Hexed :[]
player-stats-hexed-leaderboard = [gray]Top Hexed :[]

player-stats-total-games = [gray]Parties au total :[]
player-stats-victories = [gray]Victoires :[]
player-stats-survival-summary = [gray]Survie :[]
player-stats-hexed-summary = [gray]Ancien Hexed :[]

player-stats-blocks-built = [gray]Blocs construits :[]
player-stats-blocks-deconstructed = [gray]Démontés :[]
player-stats-blocks-destroyed = [gray]Détruits :[]
player-stats-units-produced = [gray]Unités produites :[]
player-stats-units-lost = [gray]Unités perdues :[]

player-stats-games-played-value = [white]{ $count }[] [gray]parties[]
player-stats-victories-value = [lime]{ $wins }[] [gray]victoires[]  [darkgray]|[]  [sky]{ $winRate }%[] [gray]de victoires[]
player-stats-hexed-points = [gray]({ $points } pts)[]

player-stats-btn-settings = Paramètres
player-stats-btn-audit = Historique
player-stats-btn-players = En ligne
player-stats-btn-close = Fermer
player-stats-admin-tag = [coral]<Admin>[]
player-menu-players = Joueurs en ligne
player-menu-players-title = [orange]{ -xcore } — Joueurs en ligne
player-menu-players-content = [white]Page [green]{ $page }[] sur [green]{ $total }[]
player-menu-players-empty = Aucun joueur en ligne
player-menu-players-row = [white]{ $nickname } [gray](PID : { $pid })[]
player-menu-settings = Paramètres
player-menu-settings-title = [orange]{ -xcore } — Paramètres du joueur
player-menu-settings-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $displayNickname }[] [gray]#{ $pid }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Profil[]
    [gray]Nom : [white]{ $nickname } [darkgray]|[gray] Affiché : [lime]{ $customNickname }[]
    [gray]Badge : [white]{ $activeBadge } [darkgray]|[gray] Système : [coral]{ $systemBadge }[]
    { "" }
    [accent]■ Visibilité[]
    [gray]Classement : [white]{ $leaderboard }[]
    { "" }
    [accent]■ Chat[]
    [gray]Global : [white]{ $globalChat } [darkgray]|[gray] Discord : [white]{ $discordRelay }[]
    [gray]Traducteur : [white]{ $translatorLanguage }[]
    { "" }
    [accent]■ Localisation[]
    [gray]Langue : [white]{ $language }[]
player-menu-settings-chat = Paramètres du chat
player-menu-settings-chat-title = [orange]{ -xcore } — Paramètres du chat

# Modern Player Settings Form
player-settings-tab-profile = Profil
player-settings-tab-chat = Chat
player-settings-tab-badges = Badges
player-settings-chat-preview = Aperçu du chat
player-settings-chat-preview-sample = Exemple
player-settings-chat-preview-message = Bonjour tout le monde !
player-settings-symbol-color-mode = Couleur du symbole
player-settings-badges-my = Mes badges
player-settings-badges-all = Tous les badges
player-settings-badge-equip = Équiper
player-settings-badge-unequip = Retirer
player-settings-badge-preview = Aperçu
player-settings-badge-previewing = En aperçu
player-settings-translator-lang = Traducteur du chat
player-settings-translator-off = Désactivé
player-settings-badges-empty = Vous n'avez encore débloqué aucun badge.
player-settings-manage-badges = Gérer les badges
player-settings-global-chat = Chat global
player-settings-discord-relay = Relais Discord
player-settings-leaderboard = Afficher le classement
player-settings-language = Langue
player-settings-saved = [lime]Paramètres enregistrés ![]
player-settings-reset-feedback = [lightgray]Pseudo personnalisé réinitialisé.[]
player-settings-edit-badges = [accent]Modifier[]
player-settings-tab-language = Langue
player-settings-identity = Nom et description
player-settings-interface = Écran
player-settings-leaderboard-hint = La liste des meilleurs joueurs au-dessus du jeu. Fonctionne en Mini-PvP.
player-settings-global-chat-hint = Messages des joueurs des autres serveurs XCore.
player-settings-discord-relay-hint = Messages du salon Discord du serveur dans le chat du jeu.
player-settings-translator-hint = Les messages des autres joueurs sont traduits dans la langue choisie.
player-settings-language-hint = La langue des menus et des messages du serveur. Auto suit la langue de votre jeu.
player-menu-settings-chat-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [accent]■ Visibilité du chat[]
    [gray]Chat global : [white]{ $globalChat }[]
    [gray]Relais Discord : [white]{ $discordRelay }[]
    { "" }
    [accent]■ Traduction[]
    [gray]Langue du traducteur : [white]{ $translatorLanguage }[]
player-menu-settings-translator-title = [orange]{ -xcore } — Choix de la langue du traducteur
player-menu-settings-language-title = [orange]{ -xcore } — Choix de la langue
player-menu-settings-customNickname = Modifier le nom
player-menu-settings-customNickname-title = [orange]{ -xcore } — Modifier le nom
player-menu-settings-customNickname-message = [lightgray]Laissez vide pour réinitialiser
player-menu-settings-customNickname-reset = [scarlet]Réinitialiser le nom
player-menu-settings-description = Modifier la description
player-menu-settings-description-title = [orange]{ -xcore } — Modifier la description
player-menu-settings-badges = Badges
player-menu-settings-global-chat-on = [green]Chat global
player-menu-settings-global-chat-off = [red]Chat global
player-menu-settings-discord-relay-on = [green]Relais Discord
player-menu-settings-discord-relay-off = [red]Relais Discord
audit-menu-open = Historique
audit-menu-actions-open = Actions
audit-menu-history-title = [orange]{ -xcore } — Historique
audit-menu-history-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $player }[] [gray]#{ $pid }[]
    [gray]Entrées affichées : [accent]{ $entriesShown }[]
    [gray]{ $pageState } [darkgray]|[] { $nextState }
    [lightgray]{ $hint }[]
audit-menu-history-page-first = Entrées récentes
audit-menu-history-page-older = Entrées plus anciennes
audit-menu-tab-sanctions = Sanctions reçues
audit-menu-tab-actions = Actions effectuées
audit-menu-filter-all = Toutes
audit-menu-filter-bans = Bannissements
audit-menu-filter-mutes = Mutes
audit-menu-filter-warns = Avertissements
audit-menu-filter-other = Autres
audit-menu-btn-back = Retour à l'historique
audit-menu-btn-copy-id = Copier l'ID
audit-menu-copy-id-success = L'ID de l'entrée a été envoyé dans le chat
audit-menu-field-id = ID de l'entrée
audit-menu-page = Page { $page }
audit-menu-details-unavailable = L'entrée n'est pas disponible.
audit-menu-field-target = Joueur
audit-menu-field-actor = Effectué par
audit-menu-field-server = Serveur
audit-menu-field-reason = Raison
audit-menu-section-time = Temps
audit-menu-field-occurred = Quand
audit-menu-field-duration = Durée
audit-menu-field-expires = Expire
audit-menu-btn-revoke = Lever la sanction
audit-menu-revoke-success = Sanction levée.
audit-menu-status-active = ACTIVE
audit-menu-status-expired = EXPIRÉE
audit-menu-status-permanent = PERMANENTE
audit-menu-history-more = D'autres entrées sont disponibles
audit-menu-history-end = Fin de l'historique
audit-menu-history-empty = Aucune entrée pour ce joueur pour l'instant.
audit-menu-history-hint = Choisissez une entrée ci-dessous pour voir les détails.
audit-menu-actions-title = [orange]{ -xcore } — Actions
audit-menu-actions-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $player }[] [gray]#{ $pid }[]
    [gray]Entrées affichées : [accent]{ $entriesShown }[]
    [gray]{ $pageState } [darkgray]|[] { $nextState }
    [lightgray]{ $hint }[]
audit-menu-actions-empty = Aucune action pour ce joueur pour l'instant.
audit-menu-actions-hint = Choisissez une entrée ci-dessous pour voir ce que ce joueur a fait.
audit-menu-summary-row = [accent]{ $action }[] [darkgray]•[] [white]{ $actor }[] [gray]— { $reason }
audit-menu-action-summary-row = [accent]{ $action }[] [darkgray]•[] [white]{ $target }[] [gray]— { $reason }
audit-menu-details-title = [orange]{ -xcore } — Détails de l'entrée
audit-menu-details-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $player }[] [gray]#{ $pid }[]
    { "" }
    [accent]■ Événement[]
    [gray]Action : [white]{ $action }[]
    [gray]Auteur : [white]{ $actor }[]
    [gray]Raison : [white]{ $reason }[]
    { "" }
    [accent]■ Temps[]
    [gray]Date : [white]{ $occurredAt }[]
    [gray]Durée : [white]{ $duration }[]
    [gray]Expire : [white]{ $expiresAt }[]
    { "" }
    [accent]■ Métadonnées[]
    [gray]ID de l'entrée : [white]{ $auditId }[]
audit-menu-unknown-actor = Inconnu
audit-menu-unknown-target = Inconnu
audit-menu-reason-unspecified = Non précisée
audit-menu-duration-permanent = Permanente
audit-menu-action-ban = Bannissement
audit-menu-action-unban = Débannissement
audit-menu-action-mute = Mute
audit-menu-action-unmute = Fin du mute
audit-menu-action-warn = Avertissement
audit-menu-action-kick = Expulsion
audit-menu-action-note = Note
audit-menu-action-quarantine = Quarantaine
audit-menu-action-unquarantine = Fin de quarantaine
player-menu-player-max-rank = Rang maximal atteint
player-menu-player-hexed-progress = [gray]Victoires nécessaires pour [white]{ $nextRankName }[gray] : [accent]{ $requiredPoints }[]
player-menu-player-no-mode-stats = [gray]aucune donnée[]
player-menu-player-pvp-summary = [gray]parties [white]{ $gamesPlayed }[], victoires [lime]{ $gamesWon }[], [sky]{ $winRate }%[]
player-menu-player-survival-summary = [gray]vagues : max [lime]{ $bestWave }[], moy. [white]{ $averageWave }[] [gray](parties : { $gamesPlayed })[]
player-menu-player-hexed-summary = [gray]parties [white]{ $gamesPlayed }[], 1re place [lime]{ $gamesWon }[], meilleure place [accent]#{ $bestPlacement }[]
player-menu-time-days = { $value } j
player-menu-time-hours = { $value } h
player-menu-time-minutes = { $value } min
settings-language-label = Langue : [green]{ $lang }[]
settings-translator-label = Traducteur : [green]{ $lang }[]
badge-menu-title = [orange]{ -xcore } — Badges
badge-menu-content =
    [white]Badge système : [green]{ $systemBadge }[]
    [white]Badge actif : [green]{ $activeBadge }[]
    [white]Couleur du symbole : [green]{ $symbolColorMode }[]
badge-menu-empty = [lightgray]Vous n'avez encore débloqué aucun badge.
badge-menu-row = [white]{ $badge }[] [gray]-[] { $description }
badge-menu-symbol-color-button = Couleur du symbole : [green]{ $mode }[]
badge-menu-symbol-color-title = [orange]{ -xcore } — Couleur du symbole du badge
badge-menu-symbol-color-content =
    [white]Mode actuel : [green]{ $mode }[]
    [lightgray]Choisissez comment colorer le symbole du badge.
badge-menu-symbol-color-default = Couleur par défaut du badge
badge-menu-symbol-color-player-color = Couleur du joueur
badge-menu-view-all = Voir tous les badges
badge-menu-all-title = [orange]{ -xcore } — Tous les badges
badge-menu-all-content = [lightgray]Tous les badges, avec leur statut et leur description.
badge-menu-all-row = [white]{ $badge }[] [gray]-[] [accent]{ $state }[] [gray]-[] { $description }
badge-clear-button = Retirer le badge actif
badge-state-system = Système
badge-state-system-active = Système actif
badge-state-active = Actif
badge-state-unlocked = Débloqué
badge-state-locked = Verrouillé
badge-set-success = [accent]Badge actif : [green]{ $badge }[].
badge-clear-success = [accent]Badge actif retiré.
badge-grant-success = [accent][green]{ $badge }[] accordé à [green]{ $nickname }[][gray]#{ $pid }[].
badge-revoke-success = [accent][green]{ $badge }[] retiré à [green]{ $nickname }[][gray]#{ $pid }[].
badge-already-unlocked = [scarlet]⚠ Le badge [accent]{ $badge }[scarlet] est déjà débloqué.
badge-not-owned = [scarlet]⚠ Le joueur n'a pas le badge [accent]{ $badge }[scarlet].
error-badge-not-found = [scarlet]⚠ Le badge [accent]{ $badge }[scarlet] est introuvable.
error-badge-not-unlocked = [scarlet]⚠ Le badge [accent]{ $badge }[scarlet] n'est pas débloqué.
error-badge-not-selectable = [scarlet]⚠ Le badge [accent]{ $badge }[scarlet] ne peut pas être choisi manuellement.
badge-admin-name = Admin
badge-admin-description = Badge automatique des administrateurs.
badge-developer-name = Développeur
badge-developer-description = Attribué aux développeurs de XCore.
badge-translator-name = Traducteur
badge-translator-description = Attribué à ceux qui traduisent XCore.
badge-map-maker-name = Créateur de cartes
badge-map-maker-description = Attribué aux créateurs de cartes utilisées sur le serveur.
badge-contributor-name = Contributeur
badge-contributor-description = Attribué pour des contributions à XCore ou à sa communauté.
badge-bug-finder-name = Chasseur de bugs
badge-bug-finder-description = Attribué pour des rapports de bugs réguliers et de qualité.
badge-event-winner-name = Gagnant d'événement
badge-event-winner-description = Attribué aux gagnants des événements spéciaux du serveur.
badge-veteran-name = Vétéran
badge-veteran-description = Attribué aux joueurs fidèles et respectés.
badge-season-champion-name = Champion de saison
badge-season-champion-description = Attribué pour une place sur le podium d'une saison classée.
commands-lb-description = Active/désactive le classement.
commands-lb-success =
    { $leaderboardEnabled ->
        [true] [accent]Classement [green]activé.
       *[other] [accent]Classement [scarlet]désactivé.
    }
leaderboard = [blue]Classement
commands-observer-description = Passe en mode spectateur. Votre unité actuelle est retirée et vous rejoignez l'équipe des spectateurs.
commands-rank-description = Affiche votre rang ou celui d'un autre joueur.
commands-rank-content =
    { $nickname }
    { $rankTag } [accent]{ $rankName }
    [gold]Victoires : { $points }/{ $requiredPoints }
commands-ranks-description = Affiche des informations sur les rangs.
commands-ranks-content =
    { $rankTag } [accent]{ $rankName }
    [gold]Condition : [grey]{ $requiredPoints } [accent]victoires[]
commands-ranks-footer = Les victoires ne comptent que contre un joueur de votre rang ou d'un rang supérieur.
commands-top-description = Meilleurs joueurs.
commands-season-description = Saison classée : temps restant, votre position et les gagnants de la saison précédente.
commands-top-hexed-content = [orange]{ $index }. { $nickname }[accent] : [blue]{ $rankName } [cyan]{ $points } []victoires
commands-top-pvp-content = [orange]{ $index }. { $nickname }[accent] : [cyan]{ $rating }
top-menu-title = [orange]{ -xcore } — Meilleurs joueurs : [accent]{ $category }
top-menu-content =
    [lightgray]Choisissez un joueur pour ouvrir son profil.[]
    [lightgray]Page [green]{ $page }[]/[green]{ $totalPages }[] [gold]•[] [lightgray]Joueurs : [green]{ $totalEntries }[]
    { $selfRankLine }
top-menu-empty =
    [accent]Catégorie : [green]{ $category }[]
    [gray]Aucun joueur pour l'instant.
top-menu-categories-title = [orange]{ -xcore } — Catégorie du classement
top-menu-categories-content =
    [lightgray]Choisissez le classement à afficher.[]
    [lightgray]Actuel : [green]{ $category }[]
top-menu-category-button = [accent]Catégorie : [green]{ $category }[]
top-menu-category-mini-pvp = MiniPvP
top-menu-category-playtime = Temps de jeu
top-menu-category-hexed = Hexed
top-menu-self-rank-known = [lightgray]Votre position : [accent]#{ $rank }[]
top-menu-self-rank-unknown = [lightgray]Votre position : [gray]introuvable[]
top-menu-entry-mini-pvp = { $rankLabel } { $leagueIcon } [accent]{ $nickname }[] [gray]—[] [sky]{ $value }[]
top-menu-entry-playtime = { $rankLabel } [accent]{ $nickname }[] [gray]—[] [green]{ $value }[]
top-menu-entry-hexed = { $rankLabel } [accent]{ $nickname }[] [gray]—[] [violet]{ $rankName }[] [gold]•[] [cyan]{ $value }[]
top-menu-total-count = { $count } joueurs
top-menu-btn-find-self = Me trouver
top-menu-on-this-page = sur cette page
top-menu-unranked = Vous n'êtes pas encore classé dans cette catégorie
top-menu-self-rank-line = Votre rang : { $rank }
top-menu-score-points = { $points } pts
top-menu-score-minutes = { $time }
# ==============================================================================
# Game Modes (Hexed, PvP, Surrender, AI)
# ==============================================================================
commands-surrender-description = Abandonner en Hexed. Votre équipe actuelle est détruite, votre unité retirée et vous rejoignez l'équipe des spectateurs.
commands-surrender-success = [green]Vous avez abandonné et êtes maintenant spectateur
commands-observer-success = [green]Vous êtes maintenant spectateur
commands-observer-exit-success = [green]Vous n'êtes plus spectateur
commands-ai-description = Contrôle l'IA.
commands-ai-usage = [red]attack(a) []ou [accent]idle(i)
hexed-popup = [blue]{ DURATION($remaining, style: "timer") }[] avant la fin de la partie.
hexed-eliminated = { $nickname } [gold]a été [scarlet]éliminé[] !
hexed-leaderboard-content = [orange]{ $index }. { $nickname }[accent] : [cyan]{ $hexes } [accent]hexagones
hexed-ranks-newbie = Débutant
hexed-ranks-regular = Habitué
hexed-ranks-advanced = Confirmé
hexed-ranks-veteran = Vétéran
hexed-ranks-davastator = Dévastateur
hexed-ranks-the_legend = La Légende
hexed-game-over-header = Partie terminée. Gagnants :
hexed-game-over-winner-row =
    [orange]{ $index }. { $name }[][accent] : [cyan]{ $cores } { $cores ->
        [one] hexagone
       *[other] hexagones
    }
hexed-game-over-no-winners = Partie terminée. Malheureusement, aucun gagnant n'a été trouvé.
hexed-game-over-restart = Nouvelle partie dans 10 secondes…
rating_league_scrap = Ferraille
rating_league_copper = Cuivre
rating_league_lead = Plomb
rating_league_graphite = Graphite
rating_league_silicon = Silicium
rating_league_titanium = Titane
rating_league_thorium = Thorium
rating_league_plastanium = Plastanium
rating_league_phase_fabric = Tissu phasé
rating_league_surge_alloy = Alliage de surtension
pvp-team-won = Votre équipe a gagné. Votre classement augmente de { $increased }
pvp-team-lose = Votre équipe a perdu. Votre classement baisse de { $reduced }
pvp-match-settlement-win = [accent]■ Résultat : [green]Victoire ![] Votre classement : [stat]{ $oldRating }[] -> [stat]{ $newRating }[] [green](+{ $delta })[] { $leagueIcon } { $leagueName }
pvp-match-settlement-loss = [accent]■ Résultat : [scarlet]Défaite ![] Votre classement : [stat]{ $oldRating }[] -> [stat]{ $newRating }[] [scarlet]({ $delta })[] { $leagueIcon } { $leagueName }
pvp-match-settlement-draw = [accent]■ Résultat : [yellow]Égalité ![] Votre classement : [stat]{ $oldRating }[] -> [stat]{ $newRating }[] [yellow]({ $delta })[] { $leagueIcon } { $leagueName }
pvp-match-settlement-exempt = [accent]■ Résultat : [lightgray]Pas de changement de classement (temps de jeu actif insuffisant).[]
season-ending-soon = [accent]■ [white]{ $ladder }[] : la saison { $number } se termine dans [stat]{ $remaining }[]. Profitez de vos dernières parties !
season-started = [accent]■ [white]{ $ladder }[] : la saison { $previous } est terminée. La saison { $number } commence !
season-reset-soft = [lightgray]Les classements ont été rapprochés de la valeur de départ et les compteurs de parties repartent de zéro.[]
season-reset-hard = [lightgray]Tout le monde repart du classement de départ.[]
season-reset-none = [lightgray]Les classements sont conservés, les compteurs de parties repartent de zéro.[]
season-title = Saison { $number }
top-menu-scope-current = { $season } [lightgray]· se termine dans { $remaining }[]
top-menu-scope-past = { $season } [gray]· { $from } – { $to }[]
ladder-profile-standing = { $leagueIcon } [white]{ $league }[] [accent]{ $rating } ELO[]
ladder-profile-headline = [accent]{ $icon }[] [lightgray]{ $ladder } :[] { $standing }
ladder-profile-unplaced = [gray]aucune partie classée cette saison[]
ladder-profile-matches = [lightgray]Parties :[] [white]{ $matches }[]
ladder-profile-wins = [lightgray]Victoires :[] [white]{ $wins }[] [gray]({ $rate }%)[]
ladder-profile-rank = [lightgray]Place :[] [accent]#{ $rank }[]
ladder-profile-peak = [lightgray]Record :[] [white]{ $rating }[]
ladder-profile-season = [lightgray]{ $season } · se termine dans [white]{ $remaining }[][]
ladder-profile-season-closing = [lightgray]{ $season } · décompte des résultats[]
ladder-profile-history = [gray]{ $season } — { $rank }, { $league }, { $rating } ELO[]
season-menu-title = Saisons classées
season-menu-card-title = { $ladder } — { $season }
season-menu-ends = [lightgray]Se termine dans [white]{ $remaining }[] ({ $date })[]
season-menu-closing = [lightgray]La saison est terminée, les résultats sont en cours de décompte.[]
season-menu-participants = [lightgray]Joueurs cette saison : [white]{ $count }[][]
season-menu-you = [lightgray]Vous :[] { $standing }
season-menu-previous = [lightgray]{ $season } — gagnants :[]
season-menu-podium-entry = [gold]{ $place }.[] [white]{ $name }[] [gray]—[] { $league } [accent]{ $rating }[]
season-menu-open-top = Classement
season-menu-prizes = [lightgray]Récompenses de la saison :[]
season-menu-prize-entry = [gold]{ $places }.[] [white]{ $prize }[]
season-menu-podium-prizes = [gray] · [gold]{ $prizes }[]
prize-grant-line = [lightgray]{ $season } · récompense :[] [gold]{ $prize }[] [gray]({ $status })[]
prize-status-pending = en attente de remise
prize-status-granted = reçue
prize-status-delivered = livrée
prize-status-failed = un admin va s'en occuper
season-menu-empty = Il n'y a pas encore de saisons classées.
pvp-hud-status = [accent]MiniPvP[] | [stat]En vie :[] { $teams } | [gray]{ $time }[]
pvp-leaderboard-content = [orange]{ $index }. { $nickname }[accent] :[cyan] { $rating } [accent]points
pvp-you-spectator = [scarlet]Vous avez été éliminé. Attendez la prochaine partie.
# ==============================================================================
# Events & Notifications
# ==============================================================================
player-joined-pid = { $nickname } [grey]#[white]{ $pid }[grey] [accent]a rejoint la partie.
player-joined-username = { $nickname } [grey]@[white]{ $username }[grey] [accent]a rejoint la partie.
player-joined-both = { $nickname } [accent]@{ $username } [grey]#[white]{ $pid }[grey] [accent]a rejoint la partie.
player-joined-none = { $nickname } [accent]a rejoint la partie.

player-left-pid = { $nickname } [grey]#[white]{ $pid }[grey] [accent]est parti.
player-left-username = { $nickname } [grey]@[white]{ $username }[grey] [accent]est parti.
player-left-both = { $nickname } [accent]@{ $username } [grey]#[white]{ $pid }[grey] [accent]est parti.
player-left-none = { $nickname } [accent]est parti.

player-settings-identity-mode = [accent]Identifiant dans les annonces :[]
player-settings-identity-mode-pid = ID seulement (#12)
player-settings-identity-mode-username = Nom d'utilisateur seulement (@Steve)
player-settings-identity-mode-both = Les deux (@Steve #12)
player-settings-identity-mode-none = Masqué

notification-votekick-playtime =
    [accent]Félicitations ! Vous avez joué [lightgray]{ $votekickPlayTime }[] { $votekickPlayTime ->
        [one] minute
       *[other] minutes
    } et pouvez maintenant lancer un vote d'expulsion.
notification-global-chat-playtime =
    [accent]Félicitations ! Vous avez joué [lightgray]{ $globalChatPlayTime }[] { $globalChatPlayTime ->
        [one] minute
       *[other] minutes
    } et pouvez maintenant écrire dans le chat global.
    [lightgray]Tapez [accent]/g [gray]<message…>[lightgray] pour envoyer un message.
notification-admin-kick = { $admin }[accent] a expulsé { $target }[].
notification-admin-wave-skip = { $admin }[accent] a passé la vague.
server-restart-countdown =
    Redémarrage dans { $seconds ->
        [one] { $seconds } seconde
       *[other] { $seconds } secondes
    }
like-map-success = [green]Vous aimez cette carte !
like-map-changed = [green]Vous avez changé d'avis : vous aimez !
dislike-map-success = [orange]Vous n'aimez pas cette carte.
dislike-map-changed = [orange]Vous avez changé d'avis : vous n'aimez pas.
like-event-success = [green]Vous aimez cet événement !
like-event-changed = [green]Vous avez changé d'avis : vous aimez !
dislike-event-success = [orange]Vous n'aimez pas cet événement.
dislike-event-changed = [orange]Vous avez changé d'avis : vous n'aimez pas.

# ==============================================================================
# Events (Server)
# ==============================================================================

commands-event-description = Menu de gestion des événements.
commands-events-description = Liste de tous les événements des serveurs.
event-events = Événements
event-menu-main = Événements principaux
event-menu-main-title = [orange]{ -xcore } — Événements
event-menu-main-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]Centre des événements[]
    [lightgray]Tous les événements actifs et prévus du serveur au même endroit.[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Événement actuel[]
    [gray]Statut : [white]{ $currentEventState }[]
    [gray]Sélectionné : [white]{ $currentEventName }[]
    { "" }
    [accent]■ Vote[]
    [gray]Session de vote : [white]{ $voteStatus }[]
    { "" }
    [accent]■ Actions[]
    [gray]Ouvrez le catalogue, consultez l'événement actuel ou préparez-en un nouveau.[]
event-menu-event = Événement
event-menu-event-title = [orange]{ -xcore } — Événement
event-menu-event-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $name }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Aperçu[]
    [gray]Auteur : [white]{ $author }[]
    [gray]Carte : [white]{ $mapName }[]
    [gray]Type : [white]{ $eventType }[] [darkgray]|[gray] État : [white]{ $eventState }[]
    [gray]Temporaire : [white]{ $isTemporary }[]
    { "" }
    [accent]■ Calendrier[]
    [gray]Créé : [white]{ $createdEventTime }[]
    [gray]Début prévu : [white]{ $plannedStartTime }[]
    [gray]Fin prévue : [white]{ $plannedEndTime }[]
    { "" }
    [accent]■ Réputation[]
    [gray]J'aime : [white]{ $like }[] [darkgray]|[gray] Je n'aime pas : [white]{ $dislike }[]
event-menu-event-map = Voir la carte
event-menu-events = Liste des événements
event-menu-events-title = [orange]{ -xcore } — Liste des événements
event-menu-events-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]Catalogue des événements[]
    [lightgray]Page [green]{ $page }[]/[green]{ $total }[] [gold]•[] [lightgray]Événements : [green]{ $count }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Filtres[]
    [gray]Terminés : [white]{ $finished }[]
    [gray]Majeurs : [white]{ $major }[] [darkgray]|[gray] Actifs : [white]{ $active }[]
    { "" }
    [accent]■ Liste[]
    [gray]Choisissez un événement ci-dessous pour voir sa fiche.[]
event-menu-events-empty =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]Catalogue des événements[]
    [lightgray]Aucun événement ne correspond encore aux filtres actuels.[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Filtres[]
    [gray]Terminés : [white]{ $finished }[]
    [gray]Majeurs : [white]{ $major }[] [darkgray]|[gray] Actifs : [white]{ $active }[]
event-menu-events-row = [accent]{ $state }[] [darkgray]•[] [white]{ $type }[] [darkgray]—[] { $name }
event-menu-events-selected = [green]●[] [accent]{ $state }[] [darkgray]•[] [white]{ $type }[] [darkgray]—[] { $name }
event-menu-create-start = Créer
event-menu-create-start-title = [orange]{ -xcore } — Création d'événement
event-menu-create-start-message = Saisissez le nom du futur événement
event-menu-create-start-default = Événement de { $playerName }
event-menu-create-start-map = Créer un événement pour cette carte
event-menu-edit = Modifier
event-menu-edit-title = [orange]{ -xcore } — Modifier l'événement
event-menu-edit-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $name }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Identité[]
    [gray]Auteur : [white]{ $author }[]
    [gray]Type : [white]{ $eventType }[]
    { "" }
    [accent]■ Carte[]
    [gray]Carte choisie : [white]{ $mapName }[]
    { "" }
    [accent]■ Calendrier[]
    [gray]Début prévu : [white]{ $plannedStartTime }[]
    [gray]Fin prévue : [white]{ $plannedEndTime }[]
    { "" }
    [accent]■ Options[]
    [gray]Temporaire : [white]{ $isTemporary }[]
event-menu-edit-name = Nom
event-menu-edit-name-reset = [scarlet]Réinitialiser le nom
event-menu-edit-name-title = [orange]{ -xcore } — Modifier l'événement
event-menu-edit-name-message = Modifier le nom :
event-menu-edit-description = Description
event-menu-edit-description-title = [orange]{ -xcore } — Modifier l'événement
event-menu-edit-description-message = Modifier la description :
event-menu-edit-map = Changer la carte
event-menu-edit-temporary-active = [green]Temporaire
event-menu-edit-temporary-inactive = [gray]Temporaire
event-menu-edit-major-active = [green]Majeur
event-menu-edit-major-inactive = [gray]Majeur
event-menu-edit-planned-start = Début de l'événement
event-menu-edit-planned-start-title = [orange]{ -xcore } — Modifier l'événement
event-menu-edit-planned-start-message = Saisissez l'heure de début en ms ou avec m/h/d :
event-menu-edit-planned-end = Fin de l'événement
event-menu-edit-planned-end-title = [orange]{ -xcore } — Modifier l'événement
event-menu-edit-planned-end-message = Saisissez l'heure de fin en ms ou avec m/h/d :
event-menu-maps = Cartes
event-menu-maps-title = [orange]{ -xcore } — Choisir une carte
event-menu-maps-content = [white]Page [green]{ $page }[] sur [green]{ $total }[]
vote-event-vote =
    { $nickname }[lightgray] a voté pour passer à l'événement [orange]{ $name }[lightgray]. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Tapez [orange]y[] ou [orange]n[] pour voter.
vote-event-left = { $nickname }[lightgray] est parti. Son vote pour changer d'événement a été annulé. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
vote-event-fail = [lightgray]Vote échoué. Pas assez de votes pour passer à l'événement [orange]{ $name }[].
vote-event-success = [orange]Vote réussi. L'événement [accent]{ $name }[] sera chargé au prochain changement de carte.
vote-event-cancelled = [lightgray]Le vote pour passer à l'événement [orange]{ $name }[lightgray] a été annulé par l'administrateur { $admin }.
event-vote = [orange]Vote
event-avote = [red]Changement immédiat
event-menu-vote-stop = Arrêter le vote
event-menu-stop = Arrêter l'événement
event-menu-this-event = [orange]Événement actuel
event-menu-type-major = Événement majeur
event-menu-type-regular = Événement normal
event-menu-state-none = Aucun événement actif
event-menu-state-planned = Prévu
event-menu-state-active = En cours
event-menu-state-finished = Terminé
event-menu-vote-status-running = En cours
event-menu-vote-status-idle = Inactif
date-time-picker-title = [orange]{ -xcore } — Date et heure
date-time-picker-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $field }[]
    [lightgray]Valeur actuelle : [white]{ $value }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Date[]
    [gray]Choisissez d'abord un jour, puis ajustez l'heure ci-dessous.[]
    { "" }
    [accent]■ Heure[]
    [gray]Utilisez les préréglages ou les ajustements fins pour planifier précisément.[]
    { "" }
    [accent]■ Saisie manuelle[]
    [gray]Uniquement si vous avez besoin de millisecondes exactes ou d'une valeur relative +m/+h/+d.[]
date-time-picker-field-generic = Heure prévue
date-time-picker-today = Aujourd'hui
date-time-picker-tomorrow = Demain
date-time-picker-plus-2d = +2 jours
date-time-picker-plus-7d = +7 jours
date-time-picker-now = Maintenant
date-time-picker-time-0000 = 00:00
date-time-picker-time-0600 = 06:00
date-time-picker-time-1200 = 12:00
date-time-picker-time-1800 = 18:00
date-time-picker-minus-1d = -1 j
date-time-picker-plus-1d = +1 j
date-time-picker-minus-1h = -1 h
date-time-picker-plus-1h = +1 h
date-time-picker-minus-15m = -15 min
date-time-picker-plus-15m = +15 min
date-time-picker-reset = Réinitialiser
date-time-picker-manual = Saisie manuelle
date-time-picker-manual-title = [orange]{ -xcore } — Saisie manuelle de l'heure
date-time-picker-manual-message = Saisissez des millisecondes absolues ou une durée relative comme +30m, +2h, +1d.
event-end = L'événement [green]{ $name }[] est terminé !
# ==============================================================================
# Errors
# ==============================================================================
error-access-denied = [scarlet]⚠ Accès refusé.
error-ip-changed = [scarlet]⚠ Votre adresse IP a changé. Les droits d'admin ont été retirés.
error-not-enough-params = [scarlet]⚠ Pas assez de paramètres de position.
error-player-not-found = [scarlet]Joueur introuvable.
error-player-not-teammate = [scarlet]⚠ Ce joueur n'est pas dans votre équipe.
error-player-admin = [scarlet]⚠ N'essayez pas d'expulser un admin. ⚠
error-already-voted = [scarlet]⚠ Vous avez déjà voté. Du calme.
error-playtime-requirement =
    [scarlet]⚠ Vous devez avoir joué au moins { $time } { $time ->
        [one] minute
       *[other] minutes
    } pour utiliser cette fonction.
error-globalchat-total-playtime =
    [scarlet]⚠ Pour écrire dans le chat global, vous devez avoir joué { $globalChatPlayTime } { $globalChatPlayTime ->
        [one] minute
       *[other] minutes
    }.
error-votekick-total-playtime =
    [scarlet]⚠ Pour lancer un vote d'expulsion, vous devez avoir joué { $votekickPlayTime } { $votekickPlayTime ->
        [one] minute
       *[other] minutes
    }.
error-vote-yourself = [scarlet]⚠ Vous ne pouvez pas voter dans votre propre vote.
error-vote-in-progress = [scarlet]⚠ Un vote est déjà en cours.
error-no-voting = [scarlet]⚠ Aucun vote en cours pour le moment.
error-wave-vote-unavailable = [scarlet]⚠ Lancer une vague plus tôt n'est possible que dans les modes à vagues.
error-no-map = [scarlet]⚠ Aucune carte définie.
error-map-not-event = [scarlet]⚠ Cette carte ne fait pas partie de l'événement actuel.
error-map-not-found = [scarlet]⚠ Carte introuvable ! [accent]Utilisez [cyan]/maps[] pour voir toutes les cartes disponibles.
error-maps-empty = [scarlet]⚠ La liste des cartes est vide.
error-event-not-found = [scarlet]⚠ Événement introuvable ! [accent]Utilisez [cyan]/events[] pour voir les événements disponibles.
error-page-between = [scarlet]⚠ 'page' doit être un nombre entre[orange] 1[] et [orange]{ $totalPages }[].
error-page-number = [scarlet]'page' doit être un nombre.
error-wrong-number = [scarlet]⚠ Format de nombre incorrect.
error-wrong-period-format = [scarlet]⚠ Format de durée incorrect. Exemple : 1h 30m, 30 ({ hours })
error-invalid-id = [scarlet]⚠ ID de joueur invalide.
error-spectator = [scarlet]⚠ Vous êtes spectateur et ne pouvez pas utiliser cette commande.
error-admin-password-too-short = [scarlet]⚠ Le mot de passe administrateur doit comporter au moins 8 caractères.
error-wrong-admin-password = [scarlet]⚠ Mot de passe admin incorrect.
error-internal = [scarlet]Erreur interne.
error-processing-request = [scarlet]Une erreur s'est produite lors du traitement de la requête.
error-no-access = [scarlet]⚠ Pas d'accès.
error-nickname-too-long = [scarlet]⚠ Pseudo trop long. { $max } caractères visibles au maximum.
error-private-message-invalid-pid = [scarlet]⚠ PID de message privé invalide. Format : [lightgray]#123[].
error-private-message-self = [scarlet]⚠ Vous ne pouvez pas vous envoyer un message privé.
error-private-message-empty = [scarlet]⚠ Le message ne peut pas être vide.
error-private-message-too-long = [scarlet]⚠ Message trop long. { $max } caractères au maximum.
error-private-message-cooldown = [scarlet]⚠ Attendez { DURATION($seconds) } avant d'envoyer un autre message privé.
error-private-message-target-unavailable = [scarlet]⚠ Ce joueur ne peut pas recevoir de messages privés pour le moment.
error-private-message-no-reply-target = [scarlet]⚠ Aucun contact récent à qui répondre.
error-private-message-not-found = [scarlet]⚠ Message introuvable.
error-private-message-block-self = [scarlet]⚠ Vous ne pouvez pas vous bloquer vous-même.
error-private-message-block-limit = [scarlet]⚠ Limite de la liste de blocage atteinte ({ $limit }).
ban-menu-duration-title = [orange]{ -xcore } - Durée du bannissement
ban-menu-duration-message = Saisissez la durée du bannissement de { $nickname }. Exemple : 1d, 12h, 30m
ban-menu-reason-title = [orange]{ -xcore } - Raison du bannissement
ban-menu-reason-message = Saisissez la raison du bannissement de { $nickname }. Laissez vide pour la raison par défaut.
ban-menu-confirm-title = [orange]{ -xcore } - Confirmer le bannissement
ban-menu-confirm-content =
    [white]Joueur : { $nickname }[]
    [white]Durée : [accent]{ $duration }[]
    [white]Raison : [accent]{ $reason }[]
ban-menu-confirm-action = [scarlet]Bannir le joueur
error-invalid-syntax = [scarlet]⚠ Syntaxe de commande invalide. Utilisation : [lightgray]/'{ $syntax }'.
error-invalid-sender = [scarlet]⚠ Expéditeur de commande invalide. Cette commande nécessite : '[lightgray]{ $type }[]'.
error-argument-parse-generic = [scarlet]⚠ Argument invalide : '{ $error }'.
exception-unexpected = [scarlet]⚠ Une erreur interne s'est produite lors de l'exécution de cette commande.
exception-invalid-argument = [scarlet]⚠ Argument de commande invalide : '{ $cause }'.
exception-no-such-command = [scarlet]⚠ Commande inconnue.
exception-no-permission = [scarlet]⚠ Accès refusé.
exception-invalid-sender = [scarlet]⚠ '{ $actual }' ne peut pas exécuter cette commande. Expéditeur requis : [lightgray]{ $expected }[].
exception-invalid-sender-list = [scarlet]⚠ '{ $actual }' ne peut pas exécuter cette commande. Expéditeurs autorisés : [lightgray]{ $expected }[].
exception-invalid-syntax = [scarlet]⚠ Syntaxe de commande invalide. Utilisation : [lightgray]/'{ $syntax }'.
argument-parse-failure-boolean = [scarlet]⚠ Impossible de lire un booléen dans '{ $input }'.
argument-parse-failure-number = [scarlet]⚠ '{ $input }' n'est pas un nombre valide dans l'intervalle [{ $min }, { $max }].
argument-parse-failure-char = [scarlet]⚠ '{ $input }' n'est pas un caractère valide.
argument-parse-failure-enum = [scarlet]⚠ '{ $input }' n'est pas une option valide. Autorisé : [lightgray]{ $acceptableValues }
argument-parse-failure-string = [scarlet]⚠ Format de texte invalide pour '{ $input }'.
argument-parse-failure-uuid = [scarlet]⚠ Format d'UUID invalide : '{ $input }'.
argument-parse-failure-regex = [scarlet]⚠ La saisie '{ $input }' ne correspond pas au modèle '{ $pattern }'.
argument-parse-failure-color = [scarlet]⚠ '{ $input }' n'est pas une couleur valide.
argument-parse-failure-duration = [scarlet]⚠ '{ $input }' n'est pas un format de durée valide.
argument-parse-failure-aggregate-missing = [scarlet]⚠ Composant manquant '{ $component }'.
argument-parse-failure-aggregate-failure = [scarlet]⚠ Composant invalide '{ $component }' : '{ $failure }'.
argument-parse-failure-either = [scarlet]⚠ Impossible d'obtenir { $primary } ou { $fallback } à partir de '{ $input }'.
argument-parse-failure-flag-unknown = [scarlet]⚠ Option inconnue : '{ $flag }'.
argument-parse-failure-flag-duplicate = [scarlet]⚠ Option en double : '{ $flag }'.
argument-parse-failure-flag-duplicate-flag = [scarlet]⚠ Option en double : '{ $flag }'.
argument-parse-failure-flag-no-flag-started = [scarlet]⚠ Aucune option commencée. Que faire de '{ $input }' ?
argument-parse-failure-flag-missing-argument = [scarlet]⚠ Argument manquant pour l'option : '{ $flag }'.
argument-parse-failure-flag-no-permission = [scarlet]⚠ Vous n'avez pas le droit d'utiliser l'option '{ $flag }'.
argument-parse-failure-selector-syntax = [scarlet]⚠ Sélecteur invalide '{ $input }' : [lightgray]{ $reason }
argument-parse-failure-selector-no-such-target = [scarlet]⚠ Rien ne correspond à '{ $input }'.
argument-parse-failure-selector-too-many-targets = [scarlet]⚠ '{ $input }' correspond à plusieurs cibles, mais cette commande n'en accepte qu'une.
argument-parse-failure-selector-denied = [scarlet]⚠ Les sélecteurs ne sont pas autorisés ici : [lightgray]{ $reason }
argument-parse-failure-selector-kind-not-allowed = [scarlet]⚠ Le sélecteur '{ $kind }' n'est pas autorisé ici.
argument-parse-failure-selector-sender-required = [scarlet]⚠ Le sélecteur '{ $kind }' n'est utilisable qu'en jeu.
argument-parse-failure-selector-limit-exceeded = [scarlet]⚠ Le sélecteur correspond à { $count } cibles, la limite est { $limit }.
argument-parse-failure-team = [scarlet]⚠ Équipe '{ $input }' introuvable.
argument-parse-failure-content = [scarlet]⚠ '{ $input }' n'est pas un { $type } valide.
# ==============================================================================
# Button Status
# ==============================================================================
finished = terminé
finished-neutral = [orange]Terminés
finished-active = [green]Terminés
finished-inactive = [red]Terminés
major = Majeur
major-neutral = [orange]Majeurs
major-active = [green]Majeurs
major-inactive = [red]Majeurs
active = Actif
active-neutral = [orange]Actifs
active-active = [green]Actifs
active-inactive = [red]Actifs
admin = Admin
admin-neutral = [orange]Admin
admin-active = [green]Admin
admin-inactive = [red]Admin
player-leaderboard-active = [green]Classement : activé[]
player-leaderboard-inactive = [red]Classement : désactivé[]
# ==============================================================================
# Miscellaneous
# ==============================================================================
hours = heures
days = jours
success = [green]Succès
empty = [accent]Vide
never = Jamais
save = Enregistrer
close = [scarlet]Fermer
previous = [accent]« Précédent
next = [accent]Suivant »
cancel = Annuler
back = Retour
yes = Oui
no = Non
test = Test
no-description = Aucune description
discord = Discord
github = Github
donatello = Donatello
weblate = Weblate
discord-red-vs-blue = RedVSBlue
auto = Auto
on = Activé
off = Désactivé
error-command-disabled = [scarlet]⚠ La commande [accent]/{ $command }[scarlet] est désactivée sur ce serveur.
error-feature-disabled = [scarlet]⚠ Cette fonction est désactivée sur ce serveur.
none = Aucun
unknown = Inconnu
error-nickname-badge-glyph = [scarlet]⚠ Le pseudo personnalisé ne peut pas contenir d'icônes de badge réservées.

# Server browser (/servers)
player-servers-title = RÉSEAU DE SERVEURS XCORE
player-servers-cat-all = Tous
player-servers-cat-pvp = PvP
player-servers-cat-survival = Survie
player-servers-cat-special = Spécial
player-servers-hint = Touchez la fiche d'un serveur pour vous connecter
player-servers-refresh = Actualiser
player-servers-already-connected = [gold]● Vous êtes déjà connecté à ce serveur !
player-servers-transferring = [accent]Transfert vers le serveur [white]{ $server }[]...
player-servers-full = [scarlet]Le serveur { $server } est plein ! Attendez qu'une place se libère.
player-servers-offline = [scarlet]Le serveur { $server } est actuellement hors ligne.
player-servers-not-found = [scarlet]Serveur '{ $server }' introuvable.
player-servers-empty-category = [lightgray]Aucun serveur disponible dans cette catégorie.[]
player-servers-online-summary = [green]● { $players } [gray]en jeu[] [darkgray]|[] [sky]{ $servers } [gray]en ligne[]
player-servers-badge-current = [gold]● VOUS ÊTES ICI[]
player-servers-offline-badge = [darkgray]● HORS LIGNE[]
player-servers-capacity-full = [scarlet]● { $players }/{ $max } PLEIN[]
player-servers-capacity-normal = [green]● { $players }/{ $max } { $bar }
player-servers-card-current = [lightgray]Vous êtes connecté à ce serveur[]
player-servers-card-wave = [darkgray]|[] [accent]Vague { $wave }[]
player-servers-card-empty = [sky]Soyez le premier ! Lancez une partie[]
player-servers-card-map = [gray]Carte :[] [white]{ $map }[]
player-servers-card-mode = [gray]Mode :[] [white]{ $mode }[]

announcement-hub =
    [gold]★ [accent]Serveurs XCore [lightgray]» [white]Cette partie vous ennuie ?
    [lightgray]Découvrez les autres serveurs à tout moment avec [accent]/hub[lightgray] !
announcement-discord =
    [gold]★ [accent]Communauté XCore [lightgray]» [white]Vous cherchez des coéquipiers et des nouvelles ?
    [lightgray]Rejoignez notre serveur Discord avec [accent]/discord[lightgray] !
announcement-help =
    [gold]★ [accent]Aide XCore [lightgray]» [white]Besoin d'aide ou de la liste des commandes ?
    [lightgray]Tapez [accent]/help[lightgray] pour voir toutes les commandes disponibles !


error-only-players = [scarlet]⚠ Cette commande ne peut être utilisée que par les joueurs.

player-settings-username-editable = [gold]★ Nom d'utilisateur (récompense d'événement) :[]
player-settings-username-hint = Saisissez un nom d'utilisateur unique (4 à 32 caractères)...
player-settings-username-locked = [gray]Nom d'utilisateur :[] [accent]@{ $username }[] [darkgray](Verrouillé)[]
player-settings-username-none = [gray]Nom d'utilisateur : [darkgray]Non défini (gagnez un événement pour le débloquer)[]

error-username-empty = Le nom d'utilisateur ne peut pas être vide !
error-username-length = Le nom d'utilisateur doit contenir entre 4 et 32 caractères !
error-username-invalid-chars = Le nom d'utilisateur ne peut contenir que des lettres latines, des chiffres et des tirets bas !
error-username-taken = Ce nom d'utilisateur est déjà pris par un autre joueur !

# ==============================================================================
# Permission nodes
# ==============================================================================
permission-mindustry-admin = Utiliser le menu admin intégré du jeu et passer des vagues
permission-xcore-moderation-mute = Rendre muets les joueurs
permission-xcore-moderation-unmute = Lever le mute des joueurs
permission-xcore-moderation-kick = Expulser des joueurs
permission-xcore-moderation-ban = Bannir des joueurs
permission-xcore-moderation-unban = Débannir des joueurs
permission-xcore-moderation-audit-others = Voir l'historique de modération des autres joueurs
permission-xcore-moderation-votekick-immune = Ne peut pas être expulsé par vote
permission-xcore-admin-tp = Téléporter des joueurs
permission-xcore-admin-broadcast = Envoyer des annonces à tous
permission-xcore-admin-kill = Tuer des unités et des joueurs
permission-xcore-admin-heal = Soigner des unités et des joueurs
permission-xcore-admin-set-team = Changer l'équipe d'un joueur
permission-xcore-maps-force-rtv = Changer de carte sans vote
permission-xcore-maps-force-vnw = Passer une vague sans vote
permission-xcore-votes-cancel = Annuler un vote en cours
permission-xcore-events-create-major = Créer des événements majeurs
permission-xcore-events-edit-others = Modifier les événements d'autres joueurs
permission-xcore-events-force-vote = Lancer un événement sans vote
permission-xcore-events-stop = Arrêter l'événement en cours
permission-xcore-players-settings-others = Modifier les paramètres d'autres joueurs
permission-xcore-players-private-info = Voir les données privées des joueurs, comme leur adresse IP
permission-xcore-bypass-playtime = Ignorer les conditions de temps de jeu des commandes
permission-xcore-permissions-inspect = Voir qui a quelles permissions
permission-xcore-permissions-manage = Modifier les rôles et les permissions

error-target-outranks = [scarlet]⚠ Impossible sur un joueur dont le rôle n'est pas inférieur au vôtre.
perm-me-legacy = [accent]Les rôles sont désactivés sur ce serveur. Admin : [white]{ $admin }
perm-me-header = [accent]Vos rôles ici (poids [white]{ $weight }[accent]) :
perm-me-none = [lightgray] - aucun
perm-me-logged-in = [green]Vous êtes connecté en tant que membre de l'équipe.
perm-me-not-logged-in = [yellow]Vous n'êtes pas connecté en tant que membre de l'équipe. Utilisez [white]/login <mot de passe>[yellow].
perm-me-stale = [scarlet]Vos droits d'équipe sont suspendus : le serveur n'a pas pu les actualiser. Ils reviendront d'eux-mêmes.

help-ui-search-hint = Rechercher une commande, un alias ou une description
help-ui-search-empty = [lightgray]Aucune commande ne correspond à votre recherche.[]
