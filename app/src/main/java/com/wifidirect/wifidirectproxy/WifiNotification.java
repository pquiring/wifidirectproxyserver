package com.wifidirect.wifidirectproxy;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;

/**
 * Helper class for showing and canceling wifi notifications.
 */

public class WifiNotification {
    /**
     * The unique identifier for this type of notification.
     */
    private static final String NOTIFICATION_TAG = "com.wifidirect.wifidirectproxy.Wifi";
    private static NotificationChannel mChannel;
    private static String channelID = "com.wifidirect.wifidirectproxy.WifiChannel";
    private static Context context;
    private static Notification notification;

    public static void initOnce(Context context) {
        if (mChannel != null) return;
        WifiNotification.context = context;
        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // The user-visible name of the channel.
        CharSequence name = "WifiNotifyChannel";

        // The user-visible description of the channel.
        String description = "WifiNotification Channel";

        int importance = NotificationManager.IMPORTANCE_LOW;

        NotificationChannel mChannel = new NotificationChannel(channelID, name,importance);

// Configure the notification channel.
        mChannel.setDescription(description);

        mChannel.enableLights(true);
// Sets the notification light color for notifications posted to this
// channel, if the device supports this feature.
        mChannel.setLightColor(android.graphics.Color.GREEN);

//        mChannel.enableVibration(true);
//        mChannel.setVibrationPattern(new long[]{100, 200, 300, 400, 500, 400, 300, 200, 400});

        mNotificationManager.createNotificationChannel(mChannel);
    }

    public static Notification create() {
        String ticker = "WifiDirect+Proxy";
        String title = "WifiDirect+Proxy";
        String text = "WifiDirect+Proxy Running";

        Notification.Builder builder = new Notification.Builder(context, "WifiChannel")
            .setChannelId(channelID)

            // Set required fields, including the small icon, the
            // notification title, and text.
            .setSmallIcon(R.drawable.ic_stat_wifi)
            .setContentTitle(title)
            .setContentText(text)

            // Provide a large icon, shown with the notification in the
            // notification drawer on devices running Android 3.0 or later.
//                .setLargeIcon(picture)

            // Set ticker text (preview) information for this notification.
            .setTicker(ticker)

            // Show a number. This is useful when stacking notifications of
            // a single type.
            //.setNumber(number)

            // If this notification relates to a past or upcoming event, you
            // should set the relevant time information using the setWhen
            // method below. If this call is omitted, the notification's
            // timestamp will by set to the time at which it was shown.
            //.setWhen(...)

            // Set the pending intent to be initiated when the user touches
            // the notification.
            .setContentIntent(PendingIntent.getActivity(context,1
                , new Intent(context, MainActivity.class)
                , PendingIntent.FLAG_UPDATE_CURRENT));

        notification = builder.build();
        return notification;
    }
}
