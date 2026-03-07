package com.chaitalkstech.teslaheadsupdisplay

internal fun parseMaxspeed(raw: String): String? {
    val trimmed = raw.trim()
    return when {
        trimmed.endsWith("mph", ignoreCase = true) ->
            trimmed.replace(Regex("[^0-9]"), "").toIntOrNull()?.toString()
        trimmed.endsWith("km/h", ignoreCase = true) || trimmed.endsWith("kph", ignoreCase = true) ->
            trimmed.replace(Regex("[^0-9]"), "").toIntOrNull()
                ?.let { (it * 0.621371).toInt().toString() }
        trimmed.toIntOrNull() != null ->
            (trimmed.toInt() * 0.621371).toInt().toString()
        else -> null  // "none", "walk", "variable" — omit
    }
}
