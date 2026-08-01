package com.arcadia.station.client;

import com.arcadia.station.client.dto.AssistantQueryResult;
import com.arcadia.station.client.dto.RagDiscoveredClueRef;
import com.arcadia.station.domain.GameSession;
import com.arcadia.station.domain.caseblueprint.CaseBlueprint;
import com.arcadia.station.domain.caseblueprint.EvidenceRecord;
import com.arcadia.station.repository.GameSessionRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 실제 AI 서버 없이 6장 RAG 계약을 흉내내는 Fake 구현체. 질문 문자열이 evidenceRecords의
 * searchTerms/title에 부분 일치하면 해당 기록을 인용하고 revealsClueIds를 후보로 제시한다.
 */
@Component
@Profile("!real-ai")
public class FakeAssistantClient implements AssistantClient {

    private static final List<String> SUGGESTED_QUERIES = List.of(
            "다른 시각의 기록도 보여줘", "다른 인물 관련 기록을 보여줘");

    private final GameSessionRepository gameSessionRepository;
    private final ObjectMapper objectMapper;

    public FakeAssistantClient(GameSessionRepository gameSessionRepository, ObjectMapper objectMapper) {
        this.gameSessionRepository = gameSessionRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public AssistantQueryResult query(String aiCaseRequestId, String question) {
        CaseBlueprint blueprint = readBlueprint(aiCaseRequestId);
        String normalizedQuestion = question.toLowerCase();

        List<EvidenceRecord> matched = blueprint.evidenceRecords().stream()
                .filter(record -> matches(record, normalizedQuestion))
                .toList();

        if (matched.isEmpty()) {
            return new AssistantQueryResult("관련 기록을 찾지 못했습니다.", List.of(), SUGGESTED_QUERIES, List.of());
        }

        List<String> citedRecordIds = matched.stream().map(EvidenceRecord::recordId).toList();
        String answer = matched.stream().map(EvidenceRecord::body).reduce("", (a, b) -> a.isEmpty() ? b : a + " " + b);

        Set<String> clueIds = new LinkedHashSet<>();
        matched.forEach(record -> clueIds.addAll(record.revealsClueIds()));
        List<RagDiscoveredClueRef> newlyDiscoveredClues = clueIds.stream().map(RagDiscoveredClueRef::new).toList();

        return new AssistantQueryResult(answer, citedRecordIds, SUGGESTED_QUERIES, newlyDiscoveredClues);
    }

    private boolean matches(EvidenceRecord record, String normalizedQuestion) {
        return record.searchTerms().stream()
                .map(String::toLowerCase)
                .anyMatch(normalizedQuestion::contains);
    }

    private CaseBlueprint readBlueprint(String aiCaseRequestId) {
        GameSession session = gameSessionRepository.findByAiCaseRequestId(aiCaseRequestId)
                .orElseThrow(() -> new IllegalStateException("Unknown session: " + aiCaseRequestId));
        return objectMapper.readValue(session.getCaseBlueprintJson(), CaseBlueprint.class);
    }
}
