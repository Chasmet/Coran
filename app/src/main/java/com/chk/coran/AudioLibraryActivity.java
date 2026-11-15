package com.chk.coran;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import java.util.ArrayList;

/** Deuxième onglet : écouter et télécharger chacune des 114 sourates. */
public final class AudioLibraryActivity extends Activity {
    private String language;
    private EditText search;
    private BaseAdapter adapter;
    private final ArrayList<Integer> visible = new ArrayList<>();
    private TextView nowPlaying, playPause, status;
    private final BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { updatePlayer(); }
    };
    private boolean registered;

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setContentView(R.layout.activity_audio_library);
        language = Languages.selected(this);
        findViewById(R.id.audioBack).setOnClickListener(v -> finish());
        findViewById(R.id.audioSettings).setOnClickListener(v ->
            startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.audioReadingTab).setOnClickListener(v -> {
            Intent i = new Intent(this, MainActivity.class);
            i.putExtra("language", language);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            finish();
        });
        findViewById(R.id.audioListeningTab).setOnClickListener(v -> {});
        bindTab(R.id.audioFrench, Languages.FRENCH);
        bindTab(R.id.audioEnglish, Languages.ENGLISH);
        bindTab(R.id.audioArabic, Languages.ARABIC);

        search = findViewById(R.id.audioSearch);
        status = findViewById(R.id.audioListStatus);
        nowPlaying = findViewById(R.id.fullNowPlaying);
        playPause = findViewById(R.id.fullPlayPause);
        findViewById(R.id.fullPrevious).setOnClickListener(v ->
            control(SurahPlaybackService.ACTION_PREVIOUS));
        findViewById(R.id.fullNext).setOnClickListener(v ->
            control(SurahPlaybackService.ACTION_NEXT));
        playPause.setOnClickListener(v -> toggleCurrent());
        findViewById(R.id.fullStop).setOnClickListener(v ->
            control(SurahPlaybackService.ACTION_STOP));

        ListView list = findViewById(R.id.audioSurahList);
        adapter = new BaseAdapter() {
            @Override public int getCount() { return visible.size(); }
            @Override public Integer getItem(int pos) { return visible.get(pos); }
            @Override public long getItemId(int pos) { return visible.get(pos); }
            @Override public View getView(int position, View convert, ViewGroup parent) {
                View row = convert == null
                    ? LayoutInflater.from(AudioLibraryActivity.this)
                        .inflate(R.layout.item_audio_surah, parent, false)
                    : convert;
                int number = visible.get(position);
                ((TextView) row.findViewById(R.id.audioSurahNumber)).setText(String.valueOf(number));
                ((TextView) row.findViewById(R.id.audioSurahName)).setText(SurahCatalog.name(number));
                TextView info = row.findViewById(R.id.audioSurahInfo);
                info.setText(downloadLabel(number));
                TextView play = row.findViewById(R.id.audioSurahPlay);
                SharedPreferences p = getSharedPreferences(SurahPlaybackService.PREFS, MODE_PRIVATE);
                boolean selected = p.getBoolean("active", false)
                    && p.getInt("surah", 0) == number
                    && language.equals(p.getString("language", ""));
                boolean running = selected && p.getBoolean("playing", false);
                play.setText(running ? "⏸" : "▶");
                play.setContentDescription((running ? "Mettre en pause : " : "Écouter en entier : ")
                    + SurahCatalog.name(number));
                play.setOnClickListener(v -> {
                    if (selected) control(running
                        ? SurahPlaybackService.ACTION_PAUSE : SurahPlaybackService.ACTION_RESUME);
                    else startSurah(number);
                });
                TextView download = row.findViewById(R.id.audioSurahDownload);
                boolean saved = fullDownloaded(number);
                download.setText(saved ? "✓" : "↓");
                download.setContentDescription((saved ? "Sourate téléchargée : " : "Télécharger l'audio complet : ")
                    + SurahCatalog.name(number));
                download.setOnClickListener(v -> {
                    if (saved) Toast.makeText(AudioLibraryActivity.this,
                        "Sourate déjà téléchargée dans cette langue", Toast.LENGTH_SHORT).show();
                    else downloadSurah(number);
                });
                return row;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, index, id) -> startSurah(visible.get(index)));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }
            @Override public void afterTextChanged(Editable e) {}
        });
        updateLanguage();
    }

    private void bindTab(int id, String code) {
        findViewById(id).setOnClickListener(v -> {
            language = code;
            Languages.select(this, code);
            updateLanguage();
        });
    }

    private void updateLanguage() {
        int[] ids = {R.id.audioFrench, R.id.audioEnglish, R.id.audioArabic};
        String[] codes = {Languages.FRENCH, Languages.ENGLISH, Languages.ARABIC};
        for (int i = 0; i < 3; i++) {
            TextView tab = findViewById(ids[i]);
            boolean selected = language.equals(codes[i]);
            tab.setBackgroundResource(selected ? R.drawable.tab_selected : R.drawable.tab_normal);
            tab.setTextColor(getResources().getColor(
                selected ? android.R.color.white : R.color.text));
        }
        ((TextView) findViewById(R.id.fullAudioHeading)).setText(
            Languages.text(language, "Écouter les sourates", "Listen to surahs", "استماع السور"));
        ((TextView) findViewById(R.id.audioReaderName)).setText(
            Languages.flag(language) + " " + Languages.narrator(language));
        search.setHint(Languages.text(language,
            "Chercher une sourate…", "Search for a surah…", "البحث عن سورة…"));
        filter(search.getText().toString());
        updatePlayer();
    }

    private void filter(String term) {
        String query = SurahCatalog.normalized(term);
        visible.clear();
        for (int n = 1; n <= 114; n++) {
            if (query.isEmpty() || SurahCatalog.normalized(SurahCatalog.name(n)).contains(query)
                || String.valueOf(n).equals(query)) visible.add(n);
        }
        status.setText(visible.size() + Languages.text(language,
            " sourates · lecture complète", " surahs · full listening",
            " سورة · استماع كامل"));
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private boolean fullDownloaded(int surah) {
        return getSharedPreferences(SurahDownloadWorker.PREFS, MODE_PRIVATE)
            .getBoolean(SurahDownloadWorker.key("complete", language, surah), false);
    }

    private String downloadLabel(int surah) {
        SharedPreferences prefs = getSharedPreferences(SurahDownloadWorker.PREFS, MODE_PRIVATE);
        if (fullDownloaded(surah)) return Languages.flag(language) + " " +
            Languages.text(language, "Téléchargé · hors connexion",
                "Downloaded · offline", "تم التحميل · بدون إنترنت");
        String progress = prefs.getString(SurahDownloadWorker.key("status", language, surah), "");
        if (!progress.isEmpty()) return Languages.flag(language) + " " + progress;
        return Languages.flag(language) + " " + Languages.narrator(language)
            + " · " + Languages.text(language, "Sourate complète",
                "Full surah", "السورة كاملة");
    }

    private void downloadSurah(int surah) {
        String chosenLanguage = language;
        Data data = new Data.Builder()
            .putInt(SurahDownloadWorker.INPUT_SURAH, surah)
            .putString(SurahDownloadWorker.INPUT_LANGUAGE, chosenLanguage).build();
        Constraints network = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SurahDownloadWorker.class)
            .setInputData(data).setConstraints(network).build();
        WorkManager.getInstance(getApplicationContext())
            .enqueueUniqueWork("coran_full_download_" + chosenLanguage + "_" + surah,
                ExistingWorkPolicy.KEEP, request);
        getSharedPreferences(SurahDownloadWorker.PREFS, MODE_PRIVATE).edit()
            .putString(SurahDownloadWorker.key("status", chosenLanguage, surah),
                "Téléchargement programmé").apply();
        adapter.notifyDataSetChanged();
        Toast.makeText(this, "Téléchargement de " + SurahCatalog.name(surah)
            + " · " + Languages.name(chosenLanguage), Toast.LENGTH_LONG).show();
    }

    private void startSurah(int surah) {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 742);
        }
        try {
            Intent intent = new Intent(this, SurahPlaybackService.class)
                .setAction(SurahPlaybackService.ACTION_PLAY)
                .putExtra(SurahPlaybackService.EXTRA_SURAH, surah)
                .putExtra(SurahPlaybackService.EXTRA_LANGUAGE, language);
            ContextCompat.startForegroundService(this, intent);
            nowPlaying.setText("Préparation : " + SurahCatalog.name(surah));
        } catch (Exception e) {
            Toast.makeText(this, "Impossible de démarrer le lecteur : " + e.getMessage(),
                Toast.LENGTH_LONG).show();
        }
    }

    private void control(String action) {
        SharedPreferences p = getSharedPreferences(SurahPlaybackService.PREFS, MODE_PRIVATE);
        if (!p.getBoolean("active", false)) return;
        startService(new Intent(this, SurahPlaybackService.class).setAction(action));
    }

    private void toggleCurrent() {
        SharedPreferences p = getSharedPreferences(SurahPlaybackService.PREFS, MODE_PRIVATE);
        if (!p.getBoolean("active", false)) return;
        control(p.getBoolean("playing", false)
            ? SurahPlaybackService.ACTION_PAUSE : SurahPlaybackService.ACTION_RESUME);
    }

    private void updatePlayer() {
        if (nowPlaying == null) return;
        SharedPreferences p = getSharedPreferences(SurahPlaybackService.PREFS, MODE_PRIVATE);
        boolean active = p.getBoolean("active", false);
        boolean running = p.getBoolean("playing", false);
        boolean loading = p.getBoolean("loading", false);
        int surah = p.getInt("surah", 1);
        int verse = p.getInt("verse", 1);
        int total = p.getInt("total", 0);
        String code = p.getString("language", language);
        String error = p.getString("error", "");
        nowPlaying.setText(active
            ? Languages.flag(code) + " " + SurahCatalog.name(surah) + " · "
                + verse + (total > 0 ? "/" + total : "")
                + (loading ? " · Chargement…" : (error.isEmpty() ? "" : " · " + error))
            : "Aucune sourate en lecture");
        playPause.setText(running ? "⏸" : "▶");
        ((TextView) findViewById(R.id.audioBackgroundHint)).setText(
            "Écoute écran verrouillé · contrôles dans les notifications");
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    @Override protected void onResume() {
        super.onResume();
        String chosen = Languages.selected(this);
        if (!chosen.equals(language)) {
            language = chosen;
            if (search != null) updateLanguage();
        }
        if (!registered) {
            IntentFilter filter = new IntentFilter(SurahPlaybackService.ACTION_STATE_CHANGED);
            filter.addAction(SurahDownloadWorker.ACTION_PROGRESS);
            ContextCompat.registerReceiver(this, refreshReceiver,
                filter, ContextCompat.RECEIVER_NOT_EXPORTED);
            registered = true;
        }
        updatePlayer();
    }
    @Override protected void onPause() {
        if (registered) {
            unregisterReceiver(refreshReceiver);
            registered = false;
        }
        super.onPause();
    }
}
