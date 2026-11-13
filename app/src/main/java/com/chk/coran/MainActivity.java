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
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private final ArrayList<Integer> items = new ArrayList<>();
    private BaseAdapter adapter;
    private SharedPreferences prefs;
    private Button resume;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        resume = findViewById(R.id.resumeButton);
        Button favorites = findViewById(R.id.favoritesButton);
        favorites.setOnClickListener(v -> startActivity(new Intent(this, BookmarksActivity.class)));
        findViewById(R.id.settingsButton).setOnClickListener(v ->
            startActivity(new Intent(this, SettingsActivity.class)));

        ListView list = findViewById(R.id.surahList);
        adapter = new BaseAdapter() {
            @Override public int getCount() { return items.size(); }
            @Override public Object getItem(int pos) { return items.get(pos); }
            @Override public long getItemId(int pos) { return items.get(pos); }
            @Override public View getView(int pos, View convert, ViewGroup parent) {
                View row = convert == null
                    ? LayoutInflater.from(MainActivity.this).inflate(R.layout.item_surah, parent, false)
                    : convert;
                int n = items.get(pos);
                ((TextView) row.findViewById(R.id.surahNumber)).setText(String.valueOf(n));
                ((TextView) row.findViewById(R.id.surahTitle)).setText(SurahCatalog.name(n));
                ((TextView) row.findViewById(R.id.surahSub)).setText("Sourate " + n + " • Arabe · Français · Anglais");
                return row;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> open(items.get(position), 1));

        EditText search = findViewById(R.id.searchInput);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
        filter("");
    }

    private void filter(String term) {
        String query = SurahCatalog.normalized(term);
        items.clear();
        for (int i = 1; i <= 114; i++) {
            if (query.isEmpty() || SurahCatalog.normalized(SurahCatalog.name(i)).contains(query)
                    || String.valueOf(i).equals(query)) items.add(i);
        }
        adapter.notifyDataSetChanged();
        ((TextView) findViewById(R.id.statusText)).setText(items.size() + " sourate(s)");
    }

    private void open(int surah, int verse) {
        Intent intent = new Intent(this, ReadingActivity.class);
        intent.putExtra("surah", surah);
        intent.putExtra("jump", verse);
        startActivity(intent);
    }

    @Override protected void onResume() {
        super.onResume();
        int surah = prefs.getInt("lastSurah", 1);
        int verse = prefs.getInt("lastVerse", 1);
        resume.setText("Reprendre • " + SurahCatalog.name(surah) + " : " + verse);
        resume.setOnClickListener(v -> open(surah, verse));
        if (prefs.getBoolean("autoUpdate", true) && BuildConfig.ENABLE_GITHUB_UPDATE) {
            long now = System.currentTimeMillis();
            if (now - prefs.getLong("lastUpdateCheck", 0) > 24L * 60 * 60 * 1000) {
                UpdateManager.check(this, false, null);
            }
        }
    }
}
