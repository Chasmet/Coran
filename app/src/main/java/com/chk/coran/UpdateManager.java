package com.chk.coran;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;

public final class UpdateManager {
    private UpdateManager() {}
    public interface Status { void show(String text); }
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static void check(Activity activity, boolean manual, Status status) {
        if (!BuildConfig.ENABLE_GITHUB_UPDATE) {
            send(status, "SkillMajAuto intégré : mises à jour en place réservées aux Releases signées.");
            return;
        }
        send(status, "Recherche de mise à jour…");
        new Thread(() -> {
            try {
                SkillMajAuto.Release release = new SkillMajAuto(activity).latest();
                if (release == null) {
                    send(status, "Votre version " + BuildConfig.VERSION_NAME + " est à jour.");
                    return;
                }
                MAIN.post(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    send(status, "Version " + release.version + " disponible");
                    new AlertDialog.Builder(activity).setTitle("Mise à jour du Coran")
                        .setMessage("La version " + release.version + " est disponible.")
                        .setNegativeButton("Plus tard", null)
                        .setPositiveButton("Télécharger et installer", (d, w) ->
                            download(activity, release.code, release.url, release.sha256, status)).show();
                });
            } catch (Exception error) {
                if (manual) {
                    if (error.getMessage() != null && error.getMessage().contains("HTTP 404"))
                        send(status, "Aucune Release signée publiée pour le moment.");
                    else send(status, "Vérification impossible : " + error.getMessage());
                }
            }
        }, "coran-update-check").start();
    }

    private static void download(Activity activity, int version, String link, String sha, Status status) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
            long pending = prefs.getLong("updateDownloadId", -1);
            if (pending >= 0) {
                poll(activity, status, pending);
                return;
            }
            String filename = "coran-v1.0." + version + ".apk";
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(link));
            request.setTitle("Coran · version 1.0." + version);
            request.setDescription("Mise à jour officielle depuis GitHub Releases");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            File directory = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (directory == null) throw new IllegalStateException("Stockage indisponible");
            File previous = new File(directory, filename);
            if (previous.exists() && !previous.delete())
                throw new IllegalStateException("Impossible de remplacer l’ancien téléchargement");
            request.setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, filename);
            DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) throw new IllegalStateException("Service téléchargement indisponible");
            long id = dm.enqueue(request);
            PreferenceManager.getDefaultSharedPreferences(activity).edit()
                .putLong("updateDownloadId", id)
                .putString("updateName", filename).putString("updateSha", sha)
                .putInt("updateVersion", version).apply();
            send(status, "Téléchargement de la mise à jour…");
            poll(activity, status, id);
        } catch (Exception error) { send(status, "Téléchargement impossible : " + error.getMessage()); }
    }

    public static void resumePending(Activity activity, Status status) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(activity);
        long id = pref.getLong("updateDownloadId", -1);
        if (id != -1) poll(activity, status, id);
    }

    private static void poll(Activity activity, Status status, long id) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) return;
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
        try (Cursor cursor = dm.query(query)) {
            if (cursor == null || !cursor.moveToFirst()) {
                clear(activity);
                send(status, "Téléchargement introuvable.");
                return;
            }
            int state = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if (state == DownloadManager.STATUS_SUCCESSFUL) {
                validateAndInstall(activity, status);
            } else if (state == DownloadManager.STATUS_FAILED) {
                clear(activity);
                send(status, "Échec du téléchargement. Réessayez depuis les réglages.");
            } else {
                long received = cursor.getLong(cursor.getColumnIndexOrThrow(
                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                long total = cursor.getLong(cursor.getColumnIndexOrThrow(
                    DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                String progress = total > 0 ? " · " + Math.min(100, received * 100 / total) + " %" : "";
                send(status, "Téléchargement de la mise à jour" + progress);
                MAIN.postDelayed(() -> poll(activity, status, id), 2500);
            }
        } catch (Exception ex) { send(status, "Erreur de téléchargement : " + ex.getMessage()); }
    }

    private static void validateAndInstall(Activity activity, Status status) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(activity);
        String name = pref.getString("updateName", "");
        String expected = pref.getString("updateSha", "");
        int version = pref.getInt("updateVersion", 0);
        if (!name.matches("coran-v1\\.0\\.\\d+\\.apk") || !expected.matches("[a-f0-9]{64}")) {
            clear(activity);
            send(status, "Informations de sécurité invalides");
            return;
        }
        File file = new File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name);
        send(status, "Vérification de l'APK et de sa signature…");
        new Thread(() -> {
            try {
                if (!file.isFile()) throw new SecurityException("Fichier APK absent");
                if (!expected.equals(sha256(file))) throw new SecurityException("Empreinte SHA256 différente");
                checkSignature(activity, file, version);
                MAIN.post(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
                        send(status, "Autorisez l'installation depuis cette application puis revenez aux réglages.");
                        Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(settings);
                        return;
                    }
                    try {
                        Uri content = FileProvider.getUriForFile(activity,
                            activity.getPackageName() + ".fileprovider", file);
                        Intent install = new Intent(Intent.ACTION_VIEW);
                        install.setDataAndType(content, "application/vnd.android.package-archive");
                        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        activity.startActivity(install);
                        clear(activity);
                        send(status, "Confirmez l’installation dans Android.");
                    } catch (Exception ex) { send(status, "Installation impossible : " + ex.getMessage()); }
                });
            } catch (Exception ex) {
                clear(activity);
                send(status, "Mise à jour refusée : " + ex.getMessage());
            }
        }, "coran-apk-verify").start();
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int size;
            while ((size = input.read(buf)) != -1) digest.update(buf, 0, size);
        }
        byte[] hash = digest.digest();
        StringBuilder text = new StringBuilder(64);
        for (byte b : hash) text.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return text.toString();
    }

    private static Signature[] signatures(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null) {
            return info.signingInfo.hasMultipleSigners()
                ? info.signingInfo.getApkContentsSigners()
                : info.signingInfo.getSigningCertificateHistory();
        }
        return info.signatures;
    }

    private static void checkSignature(Context activity, File apk, int expectedVersion) throws Exception {
        PackageManager pm = activity.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES
            : PackageManager.GET_SIGNATURES;
        PackageInfo mine = pm.getPackageInfo(activity.getPackageName(), flags);
        PackageInfo candidate = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (candidate == null || !activity.getPackageName().equals(candidate.packageName))
            throw new SecurityException("Identifiant d'application différent");
        long newCode = Build.VERSION.SDK_INT >= 28 ? candidate.getLongVersionCode() : candidate.versionCode;
        if (newCode != expectedVersion || newCode <= BuildConfig.VERSION_CODE)
            throw new SecurityException("Version de l'APK incorrecte");
        Signature[] current = signatures(mine), incoming = signatures(candidate);
        if (current == null || incoming == null || current.length != incoming.length)
            throw new SecurityException("Certificat de signature absent");
        for (Signature signature : current) {
            boolean match = false;
            for (Signature proposed : incoming) {
                if (Arrays.equals(signature.toByteArray(), proposed.toByteArray())) match = true;
            }
            if (!match) throw new SecurityException("Signature APK non conforme");
        }
    }

    /** Vérifie le téléchargement avant notification de la mise à jour. */
    public static boolean verifiedForNotification(Context context, long id) {
        if (!BuildConfig.ENABLE_GITHUB_UPDATE || BuildConfig.DEBUG || id < 0) return false;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (id != prefs.getLong("updateDownloadId", -1)) return false;
        String name = prefs.getString("updateName", "");
        String hash = prefs.getString("updateSha", "");
        int version = prefs.getInt("updateVersion", 0);
        if (!name.matches("coran-v1\\.0\\.\\d+\\.apk") || !hash.matches("[a-f0-9]{64}")) return false;
        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) return false;
        try (Cursor cursor = dm.query(new DownloadManager.Query().setFilterById(id))) {
            if (cursor == null || !cursor.moveToFirst() ||
                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    != DownloadManager.STATUS_SUCCESSFUL) return false;
            File directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (directory == null) return false;
            File apk = new File(directory, name).getCanonicalFile();
            if (!directory.getCanonicalFile().equals(apk.getParentFile()) ||
                !apk.isFile() || apk.length() < 10000 || !hash.equals(sha256(apk))) return false;
            checkSignature(context, apk, version);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void clear(Activity activity) {
        PreferenceManager.getDefaultSharedPreferences(activity).edit()
            .remove("updateDownloadId").remove("updateName")
            .remove("updateSha").remove("updateVersion").apply();
    }
    private static void send(Status status, String message) {
        if (status != null) MAIN.post(() -> status.show(message));
    }
}
