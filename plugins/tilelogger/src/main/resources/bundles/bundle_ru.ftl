commands-tl-description = Инфо и инструменты TileLogger
commands-tl-memory-description = Показать использование памяти
commands-tl-select-description = Выбрать область для отката/заливки
commands-tl-subnet-description = Проверить доступ подсети
commands-tl-subnet-subnet-description = IPv4 подсеть (например 1.2.3.4/24)

commands-tl-fill-description = Заполнить выбранную область блоком
commands-tl-fill-block-description = Имя блока

commands-tl-file-description = Сброс истории в файл (только сервер)
commands-tl-file-path-description = Имя файла
commands-tl-file-mode-description = Режим: w = запись

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

tilelogger-fill-success = [#98ff98]✔ Заливка выполнена[] [#b0b5c8]блоком[] {$block}

tilelogger-history-player = [#a4b8ff]📜 История:[] [#ffd37f]{$player}[] [#6e7080]|[] [#b0b5c8]{$time}[]
tilelogger-history-tile = [#a4b8ff]📜 История тайла:[] [#ffd37f]({$x}, {$y})[] [#6e7080]|[] [#b0b5c8]{$time}[]

tilelogger-rollback-broadcast = [#a4b8ff]Откат:[] [#ffd37f]{$caller}[] [#b0b5c8]отменил действия[] [#ffd37f]{$target}[]
    [#b0b5c8]Изменено:[] [#ff8a8a]{$count}[] { $count ->
        [one] тайл
        [few] тайла
        [many] тайлов
       *[other] тайла
    }

tilelogger-server = Сервер