package com.filemind.document.service;

import com.filemind.common.util.JsonListCodec;
import com.filemind.common.util.TextContentSanitizer;
import com.filemind.document.entity.Document;
import com.filemind.version.entity.DocumentVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DocumentSearchService {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-z0-9']+");
    private static final int MAX_TEXT_SEARCH_CHARACTERS = 48_000;
    private static final int MAX_SNIPPET_SOURCE_CHARACTERS = 6_000;
    private static final int SNIPPET_RADIUS = 72;

    private final JsonListCodec jsonListCodec;

    public List<DocumentSnapshotService.DocumentSnapshot> filterAndRank(Collection<DocumentSnapshotService.DocumentSnapshot> snapshots,
                                                                        String query,
                                                                        String type) {
        String normalizedType = normalize(type);
        String normalizedQuery = normalize(query);
        List<String> queryTokens = tokenize(normalizedQuery);

        return snapshots.stream()
            .filter(snapshot -> matchesType(displayVersion(snapshot), normalizedType))
            .map(snapshot -> new ScoredSnapshot(snapshot, score(snapshot, normalizedQuery, queryTokens)))
            .filter(scored -> normalizedQuery.isBlank() || scored.score() > 0D)
            .sorted(Comparator
                .comparingDouble(ScoredSnapshot::score).reversed()
                .thenComparing(ScoredSnapshot::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(scored -> scored.snapshot().document().getId()))
            .map(ScoredSnapshot::snapshot)
            .toList();
    }

    public String buildSearchSnippet(DocumentSnapshotService.DocumentSnapshot snapshot, String query) {
        String rawQuery = query == null ? "" : query.trim();
        if (rawQuery.isBlank()) {
            return null;
        }

        List<String> tokens = tokenize(normalize(rawQuery));
        Document document = snapshot.document();
        DocumentVersion version = displayVersion(snapshot);

        String snippet = findSnippet(document.getName(), rawQuery, tokens);
        if (snippet != null) {
            return snippet;
        }
        if (version == null) {
            return null;
        }

        for (String candidate : List.of(
            version.getFileName(),
            version.getSummary(),
            version.getTitleSuggestion(),
            String.join(", ", jsonListCodec.read(version.getTagsJson())),
            String.join(", ", jsonListCodec.read(version.getKeywordsJson())),
            String.join(", ", jsonListCodec.read(version.getNamesJson())),
            String.join(", ", jsonListCodec.read(version.getDatesJson())),
            truncate(version.getExtractedText(), MAX_SNIPPET_SOURCE_CHARACTERS)
        )) {
            snippet = findSnippet(candidate, rawQuery, tokens);
            if (snippet != null) {
                return snippet;
            }
        }

        return null;
    }

    private boolean matchesType(DocumentVersion version, String normalizedType) {
        if (version == null) {
            return normalizedType.isBlank();
        }
        return normalizedType.isBlank() || normalize(version.getMediaType()).equals(normalizedType);
    }

    private double score(DocumentSnapshotService.DocumentSnapshot snapshot, String normalizedQuery, List<String> queryTokens) {
        if (normalizedQuery.isBlank()) {
            return 0D;
        }

        Document document = snapshot.document();
        DocumentVersion currentVersion = displayVersion(snapshot);
        if (currentVersion == null) {
            return phraseScore(normalizedQuery, normalize(document.getName()), 120D);
        }

        double score = 0D;
        score += phraseScore(normalizedQuery, normalize(document.getName()), 120D);
        score += phraseScore(normalizedQuery, normalize(currentVersion.getFileName()), 80D);
        score += phraseScore(normalizedQuery, normalize(currentVersion.getTitleSuggestion()), 75D);
        score += phraseScore(normalizedQuery, normalize(currentVersion.getSummary()), 55D);
        score += tokenFieldScore(queryTokens, join(jsonListCodec.read(currentVersion.getTagsJson())), 48D, 16D);
        score += tokenFieldScore(queryTokens, join(jsonListCodec.read(currentVersion.getKeywordsJson())), 44D, 14D);
        score += tokenFieldScore(queryTokens, join(jsonListCodec.read(currentVersion.getNamesJson())), 36D, 12D);
        score += tokenFieldScore(queryTokens, join(jsonListCodec.read(currentVersion.getDatesJson())), 24D, 10D);
        score += tokenFieldScore(queryTokens, normalize(currentVersion.getSummary()), 26D, 8D);
        score += tokenFieldScore(queryTokens, normalize(currentVersion.getTitleSuggestion()), 24D, 10D);
        score += tokenFieldScore(queryTokens, normalize(currentVersion.getExtractedText(), MAX_TEXT_SEARCH_CHARACTERS), 18D, 4D);

        String searchableText = join(List.of(
            normalize(document.getName()),
            normalize(currentVersion.getFileName()),
            normalize(currentVersion.getTitleSuggestion()),
            normalize(currentVersion.getSummary()),
            join(jsonListCodec.read(currentVersion.getTagsJson())),
            join(jsonListCodec.read(currentVersion.getKeywordsJson())),
            join(jsonListCodec.read(currentVersion.getNamesJson())),
            join(jsonListCodec.read(currentVersion.getDatesJson())),
            normalize(currentVersion.getExtractedText(), MAX_TEXT_SEARCH_CHARACTERS)
        ));
        if (!queryTokens.isEmpty() && queryTokens.stream().allMatch(searchableText::contains)) {
            score += 30D;
        }

        if (document.getLastViewedAt() != null) {
            score += recencyBoost(document.getLastViewedAt());
        }

        return score;
    }

    private double phraseScore(String query, String field, double weight) {
        if (query.isBlank() || field.isBlank() || !field.contains(query)) {
            return 0D;
        }
        return weight;
    }

    private double tokenFieldScore(List<String> queryTokens, String field, double cap, double perTokenWeight) {
        if (queryTokens.isEmpty() || field.isBlank()) {
            return 0D;
        }

        Set<String> distinctTokens = queryTokens.stream().collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        long matches = distinctTokens.stream().filter(field::contains).count();
        return Math.min(cap, matches * perTokenWeight);
    }

    private double recencyBoost(Instant lastViewedAt) {
        long ageDays = Math.max(0L, java.time.Duration.between(lastViewedAt, Instant.now()).toDays());
        return ageDays <= 1 ? 4D : ageDays <= 7 ? 2D : 1D;
    }

    private String join(List<String> values) {
        return values == null ? "" : values.stream()
            .map(this::normalize)
            .filter(value -> !value.isBlank())
            .collect(java.util.stream.Collectors.joining(" "));
    }

    private String normalize(String value) {
        return normalize(value, Integer.MAX_VALUE);
    }

    private String normalize(String value, int maxLength) {
        value = TextContentSanitizer.stripLeadingBom(value);
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT)
            .replace('\r', ' ')
            .replace('\n', ' ')
            .replace('\t', ' ');
        normalized = normalized.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private List<String> tokenize(String value) {
        if (value.isBlank()) {
            return List.of();
        }
        var matcher = TOKEN_PATTERN.matcher(value);
        List<String> tokens = new java.util.ArrayList<>();
        while (matcher.find()) {
            String token = matcher.group().trim();
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String findSnippet(String rawValue, String rawQuery, List<String> tokens) {
        rawValue = TextContentSanitizer.stripLeadingBom(rawValue);
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        int matchIndex = indexOfIgnoreCase(rawValue, rawQuery);
        int matchLength = rawQuery.length();
        if (matchIndex < 0) {
            for (String token : tokens) {
                matchIndex = indexOfIgnoreCase(rawValue, token);
                if (matchIndex >= 0) {
                    matchLength = token.length();
                    break;
                }
            }
        }

        if (matchIndex < 0) {
            return null;
        }

        int start = Math.max(0, matchIndex - SNIPPET_RADIUS);
        int end = Math.min(rawValue.length(), matchIndex + matchLength + SNIPPET_RADIUS);
        String snippet = rawValue.substring(start, end).replaceAll("\\s+", " ").trim();
        if (start > 0) {
            snippet = "..." + snippet;
        }
        if (end < rawValue.length()) {
            snippet = snippet + "...";
        }
        return snippet;
    }

    private int indexOfIgnoreCase(String rawValue, String query) {
        if (query == null || query.isBlank()) {
            return -1;
        }
        return rawValue.toLowerCase(Locale.ROOT).indexOf(query.toLowerCase(Locale.ROOT));
    }

    private record ScoredSnapshot(DocumentSnapshotService.DocumentSnapshot snapshot, double score) {
        private Instant updatedAt() {
            return snapshot.document().getUpdatedAt();
        }
    }

    private DocumentVersion displayVersion(DocumentSnapshotService.DocumentSnapshot snapshot) {
        return snapshot.currentVersion() != null ? snapshot.currentVersion() : snapshot.latestVersion();
    }
}
