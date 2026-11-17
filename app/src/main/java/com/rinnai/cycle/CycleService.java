package com.rinnai.cycle;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CycleService extends Service {
    public static final String ACTION_START = "com.rinnai.cycle.START";
    public static final String ACTION_STOP = "com.rinnai.cycle.STOP";
    public static final String ACTION_STATUS = "com.rinnai.cycle.STATUS";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_MINUTES = "minutes";

    private static final String CHANNEL_ID = "cycle";
    private static final int NOTIFICATION_ID = 1;

    private ScheduledExecutorService executor;
    private volatile boolean running;
    private volatile int remainingSeconds;
    private volatile int totalSeconds;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "循环控制", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopCycle();
            return START_NOT_STICKY;
        }
        int minutes = intent != null ? intent.getIntExtra(EXTRA_MINUTES, 3) : 3;
        startCycle(minutes);
        return START_NOT_STICKY;
    }

    private void startCycle(int minutes) {
        if (running) {
            stopCycle();
        }
        running = true;
        totalSeconds = Math.max(1, minutes) * 60;
        remainingSeconds = totalSeconds;
        startForeground(NOTIFICATION_ID, notification("正在连接并启动循环…"));

        final ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor();
        executor = ex;
        ex.execute(() -> {
            try {
                RinnaiMqtt.send(Config.load(this), "01");
                broadcast("循环已启动，将在 " + format(totalSeconds) + " 后关闭");
                ex.scheduleAtFixedRate(() -> {
                    if (!running) return;
                    remainingSeconds--;
                    if (remainingSeconds <= 0) {
                        finishCycle();
                    } else {
                        updateNotification("循环中 · 剩余 " + format(remainingSeconds));
                        broadcast("循环中，剩余 " + format(remainingSeconds));
                    }
                }, 1, 1, TimeUnit.SECONDS);
            } catch (Exception e) {
                running = false;
                broadcast("启动失败: " + e.getMessage());
                updateNotification("启动失败: " + e.getMessage());
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        });
    }

    private void finishCycle() {
        if (!running) return;
        running = false;
        updateNotification("正在关闭循环…");
        ScheduledExecutorService ex = executor;
        if (ex == null) {
            stopSelf();
            return;
        }
        ex.execute(() -> {
            try {
                RinnaiMqtt.send(Config.load(this), "00");
                broadcast("循环已关闭");
                updateNotification("循环已关闭");
            } catch (Exception e) {
                broadcast("关闭失败: " + e.getMessage());
                updateNotification("关闭失败: " + e.getMessage());
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        });
    }

    private void stopCycle() {
        running = false;
        ScheduledExecutorService ex = executor;
        if (ex != null) {
            ex.shutdownNow();
            executor = null;
        }
        new Thread(() -> {
            try {
                RinnaiMqtt.send(Config.load(this), "00");
                broadcast("循环已关闭");
            } catch (Exception e) {
                broadcast("关闭失败: " + e.getMessage());
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        }).start();
    }

    private void broadcast(String text) {
        Intent intent = new Intent(ACTION_STATUS);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_STATUS, text);
        sendBroadcast(intent);
    }

    private String format(int seconds) {
        return String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private void updateNotification(String text) {
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification(text));
    }

    private Notification notification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("林内循环")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
