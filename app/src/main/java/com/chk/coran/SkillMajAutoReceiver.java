package com.chk.coran;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Ne traite que les téléchargements créés par Coran et vérifiés par SkillMajAuto. */
public final class SkillMajAutoReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
        final PendingResult finishLater = goAsync();
        new Thread(() -> {
            try {
                if (UpdateManager.verifiedForNotification(context.getApplicationContext(), id)) {
                    SkillMajAutoWorker.notifyRelease(context.getApplicationContext(), "", true);
                }
            } finally {
                finishLater.finish();
            }
        }, "coran-update-receiver").start();
    }
}
