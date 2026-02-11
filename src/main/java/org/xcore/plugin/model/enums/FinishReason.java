package org.xcore.plugin.model.enums;

public enum FinishReason {
    NATURAL,    // Сама по собі (перемога/поразка)
    RTV,        // Голосування
    ARTV,       // Адмін
    RESTART,    // Рестарт сервера
    COMMAND,    // Консоль/Скрипт
    SCRIPT      // Логіка якогось мода/процесора
}