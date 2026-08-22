package com.filemind.document.service;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SmartMetadataExtractionService {

    private static final Pattern DATE_PATTERN = Pattern.compile(
        "\\b(?:\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{1,2}\\s+(?:Jan|January|Feb|February|Mar|March|Apr|April|May|Jun|June|Jul|July|Aug|August|Sep|September|Oct|October|Nov|November|Dec|December)\\s+\\d{2,4}|(?:Jan|January|Feb|February|Mar|March|Apr|April|May|Jun|June|Jul|July|Aug|August|Sep|September|Oct|October|Nov|November|Dec|December)\\s+\\d{1,2},?\\s+\\d{2,4})\\b",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NAME_PATTERN = Pattern.compile("\\b[A-Z][a-z]+(?:\\s+[A-Z][a-z]+){1,2}\\b");
    private static final Pattern WORD_PATTERN = Pattern.compile("[a-zA-Z][a-zA-Z'-]{2,}");
    private static final Set<String> STOP_WORDS = Set.of(
        "about", "after", "again", "also", "been", "before", "being", "between", "brief", "can", "content",
        "could", "document", "documents", "draft", "file", "filemind", "from", "have", "into", "just", "latest",
        "more", "most", "must", "note", "notes", "only", "other", "over", "review", "shared", "should", "some",
        "still", "than", "that", "their", "them", "then", "there", "these", "this", "those", "through", "version",
        "versions", "what", "when", "where", "which", "while", "with", "your"
    );

    public DocumentInsightMetadata extract(String text) {
        if (text == null || text.isBlank()) {
            return new DocumentInsightMetadata(List.of(), List.of(), List.of());
        }

        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).replace('\r', ' ');
        return new DocumentInsightMetadata(
            extractDates(normalized),
            extractNames(normalized),
            extractKeywords(normalized)
        );
    }

    private List<String> extractDates(String text) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        Matcher matcher = DATE_PATTERN.matcher(text);
        while (matcher.find()) {
            values.add(cleanValue(matcher.group()));
            if (values.size() == 8) {
                break;
            }
        }
        return values.stream().toList();
    }

    private List<String> extractNames(String text) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        Matcher matcher = NAME_PATTERN.matcher(text);
        while (matcher.find()) {
            String candidate = cleanValue(matcher.group());
            if (candidate.startsWith("The ") || candidate.startsWith("This ") || candidate.startsWith("That ")) {
                continue;
            }
            values.add(candidate);
            if (values.size() == 8) {
                break;
            }
        }
        return values.stream().toList();
    }

    private List<String> extractKeywords(String text) {
        Map<String, Long> ranked = WORD_PATTERN.matcher(text.toLowerCase(Locale.ROOT))
            .results()
            .map(result -> result.group().trim())
            .filter(token -> token.length() >= 4)
            .filter(token -> !STOP_WORDS.contains(token))
            .collect(Collectors.groupingBy(token -> token, Collectors.counting()));

        return ranked.entrySet()
            .stream()
            .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry::getKey))
            .map(Map.Entry::getKey)
            .limit(8)
            .toList();
    }

    private String cleanValue(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    public record DocumentInsightMetadata(
        List<String> dates,
        List<String> names,
        List<String> keywords
    ) {
    }
}
