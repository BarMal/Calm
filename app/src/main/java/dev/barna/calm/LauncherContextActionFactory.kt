package dev.barna.calm

import android.content.Context

data class LauncherContextActionCallbacks(
    val openNotification: (CalmNotificationListenerService.CalmNotification) -> Unit,
    val openPackage: (AppChapter) -> Unit,
    val dismissNotificationItem: (NotificationCardItem) -> Unit,
    val clearChapter: (AppChapter) -> Unit,
    val performNotificationAction: (NotificationAction) -> Unit,
    val openCalendarEvent: (CalendarEvent) -> Unit,
    val requestCalendarAccess: () -> Unit,
    val openSettings: () -> Unit,
    val openAppEntry: (AppEntry) -> Unit,
    val pinApp: (AppEntry) -> Unit,
    val unpinApp: (AppEntry) -> Unit,
    val openAppInfo: (AppEntry) -> Unit,
    val hideApp: (AppEntry) -> Unit,
    val appShortcuts: (AppEntry) -> List<AppShortcutEntry>,
    val launchShortcut: (AppShortcutEntry) -> Unit,
    val isDockItem: (String) -> Boolean,
    val addDockItem: (String, String) -> Unit,
    val removeDockItem: (String, String) -> Unit,
    val isClassicPageApp: (String) -> Boolean,
    val addAppToClassicPage: (AppEntry) -> Unit,
)

data class LauncherContextActionLabels(
    val open: String = "Open",
    val openApp: String = "Open app",
    val dismiss: String = "Dismiss",
    val clear: String = "Clear",
    val openCalendar: String = "Open calendar",
    val calendarAccess: String = "Calendar access",
    val allowCalendar: String = "Allow calendar",
    val settings: String = "Settings",
    val pin: String = "Pin",
    val unpin: String = "Unpin",
    val addToClassicPage: String = "Add to Classic page",
    val info: String = "Info",
    val hide: String = "Hide",
    val removeFromDock: String = "Remove from dock",
    val addToDock: String = "Add to dock",
) {
    companion object {
        fun from(context: Context): LauncherContextActionLabels {
            return LauncherContextActionLabels(
                open = context.getString(R.string.action_open),
                openApp = context.getString(R.string.action_open_app),
                dismiss = context.getString(R.string.action_dismiss),
                clear = context.getString(R.string.action_clear),
                openCalendar = context.getString(R.string.action_open_calendar),
                calendarAccess = context.getString(R.string.action_calendar_access),
                allowCalendar = context.getString(R.string.action_allow_calendar),
                settings = context.getString(R.string.action_settings),
                pin = context.getString(R.string.action_pin),
                unpin = context.getString(R.string.action_unpin),
                addToClassicPage = context.getString(R.string.action_add_to_classic_page),
                info = context.getString(R.string.action_info),
                hide = context.getString(R.string.action_hide),
                removeFromDock = context.getString(R.string.action_remove_from_dock),
                addToDock = context.getString(R.string.action_add_to_dock),
            )
        }
    }
}

class LauncherContextActionFactory(
    private val callbacks: LauncherContextActionCallbacks,
    private val labels: LauncherContextActionLabels = LauncherContextActionLabels(),
) {
    fun notificationActions(
        item: NotificationCardItem,
        chapter: AppChapter,
    ): List<ContextAction> {
        val actions = ArrayList<ContextAction>()
        actions.addAll(
            listOf(
                ContextAction(labels.open, Runnable { callbacks.openNotification(item.primary) }),
                ContextAction(labels.openApp, Runnable { callbacks.openPackage(chapter) }),
                ContextAction(
                    labels.dismiss,
                    Runnable { callbacks.dismissNotificationItem(item) },
                    ContextActionCloseBehavior.REMOVE_CARD,
                ),
                ContextAction(
                    labels.clear,
                    Runnable { callbacks.clearChapter(chapter) },
                    ContextActionCloseBehavior.REMOVE_CARD,
                ),
            ),
        )
        if (chapter.launchable) {
            actions.add(2, dockAction(chapter.launcherIdentityKey, chapter.label))
        }
        item.allActions().forEach { action ->
            actions.add(ContextAction(action.label, Runnable { callbacks.performNotificationAction(action) }))
        }
        return actions
    }

    fun calendarActions(
        event: CalendarEvent,
        hasCalendarPermission: Boolean,
    ): List<ContextAction> {
        return listOf(
            ContextAction(labels.openCalendar, Runnable { callbacks.openCalendarEvent(event) }),
            ContextAction(
                if (hasCalendarPermission) labels.calendarAccess else labels.allowCalendar,
                Runnable { callbacks.requestCalendarAccess() },
            ),
            ContextAction(labels.settings, Runnable { callbacks.openSettings() }),
        )
    }

    private companion object {
        const val MAX_SHORTCUTS = 3
    }

    fun appActions(
        app: AppEntry,
        pinned: Boolean,
    ): List<ContextAction> {
        val actions = ArrayList<ContextAction>()
        actions.add(ContextAction(labels.open, Runnable { callbacks.openAppEntry(app) }))
        actions.add(ContextAction(if (pinned) labels.unpin else labels.pin, Runnable {
            if (pinned) {
                callbacks.unpinApp(app)
            } else {
                callbacks.pinApp(app)
            }
        }))
        actions.add(dockAction(app.identityKey, app.label))
        if (!callbacks.isClassicPageApp(app.identityKey)) {
            actions.add(ContextAction(labels.addToClassicPage, Runnable { callbacks.addAppToClassicPage(app) }))
        }
        callbacks.appShortcuts(app).take(MAX_SHORTCUTS).forEach { shortcut ->
            actions.add(ContextAction(shortcut.label, Runnable { callbacks.launchShortcut(shortcut) }))
        }
        actions.add(ContextAction(labels.info, Runnable { callbacks.openAppInfo(app) }))
        actions.add(ContextAction(labels.hide, Runnable { callbacks.hideApp(app) }, ContextActionCloseBehavior.REMOVE_CARD))
        return actions
    }

    fun dockAction(identityKey: String, label: String): ContextAction {
        return if (callbacks.isDockItem(identityKey)) {
            ContextAction(labels.removeFromDock, Runnable { callbacks.removeDockItem(identityKey, label) })
        } else {
            ContextAction(labels.addToDock, Runnable { callbacks.addDockItem(identityKey, label) })
        }
    }
}
