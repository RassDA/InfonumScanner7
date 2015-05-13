package ru.infonum.infonumscanner7;

import android.view.View;

/**
 * Created by d1i on 13.05.15.
 */
public class Notifications {
    public static final int NOTIFICATION_ID = 1;

    public final String INFONUM_SITE = "http://infonum.ru/";
    public final String num = "a000aa78";
    public final String NTF_TITLE = "Инфонум: ";
    public final String NTF_TEXT = ": Подойдите к авто!";
    public final String NTF_SUBTEXT = "Нажмите сюда, чтобы перейти на страницу номера или смахните, чтобы удалить ";



    public void sendNotification(View view) {
/*
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(s + num));
        //PendingIntent pendingIntent = PendingIntent.getActivity(get, 0, intent, 0);


        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setLargeIcon(BitmapFactory.decodeResource(getSystem(), R.drawable.ic_launcher))
                .setContentTitle(NTF_TITLE + num)
                .setContentText(num + NTF_TEXT);
        //.setSubText(INFONUM_SITE + num + NTF_SUBTEXT);

        //NotificationManager notificationManager = (NotificationManager) getSystemService(
        //        NOTIFICATION_SERVICE);

        //notificationManager.notify(NOTIFICATION_ID, builder.getNotification());
        //notificationManager.notify(NOTIFICATION_ID, builder.build());
        //notificationManager.notify(NOTIFICATION_ID, build());
*/
    }

}
