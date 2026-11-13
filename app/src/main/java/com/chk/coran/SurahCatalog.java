package com.chk.coran;

import java.text.Normalizer;
import java.util.Locale;

public final class SurahCatalog {
    private SurahCatalog() {}
    public static final String[] NAMES = (
        "Al-Fatiha|Al-Baqara|Al-Imran|An-Nisa|Al-Maida|Al-Anam|Al-Araf|Al-Anfal|At-Tawba|Yunus|Hud|Yusuf|Ar-Rad|Ibrahim|Al-Hijr|An-Nahl|" +
        "Al-Isra|Al-Kahf|Maryam|Ta-Ha|Al-Anbiya|Al-Hajj|Al-Muminun|An-Nur|Al-Furqan|Ash-Shuara|An-Naml|Al-Qasas|Al-Ankabut|Ar-Rum|" +
        "Luqman|As-Sajda|Al-Ahzab|Saba|Fatir|Ya-Sin|As-Saffat|Sad|Az-Zumar|Ghafir|Fussilat|Ash-Shura|Az-Zukhruf|Ad-Dukhan|" +
        "Al-Jathiya|Al-Ahqaf|Muhammad|Al-Fath|Al-Hujurat|Qaf|Adh-Dhariyat|At-Tur|An-Najm|Al-Qamar|Ar-Rahman|Al-Waqia|Al-Hadid|" +
        "Al-Mujadila|Al-Hashr|Al-Mumtahana|As-Saff|Al-Jumua|Al-Munafiqun|At-Taghabun|At-Talaq|At-Tahrim|Al-Mulk|Al-Qalam|" +
        "Al-Haqqa|Al-Maarij|Nuh|Al-Jinn|Al-Muzzammil|Al-Muddaththir|Al-Qiyama|Al-Insan|Al-Mursalat|An-Naba|An-Naziat|Abasa|" +
        "At-Takwir|Al-Infitar|Al-Mutaffifin|Al-Inshiqaq|Al-Buruj|At-Tariq|Al-Ala|Al-Ghashiya|Al-Fajr|Al-Balad|Ash-Shams|Al-Layl|" +
        "Ad-Duha|Ash-Sharh|At-Tin|Al-Alaq|Al-Qadr|Al-Bayyina|Az-Zalzala|Al-Adiyat|Al-Qaria|At-Takathur|Al-Asr|Al-Humaza|Al-Fil|" +
        "Quraysh|Al-Maun|Al-Kawthar|Al-Kafirun|An-Nasr|Al-Masad|Al-Ikhlas|Al-Falaq|An-Nas"
    ).split("\\|");

    public static String name(int n) {
        return n >= 1 && n <= 114 ? NAMES[n - 1] : "Sourate " + n;
    }
    public static String normalized(String text) {
        String value = Normalizer.normalize(text, Normalizer.Form.NFD);
        return value.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
            .replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }
}
