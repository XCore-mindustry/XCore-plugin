## Привязка аккаунта Discord к игровому: /link, /link-status, /unlink и код, отправленный боту
## в личные сообщения. Идентификаторы начинаются с "link-" или "unlink-".

link-code-required = Укажите код привязки.
link-code-not-found = Код привязки не найден.
link-code-expired = Срок действия этого кода привязки истёк.
link-player-not-found = Игрок с этим кодом не найден.
link-taken = Этот аккаунт Mindustry уже привязан к другому аккаунту Discord.
link-done = Аккаунт `{ $name }` (`pid={ $pid }`) привязан. Можно возвращаться в игру.
link-server-offline = Сервер `{ $server }`, выдавший этот код, недоступен. Повторите, когда он вернётся, или получите новый код в игре.
link-rejected = `{ $server }` отказал в привязке `{ $name }` (`pid={ $pid }`): `{ $reason }`.
link-failed = Не удалось привязать `{ $name }` (`pid={ $pid }`) на `{ $server }`: `{ $reason }`. Если код больше не работает, получите новый в игре.
link-unknown = `{ $server }` не подтвердил привязку `{ $name }` (`pid={ $pid }`), поэтому неизвестно, состоялась ли она. Проверьте /link-status, прежде чем повторять.
link-status-none = К этому аккаунту Discord не привязан ни один аккаунт Mindustry.
link-status-title = Привязанные аккаунты Mindustry
unlink-not-yours = Этот игрок не привязан к вашему аккаунту Discord.
unlink-no-uuid = Не удалось отвязать игрока: в его данных нет UUID.
unlink-no-server = Нет доступного сервера, чтобы отвязать `{ $name }`. Попробуйте позже.
unlink-done = `{ $name }` отвязан от вашего аккаунта Discord.
unlink-rejected = `{ $server }` отказал в отвязке `{ $name }`: `{ $reason }`.
unlink-failed = Не удалось отвязать `{ $name }` на `{ $server }`: `{ $reason }`.
unlink-unknown = `{ $server }` не подтвердил отвязку `{ $name }`, поэтому неизвестно, состоялась ли она. Проверьте /link-status, прежде чем повторять.
