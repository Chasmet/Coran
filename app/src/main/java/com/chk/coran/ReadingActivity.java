package com.chk.coran;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReadingActivity extends Activity {
    private final ExecutorService worker = Executors.newFixedThreadPool(2);
    private SharedPreferences prefs;
    private QuranRepository.Surah surah;
    private int surahId, currentVerse = 1, requestId = 0;
    private boolean continuous = false;
    private MediaPlayer player;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private LinearLayout container;
    private ScrollView scroll;
    private TextView progress;
    private Button play;
    private final List<View> cards = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_reading);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        surahId = getIntent().getIntExtra("surah", 1);
        if (surahId < 1 || surahId > 114) { finish(); return; }
        currentVerse = Math.max(1, getIntent().getIntExtra("jump", 1));
        container = findViewById(R.id.versesContainer);
        scroll = findViewById(R.id.readingScroll);
        progress = findViewById(R.id.progressLabel);
        play = findViewById(R.id.playButton);
        ((TextView) findViewById(R.id.surahHeading)).setText(SurahCatalog.name(surahId));
        ((TextView) findViewById(R.id.surahSubtitle)).setText("Lecture trilingue · appui long sur FR/EN pour écouter");
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.optionsButton).setOnClickListener(v ->
            startActivity(new Intent(this, SettingsActivity.class)));
        play.setOnClickListener(v -> {
            if (player != null && player.isPlaying()) {
                player.pause();
                play.setText("▶ Reprendre");
            } else if (player != null) {
                try {
                    player.start();
                    continuous = true;
                    play.setText("⏸ Pause");
                } catch (IllegalStateException ignored) { playVerse(currentVerse, true); }
            } else playVerse(currentVerse, true);
        });
        findViewById(R.id.previousButton).setOnClickListener(v ->
            playVerse(Math.max(1, currentVerse - 1), true));
        findViewById(R.id.nextButton).setOnClickListener(v ->
            playVerse(Math.min(surah == null ? 1 : surah.verses.size(), currentVerse + 1), true));
        findViewById(R.id.downloadAudioButton).setOnClickListener(v -> confirmAudioDownload());
        tts = new TextToSpeech(getApplicationContext(), status -> ttsReady = status == TextToSpeech.SUCCESS);
        load();
    }

    private void load() {
        progress.setText("Chargement des versets…");
        worker.execute(() -> {
            try {
                QuranRepository.Surah data = QuranRepository.load(getApplicationContext(), surahId);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    surah = data;
                    ((TextView) findViewById(R.id.surahHeading)).setText(data.name);
                    ((TextView) findViewById(R.id.surahSubtitle)).setText(data.arabicName
                        + " · " + data.verses.size() + " versets");
                    showVerses();
                    goTo(Math.min(currentVerse, data.verses.size()));
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progress.setText("Impossible de charger. Vérifiez Internet puis réessayez.");
                    new AlertDialog.Builder(this).setTitle("Chargement impossible")
                        .setMessage("Une connexion est nécessaire au premier accès à chaque sourate.\n\n" + ex.getMessage())
                        .setNegativeButton("Fermer", (d, w) -> finish())
                        .setPositiveButton("Réessayer", (d, w) -> load()).show();
                });
            }
        });
    }

    private void showVerses() {
        container.removeAllViews();
        cards.clear();
        int font = prefs.getInt("fontSize", 4);
        boolean ar = prefs.getBoolean("showArabic", true);
        boolean fr = prefs.getBoolean("showFrench", true);
        boolean en = prefs.getBoolean("showEnglish", false);
        Set<String> favorites = prefs.getStringSet("bookmarks", new HashSet<>());
        for (QuranRepository.Verse verse : surah.verses) {
            View card = LayoutInflater.from(this).inflate(R.layout.item_ayah, container, false);
            TextView number = card.findViewById(R.id.ayahNumber);
            number.setText(surahId + ":" + verse.number);
            TextView arabic = card.findViewById(R.id.arabicText);
            arabic.setText(verse.ar);
            arabic.setTextSize(25 + font);
            arabic.setVisibility(ar ? View.VISIBLE : View.GONE);
            TextView french = card.findViewById(R.id.frenchText);
            french.setText(verse.fr);
            french.setTextSize(14 + font / 2f);
            french.setVisibility(fr ? View.VISIBLE : View.GONE);
            french.setOnLongClickListener(v -> { speak(verse.fr, Locale.FRENCH); return true; });
            TextView english = card.findViewById(R.id.englishText);
            english.setText(verse.en);
            english.setTextSize(14 + font / 2f);
            english.setVisibility(en ? View.VISIBLE : View.GONE);
            english.setOnLongClickListener(v -> { speak(verse.en, Locale.ENGLISH); return true; });
            Button bookmark = card.findViewById(R.id.bookmarkButton);
            String key = surahId + ":" + verse.number;
            bookmark.setText(favorites.contains(key) ? "★" : "☆");
            bookmark.setOnClickListener(v -> {
                Set<String> changed = new HashSet<>(prefs.getStringSet("bookmarks", new HashSet<>()));
                if (!changed.add(key)) changed.remove(key);
                prefs.edit().putStringSet("bookmarks", changed).apply();
                bookmark.setText(changed.contains(key) ? "★" : "☆");
                savePosition(verse.number);
            });
            card.findViewById(R.id.ayahPlayButton).setOnClickListener(v -> playVerse(verse.number, false));
            card.setOnClickListener(v -> goTo(verse.number));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(10);
            container.addView(card, lp);
            cards.add(card);
        }
        progress.setText(surah.verses.size() + " versets · Arabe / Français / Anglais");
    }

    private void speak(String text, Locale locale) {
        if (!ttsReady || tts == null) {
            Toast.makeText(this, "Voix de synthèse indisponible sur ce téléphone", Toast.LENGTH_SHORT).show();
            return;
        }
        int result = tts.setLanguage(locale);
        if (result == TextToSpeech.LANG_NOT_SUPPORTED || result == TextToSpeech.LANG_MISSING_DATA) {
            Toast.makeText(this, "Installez la voix de cette langue dans Android", Toast.LENGTH_LONG).show();
            return;
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "translation_" + System.currentTimeMillis());
    }

    private int dp(int x) { return (int) (getResources().getDisplayMetrics().density * x + 0.5f); }
    private void savePosition(int verse) {
        prefs.edit().putInt("lastSurah", surahId).putInt("lastVerse", verse).apply();
    }
    private void goTo(int verse) {
        if (surah == null || verse < 1 || verse > surah.verses.size()) return;
        currentVerse = verse;
        savePosition(verse);
        if (cards.size() >= verse) {
            View card = cards.get(verse - 1);
            scroll.post(() -> scroll.smoothScrollTo(0, card.getTop()));
        }
        progress.setText(SurahCatalog.name(surahId) + " · verset " + verse + "/" + surah.verses.size());
    }

    private void stopPlayer() {
        if (player != null) {
            try { player.reset(); } catch (Exception ignored) {}
            player.release();
            player = null;
        }
        play.setText("▶ Écouter");
    }

    private void playVerse(int number, boolean continuePlaying) {
        if (surah == null || number < 1 || number > surah.verses.size()) return;
        ++requestId;
        int token = requestId;
        continuous = continuePlaying;
        stopPlayer();
        goTo(number);
        progress.setText("Préparation audio du verset " + number + "…");
        QuranRepository.Verse verse = surah.verses.get(number - 1);
        worker.execute(() -> {
            try {
                File audio = audioFile(verse.globalNumber);
                if (!audio.isFile() || audio.length() == 0) fetchAudio(verse.globalNumber);
                runOnUiThread(() -> {
                    if (token != requestId || isFinishing() || isDestroyed()) return;
                    try {
                        player = new MediaPlayer();
                        player.setDataSource(audio.getAbsolutePath());
                        player.setOnPreparedListener(p -> {
                            if (token != requestId) return;
                            p.start();
                            play.setText("⏸ Pause");
                            progress.setText("Récitation · " + SurahCatalog.name(surahId) + " : " + number);
                        });
                        player.setOnCompletionListener(p -> {
                            if (token != requestId || surah == null) return;
                            if (continuous && number < surah.verses.size()) playVerse(number + 1, true);
                            else { stopPlayer(); progress.setText("Récitation terminée"); }
                        });
                        player.setOnErrorListener((p, what, extra) -> {
                            stopPlayer();
                            progress.setText("Lecture audio impossible");
                            return true;
                        });
                        player.prepareAsync();
                    } catch (Exception ex) {
                        stopPlayer();
                        progress.setText("Erreur audio : " + ex.getMessage());
                    }
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    if (token == requestId && !isFinishing() && !isDestroyed())
                        progress.setText("Audio indisponible : " + ex.getMessage());
                });
            }
        });
    }

    private File audioFile(int global) {
        File dir = new File(getFilesDir(), "audio");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, global + ".mp3");
    }

    private void fetchAudio(int global) throws Exception {
        File destination = audioFile(global);
        if (destination.isFile() && destination.length() > 0) return;
        String link = "https://cdn.islamic.network/quran/audio/128/ar.alafasy/" + global + ".mp3";
        HttpURLConnection connection = (HttpURLConnection) new URL(link).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(45000);
        File temp = new File(destination.getParentFile(), global + ".part");
        try {
            if (connection.getResponseCode() != 200) throw new IllegalStateException("HTTP " + connection.getResponseCode());
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(temp)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = input.read(buf)) != -1) output.write(buf, 0, len);
            }
            if (temp.length() == 0) throw new IllegalStateException("Fichier vide");
            if (!temp.renameTo(destination)) {
                if (destination.exists()) destination.delete();
                if (!temp.renameTo(destination)) throw new IllegalStateException("Sauvegarde audio impossible");
            }
        } finally { connection.disconnect(); temp.delete(); }
    }

    private void confirmAudioDownload() {
        if (surah == null) return;
        new AlertDialog.Builder(this).setTitle("Audio hors connexion")
            .setMessage("Télécharger les " + surah.verses.size()
                + " fichiers audio de " + SurahCatalog.name(surahId)
                + " ? Cela peut nécessiter plusieurs dizaines de Mo.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Télécharger", (d, w) -> downloadAllAudio()).show();
    }

    private void downloadAllAudio() {
        if (surah == null) return;
        findViewById(R.id.downloadAudioButton).setEnabled(false);
        List<QuranRepository.Verse> copy = new ArrayList<>(surah.verses);
        worker.execute(() -> {
            int completed = 0;
            String error = null;
            for (QuranRepository.Verse verse : copy) {
                try { fetchAudio(verse.globalNumber); completed++; }
                catch (Exception ex) { error = ex.getMessage(); break; }
                final int done = completed;
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed())
                        progress.setText("Téléchargement audio " + done + "/" + copy.size());
                });
            }
            final String failure = error;
            final int total = completed;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                findViewById(R.id.downloadAudioButton).setEnabled(true);
                progress.setText(failure == null ? "Audio disponible hors connexion (" + total + " versets)"
                    : "Téléchargement interrompu : " + total + "/" + copy.size() + " · " + failure);
            });
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (surah != null) showVerses();
    }
    @Override protected void onDestroy() {
        requestId++;
        stopPlayer();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        worker.shutdownNow();
        super.onDestroy();
    }
}
