package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherContextActionFactoryTest {
    @Test
    fun appActionsPinAndUnpinAccordingToPinnedState() {
        val events = ArrayList<String>()
        val factory = factory(events)
        val app = app("maps.pkg")

        val pinActions = factory.appActions(app, pinned = false)
        val unpinActions = factory.appActions(app, pinned = true)

        assertEquals(listOf("Open", "Pin", "Add to dock", "Add to Classic page", "Info", "Hide"), pinActions.labels())
        assertEquals(listOf("Open", "Unpin", "Add to dock", "Add to Classic page", "Info", "Hide"), unpinActions.labels())

        pinActions[1].action.run()
        unpinActions[1].action.run()
        pinActions[4].action.run()
        pinActions[5].action.run()

        assertEquals(listOf("pin:maps.pkg", "unpin:maps.pkg", "info:maps.pkg", "hide:maps.pkg"), events)
    }

    @Test
    fun appActionsHideRemovesCard() {
        val events = ArrayList<String>()
        val factory = factory(events)
        val app = app("maps.pkg")

        val actions = factory.appActions(app, pinned = false)
        val hideAction = actions.first { it.label == "Hide" }

        assertEquals(ContextActionCloseBehavior.REMOVE_CARD, hideAction.closeBehavior)
        hideAction.action.run()
        assertEquals(listOf("hide:maps.pkg"), events)
    }

    @Test
    fun appActionsIncludeShortcutsBeforeInfoAndHide() {
        val events = ArrayList<String>()
        val shortcuts = listOf(
            shortcut("Compose", "compose"),
            shortcut("Search", "search"),
            shortcut("Scan", "scan"),
            shortcut("Overflow", "overflow"),
        )
        val factory = factory(events, shortcuts)
        val app = app("maps.pkg")

        val actions = factory.appActions(app, pinned = false)

        assertEquals(
            listOf("Open", "Pin", "Add to dock", "Add to Classic page", "Compose", "Search", "Scan", "Info", "Hide"),
            actions.labels(),
        )
        actions[4].action.run()
        actions[6].action.run()

        assertEquals(listOf("shortcut:compose", "shortcut:scan"), events)
    }

    @Test
    fun appActionsRemoveFromDockWhenAlreadyDocked() {
        val events = ArrayList<String>()
        val factory = factory(events, dockedKeys = setOf("maps.pkg"))
        val app = app("maps.pkg")

        val actions = factory.appActions(app, pinned = false)

        assertEquals("Remove from dock", actions[2].label)
        actions[2].action.run()
        assertEquals(listOf("removeDock:maps.pkg:Maps"), events)
    }

    @Test
    fun appActionsAddToClassicPageWhenNotAlreadyPlaced() {
        val events = ArrayList<String>()
        val factory = factory(events)
        val app = app("maps.pkg")

        val actions = factory.appActions(app, pinned = false)

        val addAction = actions.first { it.label == "Add to Classic page" }
        addAction.action.run()
        assertEquals(listOf("addClassic:maps.pkg"), events)
    }

    @Test
    fun appActionsSkipClassicPageActionWhenAlreadyPlaced() {
        val events = ArrayList<String>()
        val factory = factory(events, classicPageKeys = setOf("maps.pkg"))
        val app = app("maps.pkg")

        val actions = factory.appActions(app, pinned = false)

        assertEquals(listOf("Open", "Pin", "Add to dock", "Info", "Hide"), actions.labels())
    }

    @Test
    fun calendarActionsReflectPermissionState() {
        val events = ArrayList<String>()
        val factory = factory(events)
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
        val factory = factory(events)
        val notificationAction = NotificationAction("Reply", intent = null, remoteInputs = emptyList())
        val notification = notification("note-1", notificationAction)
        val item = NotificationCardItem(listOf(notification))
        val chapter = chapter("chat.pkg")

        val actions = factory.notificationActions(item, chapter)

        assertEquals(listOf("Open", "Open app", "Add to dock", "Dismiss", "Clear", "Reply"), actions.labels())
        assertEquals(ContextActionCloseBehavior.RETURN_TO_STACK, actions[0].closeBehavior)
        assertEquals(ContextActionCloseBehavior.RETURN_TO_STACK, actions[1].closeBehavior)
        assertEquals(ContextActionCloseBehavior.RETURN_TO_STACK, actions[2].closeBehavior)
        assertEquals(ContextActionCloseBehavior.REMOVE_CARD, actions[3].closeBehavior)
        assertEquals(ContextActionCloseBehavior.REMOVE_CARD, actions[4].closeBehavior)
        assertEquals(ContextActionCloseBehavior.RETURN_TO_STACK, actions[5].closeBehavior)

        actions.forEach { it.action.run() }

        assertEquals(
            listOf(
                "openNotification:note-1",
                "openPackage:chat.pkg",
                "addDock:chat.pkg:Chat",
                "dismiss:note-1",
                "clear:chat.pkg",
                "notificationAction:Reply",
            ),
            events,
        )
    }

    @Test
    fun dockActionOnlyRequiresDockCallbacks() {
        val events = ArrayList<String>()
        val factory = LauncherContextActionFactory(
            dockCallbacks = DockContextActionCallbacks(
                isDockItem = { it == "maps.pkg" },
                addDockItem = { key, label -> events.add("addDock:$key:$label") },
                removeDockItem = { key, label -> events.add("removeDock:$key:$label") },
            ),
        )

        val action = factory.dockAction("maps.pkg", "Maps")

        assertEquals("Remove from dock", action.label)
        action.action.run()
        assertEquals(listOf("removeDock:maps.pkg:Maps"), events)
    }

    private fun factory(
        events: MutableList<String>,
        shortcuts: List<AppShortcutEntry> = emptyList(),
        dockedKeys: Set<String> = emptySet(),
        classicPageKeys: Set<String> = emptySet(),
    ): LauncherContextActionFactory {
        return LauncherContextActionFactory(
            notificationCallbacks = NotificationContextActionCallbacks(
                openNotification = { events.add("openNotification:${it.key}") },
                openPackage = { events.add("openPackage:${it.packageName}") },
                dismissNotificationItem = { events.add("dismiss:${it.primary.key}") },
                clearChapter = { events.add("clear:${it.packageName}") },
                performNotificationAction = { events.add("notificationAction:${it.label}") },
            ),
            calendarCallbacks = CalendarContextActionCallbacks(
                openCalendarEvent = { events.add("calendar:${it.title}") },
                requestCalendarAccess = { events.add("requestCalendarAccess") },
                openSettings = { events.add("settings") },
            ),
            appCallbacks = AppContextActionCallbacks(
                openAppEntry = { events.add("openApp:${it.packageName}") },
                pinApp = { events.add("pin:${it.packageName}") },
                unpinApp = { events.add("unpin:${it.packageName}") },
                openAppInfo = { events.add("info:${it.packageName}") },
                hideApp = { events.add("hide:${it.packageName}") },
                appShortcuts = { shortcuts },
                launchShortcut = { events.add("shortcut:${it.id}") },
            ),
            dockCallbacks = DockContextActionCallbacks(
                isDockItem = { it in dockedKeys },
                addDockItem = { key, label -> events.add("addDock:$key:$label") },
                removeDockItem = { key, label -> events.add("removeDock:$key:$label") },
            ),
            classicPageCallbacks = ClassicPageContextActionCallbacks(
                isClassicPageApp = { it in classicPageKeys },
                addAppToClassicPage = { events.add("addClassic:${it.identityKey}") },
            ),
        )
    }

    @Test
    fun notificationActionsSkipDockForNonLaunchableChapters() {
        val events = ArrayList<String>()
        val factory = factory(events)
        val notification = notification("note-1", NotificationAction("Reply", intent = null, remoteInputs = emptyList()))
        val item = NotificationCardItem(listOf(notification))
        val chapter = chapter("chat.pkg", launchable = false)

        val actions = factory.notificationActions(item, chapter)

        assertEquals(listOf("Open", "Open app", "Dismiss", "Clear", "Reply"), actions.labels())
    }

    private fun List<ContextAction>.labels(): List<String> {
        return map { it.label }
    }

    private fun app(packageName: String): AppEntry {
        return AppEntry(packageName = packageName, label = "Maps", hueColor = 0xff123456.toInt())
    }

    private fun shortcut(label: String, id: String): AppShortcutEntry {
        return AppShortcutEntry(label, id, "maps.pkg", null)
    }

    private fun chapter(packageName: String, launchable: Boolean = true): AppChapter {
        return AppChapter(
            packageName = packageName,
            label = "Chat",
            notifications = emptyList(),
            launchable = launchable,
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
