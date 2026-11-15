package com.chk.coran;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ReadingActivity extends Activity {
    private final ExecutorService worker = Executors.newFixedThreadPool(2);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<View> cards = new ArrayList<>();
    private SharedPreferences prefs;
    private QuranRepository.Surah surah;
    private String language;
    private int surahId, currentVerse = 1, activeVerse = -1, requestId = 0;
    private MediaPlayer player;
    private boolean prepared, continuous;
    private LinearLayout container;
    private ScrollView scroll;
    private TextView progress, play;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (player != null && prepared) {
                updateProgress();
                handler.postDelayed(this, 400);
            }
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_reading);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        language = Languages.normalize(getIntent().getStringExtra("language"));
        if (!getIntent().hasExtra("language")) language = Languages.selected(this);
        surahId = getIntent().getIntExtra("surah", 1);
        if (surahId < 1 || surahId > 114) { finish(); return; }
        currentVerse = Math.max(1, getIntent().getIntExtra("jump", 1));
        container = findViewById(R.id.versesContainer);
        scroll = findViewById(R.id.readingScroll);
        progress = findViewById(R.id.progressLabel);
        play = findViewById(R.id.playButton);
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.optionsButton).setOnClickListener(v ->
            startActivity(new Intent(this, SettingsActivity.class)));
        bindTab(R.id.readFrench, Languages.FRENCH);
        bindTab(R.id.readEnglish, Languages.ENGLISH);
        bindTab(R.id.readArabic, Languages.ARABIC);
        findViewById(R.id.previousButton).setOnClickListener(v ->
            playVerse(Math.max(1, currentVerse - 1), prefs.getBoolean("autoNext", true)));
        findViewById(R.id.nextButton).setOnClickListener(v ->
            playVerse(Math.min(surah == null ? 1 : surah.verses.size(), currentVerse + 1), prefs.getBoolean("autoNext", true)));
        play.setOnClickListener(v -> {
            if (!togglePlayback(prefs.getBoolean("autoNext", true))) playVerse(currentVerse, prefs.getBoolean("autoNext", true));
        });
        findViewById(R.id.downloadAudioButton).setOnClickListener(v -> confirmDownload());
        updateLanguageHeader();
        load();
    }

    private void bindTab(int viewId, String code) {
        findViewById(viewId).setOnClickListener(v -> {
            if (code.equals(language)) return;
            language = code;
            Languages.select(this, code);
            requestId++;
            stopPlayer();
            updateLanguageHeader();
            if (surah != null) { render(); goTo(currentVerse, false); }
        });
    }
    private void updateLanguageHeader() {
        for (int i = 0; i < 3; i++) {
            String code = i == 0 ? Languages.FRENCH : (i == 1 ? Languages.ENGLISH : Languages.ARABIC);
            TextView tab = findViewById(i == 0 ? R.id.readFrench : (i == 1 ? R.id.readEnglish : R.id.readArabic));
            boolean selected = code.equals(language);
            tab.setBackgroundResource(selected ? R.drawable.tab_selected : R.drawable.tab_normal);
            tab.setTextColor(getResources().getColor(selected ? android.R.color.white : R.color.primary_dark));
        }
        ((TextView) findViewById(R.id.readingLanguage)).setText(
            Languages.flag(language) + " " + Languages.name(language) + " · " + Languages.narrator(language));
        ((TextView) findViewById(R.id.audioCredit)).setText(Languages.text(language,
            "Voix française : Youssouf Leclerc", "English audio: Ibrahim Walk",
            "القارئ: مشاري العفاسي"));
        ((TextView) findViewById(R.id.downloadAudioButton)).setText(Languages.text(language,
            "⬇ Télécharger", "⬇ Download", "⬇ تحميل"));
        play.setText(Languages.text(language, "▶ Écouter", "▶ Listen", "▶ استماع"));
    }

    private void load() {
        progress.setText(Languages.text(language, "Chargement de la sourate…", "Loading surah…", "جار تحميل السورة…"));
        worker.execute(() -> {
            try {
                QuranRepository.Surah data = QuranRepository.load(getApplicationContext(), surahId);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    surah = data;
                    ((TextView) findViewById(R.id.surahHeading)).setText(data.name);
                    ((TextView) findViewById(R.id.surahSubtitle)).setText(
                        data.arabicName + " · " + data.verses.size() + " versets");
                    render();
                    goTo(Math.min(currentVerse, data.verses.size()), true);
                });
            } catch (Exception err) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progress.setText("Chargement impossible");
                    new AlertDialog.Builder(this).setTitle("Sourate non disponible")
                        .setMessage("Connexion nécessaire au premier chargement, puis les textes restent en cache.\n\n" + err.getMessage())
                        .setNegativeButton("Fermer", (d, w) -> finish())
                        .setPositiveButton("Réessayer", (d, w) -> load()).show();
                });
            }
        });
    }

    private void render() {
        if (surah == null) return;
        container.removeAllViews();
        cards.clear();
        int font = prefs.getInt("fontSize", 4);
        boolean ar = language.equals(Languages.ARABIC) || prefs.getBoolean("showArabic", true);
        boolean fr = language.equals(Languages.FRENCH) || prefs.getBoolean("showFrench", false);
        boolean en = language.equals(Languages.ENGLISH) || prefs.getBoolean("showEnglish", false);
        Set<String> marks = prefs.getStringSet("bookmarks", new HashSet<>());
        for (QuranRepository.Verse verse : surah.verses) {
            View card = LayoutInflater.from(this).inflate(R.layout.item_ayah, container, false);
            TextView number = card.findViewById(R.id.ayahNumber);
            number.setText("✦ " + surahId + ":" + verse.number);
            String ref = surahId + ":" + verse.number;
            TextView arabic = card.findViewById(R.id.arabicText);
            arabic.setText(verse.ar);
            arabic.setTextSize(25 + font);
            arabic.setVisibility(ar ? View.VISIBLE : View.GONE);
            TextView french = card.findViewById(R.id.frenchText);
            french.setText(verse.fr);
            french.setTextSize(language.equals(Languages.FRENCH) ? 17 + font / 2f : 14 + font / 2f);
            french.setTypeface(null, language.equals(Languages.FRENCH) ? 1 : 0);
            french.setVisibility(fr ? View.VISIBLE : View.GONE);
            TextView english = card.findViewById(R.id.englishText);
            english.setText(verse.en);
            english.setTextSize(language.equals(Languages.ENGLISH) ? 17 + font / 2f : 14 + font / 2f);
            english.setTypeface(null, language.equals(Languages.ENGLISH) ? 1 : 0);
            english.setVisibility(en ? View.VISIBLE : View.GONE);
            TextView bookmark = card.findViewById(R.id.bookmarkButton);
            bookmark.setText(marks.contains(ref) ? "★" : "☆");
            bookmark.setOnClickListener(v -> {
                Set<String> updated = new HashSet<>(prefs.getStringSet("bookmarks", new HashSet<>()));
                if (!updated.add(ref)) updated.remove(ref);
                prefs.edit().putStringSet("bookmarks", updated).apply();
                bookmark.setText(updated.contains(ref) ? "★" : "☆");
                savePosition(verse.number);
            });
            TextView audioButton = card.findViewById(R.id.ayahPlayButton);
            audioButton.setOnClickListener(v -> {
                if (verse.number == activeVerse && togglePlayback(false)) return;
                playVerse(verse.number, false);
            });
            card.findViewById(R.id.ayahBody).setOnClickListener(v -> goTo(verse.number, false));
            SeekBar seek = card.findViewById(R.id.ayahProgress);
            seek.setMax(100);
            seek.setProgress(0);
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {}
                @Override public void onStartTrackingTouch(SeekBar bar) {}
                @Override public void onStopTrackingTouch(SeekBar bar) {
                    if (player != null && prepared && activeVerse == verse.number) {
                        try {
                            player.seekTo(Math.min(bar.getProgress(), Math.max(1, player.getDuration())));
                            updateProgress();
                        } catch (Exception ignored) {}
                    }
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(12);
            container.addView(card, lp);
            cards.add(card);
        }
        updateProgress();
    }

    private int dp(int v) {
        return (int) (getResources().getDisplayMetrics().density * v + 0.5f);
    }
    private void savePosition(int verse) {
        prefs.edit().putInt("lastSurah", surahId).putInt("lastVerse", verse).apply();
    }
    private void goTo(int number, boolean scrollToVerse) {
        if (surah == null || number < 1 || number > surah.verses.size()) return;
        currentVerse = number;
        savePosition(number);
        progress.setText(Languages.flag(language) + " " + surah.name + " · "
            + number + "/" + surah.verses.size());
        if (scrollToVerse && cards.size() >= number) {
            View card = cards.get(number - 1);
            scroll.post(() -> scroll.smoothScrollTo(0, card.getTop()));
        }
    }
    private boolean togglePlayback(boolean autoNext) {
        if (player == null || !prepared) return false;
        try {
            if (player.isPlaying()) {
                player.pause();
            } else {
                player.start();
                continuous = autoNext;
            }
            updateProgress();
            return true;
        } catch (Exception ignored) { return false; }
    }
    private void stopPlayer() {
        handler.removeCallbacks(ticker);
        prepared = false;
        if (player != null) {
            try { player.reset(); } catch (Exception ignored) {}
            player.release();
            player = null;
        }
        activeVerse = -1;
        updateProgress();
    }
    private String clock(int durationMillis) {
        int sec = Math.max(0, durationMillis) / 1000;
        return String.format(java.util.Locale.ROOT, "%d:%02d", sec / 60, sec % 60);
    }
    private void updateProgress() {
        boolean playing = false;
        int position = 0, duration = 0;
        if (player != null && prepared) {
            try {
                playing = player.isPlaying();
                position = player.getCurrentPosition();
                duration = player.getDuration();
            } catch (Exception ignored) {}
        }
        if (play != null) play.setText(playing ? "⏸ Pause" :
            Languages.text(language, "▶ Écouter", "▶ Listen", "▶ استماع"));
        for (int index = 0; index < cards.size(); index++) {
            View card = cards.get(index);
            boolean active = (index + 1) == activeVerse;
            TextView button = card.findViewById(R.id.ayahPlayButton);
            button.setText(active && playing ? "❚❚" : "▶");
            TextView elapsed = card.findViewById(R.id.ayahElapsed);
            TextView total = card.findViewById(R.id.ayahDuration);
            SeekBar seek = card.findViewById(R.id.ayahProgress);
            if (active && prepared) {
                elapsed.setText(clock(position));
                total.setText(clock(duration));
                seek.setMax(Math.max(1, duration));
                seek.setProgress(position);
            } else if (!active) {
                elapsed.setText("0:00");
                total.setText("—");
                seek.setMax(100);
                seek.setProgress(0);
            }
        }
        if (player != null && prepared) {
            handler.removeCallbacks(ticker);
            handler.postDelayed(ticker, 400);
        }
    }

    private void playVerse(int number, boolean autoNext) {
        if (surah == null || number < 1 || number > surah.verses.size()) return;
        ++requestId;
        final int ticket = requestId;
        final String languageForRequest = language;
        stopPlayer();
        activeVerse = number;
        continuous = autoNext;
        goTo(number, true);
        progress.setText(Languages.text(language, "Chargement audio du verset ", "Loading audio for verse ",
            "تحميل صوت الآية ") + number + "…");
        int global = surah.verses.get(number - 1).globalNumber;
        worker.execute(() -> {
            try {
                File audio = AudioRepository.ensureAudio(getApplicationContext(),
                    languageForRequest, surahId, number, global);
                runOnUiThread(() -> {
                    if (ticket != requestId || isFinishing() || isDestroyed()) return;
                    try {
                        player = new MediaPlayer();
                        player.setDataSource(audio.getAbsolutePath());
                        player.setOnPreparedListener(mp -> {
                            if (ticket != requestId) return;
                            prepared = true;
                            mp.start();
                            updateProgress();
                            progress.setText(Languages.flag(language) + " " + Languages.narrator(language)
                                + " · " + surahId + ":" + number);
                        });
                        player.setOnCompletionListener(mp -> {
                            if (ticket != requestId) return;
                            if (continuous && number < surah.verses.size()) {
                                playVerse(number + 1, true);
                            } else {
                                stopPlayer();
                                progress.setText(Languages.text(language, "Lecture terminée",
                                    "Playback finished", "انتهى الاستماع"));
                            }
                        });
                        player.setOnErrorListener((mp, what, extra) -> {
                            if (ticket == requestId) {
                                stopPlayer();
                                progress.setText("Impossible de lire cet enregistrement");
                            }
                            return true;
                        });
                        player.prepareAsync();
                    } catch (Exception error) {
                        stopPlayer();
                        progress.setText("Audio : " + error.getMessage());
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (ticket != requestId || isFinishing() || isDestroyed()) return;
                    stopPlayer();
                    progress.setText("Audio indisponible : " + error.getMessage());
                    Toast.makeText(this, "Vérifiez votre connexion ou le téléchargement audio.",
                        Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void confirmDownload() {
        if (surah == null) return;
        new AlertDialog.Builder(this)
            .setTitle(Languages.text(language, "Écoute hors connexion", "Offline listening", "استماع دون اتصال"))
            .setMessage(Languages.text(language, "Télécharger les MP3 de cette sourate en français, anglais ou arabe selon l’onglet actif ?",
                "Download this surah's MP3 audio for the selected language?",
                "هل تريد تنزيل صوت السورة للغة المختارة؟"))
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Télécharger", (d, w) -> downloadCurrentLanguage()).show();
    }
    private void downloadCurrentLanguage() {
        if (surah == null) return;
        TextView button = findViewById(R.id.downloadAudioButton);
        button.setEnabled(false);
        String chosen = language;
        List<QuranRepository.Verse> verses = new ArrayList<>(surah.verses);
        worker.execute(() -> {
            int count = 0;
            String error = null;
            for (QuranRepository.Verse verse : verses) {
                if (Thread.currentThread().isInterrupted()) break;
                try {
                    AudioRepository.ensureAudio(getApplicationContext(), chosen,
                        surahId, verse.number, verse.globalNumber);
                    count++;
                } catch (Exception e) { error = e.getMessage(); break; }
                final int done = count;
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed())
                        progress.setText(Languages.flag(chosen) + " Audio " + done + "/" + verses.size());
                });
            }
            final String failure = error;
            final int downloaded = count;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                button.setEnabled(true);
                progress.setText(failure == null
                    ? Languages.flag(chosen) + " " + downloaded + " versets disponibles hors connexion"
                    : "Téléchargement interrompu : " + downloaded + "/" + verses.size() + " · " + failure);
            });
        });
    }

    @Override protected void onResume() {
        super.onResume();
        String preference = Languages.selected(this);
        if (!preference.equals(language)) {
            language = preference;
            requestId++;
            stopPlayer();
        }
        updateLanguageHeader();
        if (surah != null) { render(); goTo(currentVerse, false); }
    }
    @Override protected void onDestroy() {
        requestId++;
        stopPlayer();
        worker.shutdownNow();
        super.onDestroy();
    }
}
