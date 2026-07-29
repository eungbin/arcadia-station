package com.arcadia.station.ai.rag;

import com.arcadia.station.ai.casegen.CaseBlueprint;
import com.arcadia.station.ai.casegen.FrozenCaseBlueprint;
import com.arcadia.station.ai.common.ArcadiaAiProperties;
import com.arcadia.station.ai.common.OpenAiGateway;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class RagIndexBuilder {

    private final OpenAiGateway gateway;
    private final ArcadiaAiProperties properties;
    private final Map<String, List<InvestigationRagRecord>> indexes = new ConcurrentHashMap<>();

    public RagIndexBuilder(
            OpenAiGateway gateway,
            ArcadiaAiProperties properties
    ) {
        this.gateway = gateway;
        this.properties = properties;
    }

    public void index(FrozenCaseBlueprint frozen) {
        List<InvestigationRagRecord> records = frozen.blueprint().evidenceRecords().stream()
                .map(record -> toRagRecord(frozen.sessionId(), record))
                .toList();
        indexes.put(frozen.sessionId(), records);
    }

    public List<InvestigationRagRecord> records(String sessionId) {
        return indexes.getOrDefault(sessionId, List.of());
    }

    public float[] embedQuery(String query) {
        if (!embeddingEnabled()) {
            return null;
        }
        try {
            return gateway.createEmbedding(query);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private InvestigationRagRecord toRagRecord(
            String sessionId,
            CaseBlueprint.EvidenceRecord record
    ) {
        float[] embedding = null;
        if (embeddingEnabled()) {
            try {
                embedding = gateway.createEmbedding(
                        record.timestamp() + "\n"
                                + record.title() + "\n"
                                + record.body() + "\n"
                                + String.join(" ", record.searchTerms())
                );
            } catch (RuntimeException ignored) {
                embedding = null;
            }
        }
        return new InvestigationRagRecord(
                sessionId,
                record.recordId(),
                record.recordType(),
                record.timestamp(),
                record.title(),
                record.body(),
                record.metadata(),
                record.revealsClueIds(),
                embedding
        );
    }

    private boolean embeddingEnabled() {
        return properties.enabled()
                && !properties.offlineMode()
                && properties.hasActiveApiKey();
    }
}
