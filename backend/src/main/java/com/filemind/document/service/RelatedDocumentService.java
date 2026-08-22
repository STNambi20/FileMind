package com.filemind.document.service;

import com.filemind.common.util.JsonListCodec;
import com.filemind.document.dto.RelatedDocumentResponse;
import com.filemind.document.entity.Document;
import com.filemind.version.entity.DocumentVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RelatedDocumentService {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9'-]{2,}");
    private static final int MAX_EXTRACTED_TEXT_CHARACTERS = 4_000;
    private static final int MAX_MATCHED_TERMS = 4;

    private final JsonListCodec jsonListCodec;
    private final DocumentSnapshotService documentSnapshotService;

    @Transactional(readOnly = true)
    public List<RelatedDocumentResponse> findRelated(Document document, Collection<Document> accessibleDocuments) {
        if (document == null || accessibleDocuments == null || accessibleDocuments.isEmpty()) {
            return List.of();
        }

        var snapshots = documentSnapshotService.loadAllAvailable(accessibleDocuments);
        var sourceSnapshot = snapshots.get(document.getId());
        DocumentVersion sourceVersion = displayVersion(sourceSnapshot);
        if (sourceVersion == null) {
            return List.of();
        }

        Set<String> sourceTags = tokens(jsonListCodec.read(sourceVersion.getTagsJson()));
        Set<String> sourceNames = tokens(jsonListCodec.read(sourceVersion.getNamesJson()));
        Set<String> sourceKeywords = tokens(jsonListCodec.read(sourceVersion.getKeywordsJson()));
        Set<String> sourceTextTokens = tokens(truncate(sourceVersion.getExtractedText(), MAX_EXTRACTED_TEXT_CHARACTERS));
        Set<String> sourceSummaryTokens = tokens(sourceVersion.getSummary());

        if (sourceTags.isEmpty() && sourceNames.isEmpty() && sourceKeywords.isEmpty() && sourceTextTokens.isEmpty() && sourceSummaryTokens.isEmpty()) {
            return List.of();
        }

        return accessibleDocuments.stream()
            .filter(candidate -> !candidate.getId().equals(document.getId()))
            .map(candidate -> scoreCandidate(candidate, snapshots.get(candidate.getId()), sourceTags, sourceNames, sourceKeywords, sourceTextTokens, sourceSummaryTokens))
            .filter(candidate -> candidate != null && candidate.score > 0.18d)
            .sorted(Comparator.comparingDouble(ScoredRelatedDocument::score).reversed().thenComparing(ScoredRelatedDocument::updatedAt, Comparator.reverseOrder()))
            .limit(4)
            .map(ScoredRelatedDocument::toResponse)
            .toList();
    }

    private ScoredRelatedDocument scoreCandidate(Document candidateDocument,
                                                 DocumentSnapshotService.DocumentSnapshot candidateSnapshot,
                                                 Set<String> sourceTags,
                                                 Set<String> sourceNames,
                                                 Set<String> sourceKeywords,
                                                 Set<String> sourceTextTokens,
                                                 Set<String> sourceSummaryTokens) {
        DocumentVersion candidateVersion = displayVersion(candidateSnapshot);
        if (candidateVersion == null) {
            return null;
        }

        Set<String> candidateTags = tokens(jsonListCodec.read(candidateVersion.getTagsJson()));
        Set<String> candidateNames = tokens(jsonListCodec.read(candidateVersion.getNamesJson()));
        Set<String> candidateKeywords = tokens(jsonListCodec.read(candidateVersion.getKeywordsJson()));
        Set<String> candidateTextTokens = tokens(truncate(candidateVersion.getExtractedText(), MAX_EXTRACTED_TEXT_CHARACTERS));
        Set<String> candidateSummaryTokens = tokens(candidateVersion.getSummary());

        double score = 0d;
        score += overlapScore(sourceTags, candidateTags, 0.34d);
        score += overlapScore(sourceKeywords, candidateKeywords, 0.26d);
        score += overlapScore(sourceNames, candidateNames, 0.16d);
        score += overlapScore(sourceSummaryTokens, candidateSummaryTokens, 0.14d);
        score += overlapScore(sourceTextTokens, candidateTextTokens, 0.10d);

        List<String> matchedTerms = new ArrayList<>();
        matchedTerms.addAll(sharedTerms(sourceTags, candidateTags));
        matchedTerms.addAll(sharedTerms(sourceKeywords, candidateKeywords));
        matchedTerms.addAll(sharedTerms(sourceNames, candidateNames));
        matchedTerms = matchedTerms.stream().distinct().limit(MAX_MATCHED_TERMS).toList();

        return new ScoredRelatedDocument(
            candidateDocument,
            candidateVersion,
            score,
            matchedTerms
        );
    }

    private double overlapScore(Set<String> left, Set<String> right, double weight) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0d;
        }
        long intersection = left.stream().filter(right::contains).count();
        if (intersection == 0) {
            return 0d;
        }
        long union = left.size() + right.size() - intersection;
        return weight * ((double) intersection / Math.max(1L, union));
    }

    private List<String> sharedTerms(Set<String> left, Set<String> right) {
        return left.stream().filter(right::contains).limit(MAX_MATCHED_TERMS).toList();
    }

    private Set<String> tokens(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return tokens(String.join(" ", values));
    }

    private Set<String> tokens(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        LinkedHashSet<String> values = new LinkedHashSet<>();
        var matcher = TOKEN_PATTERN.matcher(normalized);
        while (matcher.find()) {
            String token = matcher.group().trim();
            if (token.length() >= 3) {
                values.add(token);
            }
        }
        return values;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private DocumentVersion displayVersion(DocumentSnapshotService.DocumentSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return snapshot.currentVersion() != null ? snapshot.currentVersion() : snapshot.latestVersion();
    }

    private record ScoredRelatedDocument(
        Document document,
        DocumentVersion version,
        double score,
        List<String> matchedTerms
    ) {
        private java.time.Instant updatedAt() {
            return document.getUpdatedAt();
        }

        private RelatedDocumentResponse toResponse() {
            return new RelatedDocumentResponse(
                document.getId(),
                document.getName(),
                version.getMediaType(),
                document.getUpdatedAt(),
                score,
                matchedTerms,
                version.getSummary()
            );
        }
    }
}
