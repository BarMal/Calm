package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherContextActionFactoryTest {
    @Test
    fun appActionsPinAndUnpinAccordingToPinnedState() {
        val events = ArrayList<String>()
        val factory = LauncherContextActionFactory(callbacks(events))
        val app = app("maps.pkg")

        val pinActions = factory.appActions(app, pinned = false)
        val unpinActions = factory.appActions(app, pinned = true)

        assertEquals(listOf("Open", "Pin", "Info", "Hide"), pinActions.labels())
        assertEquals(listOf("Open", "Unpin", "Info", "Hide"), unpinActions.labels())

        pinActions[1].action.run()
        unpinActions[1].action.run()
        pinActions[2].action.run()
        pinActions[3].action.run()

        assertEquals(listOf("pin:maps.pkg", "unpin:maps.pkg", "info:maps.pkg", "hide:maps.pkg"), events)
    }

    @Test
    fun appActionsHideRemovesCard() {
        val events = ArrayList<String>()
        val factory = LauncherContextActionFactory(callbacks(events))
        val app = app("maps.pkg")

        val actions = factory.appActions(app, pinned = false)
        val hideAction = actions.first { it.label == "Hide" }

        assertEquals(ContextActionCloseBehavior.REMOVE_CARD, hideAction.closeBehavior)
        hideAction.action.run()
        assertEquals(listOf("hide:maps.pkg"), events)
    }

    @Test
    fun calendarActionsReflectPermissionState() {
        val events = ArrayList<String>()
        val factory = LauncherContextActionFactory(callbacks(events))
        val event = CalendarEvent("Standup", begin = 10L, end = 20L, location = "", allDay = false)

        val deniedActions = factory.calendarActions(event, hasCalendarPermission = false)
        val grantedActions = factory.calendarActions(event, hasCalendarPermission = true)

        assertEquals(listOf("Open calendar", "Allow calendar", "Settings"), deniedActions.labels())
        assertEquals(listOf("Open calendar", "Calendar access", "Settings"), grantedActions.labels())

        deniedActions[0].action.run()
        deniedActions[1].action.run()
        deniedActions[2].action.run()

        assertEquals(listOf("calendar:Standup", "requestCalendarAccess", "settings"), events)
    }

    @Test
    fun notificationActionsKeepSystemActionsAndCardRemovalBehaviour() {
        val events = ArrayList<String>()
        val factory = LauncherContextActionFactory(callbacks(events))
        val notificationAction = NotificationAction("Reply", intent = null, remoteInputs = emptyList())
        val notification = notification("note-1", notificationAction)
        val item = NotificationCardItem(listOf(notification))
        val chapter = chapter("chat.pkg")

        val actions = factory.notificationActions(item, chapter)

        assertEquals(listOf("Open", "Open app", "Dismiss", "Clear", "Reply"), actions.labels())
        assertEquals(ContextActionCloseBehavior.RETURN_TO_STACK, actions[0].closeBehavior)
        assertEquals(ContextActionCloseBehavior.RETURN_TO_STACK, actions[1].closeBehavior)
        assertEquals(ContextActionCloseBehavior.REMOVE_CARD, actions[2].closeBehavior)
        assertEquals(ContextActionCloseBehavior.REMOVE_CARD, actions[3].closeBehavior)
        assertEquals(ContextActionCloseBehavior.RETURN_TO_STACK, actions[4].closeBehavior)

        actions.forEach { it.action.run() }

        assertEquals(
            listOf(
                "openNotification:note-1",
                "openPackage:chat.pkg",
                "dismiss:note-1",
                "clear:chat.pkg",
                "notificationAction:Reply",
            ),
            events,
        )
    }

    private fun callbacks(events: MutableList<String>): LauncherContextActionCallbacks {
        return LauncherContextActionCallbacks(
            openNotification = { events.add("openNotification:${it.key}") },
            openPackage = { events.add("openPackage:${it.packageName}") },
            dismissNotificationItem = { events.add("dismiss:${it.primary.key}") },
            clearChapter = { events.add("clear:${it.packageName}") },
            performNotificationAction = { events.add("notificationAction:${it.label}") },
            openCalendarEvent = { events.add("calendar:${it.title}") },
            requestCalendarAccess = { events.add("requestCalendarAccess") },
            openSettings = { events.add("settings") },
            openAppEntry = { events.add("openApp:${it.packageName}") },
            pinApp = { events.add("pin:${it.packageName}") },
            unpinApp = { events.add("unpin:${it.packageName}") },
            openAppInfo = { events.add("info:${it.packageName}") },
            hideApp = { events.add("hide:${it.packageName}") },
            appShortcuts = { emptyList() },
            launchShortcut = { },
        )
    }

    private fun List<ContextAction>.labels(): List<String> {
        return map { it.label }
    }

    private fun app(packageName: String): AppEntry {
        return AppEntry(packageName = packageName, label = "Maps", hueColor = 0xff123456.toInt())
    }

    private fun chapter(packageName: String): AppChapter {
        return AppChapter(
            packageName = packageName,
            label = "Chat",
            notifications = emptyList(),
            launchable = true,
            hueColor = 0xff123456.toInt(),
        )
    }

    private fun notification(
        key: String,
        action: NotificationAction,
    ): CalmNotificationListenerService.CalmNotification {
        return CalmNotificationListenerService.CalmNotification(
            key = key,
            packageName = "chat.pkg",
            title = "Alice",
            text = "Hello",
            subText = "",
            conversationTitle = "",
            postTime = 1L,
            contentIntent = null,
            backgroundImage = null,
            actions = listOf(action),
        )
    }
}
