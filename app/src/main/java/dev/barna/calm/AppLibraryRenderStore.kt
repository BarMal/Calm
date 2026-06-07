package dev.barna.calm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppLibraryRenderStore(
    initialApps: List<AppEntry> = emptyList(),
) {
    private val currentState = MutableStateFlow(AppLibraryRenderState.from(initialApps))
    private val reducer = AppLibraryRenderReducer()
    val stateFlow: StateFlow<AppLibraryRenderState> = currentState.asStateFlow()

    fun state(): AppLibraryRenderState = currentState.value

    @Synchronized
    fun replace(apps: List<AppEntry>, loading: Boolean = false): AppLibraryRenderState {
        val nextState = AppLibraryRenderState.from(apps, loading)
        currentState.value = nextState
        return nextState
    }

    @Synchronized
    fun dispatch(event: AppLibraryRenderEvent): AppLibraryRenderState {
        val nextState = reducer.reduce(currentState.value, event)
        currentState.value = nextState
        return nextState
    }
}

data class AppLibraryRenderState(
    val appsById: Map<String, AppEntry>,
    val orderedIds: List<String>,
    val loading: Boolean,
) {
    val apps: List<AppEntry> = orderedIds.mapNotNull(appsById::get)

    companion object {
        fun from(apps: List<AppEntry>, loading: Boolean = false): AppLibraryRenderState {
            val appsById = apps.associateBy { app -> app.identityKey }
            return AppLibraryRenderState(
                appsById = appsById,
                orderedIds = apps.map { app -> app.identityKey }.distinct(),
                loading = loading,
            )
        }
    }
}

sealed interface AppLibraryRenderEvent {
    data object LoadingStarted : AppLibraryRenderEvent
    data class AppsRemoved(
        val identityKeys: Set<String>,
        val orderedIds: List<String>,
    ) : AppLibraryRenderEvent
    data class AppsUpserted(
        val apps: List<AppEntry>,
        val orderedIds: List<String>,
    ) : AppLibraryRenderEvent
    data class LoadingFinished(
        val orderedIds: List<String>,
    ) : AppLibraryRenderEvent
}

class AppLibraryRenderReducer {
    fun reduce(state: AppLibraryRenderState, event: AppLibraryRenderEvent): AppLibraryRenderState {
        return when (event) {
            AppLibraryRenderEvent.LoadingStarted -> {
                state.copy(loading = true)
            }
            is AppLibraryRenderEvent.AppsRemoved -> {
                val nextApps = state.appsById - event.identityKeys
                state.copy(
                    appsById = nextApps,
                    orderedIds = event.orderedIds.filter(nextApps::containsKey),
                    loading = true,
                )
            }
            is AppLibraryRenderEvent.AppsUpserted -> {
                val nextApps = state.appsById + event.apps.associateBy { app -> app.identityKey }
                state.copy(
                    appsById = nextApps,
                    orderedIds = event.orderedIds.filter(nextApps::containsKey),
                    loading = true,
                )
            }
            is AppLibraryRenderEvent.LoadingFinished -> {
                state.copy(
                    orderedIds = event.orderedIds.filter(state.appsById::containsKey),
                    loading = false,
                )
            }
        }
    }
}

class AppLibraryEventPlanner {
    fun plan(currentApps: List<AppEntry>, nextApps: List<AppEntry>, batchSize: Int): List<AppLibraryRenderEvent> {
        val currentById = currentApps.associateBy { app -> app.identityKey }
        val nextById = nextApps.associateBy { app -> app.identityKey }
        val orderedIds = nextApps.map { app -> app.identityKey }.distinct()
        val events = ArrayList<AppLibraryRenderEvent>()
        events.add(AppLibraryRenderEvent.LoadingStarted)

        val removedIds = currentById.keys - nextById.keys
        if (removedIds.isNotEmpty()) {
            events.add(AppLibraryRenderEvent.AppsRemoved(removedIds, orderedIds))
        }

        val changedApps = nextApps.filter { app -> currentById[app.identityKey] != app }
        changedApps.chunked(batchSize.coerceAtLeast(1)).forEach { batch ->
            events.add(AppLibraryRenderEvent.AppsUpserted(batch, orderedIds))
        }

        events.add(AppLibraryRenderEvent.LoadingFinished(orderedIds))
        return events
    }
}
