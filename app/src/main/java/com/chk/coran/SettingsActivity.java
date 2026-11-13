package com.chk.coran;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    private SharedPreferences prefs;
    private TextView updateText;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_settings);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        CheckBox ar = findViewById(R.id.showArabic);
        CheckBox fr = findViewById(R.id.showFrench);
        CheckBox en = findViewById(R.id.showEnglish);
        ar.setChecked(prefs.getBoolean("showArabic", true));
        fr.setChecked(prefs.getBoolean("showFrench", true));
        en.setChecked(prefs.getBoolean("showEnglish", false));
        ar.setOnCheckedChangeListener((b, checked) -> saveLanguages(ar, fr, en));
        fr.setOnCheckedChangeListener((b, checked) -> saveLanguages(ar, fr, en));
        en.setOnCheckedChangeListener((b, checked) -> saveLanguages(ar, fr, en));
        SeekBar size = findViewById(R.id.fontSize);
        TextView label = findViewById(R.id.fontSizeLabel);
        size.setProgress(prefs.getInt("fontSize", 4));
        label.setText("Taille du texte : " + (14 + size.getProgress() / 2) + " sp (traductions)");
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int n, boolean user) {
                label.setText("Taille du texte : " + (14 + n / 2) + " sp (traductions)");
                if (user) prefs.edit().putInt("fontSize", n).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        CheckBox auto = findViewById(R.id.autoUpdate);
        auto.setChecked(prefs.getBoolean("autoUpdate", true));
        auto.setOnCheckedChangeListener((b, checked) ->
            prefs.edit().putBoolean("autoUpdate", checked).apply());
        updateText = findViewById(R.id.updateStatus);
        updateText.setText("Version installée : " + BuildConfig.VERSION_NAME
            + (BuildConfig.ENABLE_GITHUB_UPDATE ? " · canal GitHub Releases" : " · version de test"));
        findViewById(R.id.checkUpdate).setOnClickListener(v ->
            UpdateManager.check(this, true, message -> updateText.setText(message)));
    }
    private void saveLanguages(CheckBox ar, CheckBox fr, CheckBox en) {
        if (!ar.isChecked() && !fr.isChecked() && !en.isChecked()) {
            Toast.makeText(this, "Une langue au moins doit être visible", Toast.LENGTH_SHORT).show();
            ar.setChecked(true);
            return;
        }
        prefs.edit().putBoolean("showArabic", ar.isChecked())
            .putBoolean("showFrench", fr.isChecked())
            .putBoolean("showEnglish", en.isChecked()).apply();
    }
    @Override protected void onResume() {
        super.onResume();
        UpdateManager.resumePending(this, message -> updateText.setText(message));
    }
}
