package com.chk.coran;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Enregistrements authentiques de la plateforme AlQuran Cloud.
 * ar.alafasy / fr.leclerc / en.walk ; un MP3 par ayah.
 * Les liens sont lus dans les métadonnées des éditions audio pour ne pas deviner le bitrate.
 */
public final class AudioRepository {
    private AudioRepository() {}
    private static final java.util.concurrent.ConcurrentHashMap<String, Object> FILE_LOCKS =
        new java.util.concurrent.ConcurrentHashMap<>();

    public static File audioFile(Context context, String language, int global) {
        File folder = new File(context.getFilesDir(), "audio/" + Languages.normalize(language));
        if (!folder.isDirectory()) folder.mkdirs();
        return new File(folder, global + ".mp3");
    }

    public static File ensureAudio(Context context, String lang, int surahId, int numberInSurah, int global)
        throws Exception {
        String language = Languages.normalize(lang);
        File destination = audioFile(context, language, global);
        Object lock = FILE_LOCKS.computeIfAbsent(destination.getAbsolutePath(), k -> new Object());
        synchronized (lock) {
        if (destination.isFile() && destination.length() >= 512) return destination;
        // Compatibilité avec les fichiers arabes téléchargés par la première version.
        if (Languages.ARABIC.equals(language)) {
            File old = new File(context.getFilesDir(), "audio/" + global + ".mp3");
            if (old.isFile() && old.length() >= 512) return old;
        }
        String audioUrl = audioUrl(context, language, surahId, numberInSurah, global);
        HttpURLConnection connection = (HttpURLConnection) new URL(audioUrl).openConnection();
        connection.setConnectTimeout(16000);
        connection.setReadTimeout(60000);
        connection.setRequestProperty("User-Agent", "Coran-Android/1.1");
        File tmp = new File(destination.getParentFile(), global + ".part");
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
                throw new IllegalStateException("Audio HTTP " + connection.getResponseCode());
            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) != -1) {
                    if (Thread.currentThread().isInterrupted())
                        throw new InterruptedException("Téléchargement interrompu");
                    out.write(buf, 0, n);
                }
            }
            if (tmp.length() < 512) throw new IllegalStateException("Audio vide ou incomplet");
            if (destination.exists() && !destination.delete()) throw new IllegalStateException("Cache verrouillé");
            if (!tmp.renameTo(destination)) throw new IllegalStateException("Enregistrement audio impossible");
            return destination;
        } finally {
            connection.disconnect();
            if (tmp.exists()) tmp.delete();
        }
        }
    }

    private static synchronized String audioUrl(Context context, String language, int surah, int verse, int global)
        throws Exception {
        File folder = new File(context.getFilesDir(), "audio_metadata");
        if (!folder.isDirectory()) folder.mkdirs();
        String edition = Languages.audioEdition(language);
        File cache = new File(folder, edition + "_" + surah + ".json");
        JSONObject json;
        if (cache.isFile()) {
            try {
                json = new JSONObject(new String(QuranRepository.readFile(cache), StandardCharsets.UTF_8));
                return fromMetadata(json, edition, surah, verse, global);
            } catch (Exception invalid) {
                cache.delete();
            }
        }
        String remote = QuranRepository.downloadText(
            "https://api.alquran.cloud/v1/surah/" + surah + "/" + edition);
        json = new JSONObject(remote);
        String url = fromMetadata(json, edition, surah, verse, global);
        File tmp = new File(folder, edition + "_" + surah + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(remote.getBytes(StandardCharsets.UTF_8));
        }
        if (!tmp.renameTo(cache)) {
            if (cache.exists()) cache.delete();
            if (!tmp.renameTo(cache)) tmp.delete();
        }
        return url;
    }

    private static String fromMetadata(JSONObject metadata, String edition, int surah, int verse, int global)
        throws Exception {
        if (metadata.optInt("code") != 200) throw new IllegalStateException("Edition audio indisponible");
        JSONObject data = metadata.getJSONObject("data");
        if (data.optInt("number") != surah) throw new IllegalStateException("Sourate audio incorrecte");
        String source = data.getJSONObject("edition").optString("identifier");
        if (!edition.equals(source)) throw new IllegalStateException("Edition audio inattendue");
        JSONArray ayahs = data.getJSONArray("ayahs");
        if (verse < 1 || verse > ayahs.length()) throw new IllegalArgumentException("Verset invalide");
        JSONObject ayah = ayahs.getJSONObject(verse - 1);
        if (ayah.getInt("number") != global || ayah.getInt("numberInSurah") != verse)
            throw new IllegalStateException("Audio non aligné avec le verset");
        String audio = ayah.optString("audio");
        URL parsed = new URL(audio);
        if (!"https".equalsIgnoreCase(parsed.getProtocol())
            || !("cdn.islamic.network".equalsIgnoreCase(parsed.getHost())
                || "cdn.islamic.ru".equalsIgnoreCase(parsed.getHost())
                || "cdn.alislam.ru".equalsIgnoreCase(parsed.getHost()))
            || !parsed.getPath().contains("/quran/audio/"))
            throw new SecurityException("Source audio non reconnue");
        return audio;
    }
}
