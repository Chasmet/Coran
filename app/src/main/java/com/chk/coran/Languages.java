package com.chk.coran;

import android.content.Context;
import android.preference.PreferenceManager;

public final class Languages {
    private Languages() {}
    public static final String FRENCH = "fr";
    public static final String ENGLISH = "en";
    public static final String ARABIC = "ar";

    public static String normalize(String code) {
        return ARABIC.equals(code) || ENGLISH.equals(code) ? code : FRENCH;
    }
    public static String selected(Context context) {
        return normalize(PreferenceManager.getDefaultSharedPreferences(context).getString("appLanguage", FRENCH));
    }
    public static void select(Context context, String code) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString("appLanguage", normalize(code)).apply();
    }
    public static String flag(String code) {
        if (ENGLISH.equals(code)) return "🇬🇧";
        if (ARABIC.equals(code)) return "🇸🇦";
        return "🇫🇷";
    }
    public static String name(String code) {
        if (ENGLISH.equals(code)) return "English";
        if (ARABIC.equals(code)) return "العربية";
        return "Français";
    }
    public static String audioEdition(String code) {
        if (ENGLISH.equals(code)) return "en.walk";
        if (ARABIC.equals(code)) return "ar.alafasy";
        return "fr.leclerc";
    }
    public static String narrator(String code) {
        if (ENGLISH.equals(code)) return "Ibrahim Walk";
        if (ARABIC.equals(code)) return "Mishary Rashid Alafasy";
        return "Youssouf Leclerc";
    }
    public static String translationCredit(String code) {
        if (ENGLISH.equals(code)) return "Saheeh International";
        if (ARABIC.equals(code)) return "Texte arabe Uthmani";
        return "Muhammad Hamidullah";
    }
    public static String text(String code, String french, String english, String arabic) {
        return ARABIC.equals(code) ? arabic : (ENGLISH.equals(code) ? english : french);
    }
}
