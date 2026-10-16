# The report flow. Every message takes the same arguments in en, ru and uk.

# The panel button and its form (labels: 45 characters at most).
reports-panel = Пожаловаться на игрока
reports-field-who = ID игрока или точный ник
reports-field-what = Что произошло?
reports-field-when = Когда (необязательно)
reports-field-where = Сервер или карта (необязательно)

# The staff card.
reports-card-reported-as = Указан как
reports-card-unresolved = Игрока определить не удалось. Для бана или мута понадобится его ID.
reports-card-reason = Причина жалобы
reports-card-comment = Описание от заявителя
reports-card-when = Когда
reports-card-where = Где
reports-card-context = В игре
reports-card-in-force = Действует сейчас
reports-card-reporters = Заявителей: { $count }
reports-card-more = …и ещё { $count }
reports-online = Игрок на сервере
reports-offline = Игрок вышел с сервера
reports-no-live = Чата и истории блоков нет: когда подали жалобу, игрока не было на сервере.
reports-same-network = Та же сеть, что у заявителя
reports-current-ban = Бан до { $until }
reports-current-ban-permanent = Бессрочный бан
reports-current-mute = Мут до { $until }

# Reasons a player picks in the game.
reports-reason-griefing = Гриферство
reports-reason-toxicity = Оскорбления в чате
reports-reason-cheating = Читы
reports-reason-nsfw = Недопустимый контент
reports-reason-evasion = Обход наказания
reports-reason-other = Другое

# Reasons staff pick when rejecting.
reports-reject-no-violation = Нарушения нет
reports-reject-no-evidence = Недостаточно доказательств
reports-reject-already-handled = Меры уже приняты
reports-reject-duplicate = Дубликат
reports-reject-abuse = Злоупотребление жалобами

# What the reporter is told. Never who decided, never what the sanction was.
reports-outcome-action = Ваша жалоба #{ $number } рассмотрена, меры приняты. Спасибо.
reports-outcome-no-violation = Ваша жалоба #{ $number } рассмотрена: нарушений правил не выявлено.
reports-outcome-no-evidence = Ваша жалоба #{ $number } рассмотрена: доказательств не хватило, чтобы принять меры.
reports-outcome-already-handled = Ваша жалоба #{ $number } рассмотрена: по этому случаю меры уже приняты.
reports-outcome-duplicate = Ваша жалоба #{ $number } закрыта: она повторяет поданную раньше.
reports-outcome-abuse = Ваша жалоба #{ $number } отклонена: она сама нарушает правила.
reports-outcome-closed = Ваша жалоба #{ $number } рассмотрена и закрыта без принятия мер.

# Ban, Mute, History and Refresh.
reports-form-pid = ID игрока
reports-form-length = Срок (30m, 12h, 7d)
reports-case-reference = дело #{ $number }
reports-confirm-ban = Забанить игрока { $player } на { $duration }?
reports-confirm-mute = Выдать мут игроку { $player } на { $duration }?
reports-error-other-player = Этот ID принадлежит другому игроку, а не тому, на кого подана жалоба.
reports-history-title = Последние записи об игроке { $player }:
reports-history-empty = Записей об этом игроке нет.
reports-refreshed = Чат, история блоков и наказания обновлены.
reports-refreshed-offline = Игрока нет на сервере: обновлены только наказания.

# Lines for staff in the case post and the forum.
reports-merged = +1 жалоба (всего { $total }) от { $reporter }: { $reason }
reports-external-banned = Игрока забанили вне этого дела ({ $name }). Жалоба закрыта: меры приняты.
reports-external-muted = Игроку выдали мут вне этого дела ({ $name }). Жалоба закрыта: меры приняты.
reports-burst-title = Приём жалоб ограничен
reports-burst-alert = Достигнут предел дел по жалобам из игры: { $limit } за { $minutes } мин. Пока поток не спадёт, новые жалобы из игры не принимаются; жалоба на игрока, по которому дело уже открыто, по-прежнему добавляется к нему.
