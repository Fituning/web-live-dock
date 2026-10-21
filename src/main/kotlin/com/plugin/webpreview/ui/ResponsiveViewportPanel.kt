package com.plugin.webpreview.ui

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.JBUI
import com.plugin.webpreview.model.ResponsivePreset
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import kotlin.math.ln
import kotlin.math.roundToInt

class ResponsiveViewportPanel(
    private val browser: JBCefBrowser
) : JPanel(BorderLayout()) {

    private var currentPreset: ResponsivePreset = ResponsivePreset.AUTO
    private var isAutoFitZoom: Boolean = true
    private var manualZoomPercent: Int = 67

    private val deviceHeaderLabel = JBLabel("", SwingConstants.CENTER).apply {
        font = font.deriveFont(11f)
        foreground = JBColor.GRAY
        border = JBUI.Borders.empty(3, 8)
    }

    private val headerPanel = JPanel(BorderLayout()).apply {
        background = JBColor.PanelBackground
        border = JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0)
        add(deviceHeaderLabel, BorderLayout.CENTER)
    }

    private val deviceCard = JPanel(BorderLayout()).apply {
        background = JBColor.PanelBackground
    }

    private val centeredContainer = JPanel(GridBagLayout()).apply {
        background = JBColor(0x232426, 0x18181A)
    }

    private val scrollPane = JBScrollPane(centeredContainer).apply {
        border = JBUI.Borders.empty()
        horizontalScrollBar.unitIncrement = 16
        verticalScrollBar.unitIncrement = 16
    }

    init {
        border = JBUI.Borders.empty()

        // Assemble the single hierarchy permanently
        deviceCard.add(headerPanel, BorderLayout.NORTH)
        deviceCard.add(browser.component, BorderLayout.CENTER)

        val gbc = GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            weightx = 1.0
            weighty = 1.0
            anchor = GridBagConstraints.CENTER
        }
        centeredContainer.add(deviceCard, gbc)

        add(scrollPane, BorderLayout.CENTER)

        // Listen for window/dock resize to dynamically adapt scale in Auto-Fit mode
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) {
                recalculateSizeAndZoom()
            }
        })
    }

    fun updateConfig(preset: ResponsivePreset, autoFit: Boolean, manualZoom: Int) {
        currentPreset = preset
        isAutoFitZoom = autoFit
        manualZoomPercent = manualZoom

        recalculateSizeAndZoom()
    }

    fun applyCurrentZoom() {
        val scale = calculateScale()
        val level = ln(scale) / ln(1.2)
        try {
            browser.cefBrowser.zoomLevel = level
        } catch (_: Exception) {
            // CEF not yet ready or detached
        }
    }

    fun recalculateSizeAndZoom() {
        val availW = (width - 16).coerceAtLeast(100)
        val availH = (height - 16).coerceAtLeast(100)

        if (!currentPreset.isFixed) {
            // Responsive 100% (Auto mode)
            headerPanel.isVisible = false
            deviceCard.border = JBUI.Borders.empty()
            centeredContainer.background = JBColor.PanelBackground

            deviceCard.preferredSize = Dimension(availW, availH)
            deviceCard.minimumSize = Dimension(100, 100)
            deviceCard.maximumSize = null

            applyCurrentZoom()
        } else {
            // Preset / Device mode (Mobile, Tablet, Desktop)
            headerPanel.isVisible = true
            deviceCard.border = BorderFactory.createCompoundBorder(
                JBUI.Borders.customLine(JBColor.border(), 1),
                JBUI.Borders.empty(1)
            )
            centeredContainer.background = JBColor(0x232426, 0x18181A)

            val scale = calculateScale()
            val headerH = 26
            val displayW = (currentPreset.targetWidth * scale).roundToInt().coerceAtLeast(60)
            val displayH = (currentPreset.targetHeight * scale).roundToInt().coerceAtLeast(60)

            val percent = (scale * 100).roundToInt()
            val fitModeText = if (isAutoFitZoom) "Auto-Fit Hauteur" else "Zoom fixé"
            deviceHeaderLabel.text = "${currentPreset.displayName} • ${percent}% ($fitModeText) • ${currentPreset.targetWidth}×${currentPreset.targetHeight}px"

            val totalCardH = displayH + headerH
            val cardDim = Dimension(displayW, totalCardH)
            deviceCard.preferredSize = cardDim
            deviceCard.minimumSize = cardDim
            deviceCard.maximumSize = cardDim

            applyCurrentZoom()
        }

        deviceCard.revalidate()
        centeredContainer.revalidate()
        revalidate()
        repaint()
    }

    private fun calculateScale(): Double {
        return if (!currentPreset.isFixed) {
            if (isAutoFitZoom) {
                // In Auto Responsive, scale standard 1080p height (900px content) to fit available dock height
                val availH = (height - 16).coerceAtLeast(100)
                (availH.toDouble() / 900.0).coerceIn(0.25, 1.25)
            } else {
                (manualZoomPercent / 100.0).coerceIn(0.25, 2.0)
            }
        } else {
            if (isAutoFitZoom) {
                val headerH = 26
                val margin = 24
                val availH = (height - headerH - margin).coerceAtLeast(60)
                val availW = (width - margin).coerceAtLeast(60)

                val scaleH = availH.toDouble() / currentPreset.targetHeight.toDouble()
                val scaleW = availW.toDouble() / currentPreset.targetWidth.toDouble()

                // Fit within both width and height, prioritizing height fit in docks
                minOf(scaleH, scaleW).coerceIn(0.12, 2.0)
            } else {
                (manualZoomPercent / 100.0).coerceIn(0.2, 2.0)
            }
        }
    }
}
