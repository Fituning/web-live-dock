package com.plugin.webpreview.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ConsoleLogLevel {
    ERROR,
    WARN,
    INFO,
    DEBUG;

    companion object {
        fun fromCefSeverity(severityName: String): ConsoleLogLevel {
            return when (severityName.uppercase()) {
                "LOGSEVERITY_ERROR", "ERROR" -> ERROR
                "LOGSEVERITY_WARNING", "WARN", "WARNING" -> WARN
                "LOGSEVERITY_INFO", "INFO" -> INFO
                else -> DEBUG
            }
        }
    }
}

data class ConsoleLogItem(
    val level: ConsoleLogLevel,
    val message: String,
    val source: String = "",
    val line: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
) {
    val formattedTime: String
        get() {
            val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
            return Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .format(formatter)
        }

    val displaySource: String
        get() {
            if (source.isBlank()) return ""
            // Clean up URLs to display relative file path if possible
            val cleanSource = source
                .substringBefore("?")
                .substringBefore("#")
            val fileName = cleanSource.substringAfterLast("/")
            return if (line > 0) "$fileName:$line" else fileName
        }
}
