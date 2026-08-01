package com.arcadia.station.ai.rag;

import com.arcadia.station.ai.casegen.CaseBlueprint;
import com.arcadia.station.ai.common.ArcadiaAiProperties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class HybridEvidenceSearchService {

    private final ArcadiaAiProperties properties;
    private final RagIndexBuilder indexBuilder;

    public HybridEvidenceSearchService(
            ArcadiaAiProperties properties,
            RagIndexBuilder indexBuilder
    ) {
        this.properties = properties;
        this.indexBuilder = indexBuilder;
    }

    public List<SearchHit> search(
            String sessionId,
            CaseBlueprint blueprint,
            String query
    ) {
        Set<String> terms = tokenize(query);
        if (terms.isEmpty()) {
            return List.of();
        }
        float[] queryEmbedding = indexBuilder.embedQuery(query);
        java.util.Map<String, float[]> recordEmbeddings = indexBuilder.records(sessionId).stream()
                .filter(record -> record.embedding() != null)
                .collect(java.util.stream.Collectors.toMap(
                        InvestigationRagRecord::recordId,
                        InvestigationRagRecord::embedding
                ));
        List<SearchHit> hits = new ArrayList<>();
        for (CaseBlueprint.EvidenceRecord record : blueprint.evidenceRecords()) {
            if (record.visibility() != CaseBlueprint.RecordVisibility.SEARCHABLE) {
                continue;
            }
            String searchable = (
                    record.timestamp() + " "
                            + record.title() + " "
                            + record.body() + " "
                            + String.join(" ", record.searchTerms()) + " "
                            + String.join(" ", record.metadata().values())
            ).toLowerCase(Locale.ROOT);
            long matched = terms.stream().filter(searchable::contains).count();
            double exactScore = (double) matched / terms.size();
            float[] recordEmbedding = recordEmbeddings.get(record.recordId());
            double semanticScore = cosine(queryEmbedding, recordEmbedding);
            double score = queryEmbedding == null || recordEmbedding == null
                    ? exactScore
                    : (exactScore * 0.7) + (Math.max(0, semanticScore) * 0.3);
            if (exactScore == 0 && semanticScore < 0.55) {
                continue;
            }
            if (score >= properties.rag().minimumScore()) {
                hits.add(new SearchHit(record, score));
            }
        }
        return hits.stream()
                .sorted(Comparator.comparingDouble(SearchHit::score).reversed()
                        .thenComparing(hit -> hit.record().recordId()))
                .limit(properties.rag().topK())
                .toList();
    }

    private double cosine(float[] left, float[] right) {
        if (left == null || right == null || left.length != right.length || left.length == 0) {
            return -1;
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftNorm += left[index] * left[index];
            rightNorm += right[index] * right[index];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return -1;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private Set<String> tokenize(String query) {
        if (query == null) {
            return Set.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        Arrays.stream(query.toLowerCase(Locale.ROOT).split("[\\s,?.!]+"))
                .map(String::trim)
                .filter(token -> token.length() >= 2)
                .forEach(tokens::add);
        return tokens;
    }

    public record SearchHit(CaseBlueprint.EvidenceRecord record, double score) {}
}
