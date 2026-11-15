package com.chk.coran;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Méthode SkillMajAuto intégrée dans Coran, pas une application séparée. */
public final class SkillMajAuto {
    private static final String LATEST = "https://api.github.com/repos/Chasmet/Coran/releases/latest";
    private static final Pattern VERSION = Pattern.compile("^v1\\.0\\.(\\d+)$");
    private static final Pattern HASH = Pattern.compile("SHA256:\\s*([0-9A-Fa-f]{64})");
    private final Context app;

    public static final class Release {
        public final String version;
        public final int code;
        public final String url;
        public final String sha256;
        Release(String version, int code, String url, String sha256) {
            this.version = version;
            this.code = code;
            this.url = url;
            this.sha256 = sha256;
        }
    }

    public SkillMajAuto(Context context) { app = context.getApplicationContext(); }

    public boolean enabled() {
        return PreferenceManager.getDefaultSharedPreferences(app).getBoolean("autoUpdate", true);
    }

    public void setEnabled(boolean enabled) {
        PreferenceManager.getDefaultSharedPreferences(app)
            .edit().putBoolean("autoUpdate", enabled).apply();
        sync();
    }

    public void sync() {
        if (enabled() && BuildConfig.ENABLE_GITHUB_UPDATE && !BuildConfig.DEBUG) {
            SkillMajAutoWorker.schedule(app);
        } else SkillMajAutoWorker.cancel(app);
    }

    public Release latest() throws Exception {
        if (BuildConfig.DEBUG || !BuildConfig.ENABLE_GITHUB_UPDATE) return null;
        JSONObject doc = new JSONObject(QuranRepository.downloadText(LATEST));
        PreferenceManager.getDefaultSharedPreferences(app).edit()
            .putLong("lastUpdateCheck", System.currentTimeMillis()).apply();
        if (doc.optBoolean("draft") || doc.optBoolean("prerelease")) return null;
        Matcher ver = VERSION.matcher(doc.optString("tag_name", ""));
        if (!ver.matches()) throw new SecurityException("Version GitHub non reconnue");
        int code = Integer.parseInt(ver.group(1));
        if (code <= BuildConfig.VERSION_CODE) return null;
        Matcher sha = HASH.matcher(doc.optString("body", ""));
        if (!sha.find()) throw new SecurityException("Empreinte officielle SHA256 absente");
        String prefix = "https://github.com/Chasmet/Coran/releases/download/v1.0." + code + "/";
        JSONArray assets = doc.getJSONArray("assets");
        for (int i = 0; i < assets.length(); i++) {
            JSONObject entry = assets.getJSONObject(i);
            String name = entry.optString("name");
            String url = entry.optString("browser_download_url");
            if ("coran-release.apk".equals(name) && (prefix + name).equals(url)) {
                return new Release("1.0." + code, code, url, sha.group(1).toLowerCase(Locale.ROOT));
            }
        }
        throw new SecurityException("APK signé attendu absent de la Release");
    }
}
