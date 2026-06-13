package dev.barna.calm

import android.content.Context

data class NotificationContextActionCallbacks(
    val openNotification: (CalmNotificationListenerService.CalmNotification) -> Unit,
    val openPackage: (AppChapter) -> Unit,
    val dismissNotificationItem: (NotificationCardItem) -> Unit,
    val clearChapter: (AppChapter) -> Unit,
    val performNotificationAction: (NotificationAction) -> Unit,
) {
    companion object {
        val Empty = NotificationContextActionCallbacks(
            openNotification = {},
            openPackage = {},
            dismissNotificationItem = {},
            clearChapter = {},
            performNotificationAction = {},
        )
    }
}

data class CalendarContextActionCallbacks(
    val openCalendarEvent: (CalendarEvent) -> Unit,
    val requestCalendarAccess: () -> Unit,
    val openSettings: () -> Unit,
) {
    companion object {
        val Empty = CalendarContextActionCallbacks(
            openCalendarEvent = {},
            requestCalendarAccess = {},
            openSettings = {},
        )
    }
}

data class AppContextActionCallbacks(
    val openAppEntry: (AppEntry) -> Unit,
    val pinApp: (AppEntry) -> Unit,
    val unpinApp: (AppEntry) -> Unit,
    val openAppInfo: (AppEntry) -> Unit,
    val hideApp: (AppEntry) -> Unit,
    val appShortcuts: (AppEntry) -> List<AppShortcutEntry>,
    val launchShortcut: (AppShortcutEntry) -> Unit,
) {
    companion object {
        val Empty = AppContextActionCallbacks(
            openAppEntry = {},
            pinApp = {},
            unpinApp = {},
            openAppInfo = {},
            hideApp = {},
            appShortcuts = { emptyList() },
            launchShortcut = {},
        )
    }
}

data class DockContextActionCallbacks(
    val isDockItem: (String) -> Boolean,
    val addDockItem: (String, String) -> Unit,
    val removeDockItem: (String, String) -> Unit,
) {
    companion object {
        val Empty = DockContextActionCallbacks(
            isDockItem = { false },
            addDockItem = { _, _ -> },
            removeDockItem = { _, _ -> },
        )
    }
}

data class ClassicPageContextActionCallbacks(
    val isClassicPageApp: (String) -> Boolean,
    val addAppToClassicPage: (AppEntry) -> Unit,
) {
    companion object {
        val Empty = ClassicPageContextActionCallbacks(
            isClassicPageApp = { false },
            addAppToClassicPage = {},
        )
    }
}

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
    private val notificationCallbacks: NotificationContextActionCallbacks = NotificationContextActionCallbacks.Empty,
    private val calendarCallbacks: CalendarContextActionCallbacks = CalendarContextActionCallbacks.Empty,
    private val appCallbacks: AppContextActionCallbacks = AppContextActionCallbacks.Empty,
    private val dockCallbacks: DockContextActionCallbacks = DockContextActionCallbacks.Empty,
    private val classicPageCallbacks: ClassicPageContextActionCallbacks = ClassicPageContextActionCallbacks.Empty,
    private val labels: LauncherContextActionLabels = LauncherContextActionLabels(),
) {
    fun notificationActions(
        item: NotificationCardItem,
        chapter: AppChapter,
    ): List<ContextAction> {
        val actions = ArrayList<ContextAction>()
        actions.addAll(
            listOf(
                ContextAction(labels.open, Runnable { notificationCallbacks.openNotification(item.primary) }),
                ContextAction(labels.openApp, Runnable { notificationCallbacks.openPackage(chapter) }),
                ContextAction(
                    labels.dismiss,
                    Runnable { notificationCallbacks.dismissNotificationItem(item) },
                    ContextActionCloseBehavior.REMOVE_CARD,
                ),
                ContextAction(
                    labels.clear,
                    Runnable { notificationCallbacks.clearChapter(chapter) },
                    ContextActionCloseBehavior.REMOVE_CARD,
                ),
            ),
        )
        if (chapter.launchable) {
            actions.add(2, dockAction(chapter.launcherIdentityKey, chapter.label))
        }
        item.allActions().forEach { action ->
            actions.add(ContextAction(action.label, Runnable { notificationCallbacks.performNotificationAction(action) }))
        }
        return actions
    }

    fun calendarActions(
        event: CalendarEvent,
        hasCalendarPermission: Boolean,
    ): List<ContextAction> {
        return listOf(
            ContextAction(labels.openCalendar, Runnable { calendarCallbacks.openCalendarEvent(event) }),
            ContextAction(
                if (hasCalendarPermission) labels.calendarAccess else labels.allowCalendar,
                Runnable { calendarCallbacks.requestCalendarAccess() },
            ),
            ContextAction(labels.settings, Runnable { calendarCallbacks.openSettings() }),
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
        actions.add(ContextAction(labels.open, Runnable { appCallbacks.openAppEntry(app) }))
        actions.add(ContextAction(if (pinned) labels.unpin else labels.pin, Runnable {
            if (pinned) {
                appCallbacks.unpinApp(app)
            } else {
                appCallbacks.pinApp(app)
            }
        }))
        actions.add(dockAction(app.identityKey, app.label))
        if (!classicPageCallbacks.isClassicPageApp(app.identityKey)) {
            actions.add(ContextAction(labels.addToClassicPage, Runnable { classicPageCallbacks.addAppToClassicPage(app) }))
        }
        appCallbacks.appShortcuts(app).take(MAX_SHORTCUTS).forEach { shortcut ->
            actions.add(ContextAction(shortcut.label, Runnable { appCallbacks.launchShortcut(shortcut) }))
        }
        actions.add(ContextAction(labels.info, Runnable { appCallbacks.openAppInfo(app) }))
        actions.add(ContextAction(labels.hide, Runnable { appCallbacks.hideApp(app) }, ContextActionCloseBehavior.REMOVE_CARD))
        return actions
    }

    fun dockAction(identityKey: String, label: String): ContextAction {
        return if (dockCallbacks.isDockItem(identityKey)) {
            ContextAction(labels.removeFromDock, Runnable { dockCallbacks.removeDockItem(identityKey, label) })
        } else {
            ContextAction(labels.addToDock, Runnable { dockCallbacks.addDockItem(identityKey, label) })
        }
    }
}
