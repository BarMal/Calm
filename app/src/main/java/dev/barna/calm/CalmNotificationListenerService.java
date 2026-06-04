package dev.barna.calm;

import android.app.Notification;
import android.app.PendingIntent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class CalmNotificationListenerService extends NotificationListenerService {
    private static final Object LOCK = new Object();
    private static final Set<Runnable> LISTENERS = new CopyOnWriteArraySet<>();
    private static CalmNotificationListenerService currentService;
    private static List<CalmNotification> currentNotifications = Collections.emptyList();

    public static final class CalmNotification {
        public final String key;
        public final String packageName;
        public final String title;
        public final String text;
        public final String subText;
        public final long postTime;
        public final PendingIntent contentIntent;
        public final Bitmap backgroundImage;

        CalmNotification(
                String key,
                String packageName,
                String title,
                String text,
                String subText,
                long postTime,
                PendingIntent contentIntent,
                Bitmap backgroundImage
        ) {
            this.key = key;
            this.packageName = packageName;
            this.title = title;
            this.text = text;
            this.subText = subText;
            this.postTime = postTime;
            this.contentIntent = contentIntent;
            this.backgroundImage = backgroundImage;
        }
    }

    public static void addListener(Runnable listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(Runnable listener) {
        LISTENERS.remove(listener);
    }

    public static List<CalmNotification> snapshot() {
        synchronized (LOCK) {
            return new ArrayList<>(currentNotifications);
        }
    }

    public static boolean isConnected() {
        synchronized (LOCK) {
            return currentService != null;
        }
    }

    public static void clearPackage(String packageName) {
        CalmNotificationListenerService service;
        synchronized (LOCK) {
            service = currentService;
        }

        if (service == null) {
            return;
        }

        StatusBarNotification[] activeNotifications = service.getActiveNotifications();
        if (activeNotifications == null) {
            return;
        }

        for (StatusBarNotification status : activeNotifications) {
            if (status != null && packageName.equals(status.getPackageName())) {
                service.cancelNotification(status.getKey());
            }
        }
        service.refreshSnapshot();
    }

    @Override
    public void onListenerConnected() {
        synchronized (LOCK) {
            currentService = this;
        }
        refreshSnapshot();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        refreshSnapshot();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        refreshSnapshot();
    }

    @Override
    public void onDestroy() {
        synchronized (LOCK) {
            if (currentService == this) {
                currentService = null;
                currentNotifications = Collections.emptyList();
            }
        }
        notifyListeners();
        super.onDestroy();
    }

    private void refreshSnapshot() {
        List<CalmNotification> next = new ArrayList<>();
        StatusBarNotification[] activeNotifications = getActiveNotifications();
        if (activeNotifications != null) {
            for (StatusBarNotification status : activeNotifications) {
                CalmNotification notification = toCalmNotification(status);
                if (notification != null) {
                    next.add(notification);
                }
            }
        }

        synchronized (LOCK) {
            currentNotifications = next;
        }
        notifyListeners();
    }

    private CalmNotification toCalmNotification(StatusBarNotification status) {
        if (status == null || status.getNotification() == null) {
            return null;
        }

        Notification notification = status.getNotification();
        CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence subText = notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT);

        String resolvedTitle = title == null ? "" : title.toString();
        String resolvedText = text == null ? "" : text.toString();
        String resolvedSubText = subText == null ? "" : subText.toString();
        if (resolvedTitle.isEmpty() && resolvedText.isEmpty() && notification.tickerText != null) {
            resolvedText = notification.tickerText.toString();
        }

        return new CalmNotification(
                status.getKey(),
                status.getPackageName(),
                resolvedTitle,
                resolvedText,
                resolvedSubText,
                status.getPostTime(),
                notification.contentIntent,
                notificationBackground(notification)
        );
    }

    private Bitmap notificationBackground(Notification notification) {
        Object picture = notification.extras.get(Notification.EXTRA_PICTURE);
        if (picture instanceof Bitmap) {
            return (Bitmap) picture;
        }

        Object largeIconExtra = notification.extras.get(Notification.EXTRA_LARGE_ICON);
        if (largeIconExtra instanceof Bitmap) {
            return (Bitmap) largeIconExtra;
        }

        Icon largeIcon = notification.getLargeIcon();
        if (largeIcon == null) {
            return null;
        }

        Drawable drawable = largeIcon.loadDrawable(this);
        if (drawable == null) {
            return null;
        }

        int width = Math.max(1, drawable.getIntrinsicWidth());
        int height = Math.max(1, drawable.getIntrinsicHeight());
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    private void notifyListeners() {
        for (Runnable listener : LISTENERS) {
            listener.run();
        }
    }
}
