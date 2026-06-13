# Android entry points are instantiated by the framework from manifest or system
# callbacks, so keep their public surface stable through release shrinking.
-keep class dev.barna.calm.MainActivity { *; }
-keep class dev.barna.calm.CalmSettingsActivity { *; }
-keep class * extends android.service.notification.NotificationListenerService { *; }
-keep class * extends android.appwidget.AppWidgetProvider { *; }

# AndroidX Security depends on Tink, whose release metadata references
# compile-time annotation packages that are not needed at runtime.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy
