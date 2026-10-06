package org.example.skulvi_cv.common;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class TextUtils {
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    private TextUtils() {}

    /** Minuscules + suppression des accents. */
    public static String fold(String s) {
        if (s == null) return "";
        return MARKS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT);
    }

    /** Motif « mot entier » : "java" ne correspond pas à "javascript". À appliquer sur un texte déjà fold(). */
    public static Pattern wordPattern(String term) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(fold(term)) + "(?![\\p{L}\\p{N}])");
    }

    public static boolean containsWord(String text, String term) {
        return wordPattern(term).matcher(fold(text)).find();
    }

    public static String nz(String s) { return s == null ? "" : s; }
}
