package dev.barna.calm

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class LauncherStateViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    fun markLoading() {
        _uiState.update { state ->
            state.copy(
                loading = true,
                generation = state.generation + 1,
            )
        }
    }

    fun publish(renderModel: LauncherRenderModel) {
        _uiState.update { state ->
            LauncherUiState(
                renderModel = renderModel,
                loading = false,
                generation = state.generation + 1,
                selectedPageKey = state.selectedPageKey,
            )
        }
    }

    fun selectPage(pageKey: String) {
        _uiState.update { state ->
            state.copy(selectedPageKey = pageKey)
        }
    }
}

data class LauncherUiState(
    val renderModel: LauncherRenderModel? = null,
    val loading: Boolean = false,
    val generation: Int = 0,
    val selectedPageKey: String? = null,
)
