## Прив'язка акаунта Discord до ігрового: /link, /link-status, /unlink і код, надісланий ботові
## в особисті повідомлення. Ідентифікатори починаються з "link-" або "unlink-".

link-code-required = Вкажіть код прив'язки.
link-code-not-found = Код прив'язки не знайдено.
link-code-expired = Термін дії цього коду прив'язки минув.
link-player-not-found = Гравця з цим кодом не знайдено.
link-taken = Цей акаунт Mindustry вже прив'язано до іншого акаунта Discord.
link-done = Акаунт `{ $name }` (`pid={ $pid }`) прив'язано. Можна повертатися до гри.
link-server-offline = Сервер `{ $server }`, що видав цей код, недоступний. Повторіть, коли він повернеться, або отримайте новий код у грі.
link-rejected = `{ $server }` відмовив у прив'язці `{ $name }` (`pid={ $pid }`): `{ $reason }`.
link-failed = Не вдалося прив'язати `{ $name }` (`pid={ $pid }`) на `{ $server }`: `{ $reason }`. Якщо код більше не працює, отримайте новий у грі.
link-unknown = `{ $server }` не підтвердив прив'язку `{ $name }` (`pid={ $pid }`), тому невідомо, чи вона відбулася. Перевірте /link-status, перш ніж повторювати.
link-status-none = До цього акаунта Discord не прив'язано жодного акаунта Mindustry.
link-status-title = Прив'язані акаунти Mindustry
unlink-not-yours = Цей гравець не прив'язаний до вашого акаунта Discord.
unlink-no-uuid = Не вдалося відв'язати гравця: у його даних немає UUID.
unlink-no-server = Немає доступного сервера, щоб відв'язати `{ $name }`. Спробуйте пізніше.
unlink-done = `{ $name }` відв'язано від вашого акаунта Discord.
unlink-rejected = `{ $server }` відмовив у відв'язці `{ $name }`: `{ $reason }`.
unlink-failed = Не вдалося відв'язати `{ $name }` на `{ $server }`: `{ $reason }`.
unlink-unknown = `{ $server }` не підтвердив відв'язку `{ $name }`, тому невідомо, чи вона відбулася. Перевірте /link-status, перш ніж повторювати.
