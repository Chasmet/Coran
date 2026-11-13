package com.chk.coran;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class QuranRepository {
    private QuranRepository() {}
    public static final class Verse {
        public int number, globalNumber;
        public String ar, fr, en;
    }
    public static final class Surah {
        public int number;
        public String name;
        public String arabicName;
        public final List<Verse> verses = new ArrayList<>();
    }

    public static Surah load(Context context, int number) throws Exception {
        if (number < 1 || number > 114) throw new IllegalArgumentException("Numéro de sourate invalide");
        File dir = new File(context.getFilesDir(), "surahs");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Stockage inaccessible");
        File cache = new File(dir, number + ".json");
        if (cache.isFile()) {
            try {
                return parse(number, new String(readFile(cache), StandardCharsets.UTF_8));
            } catch (Exception ignored) { /* cache endommagé : récupération en ligne */ }
        }
        String url = "https://api.alquran.cloud/v1/surah/" + number
            + "/editions/quran-uthmani,fr.hamidullah,en.sahih";
        String json = downloadText(url);
        Surah surah = parse(number, json);
        File tmp = new File(dir, number + ".tmp");
        try (FileOutputStream output = new FileOutputStream(tmp)) {
            output.write(json.getBytes(StandardCharsets.UTF_8));
        }
        if (!tmp.renameTo(cache)) { // remplacement de cache si nécessaire
            if (cache.exists()) cache.delete();
            if (!tmp.renameTo(cache)) tmp.delete();
        }
        return surah;
    }

    private static Surah parse(int number, String raw) throws Exception {
        JSONObject root = new JSONObject(raw);
        if (root.optInt("code") != 200) throw new IllegalStateException("Réponse Coran invalide");
        JSONArray editions = root.getJSONArray("data");
        JSONObject ar = null, fr = null, en = null;
        for (int i = 0; i < editions.length(); i++) {
            JSONObject ed = editions.getJSONObject(i);
            String id = ed.getJSONObject("edition").optString("identifier");
            if ("quran-uthmani".equals(id)) ar = ed;
            if ("fr.hamidullah".equals(id)) fr = ed;
            if ("en.sahih".equals(id)) en = ed;
        }
        if (ar == null || fr == null || en == null) throw new IllegalStateException("Éditions manquantes");
        JSONArray a = ar.getJSONArray("ayahs"), f = fr.getJSONArray("ayahs"), e = en.getJSONArray("ayahs");
        if (a.length() != f.length() || a.length() != e.length() || a.length() == 0)
            throw new IllegalStateException("Nombre de versets incohérent");
        Surah surah = new Surah();
        surah.number = number;
        surah.name = ar.optString("englishName", SurahCatalog.name(number));
        surah.arabicName = ar.optString("name", "");
        for (int i = 0; i < a.length(); i++) {
            JSONObject va = a.getJSONObject(i), vf = f.getJSONObject(i), ve = e.getJSONObject(i);
            int n = va.getInt("numberInSurah");
            if (vf.getInt("numberInSurah") != n || ve.getInt("numberInSurah") != n)
                throw new IllegalStateException("Traductions non alignées");
            Verse verse = new Verse();
            verse.number = n;
            verse.globalNumber = va.getInt("number");
            verse.ar = va.getString("text");
            verse.fr = vf.getString("text");
            verse.en = ve.getString("text");
            surah.verses.add(verse);
        }
        return surah;
    }

    public static byte[] readFile(File f) throws Exception {
        try (InputStream input = new FileInputStream(f)) { return read(input); }
    }
    public static byte[] read(InputStream input) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int len;
        while ((len = input.read(buf)) >= 0) out.write(buf, 0, len);
        return out.toByteArray();
    }
    public static String downloadText(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "Coran-Android/1.0");
        try {
            if (connection.getResponseCode() != 200)
                throw new IllegalStateException("Service indisponible : HTTP " + connection.getResponseCode());
            try (InputStream in = connection.getInputStream()) {
                return new String(read(in), StandardCharsets.UTF_8);
            }
        } finally { connection.disconnect(); }
    }
}
