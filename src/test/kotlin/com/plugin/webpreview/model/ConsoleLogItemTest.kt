package com.plugin.webpreview.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleLogItemTest {

    @Test
    fun testConsoleLogLevelMapping() {
        assertEquals(ConsoleLogLevel.ERROR, ConsoleLogLevel.fromCefSeverity("LOGSEVERITY_ERROR"))
        assertEquals(ConsoleLogLevel.WARN, ConsoleLogLevel.fromCefSeverity("LOGSEVERITY_WARNING"))
        assertEquals(ConsoleLogLevel.INFO, ConsoleLogLevel.fromCefSeverity("LOGSEVERITY_INFO"))
        assertEquals(ConsoleLogLevel.DEBUG, ConsoleLogLevel.fromCefSeverity("LOGSEVERITY_VERBOSE"))
    }

    @Test
    fun testDisplaySourceFormatting() {
        val item1 = ConsoleLogItem(
            level = ConsoleLogLevel.ERROR,
            message = "Uncaught TypeError: Cannot read properties of undefined",
            source = "http://localhost:3000/_nuxt/pages/index.vue?t=1729000000",
            line = 42
        )
        assertEquals("index.vue:42", item1.displaySource)

        val item2 = ConsoleLogItem(
            level = ConsoleLogLevel.INFO,
            message = "Ready",
            source = "/Users/dev/project/src/App.vue",
            line = 10
        )
        assertEquals("App.vue:10", item2.displaySource)

        val item3 = ConsoleLogItem(
            level = ConsoleLogLevel.DEBUG,
            message = "No line",
            source = "https://example.com/main.js",
            line = 0
        )
        assertEquals("main.js", item3.displaySource)
    }

    @Test
    fun testFormattedTime() {
        val item = ConsoleLogItem(
            level = ConsoleLogLevel.INFO,
            message = "Test time",
            timestamp = 1700000000000L
        )
        assertTrue(item.formattedTime.matches(Regex("\\d{2}:\\d{2}:\\d{2}")))
    }
}
