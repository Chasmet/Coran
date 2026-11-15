package com.chk.coran;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

/** Détection automatique via WorkManager (12 heures au mieux, sans horaire garanti). */
public final class SkillMajAutoWorker extends Worker {
    private static final String JOB = "coran_skillmajauto_check";
    private static final String CHANNEL = "coran_skillmajauto";

    public SkillMajAutoWorker(Context context, WorkerParameters params) { super(context, params); }

    public static void schedule(Context context) {
        if (BuildConfig.DEBUG || !BuildConfig.ENABLE_GITHUB_UPDATE) return;
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
            SkillMajAutoWorker.class, 12, TimeUnit.HOURS).setConstraints(constraints).build();
        WorkManager.getInstance(context.getApplicationContext())
            .enqueueUniquePeriodicWork(JOB, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    public static void cancel(Context context) {
        WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(JOB);
    }

    static void notifyRelease(Context context, String version, boolean ready) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26)
            manager.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Coran · mises à jour", NotificationManager.IMPORTANCE_DEFAULT));
        Intent open = new Intent(context, SettingsActivity.class);
        open.setAction(ready ? "com.chk.coran.UPDATE_READY" : "com.chk.coran.UPDATE_AVAILABLE");
        PendingIntent pending = PendingIntent.getActivity(context, ready ? 42 : 41,
            open, PendingIntent.FLAG_UPDATE_CURRENT |
            (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(context, CHANNEL) : new Notification.Builder(context);
        builder.setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(ready ? "Coran : APK vérifié" : "Coran " + version + " disponible")
            .setContentText(ready ? "Ouvrir les réglages pour confirmer l'installation."
                : "Une nouvelle version officielle est disponible.")
            .setContentIntent(pending).setAutoCancel(true);
        manager.notify(ready ? 42 : 41, builder.build());
    }

    @Override public Result doWork() {
        Context app = getApplicationContext();
        SkillMajAuto updater = new SkillMajAuto(app);
        if (!updater.enabled() || BuildConfig.DEBUG) return Result.success();
        try {
            SkillMajAuto.Release release = updater.latest();
            if (release == null) return Result.success();
            SharedPreferences prefs = app.getSharedPreferences("coran_update_notice", Context.MODE_PRIVATE);
            if (!release.version.equals(prefs.getString("last_notified", ""))) {
                notifyRelease(app, release.version, false);
                prefs.edit().putString("last_notified", release.version).apply();
            }
        } catch (Exception ignored) {
            return Result.retry();
        }
        return Result.success();
    }
}
