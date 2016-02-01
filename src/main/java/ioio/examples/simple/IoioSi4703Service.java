package ioio.examples.simple;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOService;

/**
 * Created by dgey on 26.01.16.
 */

public class IoioSi4703Service extends IOIOService {

    Si4703Looper si4703Looper;

    @Override
    protected IOIOLooper createIOIOLooper() {
        si4703Looper = new Si4703Looper(this);
        return si4703Looper;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int result = super.onStartCommand(intent, flags, startId);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case IoioActions.ACTION_TUNE:
                    si4703Looper.tune(Integer.parseInt(intent.getStringExtra(IoioActions.EXTRA_PARAM1)));
                    break;
                case IoioActions.ACTION_SEEK:
                    si4703Looper.seek(Boolean.parseBoolean(intent.getStringExtra(IoioActions.EXTRA_PARAM1)),
                            Boolean.parseBoolean(intent.getStringExtra(IoioActions.EXTRA_PARAM2)));
                    break;
                case IoioActions.ACTION_MUTE:
                    si4703Looper.setMute(Boolean.parseBoolean(intent.getStringExtra(IoioActions.EXTRA_PARAM1)));
                    break;
                case IoioActions.ACTION_SETVOLUME:
                    si4703Looper.setSi4703Volume(Integer.parseInt(intent.getStringExtra(IoioActions.EXTRA_PARAM1)));
                    break;
                case IoioActions.ACTION_POWER:
                    //si4703Looper.setPower(Boolean.parseBoolean(intent.getStringExtra(IOIOActions.EXTRA_PARAM1)));
                    break;
                case IoioActions.ACTION_SEND_COMMAND:
                    si4703Looper.sendCommand();
                    break;
                case IoioActions.STOPSERVICE:
                    nm.cancel(0);
                    stopSelf();
                    break;
                default:
            }
        }  else {
            // Service starting. Create a notification.
            Notification notification = new Notification(
                    R.drawable.icon, "IOIO service running",
                    System.currentTimeMillis());
            notification
                    .setLatestEventInfo(this, "IOIO Service", "Click to stop",
                            PendingIntent.getService(this, 0, new Intent(
                                    IoioActions.STOPSERVICE, null, this, this.getClass()), 0));
            notification.flags |= Notification.FLAG_ONGOING_EVENT;
            nm.notify(0, notification);
            sendBroadcast(new Intent(IoioActions.ENABLEUI));
        }
        return result;
    }

    public static void tune(Context context, int frequency) {
        executeCommand(context, IoioActions.ACTION_TUNE, Integer.toString(frequency), null );
    }

    public static void mute(Context context, boolean muted) {
        executeCommand(context, IoioActions.ACTION_MUTE, Boolean.toString(muted), null );
    }

    public static void seek(Context context, boolean seekUp, boolean bWaitForSTC) {
        executeCommand(context, IoioActions.ACTION_SEEK, Boolean.toString(seekUp), Boolean.toString(bWaitForSTC) );
    }

    public static void setVolume(Context context, int volume) {
        executeCommand(context, IoioActions.ACTION_SETVOLUME, Integer.toString(volume), null );
    }

    public static void setPower(Context context, boolean powerOn) {
        executeCommand(context, IoioActions.ACTION_POWER, Boolean.toString(powerOn), null );
    }

    public static void sendCommand(Context context) {
        executeCommand(context, IoioActions.ACTION_SEND_COMMAND, null, null );
    }

    private static void executeCommand(Context context, final String ACTION, String param1, String param2) {
        Intent intent = new Intent(context, IoioSi4703Service.class);
        intent.setAction(ACTION);
        intent.putExtra(IoioActions.EXTRA_PARAM1, param1);
        intent.putExtra(IoioActions.EXTRA_PARAM2, param2);
        context.startService(intent);
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

}
