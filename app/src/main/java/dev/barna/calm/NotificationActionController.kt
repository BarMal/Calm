package dev.barna.calm

import android.app.AlertDialog
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.UserHandle
import android.widget.EditText
import android.widget.Toast
import androidx.viewpager2.widget.ViewPager2

class NotificationActionController(
    private val activity: MainActivity,
    private val settings: LauncherSettings,
    private val notificationRepository: NotificationChapterRepository,
    private val pageRemovalPlanner: ChapterPageRemovalPlanner,
    private val entryAnimator: LauncherEntryAnimator,
    private val render: () -> Unit,
    private val selectPage: (String) -> Unit,
    private val currentPages: () -> List<ChapterPage>,
    private val currentPager: () -> ViewPager2?,
    private val openAppInfo: (packageName: String, userHandle: UserHandle?, componentName: String?) -> Unit,
    private val openSettings: () -> Unit,
) {
    fun openNotification(notification: CalmNotificationListenerService.CalmNotification) {
        val contentIntent: PendingIntent? = notification.contentIntent
        if (contentIntent != null) {
            try {
                contentIntent.send()
                return
            } catch (_: PendingIntent.CanceledException) {
            }
        }
        val apps = notificationRepository.loadAppEntries()
        val app = apps.firstOrNull { it.notificationSourceKey == notification.sourceKey }
            ?: apps.firstOrNull { it.packageName == notification.packageName }
        if (app == null || !notificationRepository.openApp(app)) {
            Toast.makeText(activity, "This notification cannot be opened", Toast.LENGTH_SHORT).show()
        }
    }

    fun dismissNotificationItem(item: NotificationCardItem) {
        CalmNotificationListenerService.dismissNotifications(item.notifications.map { it.cancelKey })
        Toast.makeText(activity, if (item.isGroup) "Dismissed notification group" else "Dismissed notification", Toast.LENGTH_SHORT).show()
        render()
    }

    fun clearChapter(chapter: AppChapter) {
        CalmNotificationListenerService.clearPackage(chapter.packageName, chapter.notifications.firstOrNull()?.userSerial ?: AppIdentity.LEGACY_USER_SERIAL)
        Toast.makeText(activity, "Cleared ${chapter.label}", Toast.LENGTH_SHORT).show()
        render()
    }

    fun performNotificationAction(action: NotificationAction) {
        val intent = action.intent
        if (intent == null) {
            Toast.makeText(activity, "Action is unavailable", Toast.LENGTH_SHORT).show()
            return
        }
        if (action.requiresInput) {
            promptForNotificationActionInput(action)
            return
        }
        try {
            intent.send()
        } catch (_: PendingIntent.CanceledException) {
            Toast.makeText(activity, "Action expired", Toast.LENGTH_SHORT).show()
        }
    }

    fun showNotificationHideOptions(item: NotificationCardItem, chapter: AppChapter) {
        val options = ArrayList<Pair<String, () -> Unit>>()
        options.add("App info" to { openAppInfo(chapter.packageName, chapter.userHandle, chapter.componentName) })
        options.add("Settings" to { openSettings() })
        options.add("Hide all notifications from app" to { excludeNotificationSource(chapter) })

        val title = item.primary.title.trim()
        val body = item.primary.bodyText().trim()
        if (title.isBlank() && body.isBlank()) {
            options.add("Hide empty notifications from app" to {
                addNotificationFilter(NotificationFilter.emptyContent(chapter.identityKey, chapter.packageName), "Hidden empty notifications")
            })
        }

        if (title.isNotBlank()) {
            options.add("Hide all notifications with title\n$title" to {
                addNotificationFilter(NotificationFilter.title(chapter.identityKey, chapter.packageName, title), "Hidden matching title")
            })
            addFlexibleNotificationFilterOptions(
                options = options,
                label = "title",
                text = title,
                containsFilter = { NotificationFilter.titleContains(chapter.identityKey, chapter.packageName, it) },
                wildcardFilter = { NotificationFilter.titleWildcard(chapter.identityKey, chapter.packageName, it) },
            )
        } else {
            options.add("Hide all notifications with no title" to {
                addNotificationFilter(NotificationFilter.title(chapter.identityKey, chapter.packageName, ""), "Hidden notifications with no title")
            })
        }

        if (body.isNotBlank()) {
            options.add("Hide all notifications with body\n${body.take(80)}" to {
                addNotificationFilter(NotificationFilter.body(chapter.identityKey, chapter.packageName, body), "Hidden matching body")
            })
            addFlexibleNotificationFilterOptions(
                options = options,
                label = "body",
                text = body,
                containsFilter = { NotificationFilter.bodyContains(chapter.identityKey, chapter.packageName, it) },
                wildcardFilter = { NotificationFilter.bodyWildcard(chapter.identityKey, chapter.packageName, it) },
            )
        } else {
            options.add("Hide all notifications with no body" to {
                addNotificationFilter(NotificationFilter.body(chapter.identityKey, chapter.packageName, ""), "Hidden notifications with no body")
            })
        }

        AlertDialog.Builder(activity)
            .setTitle("Hide notifications")
            .setItems(options.map { it.first }.toTypedArray()) { _, which ->
                options[which].second.invoke()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun excludeNotificationSource(chapter: AppChapter) {
        val nextPageKey = pageRemovalPlanner.selectPageAfterRemoval(currentPages(), chapter.identityKey)
        settings.exclude(chapter)
        selectPage(nextPageKey)
        Toast.makeText(activity, "Excluded ${chapter.label}", Toast.LENGTH_SHORT).show()
        val pager = currentPager()
        if (pager == null) {
            render()
            return
        }
        entryAnimator.animateCurrentPageRemoval(pager) { render() }
    }

    fun restoreNotificationSource(packageName: String) {
        settings.restore(packageName)
        Toast.makeText(activity, "Restored notification source", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun promptForNotificationActionInput(action: NotificationAction) {
        val input = EditText(activity).apply {
            setSingleLine(false)
            minLines = 1
            maxLines = 4
            setTextColor(CalmTheme.INK)
            setHintTextColor(CalmTheme.MUTED_INK)
            hint = action.remoteInputs.firstOrNull()?.label ?: action.label
            setPadding(activity.dp(18), activity.dp(12), activity.dp(18), activity.dp(12))
        }
        AlertDialog.Builder(activity)
            .setTitle(action.label)
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Send") { _, _ ->
                sendRemoteInputAction(action, input.text?.toString().orEmpty())
            }
            .show()
    }

    private fun sendRemoteInputAction(action: NotificationAction, text: String) {
        val intent = action.intent ?: return
        val fillInIntent = Intent()
        val results = android.os.Bundle()
        action.remoteInputs.forEach { remoteInput ->
            results.putCharSequence(remoteInput.resultKey, text)
        }
        RemoteInput.addResultsToIntent(action.remoteInputs.toTypedArray(), fillInIntent, results)
        try {
            intent.send(activity, 0, fillInIntent)
        } catch (_: PendingIntent.CanceledException) {
            Toast.makeText(activity, "Action expired", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addFlexibleNotificationFilterOptions(
        options: MutableList<Pair<String, () -> Unit>>,
        label: String,
        text: String,
        containsFilter: (String) -> NotificationFilter,
        wildcardFilter: (String) -> NotificationFilter,
    ) {
        val preview = text.take(80)
        options.add("Hide notifications containing $label\n$preview" to {
            addNotificationFilter(containsFilter(text), "Hidden notifications containing $label")
        })
        val pattern = NotificationFilterPattern.generalizeNumbers(text) ?: return
        options.add("Hide similar notifications with $label\n${pattern.take(80)}" to {
            addNotificationFilter(wildcardFilter(pattern), "Hidden similar notifications")
        })
    }

    private fun addNotificationFilter(filter: NotificationFilter, message: String) {
        settings.addNotificationFilter(filter)
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        render()
    }
}
