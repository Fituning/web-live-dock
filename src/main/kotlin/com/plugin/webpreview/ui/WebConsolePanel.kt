package com.plugin.webpreview.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.plugin.webpreview.model.ConsoleLogItem
import com.plugin.webpreview.model.ConsoleLogLevel
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.DefaultListModel
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

enum class FilterMode(val displayName: String) {
    ALL("Tous les logs"),
    WARN_AND_ERROR("Avertissements & Erreurs"),
    ERROR_ONLY("Erreurs uniquement")
}

class WebConsolePanel(
    private val project: Project
) : JPanel(BorderLayout()) {

    private val allLogs = CopyOnWriteArrayList<ConsoleLogItem>()
    private val listModel = DefaultListModel<ConsoleLogItem>()
    private val logList = JBList(listModel)

    private var currentFilterMode = FilterMode.ALL
    private var searchText = ""

    private val statusLabel = JBLabel("0 logs")

    init {
        setupUI()
    }

    private fun setupUI() {
        border = JBUI.Borders.empty()

        // List Configuration
        logList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        logList.cellRenderer = LogListCellRenderer()
        logList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val selectedItem = logList.selectedValue
                    if (selectedItem != null) {
                        navigateToSource(selectedItem)
                    }
                }
            }
        })

        val scrollPane = JBScrollPane(logList)
        scrollPane.border = JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0)
        add(scrollPane, BorderLayout.CENTER)

        // Header Toolbar for Console
        val headerPanel = JPanel(BorderLayout())
        headerPanel.border = JBUI.Borders.empty(4, 8)

        val leftHeader = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        val consoleTitle = JBLabel("Console Web Client").apply {
            font = font.deriveFont(Font.BOLD)
        }
        leftHeader.add(consoleTitle)
        leftHeader.add(statusLabel)

        val rightHeader = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0))

        // Quick text search
        val searchField = SearchTextField(true).apply {
            textEditor.emptyText.text = "Filtrer console..."
            textEditor.columns = 12
            addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = onSearchChange()
                override fun removeUpdate(e: DocumentEvent?) = onSearchChange()
                override fun changedUpdate(e: DocumentEvent?) = onSearchChange()
                private fun onSearchChange() {
                    searchText = text.trim()
                    applyFilter()
                }
            })
        }
        rightHeader.add(searchField)

        // Filter dropdown
        val filterComboBox = ComboBox(FilterMode.values()).apply {
            selectedItem = FilterMode.ALL
            addActionListener {
                currentFilterMode = selectedItem as? FilterMode ?: FilterMode.ALL
                applyFilter()
            }
        }
        rightHeader.add(filterComboBox)

        // Clear action button
        val clearAction = object : AnAction("Vider la console", "Effacer tous les logs", AllIcons.Actions.GC) {
            override fun actionPerformed(e: AnActionEvent) {
                clearLogs()
            }
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
        }
        val actionGroup = DefaultActionGroup(clearAction)
        val toolbar = ActionToolbarImpl("WebConsoleToolbar", actionGroup, true)
        toolbar.targetComponent = this
        rightHeader.add(toolbar.component)

        headerPanel.add(leftHeader, BorderLayout.WEST)
        headerPanel.add(rightHeader, BorderLayout.EAST)
        add(headerPanel, BorderLayout.NORTH)
    }

    fun addLog(item: ConsoleLogItem) {
        allLogs.add(item)
        SwingUtilities.invokeLater {
            if (matchesFilter(item)) {
                listModel.addElement(item)
                // Auto-scroll to bottom
                val lastIndex = listModel.size() - 1
                if (lastIndex >= 0) {
                    logList.ensureIndexIsVisible(lastIndex)
                }
            }
            updateStatusText()
        }
    }

    fun clearLogs() {
        allLogs.clear()
        SwingUtilities.invokeLater {
            listModel.clear()
            updateStatusText()
        }
    }

    fun setFilterMode(mode: FilterMode) {
        currentFilterMode = mode
        applyFilter()
    }

    private fun matchesFilter(item: ConsoleLogItem): Boolean {
        val matchesLevel = when (currentFilterMode) {
            FilterMode.ALL -> true
            FilterMode.WARN_AND_ERROR -> item.level == ConsoleLogLevel.WARN || item.level == ConsoleLogLevel.ERROR
            FilterMode.ERROR_ONLY -> item.level == ConsoleLogLevel.ERROR
        }
        if (!matchesLevel) return false

        if (searchText.isNotBlank()) {
            val query = searchText.lowercase()
            val inMessage = item.message.lowercase().contains(query)
            val inSource = item.source.lowercase().contains(query)
            return inMessage || inSource
        }
        return true
    }

    private fun applyFilter() {
        SwingUtilities.invokeLater {
            listModel.clear()
            for (item in allLogs) {
                if (matchesFilter(item)) {
                    listModel.addElement(item)
                }
            }
            updateStatusText()
        }
    }

    private fun updateStatusText() {
        val errors = allLogs.count { it.level == ConsoleLogLevel.ERROR }
        val warns = allLogs.count { it.level == ConsoleLogLevel.WARN }
        val total = allLogs.size
        statusLabel.text = "🔴 $errors  🟠 $warns  (Total: $total)"
    }

    private fun navigateToSource(item: ConsoleLogItem) {
        if (item.source.isBlank()) return

        // Clean source URL e.g. "http://localhost:3000/_nuxt/pages/index.vue?t=123" -> "pages/index.vue" or "index.vue"
        val rawPath = item.source
            .substringBefore("?")
            .substringBefore("#")
        val fileName = rawPath.substringAfterLast("/")
        if (fileName.isBlank()) return

        val projectScope = GlobalSearchScope.projectScope(project)
        val files = FilenameIndex.getVirtualFilesByName(fileName, projectScope)

        val targetFile = files.firstOrNull() ?: return
        val targetLine = (item.line - 1).coerceAtLeast(0)

        OpenFileDescriptor(project, targetFile, targetLine, 0).navigate(true)
    }

    private class LogListCellRenderer : ColoredListCellRenderer<ConsoleLogItem>() {
        override fun customizeCellRenderer(
            list: javax.swing.JList<out ConsoleLogItem>,
            value: ConsoleLogItem?,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean
        ) {
            if (value == null) return

            ipad = JBUI.insets(3, 6)

            // Icon badge
            icon = when (value.level) {
                ConsoleLogLevel.ERROR -> WebPreviewIcons.ErrorBadge
                ConsoleLogLevel.WARN -> WebPreviewIcons.WarnBadge
                ConsoleLogLevel.INFO -> WebPreviewIcons.InfoBadge
                ConsoleLogLevel.DEBUG -> AllIcons.General.BalloonInformation
            }

            // Timestamp in subtle gray
            append("[${value.formattedTime}] ", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)

            // Level tag
            val levelAttrs = when (value.level) {
                ConsoleLogLevel.ERROR -> SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, JBColor.RED)
                ConsoleLogLevel.WARN -> SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, JBColor.ORANGE.darker())
                ConsoleLogLevel.INFO -> SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                ConsoleLogLevel.DEBUG -> SimpleTextAttributes.GRAYED_ATTRIBUTES
            }
            append("[${value.level.name}] ", levelAttrs)

            // Log Message
            val messageAttrs = if (value.level == ConsoleLogLevel.ERROR) {
                SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.RED)
            } else {
                SimpleTextAttributes.REGULAR_ATTRIBUTES
            }
            append(value.message, messageAttrs)

            // Source position (e.g. app.vue:14)
            if (value.displaySource.isNotBlank()) {
                append("  at ${value.displaySource}", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
            }
        }
    }
}
