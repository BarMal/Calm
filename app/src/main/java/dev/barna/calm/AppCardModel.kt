package dev.barna.calm

class AppCardModelFactory(
    private val textFormatter: AppCardTextFormatter = AppCardTextFormatter(),
    private val pinnedAppResolver: PinnedAppResolver = PinnedAppResolver(),
) {
    fun create(app: AppEntry, pinnedKeys: Set<String>): AppCardModel {
        return AppCardModel(
            app = app,
            text = textFormatter.cardText(app),
            hueColor = app.hueColor,
            isPinned = pinnedAppResolver.isPinned(app, pinnedKeys),
        )
    }
}

data class AppCardModel(
    val app: AppEntry,
    val text: String,
    val hueColor: Int,
    val isPinned: Boolean,
)
