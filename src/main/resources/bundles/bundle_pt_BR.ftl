# ==============================================================================
# Terms
# ==============================================================================
-xcore = Servidor XCore

# ==============================================================================
# General & Help
# ==============================================================================
menu-main = Menu principal
commands-main-description = Abre o menu principal interativo.
menu-main-title = [orange]{ -xcore } — Menu principal
menu-main-content = Menu principal do servidor
help-menu = Menu de ajuda
commands-help-description = Abre o menu de ajuda interativo.
help-menu-title = [orange]{ -xcore } — Comandos
help-menu-content =
    [gray]Página [white]{ $page }[gray]/[white]{ $total }
    [lightgray]Selecione um comando para ver detalhes de uso:
help-menu-button = [accent]/{ $command } [gray]» Descrição: [white]{ $description }
help-command-with-overload-count = { $name } de ({ $count })
help-command-title = [orange]» Nome: [white]/{ $name }
help-command-header =
    [orange]» [accent]Sintaxe: [white]{ $syntax }
    [orange]» [accent]Info: [lightgray]{ $description }
help-aliases = [orange]» [accent]Apelidos: [white]{ $aliases }
help-args-title = [orange]» [accent]Argumentos:
help-usages-title = [orange]» [accent]Uso:
help-usage-entry = [gray]• [white]{ $syntax }
help-usage-args-title = [orange]» [accent]Para [white]{ $syntax }[accent]:
help-arg-entry = [gray]• [white]{ $arg } [lightgray]- { $description }
help-no-arguments = [gray]Nenhum argumento adicional necessário.
help-no-arg-description = Sem descrição.
help-no-description = Nenhuma descrição fornecida para este comando.
help-legacy-command-content =
    [orange]» [accent]Comando: [white]/{ $name }
    [orange]» [accent]Parâmetros: [white]{ $params }
    [orange]» [accent]Info: [lightgray]{ $description }
    { "" }
    [gray](Este é um comando legado com informações limitadas)
help-legacy-command-content-no-params =
    [orange]» [accent]Comando: [white]/{ $name }
    [orange]» [accent]Info: [lightgray]{ $description }
    { "" }
    [gray](Este é um comando legado com informações limitadas)
help-back = [lightgray]« Voltar

# ==============================================================================
# Modern Reactive Help & Commands Guide (xcore-ui)
# ==============================================================================
help-ui-title = GUIA DE COMANDOS
help-ui-summary = [gray]{ $count } comandos disponíveis
help-ui-empty-category = [lightgray]Nenhum comando disponível nesta categoria.[]
help-ui-overloads = ({ $count } variantes)
help-ui-aliases = [lightgray]Apelidos:[] { $aliases }
help-ui-syntax-title = Sintaxe
help-ui-args-title = Parâmetros
help-ui-arg-required = [scarlet]Obrigatório
help-ui-arg-optional = [sky]Opcional
help-ui-btn-run = Executar
help-ui-btn-copy = No chat
help-ui-btn-back = Voltar
help-ui-copied = [accent]Comando: [white]/{ $syntax }
help-ui-executed = [accent]Executando comando: [white]/{ $syntax }

# Categories
help-cat-all = Todos
help-cat-general = Geral
help-cat-game = Jogo
help-cat-social = Chat
help-cat-votes = Votações
help-cat-admin = Admin

# ==============================================================================
# Command Argument Descriptions
# ==============================================================================
# help
commands-help-page-description = Número da página a ser exibida.
# login
commands-login-password-description = Sua senha de administrador.
# ban
commands-ban-id-description = ID do jogador a ser banido.
commands-ban-period-description = Duração do banimento (ex.: 1d, 2h, 30m).
commands-ban-reason-description = Motivo do banimento.
# unban
commands-unban-id-description = ID do jogador a ser desbanido.
# mute
commands-mute-id-description = ID do jogador a ser silenciado.
commands-mute-period-description = Duração do silenciamento (ex.: 1h, 30m).
commands-mute-reason-description = Motivo do silenciamento.
# unmute
commands-unmute-id-description = ID do jogador a ter o silenciamento removido.
# votekick
commands-votekick-target-description = Jogador a ser expulso (ID ou nome).
commands-votekick-reason-description = Motivo da expulsão.
# vote
commands-vote-choice-description = Seu voto: y (sim), n (não) ou c (cancelar, apenas admins).
# t (team chat)
commands-t-message-description = Mensagem para seus aliados de equipe.
# g (global chat)
commands-g-message-description = Mensagem para todos os servidores.
# tr (translator)
commands-tr-language-description = Código do idioma, 'auto' ou 'off'.
# stats
commands-stats-id-description = ID do jogador para ver as estatísticas
# rank
commands-rank-player-description = Jogador para ver o ranking
# map
commands-map-map-description = Nome ou número do mapa.
# maps / maps-text
commands-maps-page-description = Número da página.
commands-maps-text-page-description = Número da página.
# rtv / artv
commands-rtv-map-description = Mapa para votar (opcional).
commands-artv-map-description = Mapa para mudar forçadamente.
# ai
commands-ai-state-description = Estado da IA: attack (a) ou idle (i).
# event / events
commands-events-page-description = Número da página.

# ==============================================================================
# General & Help (continued)
# ==============================================================================
commands-information-description = Mostra informações sobre o servidor.
commands-info = Informações
commands-info-title = [orange]{ -xcore } — Nome do servidor: [orange]{ $server-name }
commands-info-text =
    [accent]XCore[white] é um servidor [cyan]gratuito[white] para jogar [accent]Mindustry[white].
    { "" }
    Versão do XCore — [accent]{ $version }[white]
commands-sync-description = Sincroniza seu jogo com o servidor. Use para corrigir erros como unidades fantasmas.
commands-discord-description = Redireciona você para o servidor do Discord.
discord-menu-title = [orange]{ -xcore } — Discord
discord-menu-content =
    [white]Gerencie sua vinculação do Discord aqui.
    { "" }
    [white]Status: { $status }
    [white]Servidor: [accent]{ $discordUrl }[]
discord-menu-open = Abrir Discord
discord-menu-link = Vincular conta
discord-menu-status = Atualizar status
discord-menu-unlink = Desvincular conta
discord-menu-status-not-linked = [lightgray]não vinculado[]
discord-menu-status-linked = [green]{ $discordUsername }[] [gray]({ $discordId })[]
discord-link-menu-title = [orange]{ -xcore } — Vincular conta do Discord
discord-link-menu-content =
    [white]No nosso servidor do Discord, execute o comando slash do bot:
    { "" }
    [accent]/link { $code }[]
    { "" }
    [white]Expira em: [accent]{ $expireMinutes }[] min
    [white]Discord: [accent]{ $discordUrl }[]
discord-link-menu-refresh = Atualizar código
discord-link-menu-copy = Copiar código
discord-link-menu-regenerate = Gerar novo código
discord-link-menu-status = Voltar ao menu do Discord
welcome =
    [accent]Bem-vindo ao { $serverName }!
    [lightgray]Digite [accent]/help[lightgray] para ver a lista de comandos
    [lightgray]Digite [accent]/vote [gray]<y/n>[lightgray] para votar na expulsão de um jogador
    [lightgray]Digite [accent]/votekick [gray]<ID/nome> <motivo…>[lightgray] para iniciar uma votação de expulsão
    [lightgray]Digite [accent]/t [gray]<mensagem…>[lightgray] para enviar mensagem para sua equipe
    [lightgray]Digite [accent]/g [gray]<mensagem…>[lightgray] para enviar mensagem para todos os servidores
    [lightgray]Digite [accent]/tr [gray]<idioma/auto>[lightgray] para ativar o tradutor
    [lightgray]Digite [accent]/discord[lightgray] para abrir o menu do Discord e vincular sua conta

# ==============================================================================
# Chat & Social
# ==============================================================================
commands-t-description = Envia uma mensagem apenas para seus aliados de equipe.
commands-t-chat = [{ "#" }{ $color }][Equipe] [coral]> { $badge }[accent]{ $name }[lightgray]: [white]{ $message }
commands-g-description = Envia uma mensagem para todos os servidores.
commands-a-description = Envia uma mensagem apenas para os admins.
commands-msg-description = Envia uma mensagem privada para um jogador.
commands-msg-id-description = ID do jogador.
commands-msg-message-description = Texto da mensagem privada.
commands-reply-description = Responde ao último jogador nas mensagens privadas.
commands-reply-message-description = Texto da resposta.
commands-inbox-description = Abre o menu de mensagens privadas.
commands-inbox-id-description = ID do jogador.
commands-tr-description = Define o idioma do tradutor.
commands-badge-description = Abre o menu de emblemas e gerencia seu emblema ativo.
commands-tr-success = [accent]O idioma do tradutor foi alterado com sucesso para [grey]{ $translatorLanguage }[]!
commands-tr-off = [accent]O tradutor está [scarlet]desativado[]!
commands-tr-not-found = [scarlet]⚠ Este idioma não existe.
discord-chat-format = [#5865F2][DISCORD][] [lightgray]| [accent]{ $author }[lightgray] >> [white]{ $message }
global-chat-format = [royal][[[orange]GLOBAL [lightgray](de [accent]{ $server }[])[] { $author }[]]: [white]{ $message }
private-message-received = [sky][MP][] [lightgray]de [accent]{ $author } [gray]#{ $pid }[lightgray]: [white]{ $message }
private-message-sent = [sky][MP][] [lightgray]para [accent]{ $target } [gray]#{ $pid }[lightgray]: [white]{ $message }
private-message-unread-count =
    [accent]Você tem [white]{ $count }[accent] { $count ->
    [one] mensagem privada não lida
    *[other] mensagens privadas não lidas
    }.
private-message-join-notification =
    [accent]Você tem [white]{ $count }[accent] { $count ->
    [one] mensagem privada não lida
    *[other] mensagens privadas não lidas
    }. Use [white]/inbox[accent] para { $count ->
    [one] lê-la
    *[other] lê-las
    }.
private-message-block-success = [accent]Mensagens privadas de [white]{ $target } [gray]#{ $pid }[accent] agora estão bloqueadas.
private-message-block-already = [lightgray]Mensagens privadas de [white]{ $target } [gray]#{ $pid }[lightgray] já estão bloqueadas.
private-message-unblock-success = [accent]Mensagens privadas de [white]{ $target } [gray]#{ $pid }[accent] não estão mais bloqueadas.
private-message-unblock-missing = [lightgray][white]{ $target } [gray]#{ $pid }[lightgray] não está bloqueado.
private-message-menu-title = [orange]{ -xcore } — Mensagens privadas
private-message-menu-content =
    [white]Página [green]{ $page }[] de [green]{ $total }[]
    [white]Não lidas: [accent]{ $unread }[]
private-message-menu-empty = [lightgray]Sua caixa de entrada está vazia.
private-message-menu-entry-unread = [accent]Não lida[] [white]{ $author } [gray]#{ $pid }[] [lightgray]({ $time })[]: [white]{ $message }
private-message-menu-entry-read = [gray]Lida[] [white]{ $author } [gray]#{ $pid }[] [lightgray]({ $time })[]: [white]{ $message }
private-message-details-title = [orange]{ -xcore } — Mensagem
private-message-details-content =
    [white]De: [accent]{ $author } [gray]#{ $pid }[]
    [white]Hora: [accent]{ $time }[]
    [white]Status: [accent]{ $status }[]
    { "" }
    [white]{ $message }
private-message-status-unread = não lida
private-message-status-read = lida
private-message-blocked-title = [orange]{ -xcore } — Jogadores bloqueados
private-message-blocked-content =
    [white]Página [green]{ $page }[] de [green]{ $total }[]
    [white]Bloqueados: [accent]{ $count }[]
private-message-blocked-empty = [lightgray]Você não tem jogadores bloqueados.
private-message-blocked-entry = [white]{ $target } [gray]#{ $pid }[]
private-message-compose = Nova mensagem
private-message-blocked = Bloqueados
private-message-block = Bloquear remetente
private-message-unblock = Desbloquear remetente
private-message-reply-title = Responder
private-message-reply-message = Digite uma mensagem para [accent]#{ $pid }[]
private-message-compose-target-title = Nova mensagem
private-message-compose-target-message = Digite o ID do jogador no formato [accent]#123[]
private-message-compose-body-title = Texto da mensagem
private-message-compose-body-message = Digite uma mensagem privada para [accent]{ $pid }[]

# ==============================================================================
# Authentication & Admin Access
# ==============================================================================
commands-login-description = Ativa os privilégios de admin se sua conta do Discord vinculada já tiver acesso.
commands-login-incorrect-password = [scarlet]⚠ Senha incorreta!
commands-login-success = [green]Privilégios de admin concedidos.
commands-login-confirmed = [green]Acesso de admin pelo Discord confirmado.
commands-login-admin-password-created =
    [green]Senha de admin criada.
    [red]Não se esqueça da sua senha! Se esquecê-la, você precisará pedir a um administrador geral para redefini-la.
commands-login-request-approval-discord = [accent]Sua conta não tem acesso de admin no Discord. Obtenha o cargo de admin no Discord e tente novamente.
commands-login-verifying = [lightgray]Verificando senha de administrador...
commands-login-already-processing = [scarlet]⚠ Uma solicitação de login já está sendo processada. Aguarde.
commands-login-rate-limited = [scarlet]⚠ Muitas tentativas de login falhas. Aguarde antes de tentar novamente.
commands-discord-link-created =
    [green]Código de vinculação do Discord criado: [accent]{ $code }[]
    [lightgray]No nosso servidor do Discord, execute o comando do bot [accent]/link { $code }[] dentro de [accent]{ $expireMinutes }[] min.
    [cyan]{ $discordUrl }
commands-discord-link-confirmed = [green]Conta do Discord vinculada: [accent]{ $discordUsername }[]
commands-discord-link-already-linked = [lightgray]Esta conta do Mindustry já está vinculada. Use [accent]/discord status[] ou [accent]/discord unlink[].
commands-discord-link-error = [scarlet]Não foi possível criar o código de vinculação do Discord. Tente novamente mais tarde.
commands-discord-status-not-linked = [lightgray]Sua conta não está vinculada ao Discord.
commands-discord-status-linked = [green]Discord vinculado: [accent]{ $discordUsername }[] [gray]({ $discordId })[]
commands-discord-unlink-not-linked = [lightgray]Sua conta não está vinculada ao Discord.
commands-discord-unlink-success = [green]Vínculo do Discord removido.
commands-logout-description = Sair. Isso [scarlet]revogará seus privilégios de admin.
commands-logout-successful = [green]Privilégios de admin revogados.

# ==============================================================================
# Moderation (Ban, Mute, Kick)
# ==============================================================================
commands-ban-description = Bane um jogador.
commands-ban-success = { $nickname } [scarlet]banido
commands-unban-description = Desbane um jogador.
commands-unban-success = { $nickname }[accent] #{ $pid } [green]desbanido com sucesso.
commands-mute-description = Silencia um jogador.
commands-mute-success = [accent]{ $nickname } foi silenciado com sucesso
commands-unmute-description = Remove o silenciamento de um jogador.
commands-unmute-success = [green]Silenciamento removido de []{ $nickname }
commands-alert-description = Exibe um banner de anúncio em destaque para jogadores selecionados ou para todos.
commands-toast-description = Exibe uma notificação de aviso rápida para jogadores selecionados.
commands-announcement-description = Transmite um anúncio periódico por chave ou o próximo na rotação.
commands-audit-description = Abre o registro de auditoria das ações da equipe e moderação.
ban-content = [scarlet]⚠ Banido[]
[accent]{ $nickname }[white] — você foi banido permanentemente deste servidor.
[orange]» [accent]ID do jogador: [white]{ $pid }
[lightgray]Para recorrer, visite o canal do Discord [gray]{ support-channel }[]:
[cyan]{ $discordUrl }
ban-cancelled = [accent]O banimento do jogador [scarlet]{ $nickname }[accent] foi cancelado
tempban-content = [scarlet]⚠ Banido[]
{ $permanent ->
[true] [accent]{ $nickname }[white] — você foi banido permanentemente deste servidor.
*[false] [accent]{ $nickname }[white] — você foi banido temporariamente deste servidor.
}
[orange]» [accent]Banimento: [white]{ $banId }
[orange]» [accent]ID do jogador: [white]{ $pid }
{ "" }
[orange]» [accent]Admin: [white]{ $adminName }
[orange]» [accent]Motivo: [gold]{ $reason }
{ $permanent ->
[true] [orange]» [accent]{ audit-menu-duration-permanent }[]
*[false] [orange]» [accent]Tempo restante: { DURATION($duration, style: "full", colored: "true", maxUnits: 2) }
{ "" }[orange]» [accent]Expira em: [white]{ DATETIME($expireDate, dateStyle: "medium", timeStyle: "short") }
}
{ "" }
[lightgray]Para recorrer, visite o canal do Discord [gray]{ support-channel }[]:
[lightgray]Informe { $banId } no seu recurso.
[cyan]{ $discordUrl }
tempban-player-banned = [scarlet] O admin { $adminName }[scarlet] baniu o jogador [gray]'[]{ $playerName }[gray]'
you-are-muted-by =
    [orange]⚠ Chat restrito[]
    [lightgray]Você foi silenciado pelo administrador [accent]{ $adminName }[lightgray].
    [orange]» [accent]Motivo: [gold]{ $reason }
    [orange]» [accent]Tempo restante: { DURATION($duration, style: "full", colored: "true", maxUnits: 2) }
you-are-muted =
    [orange]⚠ Chat restrito[]
    [lightgray]Você não pode enviar mensagens enquanto este silenciamento estiver ativo.
    [orange]» [accent]Admin: [white]{ $adminName }
    [orange]» [accent]Motivo: [gold]{ $reason }
    [orange]» [accent]Tempo restante: { DURATION($duration, style: "full", colored: "true", maxUnits: 2) }
kick-pirated-game = [accent]Cliente não autorizado detectado. [scarlet]Acesso negado[]. Por favor, jogue usando a versão [lime]oficial[] da [blue]Steam[], [blue]Google Play[] ou [blue]itch.io[].
kick-recently-kicked =
    [accent]Você foi expulso recentemente deste servidor.
    Aguarde [cyan]{ DURATION($remaining, style: "timer") }[accent] antes de entrar novamente.
kick-admintools-outdated =
    [green]Versão necessária do AdminTools: [grey]{ $requiredVersion }[]
    [scarlet]Sua versão do AdminTools: [grey]{ $version }[]
    { "" }
    [cyan]Por favor, atualize o AdminTools para entrar neste servidor.
support-channel = #tickets

# ==============================================================================
# Cases: player reports, in-game notices and the /report menu
# ==============================================================================
report-menu-title = [orange]{ -xcore } — Denunciar jogador
report-menu-pick-player = Escolha um jogador
report-menu-left-recently = Saíram recentemente
report-menu-left-ago = há { $minutes } min
report-menu-empty = Ninguém para denunciar
report-menu-staff-hint = [lightgray]Reclamações sobre a staff são feitas pelo Discord.
report-menu-pick-reason = Escolha um motivo
report-menu-comment = Comentário (opcional)
report-menu-comment-required = Um comentário é obrigatório para "Outro".
report-menu-confirm-title = Confirmar denúncia
report-menu-confirm-note = [lightgray]O chat recente e o histórico de construção serão anexados. Denúncias falsas são punidas.
report-menu-next = Próximo
report-menu-back = Voltar
report-menu-send = Enviar
report-menu-close = Fechar
report-result-sent = [green]Denúncia enviada. Obrigado!
report-result-cooldown = [scarlet]Aguarde [accent]{ $seconds } s[scarlet] antes da próxima denúncia.
report-result-same-target = [scarlet]Você já denunciou este jogador recentemente.
report-result-daily-limit = [scarlet]Limite diário de denúncias atingido. Tente novamente amanhã.
report-result-invalid-target = [scarlet]Este jogador não pode ser denunciado.
report-result-playtime = [scarlet]Jogue por mais [accent]{ $minutes } min[scarlet] antes de enviar denúncias.
report-result-disabled = [scarlet]As denúncias estão desativadas neste servidor.
report-result-failed = [scarlet]Não foi possível enviar a denúncia. Tente novamente.
report-reason-griefing = Griefing
report-reason-toxicity = Toxicidade
report-reason-cheating = Trapaça
report-reason-nsfw = NSFW
report-reason-evasion = Evasão de banimento
report-reason-other = Outro
case-notice-report-received = [lightgray]Denúncia registrada como [accent]#{ $number }[lightgray]. Obrigado!
case-notice-report-resolved = [lightgray]Denúncia [accent]#{ $number }[lightgray] analisada: [green]medidas foram tomadas[lightgray]. Obrigado.
case-notice-report-rejected =
    { $reason ->
    [no_violation] [lightgray]Denúncia [accent]#{ $number }[lightgray] analisada: nenhuma violação de regra encontrada. Obrigado.
    [no_evidence] [lightgray]Denúncia [accent]#{ $number }[lightgray] analisada: evidências insuficientes. Obrigado.
    [already_handled] [lightgray]Denúncia [accent]#{ $number }[lightgray] encerrada: o caso já havia sido resolvido. Obrigado.
    [duplicate] [lightgray]Denúncia [accent]#{ $number }[lightgray] encerrada: isso já havia sido denunciado antes. Obrigado.
    [abuse] [lightgray]Denúncia [accent]#{ $number }[lightgray] rejeitada: [scarlet]a própria denúncia violou as regras do servidor.
    *[other] [lightgray]Denúncia [accent]#{ $number }[lightgray] analisada: nenhuma medida tomada. Obrigado.
    }
case-notice-report-not-accepted = [scarlet]Sua denúncia não foi aceita.
case-notice-appeal-accepted = [lightgray]Recurso [accent]#{ $number }[lightgray] aceito: o banimento foi removido.
case-notice-appeal-shortened = [lightgray]Recurso [accent]#{ $number }[lightgray] aceito: o tempo de banimento foi reduzido.
case-notice-badge-granted = [lightgray]Solicitação de emblema [accent]#{ $number }[lightgray] aprovada: emblema [green]{ $badge }[lightgray] concedido.
case-notice-badge-rejected = [lightgray]Solicitação de emblema [accent]#{ $number }[lightgray] foi rejeitada.
case-notice-map-accepted = [lightgray]Revisão de mapa [accent]#{ $number }[lightgray]: mapa [green]{ $map }[lightgray] foi aceito.
case-notice-map-rejected = [lightgray]Revisão de mapa [accent]#{ $number }[lightgray]: mapa [scarlet]{ $map }[lightgray] foi rejeitado.
case-notice-complaint-closed = [lightgray]A solicitação [accent]#{ $number }[lightgray] foi encerrada. Obrigado.

# ==============================================================================
# Voting (VoteKick)
# ==============================================================================
commands-votekick-description = Votação para expulsar um jogador do servidor.
commands-vote-description = Votar na votação ativa.
commands-vote-vote-with = [scarlet]⚠ Vote usando [orange]/vote <y/n/c>
votekick-vote =
    { $starter } [grey]#[white]{ $starterId }[lightgray] votou para expulsar { $target } [grey]#[white]{ $targetId }[lightgray] por [orange]{ $reason }[lightgray]. ([accent]{ $votes }[]/[accent]{ $required }[])
    [lightgray]Digite [orange]/vote <y/n>[] para votar.
votekick-left = { $player }[lightgray] saiu. Seu voto foi cancelado. ([accent]{ $votes }[]/[accent]{ $required }[])
votekick-fail = [lightgray]Votação falhou. Votos insuficientes para expulsar { $target }[lightgray].
votekick-cancelled = [scarlet]A votação para expulsar { $target }[scarlet] foi cancelada por { $admin }.
votekick-success =
    [orange]Votação aprovada. { $target }[orange] expulso por [scarlet]{ $minutes }[] { $minutes ->
    [one] minuto
    *[other] minutos
    }.

# ==============================================================================
# Maps & RTV
# ==============================================================================
commands-map-description = Estatísticas de um mapa específico.
commands-map-title = [orange]{ -xcore } — Mapa
commands-map-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $name }[] [sky]{ $authorLabel }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Visão geral[]
    [gray]Tamanho: [white]{ $width }x{ $height } [darkgray]|[gray] Votos: [lime]+{ $like } [darkgray]/[scarlet] -{ $dislike }[]
    { "" }
    [accent]■ Atividade[]
    [gray]Total jogado: [white]{ $played } [darkgray]|[gray] Este ano: [white]{ $playedYear }[]
    [gray]Última partida: [white]{ $lastPlayed }[]
    [gray]Popularidade: [white]{ $popularity } [darkgray]|[gray] Interesse: [white]{ $interest } [darkgray]|[gray] Reputação: [white]{ $reputation }[]
    { "" }
    [accent]■ Duração das partidas[]
    [gray]Mín.: [white]{ $min } [darkgray]|[gray] Média: [white]{ $avg } [darkgray]|[gray] Máx.: [white]{ $max }[]
commands-maps-description = Lista de todos os mapas neste servidor.
commands-maps-title = [orange]{ -xcore } — Mapas
commands-maps-content =
    [gray]Mapa atual: [accent]{ $current }[]
    [white]Página [green]{ $page }[] de [green]{ $total }[]
commands-maps-current-row = { $name } ★
commands-maps-text-description = Lista de todos os mapas neste servidor.
commands-maps-text-start-content =
    [accent]Mapa atual: []{ $name }[white]
    [orange][gold]Lista de mapas [lightgray]{ $page }[gray]/[lightgray]{ $total }
commands-maps-text-content =
    { "" }
    { $index }. [orange] - [white]{ $name }[orange] | [green]{ $reputation }[orange] | [white]{ $width }x{ $height }[orange] | [white]{ $lastPlayed }[orange] | Por: [sky]{ $author }
commands-artv-description = Muda o mapa forçadamente.
commands-artv-map-skipped = { $nickname }[accent] pulou o mapa. Próximo mapa: { $name }.
commands-artv-event-skipped = { $nickname }[accent] pulou o evento. Próximo evento: { $name }.
commands-rtv-description = Votação para mudar de mapa.
commands-vnw-description = Votação para iniciar a próxima onda mais cedo.
commands-avnw-description = Inicia a próxima onda imediatamente.
commands-like-description = Vota a favor do mapa atual (aumenta a reputação).
commands-dislike-description = Vota contra o mapa atual.
map-vote-title = [orange]{ -xcore } — [scarlet]FIM DE JOGO!
map-vote-content =
    { "" }
    Próximo mapa: [accent]{ $mapName }[] por [accent]{ $author }[white].
    Nova partida começa em [accent]{ $seconds }[white] { $seconds ->
    [one] segundo
    *[other] segundos
    }.
    { "" }
    [cyan]Gostou deste mapa?
map-vote-like = [green]👍 Gostei
map-vote-dislike = [red]👎 Não gostei
map-vote-like-selected = [gray]Você gostou
map-vote-dislike-selected = [gray]Você não gostou
map-rtv = [orange]Votação
map-artv = [red]Mudança Imediata
map-maps = Mapas
map-maps-back = Voltar à lista de mapas
current-map = Mapa atual
next-map = Próximo mapa

# Map UI Modernized
map-ui-search-hint = Buscar mapa ou autor...
map-ui-total = [lightgray]Mapas: [white]{ $count }[]
map-ui-about-title = Sobre o mapa
map-ui-rtv-title = Mudança de mapa
map-ui-no-maps-found = [lightgray]Nenhum mapa encontrado para esta busca.[]
map-ui-by = por [lightgray]{ $author }[]
map-ui-mode = Modo: [white]{ $mode }[]
map-ui-loading = [gray]Carregando...[]
map-ui-no-preview = [gray]Sem prévia[]
map-ui-dimensions = [gray]Dimensões: [white]{ $width } x { $height }[]
map-ui-total-plays = [gray]Total de partidas: [white]{ $played } [lightgray]({ $playedYear } este ano)[]
map-ui-last-played = [gray]Última partida: [white]{ $lastPlayed }[]
map-ui-description = [gray]Descrição: [lightgray]{ $description }[]
map-ui-no-description = [gray]Descrição: [lightgray]Nenhuma descrição disponível.[]

map-ui-col-duration = Duração
map-ui-duration-min = [gray]Mín.: [white]{ $value }[]
map-ui-duration-avg = [gray]Média: [white]{ $value }[]
map-ui-duration-max = [gray]Máx.: [white]{ $value }[]

map-ui-col-popularity = Popularidade
map-ui-popularity-score = [gray]Pontuação: [white]{ $value }[]
map-ui-popularity-pop = [gray]Popularidade: [white]{ $value }[]
map-ui-popularity-interest = [gray]Interesse: [white]{ $value }[]

map-ui-col-community = Comunidade
map-ui-community-approval = [gray]Aprovação: [green]{ $rate }%[]

map-ui-btn-like = Gostei ({ $count })
map-ui-btn-dislike = Não gostei ({ $count })

map-ui-rtv-active-status = [accent]● Votação em andamento: [white]{ $votes }/{ $required }[] [gray](termina em [white]{ $seconds } s[gray])[]
map-ui-rtv-vote-yes = Votar neste mapa
map-ui-rtv-start = Iniciar votação para este mapa
map-ui-admin-rtv = Mudar de mapa agora
map-ui-admin-rtv-confirm = Pressione novamente para confirmar

gamemode-survival = Sobrevivência
gamemode-attack = Ataque
gamemode-pvp = PvP
gamemode-sandbox = Sandbox
gamemode-editor = Editor
rtv-vote =
    { $nickname }[lightgray] votou para mudar o mapa atual para [orange]{ $mapName }[lightgray]. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Digite [orange]y[] ou [orange]n[] para votar.
rtv-left = { $nickname }[lightgray] saiu. Seu voto para mudar o mapa foi cancelado. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
rtv-fail = [lightgray]Votação falhou. Votos insuficientes para mudar o mapa para [orange]{ $mapName }[].
rtv-success = [orange]Votação aprovada. O mapa [accent]{ $mapName }[] será carregado em [accent]{ $mapLoadDelay }[] { $mapLoadDelay ->
[one] segundo
*[other] segundos
}…
rtv-cancelled = [lightgray]A votação para mudar o mapa para [orange]{ $mapName }[lightgray] foi cancelada por { $admin }.
vnw-vote =
    { $nickname }[lightgray] votou para iniciar a onda [orange]{ $wave }[lightgray] mais cedo. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Digite [orange]y[] ou [orange]n[] para votar.
vnw-left = { $nickname }[lightgray] saiu. Seu voto para iniciar a onda [orange]{ $wave }[lightgray] mais cedo foi cancelado. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
vnw-fail = [lightgray]Votação falhou. Votos insuficientes para iniciar a onda [orange]{ $wave }[] mais cedo.
vnw-success = [orange]Votação aprovada. A onda [accent]{ $wave }[] está começando agora.
vnw-cancelled = [lightgray]A votação para iniciar a onda [orange]{ $wave }[lightgray] mais cedo foi cancelada por { $admin }.
vnw-obsolete = [lightgray]A onda [orange]{ $wave }[lightgray] já começou, o resultado da votação não é mais necessário.

# ==============================================================================
# Statistics & Ranks & Players
# ==============================================================================
commands-player-description = Mostra as estatísticas de um jogador.
commands-settings-description = Abre suas configurações de jogador.
player-menu-player = Jogador
player-menu-player-title = [orange]{ -xcore } — Estatísticas do jogador
player-menu-player-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $customNickname }[] [gray]#{ $pid }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Perfil[]
    [gray]Nome: [white]{ $nickname } [darkgray]|[gray] Admin: [lime]{ $admin }[]
    [gray]Emblema: [white]{ $activeBadge } [darkgray]|[gray] Sistema: [coral]{ $systemBadge }[]
    [gray]Entrou em: [white]{ $accountCreated }[]
    { "" }
    [accent]■ Classificações[]
    [gray]Tempo de jogo: [white]{ $totalPlayTime }[]
    [gray]MiniPvP: [sky]{ $pvpRating } [darkgray]|[gray] Hexed antigo: [sky]{ $hexedRankName } [gray]({ $hexedPoints } pts) [darkgray]|[gray] Top: [accent]{ $hexedTopRank }[]
    { "" }
    [accent]■ Partidas: [white]{ $gamesPlayed } [gray]jogadas [darkgray]|[lime] { $gamesWon } [gray]vitórias [darkgray]|[sky] { $winRate }% [gray]taxa de vitória[]
    [gray]• [white]PvP: { $pvpSummary }[]
    [gray]• [white]Sobrev.: { $survivalSummary }[]
    [gray]• [white]Hexed antigo: { $hexedSummary }[]
    { "" }
    [accent]■ Eficiência em combate[]
    [gray]Blocos (Construídos/Desmontados/Destruídos): [lime]{ $blocksBuilt } [darkgray]/ [orange]{ $blocksDeconstructed } [darkgray]/ [scarlet]{ $blocksDestroyed }[]

# Modern Player Stats UI
player-stats-title = [orange]{ -xcore } — Perfil do jogador
player-stats-tab-overview = Visão geral
player-stats-tab-stats = Estatísticas
player-stats-tab-matches = Partidas
player-stats-tab-blocks = Blocos
player-stats-tab-players = Online ({ $count })

player-stats-status-online = [lime]● Online[]
player-stats-status-offline = [gray]○ Offline[]
player-stats-no-bio = [gray]Nenhuma biografia disponível.[]
player-stats-loading = [lightgray]Carregando dados...[]
player-stats-no-stats = [gray]Nenhum dado de partida registrado ainda.[]
player-stats-filter-all = Filtro: Todos
player-stats-filter-admins = Filtro: Apenas admins
player-stats-filter-non-admins = Filtro: Sem admins
player-stats-refresh = Atualizar
player-stats-combat-efficiency = Eficiência de combate e construção
player-stats-waves-summary = [gray]ondas: máx. [lime]{ $best }[], média [white]{ $avg }[]
player-stats-hexed-top-placement = [gray]melhor [accent]#{ $best }[], top 3: [sky]{ $top3 }[]
player-stats-max-rank = [gold]★ RANKING MÁXIMO ALCANÇADO ★[]
player-stats-max-league = [gold]★ LIGA MÁXIMA ALCANÇADA ★[]
player-stats-hexed-wins-left = [lightgray]{ $wins } vitórias para [white]{ $rank }[]
player-stats-league-elo-left = [lightgray]{ $elo } ELO para [white]{ $league }[]
player-stats-ratio-legend = [lightgray]Proporção de blocos[]

player-stats-account-created = [gray]Entrou em:[]
player-stats-play-time = [gray]Tempo de jogo:[]
player-stats-legacy-pvp-rating = [gray]PvP antigo:[]
player-stats-hexed-rank = [gray]Hexed antigo:[]
player-stats-hexed-leaderboard = [gray]Top Hexed:[]

player-stats-total-games = [gray]Total de jogos:[]
player-stats-victories = [gray]Vitórias:[]
player-stats-survival-summary = [gray]Sobrevivência:[]
player-stats-hexed-summary = [gray]Hexed antigo:[]

player-stats-blocks-built = [gray]Blocos construídos:[]
player-stats-blocks-deconstructed = [gray]Desmontados:[]
player-stats-blocks-destroyed = [gray]Destruídos:[]
player-stats-units-produced = [gray]Unidades produzidas:[]
player-stats-units-lost = [gray]Unidades perdidas:[]

player-stats-games-played-value = [white]{ $count }[] [gray]jogos[]
player-stats-victories-value = [lime]{ $wins }[] [gray]vitórias[]  [darkgray]|[]  [sky]{ $winRate }%[] [gray]taxa de vitória[]
player-stats-hexed-points = [gray]({ $points } pts)[]

player-stats-btn-settings = Configurações
player-stats-btn-matches = Partidas
player-stats-btn-audit = Histórico
player-stats-btn-players = Online
player-stats-btn-close = Fechar
player-stats-admin-tag = [coral]<Admin>[]
player-menu-players = Jogadores online
player-menu-players-title = [orange]{ -xcore } — Jogadores online
player-menu-players-content = [white]Página [green]{ $page }[] de [green]{ $total }[]
player-menu-players-empty = Nenhum jogador online encontrado
player-menu-players-row = [white]{ $nickname } [gray](PID: { $pid })[]
player-menu-settings = Configurações
player-menu-settings-title = [orange]{ -xcore } — Configurações do jogador
player-menu-settings-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $displayNickname }[] [gray]#{ $pid }[]
    [lightgray]{ $description }[]
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    { "" }
    [accent]■ Perfil[]
    [gray]Nome: [white]{ $nickname } [darkgray]|[gray] Exibido: [lime]{ $customNickname }[]
    [gray]Emblema: [white]{ $activeBadge } [darkgray]|[gray] Sistema: [coral]{ $systemBadge }[]
    { "" }
    [accent]■ Visibilidade[]
    [gray]Placar de líderes: [white]{ $leaderboard }[]
    { "" }
    [accent]■ Chat[]
    [gray]Global: [white]{ $globalChat } [darkgray]|[gray] Discord: [white]{ $discordRelay }[]
    [gray]Tradutor: [white]{ $translatorLanguage }[]
    { "" }
    [accent]■ Idioma[]
    [gray]Idioma: [white]{ $language }[]
player-menu-settings-chat = Configurações de chat
player-menu-settings-chat-title = [orange]{ -xcore } — Configurações de chat

# Modern Player Settings Form
player-settings-tab-profile = Perfil
player-settings-tab-chat = Chat
player-settings-tab-badges = Emblemas
player-settings-chat-preview = Prévia do chat
player-settings-chat-preview-sample = Exemplo
player-settings-chat-preview-message = Olá a todos!
player-settings-symbol-color-mode = Cor do símbolo
player-settings-badges-my = Meus emblemas
player-settings-badges-all = Todos os emblemas
player-settings-badge-equip = Equipar
player-settings-badge-unequip = Desequipar
player-settings-badge-preview = Prévia
player-settings-badge-previewing = Na prévia
player-settings-translator-lang = Tradutor do chat
player-settings-translator-off = Desativado
player-settings-badges-empty = Você ainda não desbloqueou nenhum emblema.
player-settings-manage-badges = Gerenciar emblemas
player-settings-global-chat = Chat global
player-settings-discord-relay = Transmissão do Discord
player-settings-leaderboard = Mostrar placar de líderes
player-settings-language = Idioma
player-settings-saved = [lime]Configurações salvas![]
player-settings-reset-feedback = [lightgray]Nome personalizado redefinido.[]
player-settings-edit-badges = [accent]Editar[]
player-settings-tab-language = Idioma
player-settings-identity = Nome e descrição
player-settings-interface = Tela
player-settings-leaderboard-hint = A lista dos melhores jogadores acima do jogo. Funciona no Mini-PvP.
player-settings-global-chat-hint = Mensagens de jogadores de outros servidores XCore.
player-settings-discord-relay-hint = Mensagens do canal do Discord do servidor no chat do jogo.
player-settings-translator-hint = Mensagens de outros jogadores são traduzidas para o idioma escolhido.
player-settings-language-hint = Idioma dos menus e mensagens do servidor. Auto segue o idioma do seu jogo.
player-menu-settings-chat-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [accent]■ Visibilidade do chat[]
    [gray]Chat global: [white]{ $globalChat }[]
    [gray]Transmissão do Discord: [white]{ $discordRelay }[]
    { "" }
    [accent]■ Tradução[]
    [gray]Idioma do tradutor: [white]{ $translatorLanguage }[]
player-menu-settings-translator-title = [orange]{ -xcore } — Escolha do idioma do tradutor
player-menu-settings-language-title = [orange]{ -xcore } — Escolha do idioma
player-menu-settings-customNickname = Editar nome
player-menu-settings-customNickname-title = [orange]{ -xcore } — Editar nome
player-menu-settings-customNickname-message = [lightgray]Deixe em branco para redefinir
player-menu-settings-customNickname-reset = [scarlet]Redefinir nome
player-menu-settings-description = Editar descrição
player-menu-settings-description-title = [orange]{ -xcore } — Editar descrição
player-menu-settings-badges = Emblemas
player-menu-settings-global-chat-on = [green]Chat global
player-menu-settings-global-chat-off = [red]Chat global
player-menu-settings-discord-relay-on = [green]Transmissão do Discord
player-menu-settings-discord-relay-off = [red]Transmissão do Discord
audit-menu-open = Histórico
audit-menu-actions-open = Ações
audit-menu-history-title = [orange]{ -xcore } — Histórico de auditoria
audit-menu-history-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $player }[] [gray]#{ $pid }[]
    [gray]Registros exibidos: [accent]{ $entriesShown }[]
    [gray]{ $pageState } [darkgray]|[] { $nextState }
    [lightgray]{ $hint }[]
audit-menu-history-page-first = Registros mais recentes
audit-menu-history-page-older = Registros mais antigos
audit-menu-tab-sanctions = Sanções recebidas
audit-menu-tab-actions = Ações da staff
audit-menu-filter-all = Todas
audit-menu-filter-bans = Banimentos
audit-menu-filter-mutes = Silenciamentos
audit-menu-filter-warns = Avisos
audit-menu-filter-other = Outros
audit-menu-btn-back = Voltar ao histórico
audit-menu-btn-copy-id = Copiar ID
audit-menu-copy-id-success = O ID do registro foi enviado no chat
audit-menu-field-id = ID do registro
audit-menu-page = Página { $page }
audit-menu-details-unavailable = O registro não está disponível.
audit-menu-field-target = Jogador
audit-menu-field-actor = Realizado por
audit-menu-field-server = Servidor
audit-menu-field-reason = Motivo
audit-menu-section-time = Tempo
audit-menu-field-occurred = Quando
audit-menu-field-duration = Duração
audit-menu-field-expires = Expira em
audit-menu-btn-revoke = Revogar sanção
audit-menu-revoke-success = Sanção revogada com sucesso.
audit-menu-status-active = ATIVO
audit-menu-status-expired = EXPIRADO
audit-menu-status-permanent = PERMANENTE
audit-menu-history-more = Mais registros disponíveis
audit-menu-history-end = Fim do histórico
audit-menu-history-empty = Nenhum registro de auditoria para este jogador ainda.
audit-menu-history-hint = Selecione um registro abaixo para ver detalhes.
audit-menu-actions-title = [orange]{ -xcore } — Ações de auditoria
audit-menu-actions-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $player }[] [gray]#{ $pid }[]
    [gray]Registros exibidos: [accent]{ $entriesShown }[]
    [gray]{ $pageState } [darkgray]|[] { $nextState }
    [lightgray]{ $hint }[]
audit-menu-actions-empty = Nenhuma ação registrada para este jogador ainda.
audit-menu-actions-hint = Selecione um registro abaixo para ver o que este jogador fez.
audit-menu-summary-row = [accent]{ $action }[] [darkgray]•[] [white]{ $actor }[] [gray]— { $reason }
audit-menu-action-summary-row = [accent]{ $action }[] [darkgray]•[] [white]{ $target }[] [gray]— { $reason }
audit-menu-details-title = [orange]{ -xcore } — Detalhes da auditoria
audit-menu-details-content =
    [gray]━━━━━━━━━━━━━━━━━━━━━━━━━[]
    [white]{ $player }[] [gray]#{ $pid }[]
    { "" }
    [accent]■ Evento de auditoria[]
    [gray]Ação: [white]{ $action }[]
    [gray]Autor: [white]{ $actor }[]
    [gray]Motivo: [white]{ $reason }[]
    { "" }
    [accent]■ Tempo[]
    [gray]Ocorrido: [white]{ $occurredAt }[]
    [gray]Duração: [white]{ $duration }[]
    [gray]Expira em: [white]{ $expiresAt }[]
    { "" }
    [accent]■ Metadados[]
    [gray]Audit ID: [white]{ $auditId }[]
audit-menu-unknown-actor = Desconhecido
audit-menu-unknown-target = Desconhecido
audit-menu-reason-unspecified = Não especificado
audit-menu-duration-permanent = Permanente
audit-menu-action-ban = Banimento
audit-menu-action-unban = Desbanimento
audit-menu-action-mute = Silenciamento
audit-menu-action-unmute = Fim do silenciamento
audit-menu-action-warn = Aviso
audit-menu-action-kick = Expulsão
audit-menu-action-note = Nota
audit-menu-action-quarantine = Quarentena
audit-menu-action-unquarantine = Fim da quarentena
player-menu-player-max-rank = Ranking máximo alcançado
player-menu-player-hexed-progress = [gray]Vitórias necessárias para [white]{ $nextRankName }[gray]: [accent]{ $requiredPoints }[]
player-menu-player-no-mode-stats = [gray]sem dados[]
player-menu-player-pvp-summary = [gray]jogos [white]{ $gamesPlayed }[], vitórias [lime]{ $gamesWon }[], [sky]{ $winRate }%[]
player-menu-player-survival-summary = [gray]ondas: máx. [lime]{ $bestWave }[], média [white]{ $averageWave }[] [gray](partidas: { $gamesPlayed })[]
player-menu-player-hexed-summary = [gray]partidas [white]{ $gamesPlayed }[], 1º lugar [lime]{ $gamesWon }[], melhor posição [accent]#{ $bestPlacement }[]
player-menu-time-days = { $value }d
player-menu-time-hours = { $value }h
player-menu-time-minutes = { $value }m
settings-language-label = Idioma: [green]{ $lang }[]
settings-translator-label = Tradutor: [green]{ $lang }[]

badge-menu-title = [orange]{ -xcore } — Emblemas
badge-menu-content =
    [white]Emblema do sistema: [green]{ $systemBadge }[]
    [white]Emblema ativo: [green]{ $activeBadge }[]
    [white]Cor do símbolo: [green]{ $symbolColorMode }[]
badge-menu-empty = Você ainda não desbloqueou nenhum emblema.
badge-menu-row = [white]{ $badge }[] [gray]-[] { $description }
badge-menu-symbol-color-button = Cor do símbolo: [green]{ $mode }[]
badge-menu-symbol-color-title = [orange]{ -xcore } — Cor do símbolo do emblema
badge-menu-symbol-color-content =
    [white]Modo atual: [green]{ $mode }[]
    [lightgray]Escolha como o símbolo do emblema deve ser colorido.
badge-menu-symbol-color-default = Cor padrão do emblema
badge-menu-symbol-color-player-color = Seguir cor do jogador
badge-menu-view-all = Ver todos os emblemas
badge-menu-all-title = [orange]{ -xcore } — Todos os emblemas
badge-menu-all-content = [lightgray]Veja todos os emblemas, seu status e descrições.
badge-menu-all-row = [white]{ $badge }[] [gray]-[] [accent]{ $state }[] [gray]-[] { $description }
badge-clear-button = Remover emblema ativo
badge-state-system = Sistema
badge-state-system-active = Sistema ativo
badge-state-active = Ativo
badge-state-unlocked = Desbloqueado
badge-state-locked = Bloqueado
badge-set-success = [accent]Emblema ativo definido para [green]{ $badge }[].
badge-clear-success = [accent]Emblema ativo removido.
badge-grant-success = [accent]Emblema [green]{ $badge }[] concedido a [green]{ $nickname }[][gray]#{ $pid }[].
badge-revoke-success = [accent]Emblema [green]{ $badge }[] revogado de [green]{ $nickname }[][gray]#{ $pid }[].
badge-already-unlocked = [scarlet]⚠ O emblema [accent]{ $badge }[scarlet] já está desbloqueado.
badge-not-owned = [scarlet]⚠ O jogador não possui o emblema [accent]{ $badge }[scarlet].
error-badge-not-found = [scarlet]⚠ O emblema [accent]{ $badge }[scarlet] não foi encontrado.
error-badge-not-unlocked = [scarlet]⚠ O emblema [accent]{ $badge }[scarlet] não está desbloqueado.
error-badge-not-selectable = [scarlet]⚠ O emblema [accent]{ $badge }[scarlet] não pode ser selecionado manualmente.
badge-admin-name = Admin
badge-admin-description = Emblema automático exibido para administradores.
badge-developer-name = Desenvolvedor
badge-developer-description = Concedido aos desenvolvedores do XCore.
badge-translator-name = Tradutor
badge-translator-description = Concedido àqueles que ajudam na tradução do XCore.
badge-map-maker-name = Criador de mapas
badge-map-maker-description = Concedido aos autores de mapas usados no servidor.
badge-contributor-name = Colaborador
badge-contributor-description = Concedido por contribuições ao XCore ou à comunidade.
badge-bug-finder-name = Caçador de bugs
badge-bug-finder-description = Concedido por relatórios de bugs regulares e de qualidade.
badge-event-winner-name = Vencedor de evento
badge-event-winner-description = Concedido aos vencedores de eventos especiais do servidor.
badge-veteran-name = Veterano
badge-veteran-description = Concedido a jogadores antigos e respeitados.
badge-season-champion-name = Campeão da temporada
badge-season-champion-description = Concedido por terminar no pódio de uma temporada ranqueada.

commands-lb-description = Ativar/desativar o placar de líderes.
commands-lb-success =
    { $leaderboardEnabled ->
    [true] [accent]Placar de líderes [green]ativado.
    *[other] [accent]Placar de líderes [scarlet]desativado.
    }
leaderboard = [blue]Placar de líderes
commands-observer-description = Entra no modo espectador. Isso destrói sua unidade atual e move você para a equipe de espectadores.
commands-rank-description = Mostra seu ranking ou o de outro jogador.
commands-rank-content =
    { $nickname }
    { $rankTag } [accent]{ $rankName }
    [gold]Vitórias: { $points }/{ $requiredPoints }
commands-ranks-description = Mostra informações sobre o sistema de rankings.
commands-ranks-content =
    { $rankTag } [accent]{ $rankName }
    [gold]Requisitos: [grey]{ $requiredPoints } [accent]vitórias[]
commands-ranks-footer = As vitórias aumentam apenas ao derrotar um jogador do seu ranking ou superior.
commands-top-description = Melhores jogadores.
commands-season-description = Temporada ranqueada: tempo restante, sua posição e vencedores da temporada anterior.
commands-matches-description = Suas partidas ranqueadas: resultados, alterações de pontuação e com quem jogou.
commands-pvp-description = Guia do MiniPvP: a rodada, as regras do servidor e como o ranking é calculado.
commands-top-hexed-content = [orange]{ $index }. { $nickname }[accent]: [blue]{ $rankName } [cyan]{ $points } []vitórias
commands-top-pvp-content = [orange]{ $index }. { $nickname }[accent]: [cyan]{ $rating }
top-menu-title = [orange]{ -xcore } — Melhores jogadores: [accent]{ $category }
top-menu-content =
    [lightgray]Selecione um jogador para abrir seu perfil.[]
    [lightgray]Página [green]{ $page }[]/[green]{ $totalPages }[] [gold]•[] [lightgray]Jogadores: [green]{ $totalEntries }[]
    { $selfRankLine }
top-menu-empty =
    [accent]Categoria: [green]{ $category }[]
    [gray]Nenhum jogador encontrado ainda.
top-menu-categories-title = [orange]{ -xcore } — Categoria do placar
top-menu-categories-content =
    [lightgray]Escolha qual classificação exibir.[]
    [lightgray]Atual: [green]{ $category }[]
top-menu-category-button = [accent]Categoria: [green]{ $category }[]
top-menu-category-mini-pvp = MiniPvP
top-menu-category-playtime = Tempo de jogo
top-menu-category-hexed = Hexed
top-menu-self-rank-known = [lightgray]Sua posição: [accent]#{ $rank }[]
top-menu-self-rank-unknown = [lightgray]Sua posição: [gray]não encontrada[]
top-menu-entry-mini-pvp = { $rankLabel } { $leagueIcon } [accent]{ $nickname }[] [gray]—[] [sky]{ $value }[]
top-menu-entry-playtime = { $rankLabel } [accent]{ $nickname }[] [gray]—[] [green]{ $value }[]
top-menu-entry-hexed = { $rankLabel } [accent]{ $nickname }[] [gray]—[] [violet]{ $rankName }[] [gold]•[] [cyan]{ $value }[]
top-menu-total-count = { $count } jogadores
top-menu-btn-find-self = Encontrar a mim
top-menu-on-this-page = nesta página
top-menu-unranked = Você ainda não foi classificado nesta categoria
top-menu-self-rank-line = Sua posição: { $rank }
top-menu-score-points = { $points } pts
top-menu-score-minutes = { $time }

# ==============================================================================
# Game Modes (Hexed, PvP, Surrender, AI)
# ==============================================================================
commands-surrender-description = Renda-se no Hexed. Isso destrói sua equipe atual, remove sua unidade e transfere você para os espectadores.
commands-surrender-success = [green]Você se rendeu e agora está assistindo
commands-observer-success = [green]Você agora está assistindo
commands-observer-exit-success = [green]Você não está mais assistindo
commands-ai-description = Controlar IA.
commands-ai-usage = [red]attack(a) []ou [accent]idle(i)
hexed-popup = [blue]{ DURATION($remaining, style: "timer") }[] até o fim do jogo.
hexed-eliminated = { $nickname } [gold]foi [scarlet]eliminado[]!
hexed-leaderboard-content = [orange]{ $index }. { $nickname }[accent]: [cyan]{ $hexes } [accent]hexágonos
hexed-ranks-newbie = Novato
hexed-ranks-regular = Habitual
hexed-ranks-advanced = Avançado
hexed-ranks-veteran = Veterano
hexed-ranks-davastator = Devastador
hexed-ranks-the_legend = A Lenda
hexed-game-over-header = Fim de jogo. Vencedores:
hexed-game-over-winner-row =
    [orange]{ $index }. { $name }[][accent]: [cyan]{ $cores } { $cores ->
    [one] hexágono
    *[other] hexágonos
    }
hexed-game-over-no-winners = Fim de jogo. Infelizmente, nenhum vencedor foi encontrado.
hexed-game-over-restart = Novo jogo em 10 segundos…
rating_league_scrap = Sucata
rating_league_copper = Cobre
rating_league_lead = Chumbo
rating_league_graphite = Grafite
rating_league_silicon = Silício
rating_league_titanium = Titânio
rating_league_thorium = Tório
rating_league_plastanium = Plastânio
rating_league_phase_fabric = Tecido de Fase
rating_league_surge_alloy = Liga de Sobretensão
pvp-team-won = Sua equipe venceu. Sua pontuação aumentou em { $increased }
pvp-team-lose = Sua equipe perdeu. Sua pontuação diminuiu em { $reduced }
pvp-match-settlement-win = [accent]■ Resultado da partida: [green]Vitória![] Sua pontuação: [stat]{ $oldRating }[] -> [stat]{ $newRating }[] [green](+{ $delta })[] { $leagueIcon } { $leagueName }
pvp-match-settlement-loss = [accent]■ Resultado da partida: [scarlet]Derrota![] Sua pontuação: [stat]{ $oldRating }[] -> [stat]{ $newRating }[] [scarlet]({ $delta })[] { $leagueIcon } { $leagueName }
pvp-match-settlement-draw = [accent]■ Resultado da partida: [yellow]Empate![] Sua pontuação: [stat]{ $oldRating }[] -> [stat]{ $newRating }[] [yellow]({ $delta })[] { $leagueIcon } { $leagueName }
pvp-match-settlement-exempt = [accent]■ Resultado da partida: [lightgray]Isento de alteração de pontuação (tempo ativo insuficiente).[]
pvp-match-settlement-details = [lightgray]Detalhes da partida: [white]/matches[][]
season-ending-soon = [accent]■ [white]{ $ladder }[]: a temporada { $number } terminará em [stat]{ $remaining }[]. Aproveite as últimas partidas!
season-started = [accent]■ [white]{ $ladder }[]: a temporada { $previous } acabou. A temporada { $number } começou!
season-reset-soft = [lightgray]As pontuações foram aproximadas do valor inicial e os contadores de partidas zerados.[]
season-reset-hard = [lightgray]Todos recomeçam a partir da pontuação inicial.[]
season-reset-none = [lightgray]As pontuações foram mantidas; contadores de partidas zerados.[]
season-title = Temporada { $number }
top-menu-scope-current = { $season } [lightgray]· termina em { $remaining }[]
top-menu-scope-past = { $season } [gray]· { $from } – { $to }[]
ladder-profile-standing = { $leagueIcon } [white]{ $league }[] [accent]{ $rating } ELO[]
ladder-profile-headline = [accent]{ $icon }[] [lightgray]{ $ladder }:[] { $standing }
ladder-profile-unplaced = [gray]nenhuma partida ranqueada nesta temporada[]
ladder-profile-matches = [lightgray]Partidas:[] [white]{ $matches }[]
ladder-profile-wins = [lightgray]Vitórias:[] [white]{ $wins }[] [gray]({ $rate }%)[]
ladder-profile-rank = [lightgray]Posição:[] [accent]#{ $rank }[]
ladder-profile-peak = [lightgray]Pico:[] [white]{ $rating }[]
ladder-profile-season = [lightgray]{ $season } · termina em [white]{ $remaining }[][]
ladder-profile-season-closing = [lightgray]{ $season } · apurando os resultados[]
ladder-profile-history = [gray]{ $season } — { $rank }, { $league }, { $rating } ELO[]
season-menu-title = Temporadas ranqueadas
season-menu-card-title = { $ladder } — { $season }
season-menu-ends = [lightgray]Termina em [white]{ $remaining }[] ({ $date })[]
season-menu-closing = [lightgray]A temporada acabou, os resultados estão sendo apurados.[]
season-menu-participants = [lightgray]Jogadores nesta temporada: [white]{ $count }[][]
season-menu-you = [lightgray]Você:[] { $standing }
season-menu-previous = [lightgray]{ $season } — vencedores:[]
season-menu-podium-entry = [gold]{ $place }.[] [white]{ $name }[] [gray]—[] { $league } [accent]{ $rating }[]
season-menu-open-top = Placar de líderes
season-menu-prizes = [lightgray]Prêmios desta temporada:[]
season-menu-prize-entry = [gold]{ $places }.[] [white]{ $prize }[]
season-menu-podium-prizes = [gray] · [gold]{ $prizes }[]
prize-grant-line = [lightgray]{ $season } · prêmio:[] [gold]{ $prize }[] [gray]({ $status })[]
prize-status-pending = aguardando entrega
prize-status-granted = recebido
prize-status-delivered = entregue
prize-status-failed = um admin irá resolver
season-menu-empty = Ainda não há temporadas ranqueadas.
pvp-hud-status = [accent]MiniPvP[] | [stat]Vivos:[] { $teams } | [gray]{ $time }[]
pvp-leaderboard-content = [orange]{ $index }. { $nickname }[accent]:[cyan] { $rating } [accent]pontos
pvp-you-spectator = [scarlet]Você foi eliminado. Aguarde a próxima partida.

# MiniPvP guide (/pvp info)
pvp-guide-title = Como jogar aqui
pvp-guide-subtitle = PvP normal de Mindustry: as equipes constroem bases e destroem os núcleos umas das outras. Aqui você vê o que torna este servidor diferente e como o ranking funciona.
pvp-guide-hint = Abrir novamente: [accent]/pvp info[]
pvp-guide-join-hint = [lightgray]Regras e ranking do MiniPvP:[] [accent]/pvp info[]
pvp-guide-menu-button = Como jogar MiniPvP
pvp-guide-tab-round = Rodada
pvp-guide-tab-rules = Regras
pvp-guide-tab-rating = Ranking
pvp-guide-stat-minutes = minutos jogados
pvp-guide-stat-teams = equipes no jogo
pvp-guide-stat-players = jogadores nas equipes
pvp-guide-stat-zone = blocos ao redor do núcleo
pvp-guide-stat-unitcap = limite de unidades
pvp-guide-stat-banned = blocos banidos
pvp-guide-stat-start = ranking inicial
pvp-guide-stat-top = melhores jogadores
pvp-guide-stat-matches = histórico de partidas

pvp-guide-now-title = Agora mesmo
pvp-guide-now-map = Mapa: [accent]{ $map }[]
pvp-guide-now-players = { $count } { $count ->
[one] jogador
*[other] jogadores
}
pvp-guide-win-title = Como vencer
pvp-guide-win-text = Destrua todos os núcleos inimigos. Vence a última equipe com núcleos e jogadores restantes. Uma equipe sem ninguém é eliminada.
pvp-guide-out-title = Eliminado: assista até o fim
pvp-guide-out-text =
    Sua equipe perdeu o último núcleo: você assiste até a próxima rodada.
    Saiu no meio da rodada e voltou: você retorna para a mesma equipe.
pvp-guide-commands-title = Comandos
pvp-guide-cmd-info = esta janela
pvp-guide-cmd-top = placar de líderes
pvp-guide-cmd-season = temporada e prêmios
pvp-guide-cmd-matches = suas partidas e pontuações
pvp-guide-cmd-rtv = votar para mudar de mapa
pvp-guide-cmd-observer = assistir de fora

pvp-guide-zone-title = Proibido construir perto de núcleos inimigos
pvp-guide-zone-text = Não é possível construir a menos de [accent]{ $tiles }[] blocos de um núcleo inimigo. Para avançar, destrua esse núcleo primeiro.
pvp-guide-zone-polygon-title = O território é dividido entre os núcleos
pvp-guide-zone-polygon-text = Você só pode construir onde o núcleo mais próximo é seu. Para tomar terra inimiga, destrua o núcleo ao qual ela pertence.
pvp-guide-unitcap-title = Limite de unidades
pvp-guide-unitcap-text = A equipe começa com um limite de [accent]{ $cap }[], e cada núcleo o aumenta.
pvp-guide-unitcap-fixed-title = Limite de unidades
pvp-guide-unitcap-fixed-text = A equipe pode ter no máximo [accent]{ $cap }[] unidades, não importa quantos núcleos possua.
pvp-guide-multipliers-title = Balanceamento diferente
pvp-guide-multipliers-text = Neste mapa estes valores diferem do padrão:
pvp-guide-rule-unit-damage = Dano das unidades
pvp-guide-rule-unit-health = Vida das unidades
pvp-guide-rule-unit-cost = Custo das unidades
pvp-guide-rule-unit-build-speed = Velocidade de produção de unidades
pvp-guide-rule-block-damage = Dano das construções
pvp-guide-rule-block-health = Vida das construções
pvp-guide-rule-build-cost = Custo das construções
pvp-guide-rule-build-speed = Velocidade de construção
pvp-guide-banned-blocks-title = Blocos banidos
pvp-guide-banned-units-title = Unidades banidas
pvp-guide-other-title = Além disso
pvp-guide-other-fog = Névoa de guerra: você só enxerga o que suas construções e unidades veem.
pvp-guide-other-capture = Um núcleo destruído passa para a equipe que o destruiu.
pvp-guide-other-reactors = Reatores não explodem.
pvp-guide-vanilla-title = Regras normais
pvp-guide-vanilla-text = Este mapa segue as regras do PvP padrão de Mindustry.

pvp-guide-elo-title = Pontuação ELO
pvp-guide-elo-text = Todos começam com [accent]{ $start }[]. Vencer aumenta sua pontuação, perder diminui. A quantidade depende da pontuação da sua equipe e dos adversários: vencer equipes mais fortes dá mais pontos.
pvp-guide-leave-title = Sair não evita a derrota
pvp-guide-leave-text = Se sua equipe perder, você perde a pontuação inteira, mesmo se tiver saído ou virado espectador antes. Nada é perdido apenas se você tiver jogado menos de [accent]{ $seconds }[] s.
pvp-guide-unrated-title = Quando a pontuação não muda
pvp-guide-unrated-text =
    Uma rodada menor que [accent]{ $seconds }[] s ou com menos de [accent]{ $players }[] jogadores ranqueados não conta.
    O vencedor precisa jogar pelo menos [accent]{ $winner }%[] da rodada, caso contrário a pontuação não sobe.
    Entrou tarde em uma equipe derrotada e jogou menos de [accent]{ $late }%[] da rodada: sua pontuação não cai.
pvp-guide-seasons-title = Ligas e temporadas
pvp-guide-seasons-text = Sua pontuação define sua liga. O ranking segue por temporadas: no final, os melhores recebem prêmios. Resultados de cada partida: [accent]/matches[].

# ==============================================================================
# Events & Notifications
# ==============================================================================
player-joined-pid = { $nickname } [grey]#[white]{ $pid }[grey] [accent]entrou.
player-joined-username = { $nickname } [grey]@[white]{ $username }[grey] [accent]entrou.
player-joined-both = { $nickname } [accent]@{ $username } [grey]#[white]{ $pid }[grey] [accent]entrou.
player-joined-none = { $nickname } [accent]entrou.

player-left-pid = { $nickname } [grey]#[white]{ $pid }[grey] [accent]saiu.
player-left-username = { $nickname } [grey]@[white]{ $username }[grey] [accent]saiu.
player-left-both = { $nickname } [accent]@{ $username } [grey]#[white]{ $pid }[grey] [accent]saiu.
player-left-none = { $nickname } [accent]saiu.

player-settings-identity-mode = [accent]Identificador nos anúncios:[]
player-settings-identity-mode-pid = Apenas ID (#12)
player-settings-identity-mode-username = Apenas nome de usuário (@Steve)
player-settings-identity-mode-both = Ambos (@Steve #12)
player-settings-identity-mode-none = Oculto

notification-votekick-playtime =
    [accent]Parabéns! Você jogou por [lightgray]{ $votekickPlayTime }[] { $votekickPlayTime ->
    [one] minuto
    *[other] minutos
    } e agora pode iniciar votações de expulsão.
notification-global-chat-playtime =
    [accent]Parabéns! Você jogou por [lightgray]{ $globalChatPlayTime }[] { $globalChatPlayTime ->
    [one] minuto
    *[other] minutos
    } e agora pode enviar mensagens no chat global.
    [lightgray]Digite [accent]/g [gray]<mensagem…>[lightgray] para enviar uma mensagem.
notification-admin-kick = { $admin }[accent] expulsou { $target }[].
notification-admin-wave-skip = { $admin }[accent] pulou a onda.
server-restart-countdown =
    Reiniciando em { $seconds ->
    [one] { $seconds } segundo
    *[other] { $seconds } segundos
    }
like-map-success = [green]Você curtiu este mapa!
like-map-changed = [green]Você mudou sua opinião para curtir!
dislike-map-success = [orange]Você não curtiu este mapa.
dislike-map-changed = [orange]Você mudou sua opinião para não curtir.
like-event-success = [green]Você curtiu este evento!
like-event-changed = [green]Você mudou sua opinião para curtir!
dislike-event-success = [orange]Você não curtiu este evento.
dislike-event-changed = [orange]Você mudou sua opinião para não curtir.

# ==============================================================================
# Events (Server)
# ==============================================================================
commands-event-description = Menu de gerenciamento de eventos.
commands-events-description = Lista de todos os eventos nos servidores.
event-events = Eventos
event-menu-main = Eventos principais
event-menu-event = Evento
event-menu-create-start-default = Evento de { $playerName }
event-menu-create-start-map = Criar evento para este mapa
event-ui-title = Eventos
event-ui-server-time = Horário do servidor: { $zone }
event-ui-tab-upcoming = Em breve
event-ui-tab-major = Grandes
event-ui-tab-past = Anteriores
event-ui-tab-mine = Meus
event-ui-now-running = Em andamento
event-ui-now-vote = Votação
event-ui-empty-upcoming = Nenhum evento planejado ainda. Crie o primeiro!
event-ui-empty-major = Nenhum grande evento planejado ainda.
event-ui-empty-past = Nenhum evento foi finalizado ainda.
event-ui-empty-mine = Você ainda não criou nenhum evento.
event-ui-create = Criar evento
event-ui-load-failed = Não foi possível carregar os eventos. Tente novamente.
event-ui-state-active = Em andamento
event-ui-state-active-until = Em andamento até { $time }
event-ui-state-finished = Finalizado
event-ui-state-finished-at = Finalizado em { $date }
event-ui-state-unscheduled = Sem data definida
event-ui-major = Grande
event-ui-major-hint = Marcado com uma estrela e reunido em uma aba própria
event-ui-temporary = Temporário
event-ui-temporary-hint = Termina automaticamente no horário de término
event-ui-about = Sobre o evento
event-ui-no-description = Sem descrição.
event-ui-author = Autor: { $author }
event-ui-created = Criado em: { $date }
event-ui-map-schedule = Mapa e cronograma
event-ui-map-none = Nenhum mapa
event-ui-map-pick = Escolha um mapa
event-ui-planned-start = Início: { $time }
event-ui-planned-end = Término: { $time }
event-ui-started = Começou: { $time }
event-ui-ended = Terminou: { $time }
event-ui-not-set = não definido
event-ui-open-map = Abrir mapa
event-ui-rating = Avaliação
event-ui-launch = Iniciar
event-ui-launch-hint = Os próprios jogadores iniciam um evento por votação. Começa na próxima mudança de mapa.
event-ui-launch-again = Este evento já foi realizado. Pode ser iniciado novamente.
event-ui-launch-active = O evento está em andamento agora.
event-ui-launch-voting = Votação em andamento: { $votes } de { $required }. Digite y ou n no chat.
event-ui-launch-busy = Outra votação está em andamento. Aguarde o término dela.
event-ui-vote = Iniciar votação
event-ui-vote-busy = Já há uma votação em andamento.
event-ui-vote-stopped = A votação foi interrompida.
event-ui-force = Iniciar imediatamente
event-ui-force-confirm = Deseja realmente iniciar?
event-ui-stop = Parar evento
event-ui-stop-confirm = Deseja realmente parar?
event-ui-stop-vote = Parar votação
event-ui-stopped = O evento foi interrompido.
event-ui-to-list = Para a lista
event-ui-edit = Alterar
event-ui-editor-new = Novo evento
event-ui-editor-edit = Editar evento
event-ui-texts = Nome e descrição
event-ui-name = Nome
event-ui-description = Descrição
event-ui-description-hint = Sobre o que é o evento
event-ui-map = Mapa
event-ui-schedule = Cronograma e tipo
event-ui-start = Início
event-ui-end = Fim
event-ui-save = Salvar
event-ui-saved = O evento foi salvo.
event-ui-draft-restored = Seu rascunho de evento foi restaurado.
event-ui-maps-title = Escolher mapa
event-ui-to-editor = Para o editor
event-ui-time-start = Início do evento
event-ui-time-end = Fim do evento
event-ui-time-unset = Não definido
event-ui-time-unset-hint = Ajuste com as setas ou com um botão abaixo
event-ui-time-hours = { $value } h
event-ui-time-minutes = { $value } min
event-ui-time-now = Agora
event-ui-time-in-hour = Em 1 hora
event-ui-time-today-evening = Hoje às 20:00
event-ui-time-tomorrow-noon = Amanhã às 12:00
event-ui-time-plus-hour = +1 h
event-ui-time-plus-three-hours = +3 h
event-ui-time-plus-day = +1 dia
event-ui-time-apply = Concluído
event-ui-time-clear = Limpar
event-ui-error-no-map = Escolha um mapa primeiro.
event-ui-error-no-end = Um evento temporário precisa de um horário de término.
event-ui-error-end-before-start = O término deve ser posterior ao início.
event-ui-error-not-allowed = Você não tem permissão para alterar este evento.
event-ui-error-gone = Este evento não existe mais.
event-ui-error-save-failed = Não foi possível salvar o evento. Tente novamente.
vote-event-vote =
    { $nickname }[lightgray] votou para mudar o evento atual para [orange]{ $name }[lightgray]. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
    Digite [orange]y[] ou [orange]n[] para votar.
vote-event-left = { $nickname }[lightgray] saiu. Seu voto para mudar o evento foi cancelado. ([accent]{ $votes }[]/[accent]{ $votesRequired }[])
vote-event-fail = [lightgray]Votação falhou. Votos insuficientes para mudar o evento para [orange]{ $name }[].
vote-event-success = [orange]Votação aprovada. O evento [accent]{ $name }[] será carregado na próxima mudança de mapa.
vote-event-cancelled = [lightgray]A votação para mudar o evento para [orange]{ $name }[lightgray] foi cancelada pelo administrador { $admin }.
event-end = O evento [green]{ $name }[] terminou!

# ==============================================================================
# Errors
# ==============================================================================
error-access-denied = [scarlet]⚠ Acesso negado.
error-ip-changed = [scarlet]⚠ Seu endereço IP mudou. Os privilégios de admin foram revogados.
error-not-enough-params = [scarlet]⚠ Parâmetros de posição insuficientes.
error-player-not-found = [scarlet]Jogador não encontrado.
error-player-not-teammate = [scarlet]⚠ Esse jogador não está na sua equipe.
error-player-admin = [scarlet]⚠ Não tente expulsar um admin. ⚠
error-already-voted = [scarlet]⚠ Você já votou. Acalme-se.
error-playtime-requirement =
    [scarlet]⚠ Você precisa jogar por pelo menos { $time } { $time ->
    [one] minuto
    *[other] minutos
    } para usar este recurso.
error-globalchat-total-playtime =
    [scarlet]⚠ Para enviar mensagens no chat global você precisa jogar por { $globalChatPlayTime } { $globalChatPlayTime ->
    [one] minuto
    *[other] minutos
    }.
error-votekick-total-playtime =
    [scarlet]⚠ Para iniciar uma votação de expulsão você precisa jogar por { $votekickPlayTime } { $votekickPlayTime ->
    [one] minuto
    *[other] minutos
    }.
error-vote-yourself = [scarlet]⚠ Você não pode votar na sua própria votação.
error-vote-in-progress = [scarlet]⚠ Uma votação já está em andamento.
error-no-voting = [scarlet]⚠ Não há nenhuma votação no momento.
error-wave-vote-unavailable = [scarlet]⚠ Iniciar uma nova onda mais cedo só é possível em modos com ondas.
error-no-map = [scarlet]⚠ Nenhum mapa definido.
error-map-not-event = [scarlet]⚠ O mapa não faz parte do evento atual.
error-map-not-found = [scarlet]⚠ Mapa não encontrado! [accent]Use [cyan]/maps[] para ver todos os mapas disponíveis.
error-maps-empty = [scarlet]⚠ A lista de mapas está vazia.
error-event-not-found = [scarlet]⚠ Evento não encontrado! [accent]Use [cyan]/events[] para ver os eventos disponíveis.
error-page-between = [scarlet]⚠ 'page' deve ser um número entre[orange] 1[] e [orange]{ $totalPages }[].
error-page-number = [scarlet]'page' deve ser um número.
error-wrong-number = [scarlet]⚠ Formato de número incorreto.
error-wrong-period-format = [scarlet]⚠ Formato de duração incorreto. Exemplo: 1h 30m, 30 ({ hours })
error-invalid-id = [scarlet]⚠ ID de jogador inválido.
error-spectator = [scarlet]⚠ Você é um espectador e não pode usar este comando.
error-admin-password-too-short = [scarlet]⚠ A senha de administrador deve ter pelo menos 8 caracteres.
error-wrong-admin-password = [scarlet]⚠ Senha de admin incorreta.
error-internal = [scarlet]Erro interno.
error-processing-request = [scarlet]Ocorreu um erro ao processar a solicitação.
error-no-access = [scarlet]⚠ Sem acesso.
error-nickname-too-long = [scarlet]⚠ Nome de usuário muito longo. Máximo de { $max } caracteres visíveis.
error-private-message-invalid-pid = [scarlet]⚠ PID de mensagem privada inválido. Use o formato [lightgray]#123[].
error-private-message-self = [scarlet]⚠ Você não pode enviar uma mensagem privada para si mesmo.
error-private-message-empty = [scarlet]⚠ A mensagem não pode estar vazia.
error-private-message-too-long = [scarlet]⚠ A mensagem é muito longa. Máximo de { $max } caracteres.
error-private-message-cooldown = [scarlet]⚠ Aguarde { DURATION($seconds) } antes de enviar outra mensagem privada.
error-private-message-target-unavailable = [scarlet]⚠ Este jogador não pode receber mensagens privadas no momento.
error-private-message-no-reply-target = [scarlet]⚠ Nenhum contato recente para responder.
error-private-message-not-found = [scarlet]⚠ Mensagem não encontrada.
error-private-message-block-self = [scarlet]⚠ Você não pode bloquear a si mesmo.
error-private-message-block-limit = [scarlet]⚠ Limite da lista de bloqueados atingido ({ $limit }).
ban-menu-duration-title = [orange]{ -xcore } - Duração do banimento
ban-menu-duration-message = Digite a duração do banimento para { $nickname }. Exemplo: 1d, 12h, 30m
ban-menu-reason-title = [orange]{ -xcore } - Motivo do banimento
ban-menu-reason-message = Digite o motivo do banimento para { $nickname }. Deixe em branco para o motivo padrão.
ban-menu-confirm-title = [orange]{ -xcore } - Confirmar banimento
ban-menu-confirm-content =
    [white]Jogador: { $nickname }[]
    [white]Duração: [accent]{ $duration }[]
    [white]Motivo: [accent]{ $reason }[]
ban-menu-confirm-action = [scarlet]Banir jogador
error-invalid-syntax = [scarlet]⚠ Sintaxe de comando inválida. Uso: [lightgray]/'{ $syntax }'.
error-invalid-sender = [scarlet]⚠ Emissor de comando inválido. Este comando requer: '[lightgray]{ $type }[]'.
error-argument-parse-generic = [scarlet]⚠ Argumento inválido: '{ $error }'.
exception-unexpected = [scarlet]⚠ Ocorreu um erro interno ao executar este comando.
exception-invalid-argument = [scarlet]⚠ Argumento de comando inválido: '{ $cause }'.
exception-no-such-command = [scarlet]⚠ Comando desconhecido.
exception-no-permission = [scarlet]⚠ Acesso negado.
exception-invalid-sender = [scarlet]⚠ '{ $actual }' não pode executar este comando. Emissor necessário: [lightgray]{ $expected }[].
exception-invalid-sender-list = [scarlet]⚠ '{ $actual }' não pode executar este comando. Emissores permitidos: [lightgray]{ $expected }[].
exception-invalid-syntax = [scarlet]⚠ Sintaxe de comando inválida. Uso: [lightgray]/'{ $syntax }'.
argument-parse-failure-boolean = [scarlet]⚠ Não foi possível ler um valor booleano de '{ $input }'.
argument-parse-failure-number = [scarlet]⚠ '{ $input }' não é um número válido dentro do intervalo [{ $min }, { $max }].
argument-parse-failure-char = [scarlet]⚠ '{ $input }' não é um caractere válido.
argument-parse-failure-enum = [scarlet]⚠ '{ $input }' não é uma opção válida. Permitido: [lightgray]{ $acceptableValues }
argument-parse-failure-string = [scarlet]⚠ Formato de texto inválido para '{ $input }'.
argument-parse-failure-uuid = [scarlet]⚠ Formato de UUID inválido: '{ $input }'.
argument-parse-failure-regex = [scarlet]⚠ A entrada '{ $input }' não corresponde ao padrão '{ $pattern }'.
argument-parse-failure-color = [scarlet]⚠ '{ $input }' não é uma cor válida.
argument-parse-failure-duration = [scarlet]⚠ '{ $input }' não é um formato de duração válido.
argument-parse-failure-aggregate-missing = [scarlet]⚠ Componente ausente '{ $component }'.
argument-parse-failure-aggregate-failure = [scarlet]⚠ Componente inválido '{ $component }': '{ $failure }'.
argument-parse-failure-either = [scarlet]⚠ Não foi possível resolver { $primary } ou { $fallback } a partir de '{ $input }'.
argument-parse-failure-flag-unknown = [scarlet]⚠ Opção desconhecida: '{ $flag }'.
argument-parse-failure-flag-duplicate = [scarlet]⚠ Opção duplicada: '{ $flag }'.
argument-parse-failure-flag-duplicate-flag = [scarlet]⚠ Opção duplicada: '{ $flag }'.
argument-parse-failure-flag-no-flag-started = [scarlet]⚠ Nenhuma opção iniciada. Não se sabe o que fazer com '{ $input }'.
argument-parse-failure-flag-missing-argument = [scarlet]⚠ Argumento ausente para a opção: '{ $flag }'.
argument-parse-failure-flag-no-permission = [scarlet]⚠ Você não tem permissão para usar a opção '{ $flag }'.
argument-parse-failure-selector-syntax = [scarlet]⚠ Seletor inválido '{ $input }': [lightgray]{ $reason }
argument-parse-failure-selector-no-such-target = [scarlet]⚠ Nada corresponde a '{ $input }'.
argument-parse-failure-selector-too-many-targets = [scarlet]⚠ '{ $input }' correspondeu a vários alvos, mas este comando precisa de apenas um.
argument-parse-failure-selector-denied = [scarlet]⚠ Seletores não são permitidos aqui: [lightgray]{ $reason }
argument-parse-failure-selector-kind-not-allowed = [scarlet]⚠ O seletor '{ $kind }' não é permitido aqui.
argument-parse-failure-selector-sender-required = [scarlet]⚠ O seletor '{ $kind }' só pode ser usado no jogo.
argument-parse-failure-selector-limit-exceeded = [scarlet]⚠ O seletor encontrou { $count } alvos, o limite é { $limit }.
argument-parse-failure-team = [scarlet]⚠ Equipe '{ $input }' não encontrada.
argument-parse-failure-content = [scarlet]⚠ '{ $input }' não é um { $type } válido.

# ==============================================================================
# Button Status
# ==============================================================================
finished = finalizado
finished-neutral = [orange]Finalizados
finished-active = [green]Finalizados
finished-inactive = [red]Finalizados
major = Principal
major-neutral = [orange]Principais
major-active = [green]Principais
major-inactive = [red]Principais
active = Ativo
active-neutral = [orange]Ativos
active-active = [green]Ativos
active-inactive = [red]Ativos
admin = Admin
admin-neutral = [orange]Admin
admin-active = [green]Admin
admin-inactive = [red]Admin
player-leaderboard-active = [green]Placar de líderes: ativado[]
player-leaderboard-inactive = [red]Placar de líderes: desativado[]

# ==============================================================================
# Miscellaneous
# ==============================================================================
hours = horas
days = dias
success = [green]Sucesso
empty = [accent]Vazio
never = Nunca
save = Salvar
close = [scarlet]Fechar
previous = [accent]« Anterior
next = [accent]Próximo »
cancel = Cancelar
back = Voltar
yes = Sim
no = Não
test = Teste
no-description = Sem descrição
site = Site
discord = Discord
github = Github
donatello = Donatello
weblate = Weblate
discord-red-vs-blue = RedVSBlue
auto = Auto
on = Ativado
off = Desativado
error-command-disabled = [scarlet]⚠ O comando [accent]/{ $command }[scarlet] está desativado neste servidor.
error-feature-disabled = [scarlet]⚠ Este recurso está desativado neste servidor.
none = Nenhum
unknown = Desconhecido
error-nickname-badge-glyph = [scarlet]⚠ O nome personalizado não pode conter ícones de emblemas reservados.

# Server browser (/servers)
player-servers-title = REDE DE SERVIDORES XCORE
player-servers-cat-all = Todos
player-servers-cat-pvp = PvP
player-servers-cat-survival = Sobrevivência
player-servers-cat-special = Especial
player-servers-hint = Toque no cartão de um servidor para conectar
player-servers-refresh = Atualizar
player-servers-already-connected = [gold]● Você já está conectado a este servidor!
player-servers-transferring = [accent]Transferindo para o servidor [white]{ $server }[]...
player-servers-full = [scarlet]O servidor { $server } está cheio! Aguarde uma vaga livre.
player-servers-offline = [scarlet]O servidor { $server } está offline no momento.
player-servers-not-found = [scarlet]Servidor '{ $server }' não encontrado.
player-servers-empty-category = [lightgray]Nenhum servidor disponível nesta categoria.[]
player-servers-online-summary = [green]● { $players } [gray]jogando[] [darkgray]|[] [sky]{ $servers } [gray]online[]
player-servers-badge-current = [gold]● VOCÊ ESTÁ AQUI[]
player-servers-offline-badge = [darkgray]● OFFLINE[]
player-servers-capacity-full = [scarlet]● { $players }/{ $max } LOTADO[]
player-servers-capacity-normal = [green]● { $players }/{ $max } { $bar }
player-servers-card-current = [lightgray]Você está conectado a este servidor[]
player-servers-card-wave = [darkgray]|[] [accent]Onda { $wave }[]
player-servers-card-empty = [sky]Seja o primeiro! Inicie uma partida[]
player-servers-card-map = [gray]Mapa:[] [white]{ $map }[]
player-servers-card-mode = [gray]Modo:[] [white]{ $mode }[]

announcement-hub =
    [gold]★ [accent]Servidores XCore [lightgray]» [white]Cansado desta partida?
    [lightgray]Explore outros servidores a qualquer momento com [accent]/hub[lightgray]!
announcement-discord =
    [gold]★ [accent]Comunidade XCore [lightgray]» [white]Procurando aliados e novidades?
    [lightgray]Entre no nosso servidor do Discord com [accent]/discord[lightgray]!
announcement-help =
    [gold]★ [accent]Ajuda XCore [lightgray]» [white]Precisa de ajuda ou da lista de comandos?
    [lightgray]Digite [accent]/help[lightgray] para ver todos os comandos disponíveis!
announcement-report =
    [gold]★ [accent]Denúncias XCore [lightgray]» [white]Alguém está fazendo griefing ou violando regras?
    [lightgray]Denuncie com [accent]/report[lightgray] e a equipe irá analisar!

error-only-players = [scarlet]⚠ Este comando só pode ser usado por jogadores.

player-settings-username-editable = [gold]★ Nome de usuário (recompensa de evento):[]
player-settings-username-hint = Digite um nome de usuário único (4–32 caracteres)...
player-settings-username-locked = [gray]Nome de usuário:[] [accent]@{ $username }[] [darkgray](Bloqueado)[]
player-settings-username-none = [gray]Nome de usuário: [darkgray]Não definido (vença um evento para desbloquear)[]

error-username-empty = O nome de usuário não pode estar vazio!
error-username-length = O nome de usuário deve ter entre 4 e 32 caracteres!
error-username-invalid-chars = O nome de usuário só pode conter letras latinas, números e sublinhados!
error-username-taken = Este nome de usuário já está sendo usado por outro jogador!

# ==============================================================================
# Permission nodes
# ==============================================================================
permission-mindustry-admin = Usar o menu de admin integrado do jogo e pular ondas
permission-xcore-moderation-mute = Silenciar jogadores
permission-xcore-moderation-unmute = Remover silenciamento de jogadores
permission-xcore-moderation-kick = Expulsar jogadores
permission-xcore-moderation-ban = Banir jogadores
permission-xcore-moderation-unban = Desbanir jogadores
permission-xcore-moderation-audit-others = Ver o histórico de moderação de outros jogadores
permission-xcore-moderation-votekick-immune = Não pode ser expulso por votação
permission-xcore-admin-tp = Teletransportar jogadores
permission-xcore-admin-broadcast = Enviar anúncios para todos
permission-xcore-admin-kill = Matar unidades e jogadores
permission-xcore-admin-heal = Curar unidades e jogadores
permission-xcore-admin-set-team = Alterar a equipe de um jogador
permission-xcore-maps-force-rtv = Mudar de mapa sem votação
permission-xcore-maps-force-vnw = Pular uma onda sem votação
permission-xcore-votes-cancel = Cancelar uma votação em andamento
permission-xcore-events-create-major = Criar eventos principais
permission-xcore-events-edit-others = Editar eventos criados por outros jogadores
permission-xcore-events-force-vote = Iniciar um evento sem votação
permission-xcore-events-stop = Parar o evento em andamento
permission-xcore-players-settings-others = Alterar as configurações de outros jogadores
permission-xcore-players-private-info = Ver dados privados de jogadores, como endereços IP
permission-xcore-bypass-playtime = Ignorar requisitos de tempo de jogo para comandos
permission-xcore-permissions-inspect = Ver quem possui quais permissões
permission-xcore-permissions-manage = Alterar cargos e permissões

error-target-outranks = [scarlet]⚠ Não é possível fazer isso com um jogador cujo cargo não seja inferior ao seu.
perm-me-legacy = [accent]Cargos desativados neste servidor. Admin: [white]{ $admin }
perm-me-header = [accent]Seus cargos aqui (peso [white]{ $weight }[accent]):
perm-me-none = [lightgray] - nenhum
perm-me-logged-in = [green]Você está conectado como membro da equipe.
perm-me-not-logged-in = [yellow]Você não está conectado como membro da equipe. Use [white]/login <senha>[yellow].
perm-me-stale = [scarlet]Seus direitos de equipe estão pausados: o servidor não conseguiu atualizá-los. Eles retornarão sozinhos.

help-ui-search-hint = Buscar comandos, apelidos ou descrições
help-ui-search-empty = [lightgray]Nenhum comando encontrado para esta busca.[]

match-history-title = Histórico de partidas
match-history-rank = #{ $rank } nesta temporada
match-history-form = Últimas { $count }:
match-history-empty = Suas partidas ranqueadas aparecerão aqui.
match-history-since = O histórico é registrado desde { $date }.
match-history-load-failed = Não foi possível carregar o histórico de partidas. Tente novamente em instantes.
match-history-back-to-list = Voltar à lista
match-history-your-result = Seu resultado
match-history-places = Posições
match-history-team-title = #{ $place } · { $team } · média { $average }
match-history-time-now = agora mesmo
match-history-time-minutes = há { $count } min
match-history-time-hours = há { $count } h
match-history-time-yesterday = ontem
match-history-outcome-win = [lime]Vitória[]
match-history-outcome-loss = [scarlet]Derrota[]
match-history-outcome-unrated = [lightgray]Sem pontuação[]
match-history-outcome-uncounted = [lightgray]Não contabilizada[]
match-history-lineup = { $own } contra { $other }
match-history-team-place = #{ $place } de { $teams } equipes
match-history-place = #{ $place } de { $players }
match-history-counted = Contabilizada: { $reason }
match-history-not-counted = Não contabilizada: { $reason }
match-history-counted-share = Contabilizada para { $percent }% da partida
match-history-reason-winner = vitória
match-history-reason-defeated = derrota
match-history-reason-short-play = jogou menos da metade da partida
match-history-reason-late-join = entrou bem no final
match-history-reason-left-at-once = saiu quase imediatamente
match-history-reason-match-unrated = a partida não pontuava
match-history-reason-unknown = nenhum motivo registrado
match-history-skip-not-enough-players = poucos jogadores
match-history-skip-admin-stop = parada por um admin
match-history-skip-technical-error = erro técnico
match-history-skip-unknown = sem pontuação
match-history-finish-surrender = por rendição
match-history-finish-timeout = por limite de tempo
match-history-finish-admin-stop = parada por um admin
match-history-finish-technical-error = erro técnico

# Discord submission attribution; the declared creator is supplied by the uploader.
map-upload-file-author = Autor no arquivo: { $author }
map-upload-creator = Autor indicado: { $person }
map-upload-submitter = Adicionado pelo Discord: { $person }
map-upload-date = Enviado em: [lightgray]{ $date }[] (UTC)

map-upload-current-creator = [accent]{ $map }[] — { $creator }