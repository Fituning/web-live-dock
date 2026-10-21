package com.plugin.webpreview.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.JBSplitter
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.JBUI
import com.plugin.webpreview.model.ConsoleLogItem
import com.plugin.webpreview.model.ConsoleLogLevel
import com.plugin.webpreview.model.ResponsivePreset
import com.plugin.webpreview.services.WebPreviewSettingsService
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

class WebPreviewPanel(
    private val project: Project,
    private val toolWindow: ToolWindow
) : JPanel(BorderLayout()), Disposable {

    private val settingsService = WebPreviewSettingsService.getInstance(project)
    private val consolePanel = WebConsolePanel(project)

    private var jbBrowser: JBCefBrowser? = null
    private var viewportPanel: ResponsiveViewportPanel? = null
    private lateinit var splitter: JBSplitter

    private val urlField = JBTextField()
    private val zoomComboBox: ComboBox<String>
    private val presetComboBox: ComboBox<ResponsivePreset>

    private var isConsoleVisible: Boolean = settingsService.state.isConsoleVisible
    private var isSplitVertical: Boolean = settingsService.state.isSplitVertical

    private val zoomOptions = linkedMapOf(
        "Auto-Fit (Hauteur)" to -1,
        "50%" to 50,
        "67%" to 67,
        "75%" to 75,
        "100%" to 100,
        "125%" to 125
    )

    init {
        val savedZoom = settingsService.state.zoomPercent
        val initialZoomKey = zoomOptions.entries.firstOrNull { it.value == savedZoom }?.key ?: "Auto-Fit (Hauteur)"
        zoomComboBox = ComboBox(zoomOptions.keys.toTypedArray()).apply {
            selectedItem = initialZoomKey
            toolTipText = "Niveau de zoom ou Auto-Fit à la hauteur du dock"
        }

        val initialPreset = ResponsivePreset.fromId(settingsService.state.responsivePresetId)
        presetComboBox = ComboBox(ResponsivePreset.values()).apply {
            selectedItem = initialPreset
            toolTipText = "Sélectionner un format d'appareil ou breakpoint Tailwind CSS"
            renderer = SimpleListCellRenderer.create { label, value, _ ->
                label.text = value?.displayName ?: ""
                label.icon = when (value) {
                    ResponsivePreset.AUTO -> AllIcons.General.Web
                    ResponsivePreset.MOBILE -> AllIcons.Toolwindows.ToolWindowDebugger
                    ResponsivePreset.SM, ResponsivePreset.MD -> AllIcons.Actions.PreviewDetails
                    ResponsivePreset.LG, ResponsivePreset.XL, ResponsivePreset.TW_2XL -> AllIcons.Nodes.Desktop
                    null -> null
                }
            }
        }

        setupUI()
    }

    private fun setupUI() {
        border = JBUI.Borders.empty()

        if (!JBCefApp.isSupported()) {
            val unsupportedPanel = JPanel(BorderLayout())
            val message = JBLabel(
                "<html><center><h3>JCEF (Chromium) n'est pas disponible</h3>" +
                "<p>Votre environnement Java ne prend pas en charge JCEF.<br/>" +
                "Veuillez vous assurer d'exécuter l'IDE avec le <b>JetBrains Runtime (JBR)</b> avec JCEF activé.</p></center></html>",
                SwingConstants.CENTER
            )
            unsupportedPanel.add(message, BorderLayout.CENTER)
            add(unsupportedPanel, BorderLayout.CENTER)
            return
        }

        // Initialize JCEF Browser
        val initialUrl = settingsService.state.url.ifBlank { "http://localhost:3000" }
        val browser = JBCefBrowser(initialUrl)
        jbBrowser = browser
        Disposer.register(this, browser)

        // Setup Responsive Viewport Wrapper
        val viewport = ResponsiveViewportPanel(browser)
        viewportPanel = viewport

        // Setup Handlers
        setupCefHandlers(browser)

        // Main Toolbar
        val toolbarPanel = createMainToolbar(browser, initialUrl)
        add(toolbarPanel, BorderLayout.NORTH)

        // Setup Splitter - permanent hierarchy to prevent JCEF detachment bugs
        val initialProportion = settingsService.state.splitProportion.coerceIn(0.2f, 0.8f)
        splitter = JBSplitter(isSplitVertical, initialProportion).apply {
            firstComponent = viewport
            secondComponent = if (isConsoleVisible) consolePanel else null
            dividerWidth = 4
            setHonorComponentsMinimumSize(true)
        }

        splitter.addPropertyChangeListener(JBSplitter.PROP_PROPORTION) {
            if (isConsoleVisible) {
                settingsService.state.splitProportion = splitter.proportion
            }
        }

        add(splitter, BorderLayout.CENTER)

        // Initial configuration
        SwingUtilities.invokeLater {
            applyViewportConfig()
        }
    }

    private fun toggleConsole() {
        isConsoleVisible = !isConsoleVisible
        settingsService.state.isConsoleVisible = isConsoleVisible

        if (isConsoleVisible) {
            splitter.secondComponent = consolePanel
            splitter.proportion = settingsService.state.splitProportion
        } else {
            settingsService.state.splitProportion = splitter.proportion
            splitter.secondComponent = null
        }

        splitter.revalidate()
        splitter.repaint()

        SwingUtilities.invokeLater {
            viewportPanel?.recalculateSizeAndZoom()
        }
    }

    private fun applyViewportConfig() {
        val preset = presetComboBox.selectedItem as? ResponsivePreset ?: ResponsivePreset.AUTO
        val zoomVal = settingsService.state.zoomPercent
        val isAutoFit = (zoomVal == -1)
        val manualZoom = if (zoomVal > 0) zoomVal else 67

        viewportPanel?.updateConfig(preset, isAutoFit, manualZoom)
    }

    private fun setupCefHandlers(browser: JBCefBrowser) {
        val client = browser.jbCefClient

        // Display Handler: capture console logs and URL changes
        client.addDisplayHandler(object : CefDisplayHandlerAdapter() {
            override fun onConsoleMessage(
                cefBrowser: CefBrowser,
                level: CefSettings.LogSeverity,
                message: String,
                source: String,
                line: Int
            ): Boolean {
                val logLevel = when (level) {
                    CefSettings.LogSeverity.LOGSEVERITY_ERROR -> ConsoleLogLevel.ERROR
                    CefSettings.LogSeverity.LOGSEVERITY_WARNING -> ConsoleLogLevel.WARN
                    CefSettings.LogSeverity.LOGSEVERITY_INFO -> ConsoleLogLevel.INFO
                    else -> ConsoleLogLevel.DEBUG
                }

                consolePanel.addLog(
                    ConsoleLogItem(
                        level = logLevel,
                        message = message,
                        source = source,
                        line = line
                    )
                )
                return false
            }

            override fun onAddressChange(cefBrowser: CefBrowser, frame: CefFrame?, url: String?) {
                if (url != null && (frame == null || frame.isMain)) {
                    SwingUtilities.invokeLater {
                        if (!urlField.hasFocus()) {
                            urlField.text = url
                            settingsService.state.url = url
                        }
                    }
                }
            }
        }, browser.cefBrowser)

        // Load Handler: re-apply zoom on page load end
        client.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(cefBrowser: CefBrowser, frame: CefFrame?, httpStatusCode: Int) {
                if (frame == null || frame.isMain) {
                    SwingUtilities.invokeLater {
                        viewportPanel?.applyCurrentZoom()
                    }
                }
            }
        }, browser.cefBrowser)
    }

    private fun createMainToolbar(browser: JBCefBrowser, initialUrl: String): JPanel {
        val toolbarContainer = JPanel(BorderLayout())
        toolbarContainer.border = JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0)

        // Navigation Actions
        val navActionGroup = DefaultActionGroup().apply {
            add(object : AnAction("Précédent", "Page précédente", AllIcons.Actions.Back) {
                override fun actionPerformed(e: AnActionEvent) {
                    if (browser.cefBrowser.canGoBack()) {
                        browser.cefBrowser.goBack()
                    }
                }
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = browser.cefBrowser.canGoBack()
                }
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            })

            add(object : AnAction("Suivant", "Page suivante", AllIcons.Actions.Forward) {
                override fun actionPerformed(e: AnActionEvent) {
                    if (browser.cefBrowser.canGoForward()) {
                        browser.cefBrowser.goForward()
                    }
                }
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = browser.cefBrowser.canGoForward()
                }
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            })

            add(object : AnAction("Actualiser", "Recharger la page", AllIcons.Actions.Refresh) {
                override fun actionPerformed(e: AnActionEvent) {
                    browser.cefBrowser.reload()
                }
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
            })
        }

        val navToolbar = ActionToolbarImpl("WebPreviewNavToolbar", navActionGroup, true)
        navToolbar.targetComponent = this

        val leftPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(navToolbar.component)
        }

        // Center Panel: URL input
        urlField.text = initialUrl
        urlField.columns = 20
        urlField.emptyText.text = "http://localhost:3000"
        urlField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    navigateToEnteredUrl()
                }
            }
        })

        val centerPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(2, 4)
            add(urlField, BorderLayout.CENTER)
        }

        // Right Panel: Responsive Breakpoints + Zoom + Layout Toggles + DevTools
        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 2))

        // Preset Selector (Tailwind Breakpoints)
        presetComboBox.addActionListener {
            val selected = presetComboBox.selectedItem as? ResponsivePreset ?: ResponsivePreset.AUTO
            settingsService.state.responsivePresetId = selected.id
            applyViewportConfig()
        }
        rightPanel.add(presetComboBox)

        // Zoom Selector
        val zoomLabel = JBLabel("Zoom:")
        rightPanel.add(zoomLabel)

        zoomComboBox.addActionListener {
            val selected = zoomComboBox.selectedItem as? String ?: "Auto-Fit (Hauteur)"
            val zoomVal = zoomOptions[selected] ?: -1
            settingsService.state.zoomPercent = zoomVal
            applyViewportConfig()
        }
        rightPanel.add(zoomComboBox)

        // Actions toolbar (Orientation, Toggle Console, DevTools)
        val viewActionsGroup = DefaultActionGroup().apply {
            // Orientation Toggle Action
            add(object : AnAction("Changer l'orientation", "Basculer entre disposition côte-à-côte et superposée", AllIcons.Actions.SplitHorizontally) {
                override fun actionPerformed(e: AnActionEvent) {
                    isSplitVertical = !isSplitVertical
                    settingsService.state.isSplitVertical = isSplitVertical
                    splitter.orientation = isSplitVertical
                    splitter.revalidate()
                    splitter.repaint()
                    SwingUtilities.invokeLater {
                        viewportPanel?.recalculateSizeAndZoom()
                    }
                }
                override fun update(e: AnActionEvent) {
                    e.presentation.icon = if (isSplitVertical) AllIcons.Actions.SplitHorizontally else AllIcons.Actions.SplitVertically
                    e.presentation.text = if (isSplitVertical) "Passer en côte-à-côte (horizontal)" else "Passer en superposé (vertical)"
                    e.presentation.isEnabled = isConsoleVisible
                }
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            })

            // Toggle Console Action
            add(object : AnAction("Masquer la console", "Basculer l'affichage de la console web", AllIcons.Toolwindows.ToolWindowMessages) {
                override fun actionPerformed(e: AnActionEvent) {
                    toggleConsole()
                }
                override fun update(e: AnActionEvent) {
                    e.presentation.icon = if (isConsoleVisible) AllIcons.Toolwindows.ToolWindowMessages else AllIcons.Actions.ToggleVisibility
                    e.presentation.text = if (isConsoleVisible) "Masquer la console" else "Afficher la console"
                }
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            })

            // DevTools Action
            add(object : AnAction("DevTools", "Ouvrir l'inspecteur Chromium", AllIcons.Toolwindows.ToolWindowDebugger) {
                override fun actionPerformed(e: AnActionEvent) {
                    try {
                        browser.openDevtools()
                    } catch (ex: Exception) {
                        browser.cefBrowser.devTools
                    }
                }
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
            })
        }

        val viewActionsToolbar = ActionToolbarImpl("WebPreviewViewActionsToolbar", viewActionsGroup, true)
        viewActionsToolbar.targetComponent = this
        rightPanel.add(viewActionsToolbar.component)

        toolbarContainer.add(leftPanel, BorderLayout.WEST)
        toolbarContainer.add(centerPanel, BorderLayout.CENTER)
        toolbarContainer.add(rightPanel, BorderLayout.EAST)

        return toolbarContainer
    }

    private fun navigateToEnteredUrl() {
        var rawUrl = urlField.text.trim()
        if (rawUrl.isBlank()) {
            rawUrl = "http://localhost:3000"
            urlField.text = rawUrl
        }
        if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://") && !rawUrl.startsWith("file://")) {
            rawUrl = "http://$rawUrl"
            urlField.text = rawUrl
        }

        settingsService.state.url = rawUrl
        jbBrowser?.loadURL(rawUrl)
    }

    override fun dispose() {
        jbBrowser?.let {
            Disposer.dispose(it)
        }
        jbBrowser = null
    }
}
