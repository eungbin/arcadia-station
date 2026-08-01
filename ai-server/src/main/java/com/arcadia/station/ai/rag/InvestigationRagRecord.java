package com.arcadia.station.ai.rag;

import java.util.List;
import java.util.Map;

public record InvestigationRagRecord(
        String sessionId,
        String recordId,
        String type,
        String timestamp,
        String title,
        String body,
        Map<String, String> metadata,
        List<String> revealsClueIds,
        float[] embedding
) {}
