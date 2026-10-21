package com.plugin.webpreview.services

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(
    name = "WebPreviewSettings",
    storages = [Storage("webPreviewSettings.xml")]
)
class WebPreviewSettingsService : PersistentStateComponent<WebPreviewSettingsService.State> {

    data class State(
        var url: String = "http://localhost:3000",
        var zoomPercent: Int = -1, // -1 = Auto-Fit (Hauteur)
        var filterIndex: Int = 0,
        var splitProportion: Float = 0.6f,
        var responsivePresetId: String = "auto",
        var isConsoleVisible: Boolean = true,
        var isSplitVertical: Boolean = false
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(project: Project): WebPreviewSettingsService {
            return project.getService(WebPreviewSettingsService::class.java)
        }
    }
}
