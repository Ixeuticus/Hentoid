package me.devsaki.hentoid.util.notification;

import android.content.Context;
import android.support.annotation.NonNull;

/**
 * Implement this and use {@link NotificationManager#notify(Notification)}
 */
public interface Notification {

    @NonNull
    android.app.Notification onCreateNotification(Context context);
}
