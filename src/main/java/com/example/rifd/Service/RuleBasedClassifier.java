package com.example.rifd.Service;

import com.example.rifd.Enum.ActorType;
import com.example.rifd.Enum.IncidentCategory;
import com.example.rifd.Enum.Severity;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static com.example.rifd.Enum.IncidentCategory.*;

@Service
public class RuleBasedClassifier {

    public static final double FALLBACK_CONFIDENCE = 0.5;

    // shared result for AI and rule-based classification
    public record Classification(IncidentCategory category, Severity severity, Double confidence,
                                 String summary, ActorType decidedBy) {
    }

    private static final Pattern DIACRITICS = Pattern.compile("[\\u064B-\\u0652\\u0640]");
    private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final List<String> AR_PREFIXES = List.of("", "ال", "و", "وال", "ب", "بال", "ل", "لل", "ف", "فال");

    // checked in this order, first match wins
    // ACCESSIBILITY before TECHNICAL_FAILURE so all elevator alerts get the same category (needed for merging)
    private static final Map<IncidentCategory, List<String>> KEYWORDS = new LinkedHashMap<>();
    private static final Map<IncidentCategory, Severity> DEFAULT_SEVERITY = new EnumMap<>(IncidentCategory.class);
    private static final List<String> ESCALATORS;

    static {
        add(FIRE, "حريق", "دخان", "نار", "النيران", "fire", "smoke", "burning", "flames");
        add(MEDICAL, "إغماء", "مغمى", "اغمي", "مصاب", "مصابة", "مصابين", "إصابة", "نزيف", "إسعاف",
                "سقط", "سقطت", "سقوط", "fainted", "faint", "injured", "injury", "ambulance", "bleeding",
                "unconscious", "collapsed");
        add(LOST_PERSON, "طفل مفقود", "مفقود", "مفقودة", "تائه", "تائهة", "lost child", "missing", "lost");
        add(SECURITY, "شجار", "مضاربة", "سرقة", "مشبوه", "مشتبه", "fight", "fighting", "theft", "stolen",
                "suspicious");
        add(CROWD, "ازدحام", "زحمة", "زحام", "تدافع", "اكتظاظ", "crowd", "crowded", "crowding",
                "overcrowding", "overcrowded", "stampede", "congestion");
        add(ACCESSIBILITY, "مصعد", "كرسي متحرك", "منحدر", "elevator", "lift", "wheelchair", "ramp");
        add(TECHNICAL_FAILURE, "عطل", "تعطل", "معطل", "متعطل", "انقطاع", "broken", "outage", "malfunction",
                "fault", "not working", "out of order");
        add(CLEANLINESS, "انسكاب", "نظافة", "قمامة", "نفايات", "spill", "spilled", "trash", "garbage", "dirty");

        ESCALATORS = normalizeAll(List.of("شديد", "شديدة", "خطير", "خطيرة", "عاجل", "تدافع", "فاقد الوعي",
                "severe", "urgent", "critical", "unconscious", "emergency"));

        DEFAULT_SEVERITY.put(FIRE, Severity.HIGH);
        DEFAULT_SEVERITY.put(MEDICAL, Severity.HIGH);
        DEFAULT_SEVERITY.put(CROWD, Severity.MEDIUM);
        DEFAULT_SEVERITY.put(SECURITY, Severity.MEDIUM);
        DEFAULT_SEVERITY.put(TECHNICAL_FAILURE, Severity.MEDIUM);
        DEFAULT_SEVERITY.put(ACCESSIBILITY, Severity.MEDIUM);
        DEFAULT_SEVERITY.put(LOST_PERSON, Severity.MEDIUM);
        DEFAULT_SEVERITY.put(CLEANLINESS, Severity.LOW);
        DEFAULT_SEVERITY.put(OTHER, Severity.LOW);
    }

    public Classification classify(String text) {
        List<String> tokens = tokenize(text);
        Set<String> tokenSet = new HashSet<>(tokens);
        String joined = " " + String.join(" ", tokens) + " ";

        IncidentCategory category = KEYWORDS.entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(k -> matches(k, tokenSet, joined)))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(OTHER);

        Severity severity = defaultSeverity(category);
        if (ESCALATORS.stream().anyMatch(k -> matches(k, tokenSet, joined))) {
            severity = IncidentService.raise(severity);
        }
        return new Classification(category, severity, FALLBACK_CONFIDENCE, null, ActorType.SYSTEM);
    }

    public static Severity defaultSeverity(IncidentCategory category) {
        return DEFAULT_SEVERITY.getOrDefault(category, Severity.LOW);
    }

    // lowercase + unify Arabic letter forms + remove diacritics
    public static String normalize(String text) {
        if (text == null) return "";
        String s = DIACRITICS.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("");
        return s.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
                .replace('ة', 'ه').replace('ى', 'ي');
    }

    public static List<String> tokenize(String text) {
        return Arrays.stream(SEPARATORS.split(normalize(text)))
                .filter(t -> !t.isEmpty())
                .toList();
    }

    private static void add(IncidentCategory category, String... keywords) {
        KEYWORDS.put(category, normalizeAll(Arrays.asList(keywords)));
    }

    private static List<String> normalizeAll(List<String> keywords) {
        return keywords.stream().map(k -> String.join(" ", tokenize(k))).toList();
    }

    // single word: whole token (with common Arabic prefixes), phrase: contained in the text
    private static boolean matches(String keyword, Set<String> tokens, String joined) {
        if (keyword.isEmpty()) return false;
        if (keyword.indexOf(' ') >= 0) return joined.contains(" " + keyword + " ");
        for (String prefix : AR_PREFIXES) {
            if (tokens.contains(prefix + keyword)) return true;
        }
        return false;
    }
}
