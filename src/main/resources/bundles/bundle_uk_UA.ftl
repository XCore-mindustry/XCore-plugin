commands-help-params = [сторінка]
commands-help-description = Показує список усіх команд.
commands-help-start-content = [orange]-- Сторінка Команд[lightgray] {$page}[gray]/[lightgray]{$totalPages}[orange] --
commands-help-content = [orange] /{$commandName}[white] {$commandParams}[lightgray] - {$commandDescription}
commands-information-params = ${""}
commands-information-description = Показати інформацію про сервер
commands-info-title = [orange]XCore сервер — {$xcorServerName}
commands-info-text = [accent]XCore[white] це [cyan]безплатний[white] сервер для гри у [accent]Mindustry[white].
    {""}
    {""}Версія XCore — [accent]{$xcoreVersion}[white]
commands-t-params = <повідомлення...>
commands-t-description = Надіслати повідомлення тільки своїм союзникам по команді.
commands-g-params = <повідомлення...>
commands-g-description = Надіслати повідомлення на всі сервери.
commands-t-chat = [{"#"}{$color}][Команді] [coral]>[accent] {$name}[coral]:[white] {$message}
commands-a-params = <повідомлення...>
commands-a-description = Надіслати повідомлення тільки адміністраторам.
commands-sync-params = {""}
commands-sync-description = Синхронізувати гру з сервером. Використовуйте це для виправлення помилок (наприклад, фантомних юнітів).
commands-discord-params = {""}
commands-discord-description = Перенаправляє вас на наш Discord сервер.
commands-js-params = <код...>
commands-js-description = Виконати JavaScript. [scarlet]Тільки для користувачів з доступом до JS.
commands-js-denied = [scarlet]⚠ Доступ заборонено
commands-artv-params = [карта...]
commands-artv-description = Примусово змінити карту. [scarlet]Тільки для адмінів.
commands-artv-map-skipped = {$nickname}[accent] пропустив карту.
commands-rtv-params = [карта...]
commands-rtv-description = Голосування за зміну карти (Rock the vote).
commands-stats-params = [id-гравця]
commands-stats-description = Переглянути статистику гравця.
commands-stats-content = Статистика гравця {$nickname} [grey]#{$pid}
    {""}[brown]Час у грі: [grey]{$totalPlayTime}[] хвилин
    Ранг у Hexed: [grey]{$hexedRankTag} {$hexedRankName}
    Рейтинг MiniPvP: {$pvpRating}
commands-history-params = [розмір] [x] [y]
commands-history-description = Увімкнути/вимкнути історію блоків.
commands-history-success = [accent]Історію блоків встановлено на [scarlet]{0}
commands-lb-params = {""}
commands-lb-description = Увімкнути/вимкнути таблицю лідерів.
commands-lb-success = { $leaderboardEnabled ->
[true] [accent]Таблиця лідерів [green]увімкнена
*[other] [accent]Таблиця лідерів [scarlet]вимкнена
}
commands-login-params = <пароль>
commands-login-description = Запит на права адміна. Не використовуйте, якщо не знаєте, що робите.
commands-login-incorrect-password = [scarlet]⚠ Невірний пароль!
commands-login-success = [green]Права адміна отримано.
commands-login-confirmed = [green]Права адміна підтверджено.
commands-login-admin-password-created = [green]Пароль адміна створено.
    {""}[red]Не забудьте свій пароль! Якщо ви його забудете, вам доведеться просити головного адміністратора скинути його.
commands-login-request-approval-discord = [accent]Вам потрібно підтвердити запит на права адміна в каналі [orange]#admin-bots[] на нашому Discord сервері.
commands-logout-params = {""}
commands-logout-description = Вийти з адмін-панелі. Це [scarlet]відкличе ваші права адміна.
commands-logout-successful = [green]Права адміна відкликано.
commands-tr-params = <мова>
commands-tr-description = Встановити мову перекладача.
commands-tr-success = [accent]Мову перекладача успішно змінено на [grey]{$translatorLanguage}[]!
commands-tr-off = [accent]Перекладач [scarlet]вимкнено[]!
commands-tr-not-found = [scarlet]⚠ Такої мови не існує.
commands-maps-params = [сторінка]
commands-maps-description = Список усіх карт на цьому сервері.
commands-ban-params = <id-гравця> <період> [причина...]
commands-ban-description = Забанити гравця. [scarlet]Тільки для адмінів.
commands-ban-success = {$nickname} [scarlet]забанений
commands-unban-params = <id-гравця>
commands-unban-description = Розбанити гравця. [scarlet]Тільки для адмінів.
commands-unban-success = {$nickname}[accent] #{$pid} [green]успішно розбанений.
commands-mute-params = <id-гравця> <період> [причина...]
commands-mute-description = Заглушити (змутити) гравця. [scarlet]Тільки для адмінів.
commands-mute-success = [accent]Успішно заглушено гравця {$nickname}
commands-unmute-params = <id-гравця>
commands-unmute-description = Зняти мут з гравця. [scarlet]Тільки для адмінів.
commands-unmute-success = [green]Успішно знято мут з гравця []{$nickname}
commands-map-stats-content = {""}[white]Статистика карти для [green]{$mapName}
    {""}[white]Автор:[green] {$mapAuthor}[orange] | [white]Розмір:[green] {$mapWidth}x{$mapHeight}[orange]
    {""}[white]Репутація:[green] {$mapReputation}[orange] | [white]Популярність:[green] {$mapPopularity}[orange] | [white]Цікавість:[green] {$mapInterest}[orange]
    {""}[white]Зіграно разів:[green] {$mapPlayedTimes}[orange] | [white]Зіграно за рік:[green] {$mapPlayedTimesYear}[orange] | [white]Остання гра:[green] {$mapLastPlayed}[orange]
    {""}[white]Мін. час:[green] {$mapMin}[orange] | [white]Сер. час:[green] {$mapAvg}[orange] | [white]Макс. час:[green] {$mapMax}[orange]
    {""}[green]{$mapDescription}[white]
commands-map-stats-params = [назва-карти]
commands-map-stats-description = Статистика конкретної карти
commands-maps-page-must-number = [scarlet]'сторінка' має бути числом
commands-maps-start-content = [accent]Поточна карта: []{$mapName}[white]
    {""}[orange][gold]Список карт [lightgray]{$page}[gray]/[lightgray]{$pageCount}
commands-maps-content = {""}
    {$index}. [orange] - [white]{$mapName}[orange] | [green]{$mapReputation}[orange] | [white]{$mapWidth}x{$mapHeight}[orange] | [white]{$mapLastPlayed}[orange] | Від: [sky]{$mapAuthor}
commands-votekick-params = <ID/ім'я> <причина...>
commands-votekick-description = Голосування за вигнання (кік) гравця з сервера.
commands-vote-params = <y/n>
commands-vote-description = Проголосувати у поточному голосуванні.
commands-vote-vote-with = [scarlet]⚠ Голосуйте за допомогою [orange]/vote <y/n/c>
commands-rank-params = [гравець...]
commands-rank-description = Показує інформацію про ваш ранг або ранг іншого гравця.
commands-rank-content = {$nickname}
    {$rankTag} [accent]{$rankName}
    {""}[gold]Перемог: {$points}/{$requiredPoints}
commands-ranks-params = {""}
commands-ranks-description = Показує інформацію про систему рангів.
commands-ranks-content = {$rankTag} [accent]{$rankName}
    {""}[gold]Вимоги: [grey]{$requiredPoints} [accent]перемог[]
commands-ranks-footer = Кількість перемог збільшується лише при перемозі над гравцем вашого рангу або вище.
commands-top-params = {""}
commands-top-description = Топ гравців.
commands-top-hexed-content = [orange]{$index}. {$nickname}[accent]: [blue]{$rankName} [cyan]{$points} []перемог
commands-top-pvp-content = [orange]{$index}. {$nickname}[accent]: [cyan]{$rating}
commands-spectate-params = {""}
commands-spectate-description = Перейти в режим спостерігача. Це видалить вашого юніта. Напишіть ще раз, щоб повернутися в команду.
commands-spectate-success = [green]Тепер ви спостерігаєте за грою
commands-spectate-success2 = [green]Ви більше не спостерігач
commands-ai-params = <idle/i/attack/a>
commands-ai-description = Керування ШІ (AI)
commands-ai-usage = [red]attack(i) []або [accent]idle(i)
rtv-vote = {$nickname}[lightgray] проголосував за зміну поточної карти на [orange]{$mapName}[lightgray]. ([accent]{$votes}[]/[accent]{$votesRequired}[])
    Напишіть [orange]y[] або [orange]n[], щоб проголосувати.
rtv-left = {$nickname}[lightgray] вийшов. Його голос за зміну карти скасовано. ([accent]{$votes}[]/[accent]{$votesRequired}[])
rtv-fail = [lightgray]Голосування не пройшло. Недостатньо голосів для зміни карти на [orange]{$mapName}[].
rtv-success = [orange]Голосування пройшло. Карта [accent]{$mapName}[] буде завантажена через [accent]{$mapLoadDelay}[] секунд...
votekick-vote = {$nickname} [grey]#[white]{$nicknameId}[lightgray] проголосував за вигнання {$targetNickname} [grey]#[white]{$targetNicknameId}[lightgray] через [orange]{$reason}[lightgray]. ([accent]{$votes}[]/[accent]{$votesRequired}[])
    {""}[lightgray]Напишіть [orange]/vote <y/n>[], щоб проголосувати.
votekick-left = {$nickname}[lightgray] вийшов. Його голос за вигнання гравця скасовано. ([accent]{$votes}[]/[accent]{$votesRequired}[])
votekick-cancelled = [scarlet]Голосування за вигнання {$nickname}[scarlet] було скасовано адміністратором {$admin}
votekick-fail = [lightgray]Голосування не пройшло. Недостатньо голосів, щоб вигнати {$nickname}[lightgray] з сервера.
votekick-success = [orange]Голосування пройшло. {$nickname}[orange] вигнаний з сервера на [scarlet]{$minutes}[] хвилин.
player-joined = {$nickname} [grey]#[white]{$pid}[grey] [accent]приєднався.
player-left = {$nickname} [grey]#[white]{$pid}[grey] [accent]вийшов.
notification-votekick-playtime = [accent]Вітаємо! Ви відіграли [lightgray]{0}[] хвилин і тепер можете почати голосування за вигнання.
notification-global-chat-playtime = [accent]Вітаємо! Ви відіграли [lightgray]{0}[] хвилин і тепер можете писати в глобальний чат.
    {""}[lightgray]Напишіть [accent]/g [gray]<повідомлення...>[lightgray], щоб надіслати повідомлення.

error-access-denied = [scarlet]⚠ Доступ заборонено
error-ip-changed = [scarlet]⚠ Ваша IP-адреса змінилася. Права адміністратора було відкликано.
error-not-enough-params = [scarlet]⚠ Недостатньо параметрів
error-player-not-found = [scarlet]⚠ Гравця не знайдено
error-player-not-teammate = [scarlet]⚠ Цей гравець не у вашій команді
error-player-admin = [scarlet]⚠ Не намагайтеся вигнати адміністратора ⚠
error-already-voted = [scarlet]⚠ Ви вже проголосували. Заспокойтесь.
error-globalchat-total-playtime = [scarlet]⚠ Щоб писати в глобальний чат, вам потрібно відіграти {$globalChatPlayTime} хвилин.
error-votekick-total-playtime = [scarlet]⚠ Щоб почати голосування за вигнання, вам потрібно відіграти {$votekickPlayTime} хвилин.
error-vote-yourself = [scarlet]⚠ Ви не можете голосувати у власному голосуванні.
error-vote-in-progress = [scarlet]⚠ Голосування вже триває.
error-no-voting = [scarlet]⚠ На даний момент голосування не проводиться.
error-map-not-found = [scarlet]⚠ Карту не знайдено! [accent]Використовуйте [cyan]/maps[], щоб побачити список доступних карт.
error-page-between = [scarlet]⚠ 'сторінка' має бути числом від[orange] 1[] до [orange]{$pageCount}[]
error-page-number = [scarlet]'сторінка' має бути числом
error-wrong-number = [scarlet]⚠ Неправильний формат числа
error-wrong-period-format = [scarlet]⚠ Неправильний формат періоду. Приклад: 1h 30m, 30 ({hours})
error-invalid-id = [scarlet]⚠ Невірний ID гравця
error-spectator = [scarlet]⚠ Ви спостерігач. Напишіть /spectate, щоб повернутися.
error-admin-password-too-short = [scarlet]⚠ Пароль адміністратора має бути не коротшим за 4 символи
error-wrong-admin-password = [scarlet]⚠ Невірний пароль адміністратора

hours = годин
days = днів
support-channel = #reports-appeals
ban-content = {$nickname} [accent]був [scarlet]забанений[].
    Щоб оскаржити бан, відвідайте Discord (канал [gray]{support-channel}[]):
    {""}[cyan]{$discordUrl}
ban-cancelled = [accent]Бан гравця [scarlet]{$nickname}[accent] було скасовано
tempban-content = {$nickname}[accent] був забанений.
    Адмін: {$adminName}[accent]
    Причина: "[gold]{$reason}[]"
    Ви будете розбанені через: {$days} днів, {$hours} годин та {$minutes} хвилин
    Щоб оскаржити бан, відвідайте Discord (канал [gray]{support-channel}[]):
    {""}[cyan]{$discordUrl}
tempban-player-banned = [scarlet] Адмін {$adminName}[scarlet] забанив гравця [gray]'[]{$playerName}[gray]'
you-are-muted-by = [scarlet]Ви були заглушені адміністратором [accent]{$adminName}[blue] на {$remainMinutes}:{$remainSeconds} хвилин,
    причина: {$reason}
you-are-muted = [scarlet]Ви не можете писати в чат. [accent]Ви були заглушені адміністратором {$adminName}[blue] на {$remainMinutes}:{$remainSeconds} хвилин,
    причина: {$reason}
success = [green]Успішно
empty = [accent]Порожньо
leaderboard = [blue]Таблиця лідерів
pvp-team-won = Ваша команда перемогла. Ваш рейтинг зріс на {$increased}
pvp-team-lose = Ваша команда програла. Ваш рейтинг знизився на {$reduced}
pvp-leaderboard-content = [orange]{$index}. {$nickname}[accent]:[cyan] {$rating} [accent]рейтинг
hexed-popup = [blue]{$minutes}:{$seconds}[] до кінця гри.
hexed-eliminated = {$nickname} [gold]був [scarlet]знищений[]!
hexed-leaderboard-content = [orange]{$index}. {$nickname}[accent]: [cyan]{$hexes} [accent]хексів
hexed-ranks-newbie = Новачок
hexed-ranks-regular = Звичайний
hexed-ranks-advanced = Досвідчений
hexed-ranks-veteran = Ветеран
hexed-ranks-davastator = Руйнівник
hexed-ranks-the_legend = Легенда
pvp-you-spectator = [scarlet]Ви програли. Будь ласка, зачекайте наступної гри.
kick-pirated-game = [accent]Грати на піратській версії гри [scarlet]заборонено[]. Встановіть [lime]офіційну[] версію гри з [blue]App Store[], [blue]Google Play[] або [blue]itch.io: https://anuke-itch-io/mindustry
kick-recently-kicked = [accent]Ви були нещодавно вигнані з цього сервера.
    Зачекайте [cyan]{$remainMinutes}:{$remainSeconds}[accent] перед повторним входом.
welcome = [accent]Ласкаво просимо на {$serverName}!
    {""}[lightgray]Напишіть [accent]/help[lightgray], щоб побачити список команд
    {""}[lightgray]Напишіть [accent]/vote [gray]<y/n>[lightgray], щоб проголосувати за вигнання гравця
    {""}[lightgray]Напишіть [accent]/votekick [gray]<ID/ім'я> <причина...>[lightgray], щоб почати голосування за вигнання
    {""}[lightgray]Напишіть [accent]/t [gray]<повідомлення...>[lightgray], щоб надіслати повідомлення союзникам
    {""}[lightgray]Напишіть [accent]/g [gray]<повідомлення...>[lightgray], щоб надіслати повідомлення на всі сервери
    {""}[lightgray]Напишіть [accent]/tr [gray]<мова/auto>[lightgray], щоб увімкнути перекладач
    {""}[lightgray]Напишіть [accent]/discord[lightgray], щоб перейти на наш Discord сервер

commands-like-params = {""}
commands-like-description = Проголосувати за карту (підвищує репутацію)
commands-like-success = [green]Ви вподобали цю карту!
commands-like-changed = [green]Ви змінили свою думку на Вподобайку!
commands-dislike-params = {""}
commands-dislike-description = Проголосувати проти карти
commands-dislike-success = [orange]Ви поставили не подобається цій карті.
commands-dislike-changed = [orange]Ви змінили свою думку на "Не подобається".
never = Ніколи
map-vote-title = [scarlet]ГРА ЗАКІНЧЕНА!
map-vote-content = {""}
    {""}Наступна карта: [accent]{$mapName}[] від [accent]{$author}[white].
    {""}Нова гра почнеться через [accent]{$seconds}[white] секунд.
    {""}
    {""}[cyan]Чи сподобалась ця карта?
map-vote-like = [green]👍 Подобається
map-vote-dislike = [red]👎 Не подобається
map-vote-like-selected = [gray]Вам вже подобається
map-vote-dislike-selected = [gray]Вам вже не подобається
close = Закрити