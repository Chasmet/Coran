package com.chk.coran;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;

public final class SettingsActivity extends Activity {
    private SharedPreferences prefs;
    private TextView updateStatus, versionText;
    private String language;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_settings);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        language = Languages.selected(this);
        findViewById(R.id.backSettings).setOnClickListener(v -> finish());
        bindTab(R.id.settingsFrench, Languages.FRENCH);
        bindTab(R.id.settingsEnglish, Languages.ENGLISH);
        bindTab(R.id.settingsArabic, Languages.ARABIC);

        CheckBox ar = findViewById(R.id.showArabic);
        CheckBox fr = findViewById(R.id.showFrench);
        CheckBox en = findViewById(R.id.showEnglish);
        ar.setChecked(prefs.getBoolean("showArabic", true));
        fr.setChecked(prefs.getBoolean("showFrench", false));
        en.setChecked(prefs.getBoolean("showEnglish", false));
        ar.setOnCheckedChangeListener((v, checked) -> prefs.edit().putBoolean("showArabic", checked).apply());
        fr.setOnCheckedChangeListener((v, checked) -> prefs.edit().putBoolean("showFrench", checked).apply());
        en.setOnCheckedChangeListener((v, checked) -> prefs.edit().putBoolean("showEnglish", checked).apply());

        SeekBar font = findViewById(R.id.fontSize);
        TextView fontLabel = findViewById(R.id.fontSizeLabel);
        font.setProgress(prefs.getInt("fontSize", 4));
        fontLabel.setText("Taille : " + (100 + (font.getProgress() - 4) * 5) + " %");
        font.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar view, int size, boolean user) {
                fontLabel.setText("Taille : " + (100 + (size - 4) * 5) + " %");
                if (user) prefs.edit().putInt("fontSize", size).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar view) {}
            @Override public void onStopTrackingTouch(SeekBar view) {}
        });

        CheckBox auto = findViewById(R.id.autoUpdate);
        auto.setChecked(prefs.getBoolean("autoUpdate", true));
        auto.setOnCheckedChangeListener((v, checked) ->
            prefs.edit().putBoolean("autoUpdate", checked).apply());
        CheckBox autoNext = findViewById(R.id.autoNext);
        autoNext.setChecked(prefs.getBoolean("autoNext", true));
        autoNext.setOnCheckedChangeListener((v, checked) ->
            prefs.edit().putBoolean("autoNext", checked).apply());

        versionText = findViewById(R.id.versionValue);
        versionText.setText(BuildConfig.VERSION_NAME);
        updateStatus = findViewById(R.id.updateStatus);
        updateStatus.setText(BuildConfig.ENABLE_GITHUB_UPDATE
            ? "GitHub Releases · installation Android avec vérification de signature"
            : "APK de test : mise à jour intégrée disponible après signature de la Release.");
        findViewById(R.id.checkUpdate).setOnClickListener(v ->
            UpdateManager.check(this, true, message -> updateStatus.setText(message)));
        updateLanguageTabs();
    }

    private void bindTab(int id, String code) {
        findViewById(id).setOnClickListener(v -> {
            language = code;
            Languages.select(this, code);
            updateLanguageTabs();
        });
    }
    private void updateLanguageTabs() {
        updateTab(R.id.settingsFrench, Languages.FRENCH);
        updateTab(R.id.settingsEnglish, Languages.ENGLISH);
        updateTab(R.id.settingsArabic, Languages.ARABIC);
    }
    private void updateTab(int id, String code) {
        TextView tab = findViewById(id);
        boolean selected = language.equals(code);
        tab.setBackgroundResource(selected ? R.drawable.tab_selected : R.drawable.tab_normal);
        tab.setTextColor(getResources().getColor(selected ? android.R.color.white : R.color.primary_dark));
    }

    @Override protected void onResume() {
        super.onResume();
        if (updateStatus != null) {
            UpdateManager.resumePending(this, message -> updateStatus.setText(message));
        }
    }
}
