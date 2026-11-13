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
import android.widget.Toast;
import androidx.core.content.FileProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateManager {
    private UpdateManager() {}
    public interface Status { void show(String text); }
    private static final String ENDPOINT = "https://api.github.com/repos/Chasmet/Coran/releases/latest";
    private static final Pattern TAG = Pattern.compile("^v1\\.0\\.(\\d+)$");
    private static final Pattern HASH = Pattern.compile("SHA256:\\s*([0-9a-fA-F]{64})");
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static void check(Activity activity, boolean manual, Status status) {
        if (!BuildConfig.ENABLE_GITHUB_UPDATE) {
            send(status, "Les mises à jour intégrées sont réservées à l'APK de Release signé.");
            return;
        }
        send(status, "Recherche de mise à jour…");
        new Thread(() -> {
            try {
                JSONObject release = new JSONObject(QuranRepository.downloadText(ENDPOINT));
                Matcher matcher = TAG.matcher(release.getString("tag_name"));
                if (!matcher.matches()) throw new IllegalStateException("Tag de version inattendu");
                int newer = Integer.parseInt(matcher.group(1));
                SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(activity);
                pref.edit().putLong("lastUpdateCheck", System.currentTimeMillis()).apply();
                if (newer <= BuildConfig.VERSION_CODE) {
                    send(status, "Votre version " + BuildConfig.VERSION_NAME + " est à jour.");
                    return;
                }
                JSONArray assets = release.getJSONArray("assets");
                String link = null;
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    if ("coran-release.apk".equals(asset.optString("name"))) {
                        link = asset.getString("browser_download_url");
                        break;
                    }
                }
                if (link == null || !trusted(link)) throw new SecurityException("APK officiel introuvable");
                Matcher hashMatch = HASH.matcher(release.optString("body"));
                if (!hashMatch.find()) throw new SecurityException("Empreinte SHA256 manquante");
                final String apkUrl = link;
                final String sha = hashMatch.group(1).toLowerCase(Locale.ROOT);
                MAIN.post(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    send(status, "Version 1.0." + newer + " disponible");
                    new AlertDialog.Builder(activity).setTitle("Mise à jour du Coran")
                        .setMessage("Une nouvelle version 1.0." + newer
                            + " est disponible. Vos favoris et vos sourates seront conservés.")
                        .setNegativeButton("Plus tard", null)
                        .setPositiveButton("Télécharger", (d, w) ->
                            download(activity, newer, apkUrl, sha, status)).show();
                });
            } catch (Exception error) {
                if (manual) send(status, "Vérification impossible : " + error.getMessage());
            }
        }, "coran-update-check").start();
    }

    private static boolean trusted(String link) {
        try {
            URL url = new URL(link);
            return "https".equals(url.getProtocol()) && "github.com".equals(url.getHost())
                && url.getUserInfo() == null
                && url.getPath().startsWith("/Chasmet/Coran/releases/download/")
                && url.getPath().endsWith("/coran-release.apk");
        } catch (Exception ignored) { return false; }
    }

    private static void download(Activity activity, int version, String link, String sha, Status status) {
        try {
            String filename = "coran-v1.0." + version + ".apk";
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(link));
            request.setTitle("Coran · version 1.0." + version);
            request.setDescription("Mise à jour officielle depuis GitHub Releases");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
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
                        clear(activity);
                        send(status, "L'installation nécessite votre confirmation Android.");
                        activity.startActivity(install);
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

    private static void checkSignature(Activity activity, File apk, int expectedVersion) throws Exception {
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

    private static void clear(Activity activity) {
        PreferenceManager.getDefaultSharedPreferences(activity).edit()
            .remove("updateDownloadId").remove("updateName")
            .remove("updateSha").remove("updateVersion").apply();
    }
    private static void send(Status status, String message) {
        if (status != null) MAIN.post(() -> status.show(message));
    }
}
