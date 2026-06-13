# Android entry points are instantiated by the framework from manifest or system
# callbacks, so keep their public surface stable through release shrinking.
-keep class dev.barna.calm.MainActivity { *; }
-keep class dev.barna.calm.CalmSettingsActivity { *; }
-keep class * extends android.service.notification.NotificationListenerService { *; }
-keep class * extends android.appwidget.AppWidgetProvider { *; }
