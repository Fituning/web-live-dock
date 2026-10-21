package com.plugin.webpreview.ui

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object WebPreviewIcons {
    @JvmField
    val ToolWindow: Icon = IconLoader.getIcon("/icons/webPreviewToolWindow.svg", WebPreviewIcons::class.java)

    @JvmField
    val ErrorBadge: Icon = IconLoader.getIcon("/icons/errorBadge.svg", WebPreviewIcons::class.java)

    @JvmField
    val WarnBadge: Icon = IconLoader.getIcon("/icons/warnBadge.svg", WebPreviewIcons::class.java)

    @JvmField
    val InfoBadge: Icon = IconLoader.getIcon("/icons/infoBadge.svg", WebPreviewIcons::class.java)
}
