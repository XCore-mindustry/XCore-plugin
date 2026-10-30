commands-tl-description = Инфо и инструменты TileLogger
commands-tl-memory-description = Показать использование памяти
commands-tl-select-description = Выбрать область для отката/заливки
commands-tl-subnet-description = Проверить доступ подсети
commands-tl-subnet-subnet-description = IPv4 подсеть (например 1.2.3.4/24)

commands-tl-fill-description = Заполнить выбранную область блоком
commands-tl-fill-block-description = Имя блока

commands-tl-file-path-description = Имя файла

commands-history-description = История изменений тайлов/игрока
commands-history-size-description = Количество записей
commands-history-target-description = Имя/uuid/id игрока
commands-history-x-description = Координата X
commands-history-y-description = Координата Y

commands-rollback-description = Откат действий
commands-rollback-target-description = Имя/uuid/id игрока
commands-rollback-time-description = Период (10m, 2h, 1d)
commands-rollback-selection-description = Использовать выделение

error-not-allowed-from-console = [#ff8a8a]✖ Недоступно из консоли[]
error-not-allowed-from-player = [#ff8a8a]✖ Недоступно для игрока[]
error-block-not-found = [#ff8a8a]✖ Блок не найден[]
error-too-many-params = [#ff8a8a]✖ Слишком много параметров[]

tilelogger-info =
    [#a4b8ff]TileLogger[]
        [#b0b5c8]Автор:[]  [#ffd37f] (Gorynych)[]
        [#b0b5c8]Сборка:[] [#a4b8ff]{$build}[]

tilelogger-memory =
    [#a4b8ff]▬▬▬ Память (MB) ▬▬▬[]
        [#b0b5c8]JVM:[]     [#ffd37f]{$jvmUsed}[] [#6e7080]/[] [#b0b5c8]{$jvmMax}[]
        [#b0b5c8]История:[] [#ffd37f]{$historyUsed}[] [#6e7080]/[] [#b0b5c8]{$historyCap}[]
        [#b0b5c8]Игроки:[]  [#ffd37f]{$playersUsed}[] [#6e7080]/[] [#b0b5c8]{$playersCap}[]
        [#b0b5c8]Конфиги:[] [#ffd37f]{$configsUsed}[] [#6e7080]/[] [#b0b5c8]{$configsCap}[]

tilelogger-select-start = [#a4b8ff] Выделение:[] [#b0b5c8]Нажмите на левый верхний угол.[]
tilelogger-select-pos1 = [#a4b8ff] Точка 1 задана.[] [#b0b5c8]Нажмите на правый нижний угол.[]
tilelogger-select-done = [#98ff98]✔ Область выбрана.[] [#b0b5c8]Тайлов:[] [#ffd37f]{$area}[] { $area ->
    [one] тайл
    [few] тайла
    [many] тайлов
   *[other] тайла
}

tilelogger-subnet-accept = [#98ff98]✔ РАЗРЕШЕНО[] [#b0b5c8]Подсеть:[] [#ffd37f]{$subnet}[]
tilelogger-subnet-deny = [#ff8a8a]✖ ЗАПРЕЩЕНО[] [#b0b5c8]Подсеть:[] [#ffd37f]{$subnet}[]

tilelogger-fill-success = [#98ff98]✔ Заливка выполнена[] [#b0b5c8]блоком[] {$emoji} {$block}

tilelogger-history-player = [#a4b8ff]📜 История:[] [#ffd37f]{$player}[] [#6e7080]|[] [#b0b5c8]{$time}[]
tilelogger-history-tile = [#a4b8ff]📜 История тайла:[] [#ffd37f]({$x}, {$y})[] [#6e7080]|[] [#b0b5c8]{$time}[]


tilelogger-server = Сервер

commands-rollback-preview-description = Показать план отката без изменения мира и истории
commands-history-success = Размер истории при осмотре: { $size } (0 = выключено).
tilelogger-error-limit = [scarlet]Размер истории должен быть от 1 до { $max }.[]
tilelogger-error-coordinates = [scarlet]Координаты выходят за границы текущей карты.[]
tilelogger-error-selection = [scarlet]Сначала выберите оба угла на этой карте через /tl select.[]
tilelogger-error-duration = [scarlet]Длительность выходит за поддерживаемый диапазон.[]
tilelogger-error-session = [scarlet]Карта/сессия изменилась или запись остановлена. Начните живую сессию и повторите команду.[]
tilelogger-error-storage = [scarlet]Хранилище TileLogger недоступно: { $reason }[]
tilelogger-error-archive = [scarlet]Не удалось открыть архив. Активная история сохранена; подробности в логе сервера.[]
tilelogger-error-lookup = [scarlet]Не удалось найти игрока из-за ошибки хранилища. Попробуйте позже.[]
tilelogger-error-mutations = [scarlet]За одну операцию можно изменить до { $max } зданий. Выберите меньшую область.[]
tilelogger-error-protected = [scarlet]Операция затронула бы защищённое ядро или строящийся блок.[]
tilelogger-error-team = [scarlet]Укажите команду для размещаемых блоков.[]
tilelogger-error-footprint = [scarlet]Полный размер здания должен помещаться в выделение и не затрагивать другие здания.[]
tilelogger-error-mode = [scarlet]Режим r открывает архив для чтения; w начинает новый журнал живой сессии.[]
tilelogger-archive-open = [accent]Архив открыт: { $status }. Изменения мира отключены.[]
tilelogger-session-started = [accent]Начата живая история, начальное состояние карты записано.[]
tilelogger-read-only = [gray]Архив только для чтения. Старый формат имеет неоднозначное 16-битное время.[]
tilelogger-rollback-preview = [accent]План: { $count } можно применить; { $protected } защищено; { $conflicts } конфликтов; { $unsupported } невосстановимых. Изменений нет.[]
tilelogger-rollback-broadcast = [accent]{ $caller } откатил действия { $target }: { $count } применено; { $protected } защищено; { $conflicts } конфликтов; { $unsupported } невосстановимых; { $failed } ошибок.[]
commands-tl-file-description = Открыть архив для чтения или начать живую историю (только сервер)
commands-tl-file-mode-description = r = архив для чтения; w = новый журнал (существующие файлы не заменяются)

tilelogger-action-baseline = Начальная карта
tilelogger-action-build-started = Строительство начато
tilelogger-action-build-completed = Построено
tilelogger-action-destroy-started = Снос начат
tilelogger-action-destroy-completed = Разрушено
tilelogger-action-rotate = Поворот
tilelogger-action-configure = Настройка
tilelogger-action-pickup = Поднято
tilelogger-action-drop = Выгружено
tilelogger-action-admin-fill = Заливка админом
tilelogger-action-legacy = Снимок старого формата
tilelogger-error-plan = [scarlet]Область отката или объём конфигураций слишком велики. Выберите меньшую область.[]
tilelogger-error-payload = [scarlet]Откат затрагивает подбор или выгрузку постройки. Выберите область или период без переноса payload.[]
