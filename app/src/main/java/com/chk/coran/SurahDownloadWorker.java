package com.chk.coran;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.List;

/**
 * Télécharge les MP3 de toute une sourate sous WorkManager ForegroundInfo.
 * Le téléchargement continue écran verrouillé, sans imposer l'ouverture de l'écran audio.
 */
public final class SurahDownloadWorker extends Worker {
    public static final String INPUT_SURAH = "surah";
    public static final String INPUT_LANGUAGE = "language";
    public static final String PREFS = "coran_audio_downloads";
    public static final String ACTION_PROGRESS = "com.chk.coran.audio.DOWNLOAD_PROGRESS";
    private static final String CHANNEL = "coran_audio_downloads";

    public SurahDownloadWorker(Context context, WorkerParameters parameters) {
        super(context, parameters);
    }

    public static String key(String part, String language, int surah) {
        return part + "_" + Languages.normalize(language) + "_" + surah;
    }

    private static int noticeId(String language, int surah) {
        return 24000 + surah * 3 + (Languages.ARABIC.equals(language) ? 2
            : (Languages.ENGLISH.equals(language) ? 1 : 0));
    }

    private Notification notice(String title, String text, int done, int total) {
        Context context = getApplicationContext();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL, "Téléchargements audio du Coran", NotificationManager.IMPORTANCE_LOW));
            }
        }
        Intent open = new Intent(context, AudioLibraryActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT |
            (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pending = PendingIntent.getActivity(context, 24001,
            open, flags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(context, CHANNEL) : new Notification.Builder(context);
        builder.setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title).setContentText(text)
            .setContentIntent(pending).setOnlyAlertOnce(true)
            .setOngoing(done < total);
        if (total > 0) builder.setProgress(total, done, false);
        return builder.build();
    }

    private ForegroundInfo foreground(String title, String description, int done, int total,
                                     String language, int surah) {
        Notification notification = notice(title, description, done, total);
        if (Build.VERSION.SDK_INT >= 29) {
            return new ForegroundInfo(noticeId(language, surah), notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        }
        return new ForegroundInfo(noticeId(language, surah), notification);
    }

    private void progress(int surah, String language, String state, boolean complete) {
        getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key("status", language, surah), state)
            .putBoolean(key("complete", language, surah), complete)
            .apply();
        getApplicationContext().sendBroadcast(new Intent(ACTION_PROGRESS)
            .setPackage(getApplicationContext().getPackageName()));
    }

    @Override public Result doWork() {
        Context app = getApplicationContext();
        int surah = getInputData().getInt(INPUT_SURAH, 0);
        String language = Languages.normalize(getInputData().getString(INPUT_LANGUAGE));
        if (surah < 1 || surah > 114) return Result.failure();
        String title = Languages.flag(language) + " " + SurahCatalog.name(surah);
        progress(surah, language, "Téléchargement audio en préparation…", false);
        try {
            setForegroundAsync(foreground(title, "Préparation de la sourate", 0, 0,
                language, surah)).get();
            QuranRepository.Surah verses = QuranRepository.load(app, surah);
            List<QuranRepository.Verse> all = verses.verses;
            int total = all.size();
            for (int i = 0; i < total; i++) {
                if (isStopped()) {
                    progress(surah, language, "Téléchargement interrompu", false);
                    return Result.failure();
                }
                QuranRepository.Verse v = all.get(i);
                AudioRepository.ensureAudio(app, language, surah, v.number, v.globalNumber);
                int downloaded = i + 1;
                progress(surah, language, downloaded + "/" + total + " versets enregistrés", false);
                if (downloaded == 1 || downloaded % 6 == 0 || downloaded == total) {
                    NotificationManager manager = (NotificationManager)
                        app.getSystemService(Context.NOTIFICATION_SERVICE);
                    if (manager != null) manager.notify(noticeId(language, surah),
                        notice(title, downloaded + "/" + total + " versets",
                            downloaded, total));
                }
            }
            progress(surah, language, "✓ Sourate complète disponible hors connexion", true);
            return Result.success();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            progress(surah, language, "Téléchargement interrompu", false);
            return Result.failure();
        } catch (Exception error) {
            progress(surah, language, "Erreur : " + error.getMessage(), false);
            return getRunAttemptCount() < 3 ? Result.retry() : Result.failure();
        }
    }
}
