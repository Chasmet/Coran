package com.chk.coran;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.TextView;

public final class WelcomeActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_welcome);
        findViewById(R.id.welcomeSettings).setOnClickListener(v ->
            startActivity(new Intent(this, SettingsActivity.class)));
        bind(R.id.welcomeFrench, Languages.FRENCH);
        bind(R.id.welcomeEnglish, Languages.ENGLISH);
        bind(R.id.welcomeArabic, Languages.ARABIC);
    }
    private void bind(int id, String language) {
        findViewById(id).setOnClickListener(v -> {
            Languages.select(this, language);
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("language", language);
            startActivity(intent);
        });
    }
    @Override protected void onResume() {
        super.onResume();
        String current = Languages.selected(this);
        TextView hint = findViewById(R.id.welcomeHint);
        hint.setText(Languages.text(current,
            "Choisissez votre langue pour lire et écouter les 114 sourates",
            "Choose your language to read and listen to all 114 surahs",
            "اختر لغتك لقراءة واستماع سور القرآن الكريم"));
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(this);
        if (BuildConfig.ENABLE_GITHUB_UPDATE && p.getBoolean("autoUpdate", true)
            && System.currentTimeMillis() - p.getLong("lastUpdateCheck", 0) > 24L * 60 * 60 * 1000) {
            UpdateManager.check(this, false, null);
        }
    }
}
