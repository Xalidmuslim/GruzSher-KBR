package ru.kbr.cargoshare

internal fun categoryMatches(type: String, filter: String): Boolean {
    val value = type.lowercase()
    return when (filter) {
        "Фургоны" -> value.contains("фургон")
        "Бортовые" -> value.contains("борт") || value.contains("платформ")
        "Рефрижераторы" -> value.contains("рефриж") || value.contains("холод")
        else -> true
    }
}

