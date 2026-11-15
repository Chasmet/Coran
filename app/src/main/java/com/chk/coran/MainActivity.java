package com.chk.coran;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
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
import java.util.ArrayList;

public class MainActivity extends Activity {
    private final ArrayList<Integer> items = new ArrayList<>();
    private SharedPreferences prefs;
    private BaseAdapter adapter;
    private EditText search;
    private TextView resume, favorites, status, subtitle;
    private String language;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        language = Languages.normalize(getIntent().getStringExtra("language"));
        if (!getIntent().hasExtra("language")) language = Languages.selected(this);
        Languages.select(this, language);

        resume = findViewById(R.id.resumeButton);
        favorites = findViewById(R.id.favoritesButton);
        status = findViewById(R.id.statusText);
        subtitle = findViewById(R.id.mainSubtitle);
        search = findViewById(R.id.searchInput);

        findViewById(R.id.settingsButton).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.homeButton).setOnClickListener(v -> {
            startActivity(new Intent(this, WelcomeActivity.class));
            finish();
        });
        favorites.setOnClickListener(v -> startActivity(new Intent(this, BookmarksActivity.class)));
        bindTab(R.id.tabFrench, Languages.FRENCH);
        bindTab(R.id.tabEnglish, Languages.ENGLISH);
        bindTab(R.id.tabArabic, Languages.ARABIC);

        ListView list = findViewById(R.id.surahList);
        adapter = new BaseAdapter() {
            @Override public int getCount() { return items.size(); }
            @Override public Integer getItem(int pos) { return items.get(pos); }
            @Override public long getItemId(int pos) { return items.get(pos); }
            @Override public View getView(int pos, View old, ViewGroup parent) {
                View row = old == null ? LayoutInflater.from(MainActivity.this)
                    .inflate(R.layout.item_surah, parent, false) : old;
                int n = items.get(pos);
                ((TextView) row.findViewById(R.id.surahNumber)).setText(String.valueOf(n));
                ((TextView) row.findViewById(R.id.surahTitle)).setText(SurahCatalog.name(n));
                ((TextView) row.findViewById(R.id.surahSub)).setText(
                    Languages.text(language, "Sourate ", "Surah ", "سورة ")
                        + n + " · " + Languages.flag(language) + " " + Languages.name(language));
                return row;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> open(items.get(position), 1));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
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
    private void updateTab(int id, String code) {
        TextView tab = findViewById(id);
        boolean selected = language.equals(code);
        tab.setBackgroundResource(selected ? R.drawable.tab_selected : R.drawable.tab_normal);
        tab.setTextColor(getResources().getColor(selected ? android.R.color.white : R.color.primary_dark));
    }
    private void updateLanguage() {
        updateTab(R.id.tabFrench, Languages.FRENCH);
        updateTab(R.id.tabEnglish, Languages.ENGLISH);
        updateTab(R.id.tabArabic, Languages.ARABIC);
        ((TextView) findViewById(R.id.mainHeading)).setText(Languages.text(language,
            "Le Saint Coran", "The Holy Quran", "القرآن الكريم"));
        subtitle.setText(Languages.text(language,
            "114 sourates · lecture et récitation", "114 surahs · reading and audio",
            "١١٤ سورة · قراءة واستماع"));
        favorites.setText(Languages.text(language, "★ Favoris", "★ Bookmarks", "★ المفضلة"));
        search.setHint(Languages.text(language, "Rechercher une sourate…", "Search for a surah…", "بحث عن سورة…"));
        filter(search.getText().toString());
        updateResume();
    }

    private void filter(String term) {
        String query = SurahCatalog.normalized(term);
        items.clear();
        for (int i = 1; i <= 114; i++) {
            if (query.isEmpty() || SurahCatalog.normalized(SurahCatalog.name(i)).contains(query)
                || String.valueOf(i).equals(query)) items.add(i);
        }
        adapter.notifyDataSetChanged();
        status.setText(items.size() + Languages.text(language,
            " sourate(s) · " , " surah(s) · ", " سورة · ") + Languages.name(language));
    }

    private void open(int surah, int verse) {
        Intent intent = new Intent(this, ReadingActivity.class);
        intent.putExtra("surah", surah);
        intent.putExtra("jump", verse);
        intent.putExtra("language", language);
        startActivity(intent);
    }
    private void updateResume() {
        int surah = prefs.getInt("lastSurah", 1);
        int verse = prefs.getInt("lastVerse", 1);
        resume.setText(Languages.text(language, "▶ Reprendre", "▶ Continue reading", "▶ متابعة القراءة")
            + " · " + SurahCatalog.name(surah) + " : " + verse);
        resume.setOnClickListener(v -> open(surah, verse));
    }
    @Override protected void onResume() {
        super.onResume();
        String stored = Languages.selected(this);
        if (!stored.equals(language)) {
            language = stored;
            updateLanguage();
        } else updateResume();
        if (prefs.getBoolean("autoUpdate", true) && BuildConfig.ENABLE_GITHUB_UPDATE) {
            long now = System.currentTimeMillis();
            if (now - prefs.getLong("lastUpdateCheck", 0) > 24L * 60 * 60 * 1000) {
                UpdateManager.check(this, false, null);
            }
        }
    }
}
