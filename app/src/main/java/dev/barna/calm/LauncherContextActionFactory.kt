package dev.barna.calm

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
    val appShortcuts: (AppChapter) -> List<AppShortcutEntry>,
    val launchShortcut: (AppShortcutEntry) -> Unit,
)

class LauncherContextActionFactory(
    private val callbacks: LauncherContextActionCallbacks,
) {
    fun notificationActions(
        item: NotificationCardItem,
        chapter: AppChapter,
    ): List<ContextAction> {
        val actions = ArrayList<ContextAction>()
        actions.addAll(
            listOf(
                ContextAction("Open", Runnable { callbacks.openNotification(item.primary) }),
                ContextAction("Open app", Runnable { callbacks.openPackage(chapter) }),
                ContextAction(
                    "Dismiss",
                    Runnable { callbacks.dismissNotificationItem(item) },
                    ContextActionCloseBehavior.REMOVE_CARD,
                ),
                ContextAction(
                    "Clear",
                    Runnable { callbacks.clearChapter(chapter) },
                    ContextActionCloseBehavior.REMOVE_CARD,
                ),
            ),
        )
        item.allActions().forEach { action ->
            actions.add(ContextAction(action.label, Runnable { callbacks.performNotificationAction(action) }))
        }
        callbacks.appShortcuts(chapter).take(MAX_SHORTCUTS).forEach { shortcut ->
            actions.add(ContextAction(shortcut.label, Runnable { callbacks.launchShortcut(shortcut) }))
        }
        return actions
    }

    fun calendarActions(
        event: CalendarEvent,
        hasCalendarPermission: Boolean,
    ): List<ContextAction> {
        return listOf(
            ContextAction("Open calendar", Runnable { callbacks.openCalendarEvent(event) }),
            ContextAction(
                if (hasCalendarPermission) "Calendar access" else "Allow calendar",
                Runnable { callbacks.requestCalendarAccess() },
            ),
            ContextAction("Settings", Runnable { callbacks.openSettings() }),
        )
    }

    private companion object {
        const val MAX_SHORTCUTS = 3
    }

    fun appActions(
        app: AppEntry,
        pinned: Boolean,
    ): List<ContextAction> {
        return listOf(
            ContextAction("Open", Runnable { callbacks.openAppEntry(app) }),
            ContextAction(if (pinned) "Unpin" else "Pin", Runnable {
                if (pinned) {
                    callbacks.unpinApp(app)
                } else {
                    callbacks.pinApp(app)
                }
            }),
            ContextAction("Info", Runnable { callbacks.openAppInfo(app) }),
        )
    }
}
