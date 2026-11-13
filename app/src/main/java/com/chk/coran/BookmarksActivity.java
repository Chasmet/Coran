package com.chk.coran;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;

public class BookmarksActivity extends Activity {
    private final ArrayList<String> refs = new ArrayList<>();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 24, 20, 12);
        root.setBackgroundColor(getResources().getColor(R.color.background));
        TextView title = new TextView(this);
        title.setText("★ Mes favoris");
        title.setTextSize(24);
        title.setTextColor(getResources().getColor(R.color.primary));
        root.addView(title);
        ListView list = new ListView(this);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Set<String> saved = prefs.getStringSet("bookmarks", Collections.emptySet());
        refs.addAll(saved);
        Collections.sort(refs, (a, b) -> {
            try {
                String[] x = a.split(":"), y = b.split(":");
                int n = Integer.compare(Integer.parseInt(x[0]), Integer.parseInt(y[0]));
                return n != 0 ? n : Integer.compare(Integer.parseInt(x[1]), Integer.parseInt(y[1]));
            } catch (Exception ignored) { return a.compareTo(b); }
        });
        ArrayList<String> names = new ArrayList<>();
        for (String key : refs) {
            String[] split = key.split(":");
            if (split.length == 2) {
                try {
                    int surah = Integer.parseInt(split[0]);
                    int verse = Integer.parseInt(split[1]);
                    names.add(SurahCatalog.name(surah) + " · Verset " + verse);
                } catch (NumberFormatException error) { names.add(key); }
            } else names.add(key);
        }
        if (names.isEmpty()) names.add("Aucun favori. Touchez ☆ pendant la lecture.");
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names));
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (refs.isEmpty()) return;
            try {
                String[] split = refs.get(position).split(":");
                Intent intent = new Intent(this, ReadingActivity.class);
                intent.putExtra("surah", Integer.parseInt(split[0]));
                intent.putExtra("jump", Integer.parseInt(split[1]));
                startActivity(intent);
            } catch (Exception ignored) {}
        });
    }
}
