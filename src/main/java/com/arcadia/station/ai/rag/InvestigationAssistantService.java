package com.arcadia.station.ai.rag;

import com.arcadia.station.ai.casegen.CaseBlueprint;
import com.arcadia.station.ai.common.AiPurpose;
import com.arcadia.station.ai.common.ArcadiaAiProperties;
import com.arcadia.station.ai.common.JsonSchemaRepository;
import com.arcadia.station.ai.common.OpenAiGateway;
import com.arcadia.station.ai.common.StructuredPrompt;
import com.arcadia.station.game.api.dto.PublicClueView;
import com.arcadia.station.game.application.GameSessionService;
import com.arcadia.station.game.domain.GameSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class InvestigationAssistantService {

    private final GameSessionService sessions;
    private final HybridEvidenceSearchService searchService;
    private final OpenAiGateway gateway;
    private final JsonSchemaRepository schemas;
    private final ArcadiaAiProperties properties;
    private final ObjectMapper objectMapper;

    public InvestigationAssistantService(
            GameSessionService sessions,
            HybridEvidenceSearchService searchService,
            OpenAiGateway gateway,
            JsonSchemaRepository schemas,
            ArcadiaAiProperties properties,
            ObjectMapper objectMapper
    ) {
        this.sessions = sessions;
        this.searchService = searchService;
        this.gateway = gateway;
        this.schemas = schemas;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public AssistantQueryResponse query(String sessionId, String question) {
        GameSession session = sessions.requireSession(sessionId);
        CaseBlueprint blueprint = sessions.requireFrozenCase(sessionId).blueprint();
        session.startInvestigation();
        List<HybridEvidenceSearchService.SearchHit> hits = searchService.search(
                sessionId,
                blueprint,
                question
        );
        Set<String> allowedRecordIds = hits.stream()
                .map(hit -> hit.record().recordId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        RagSummary summary = summarize(question, hits);
        if (!allowedRecordIds.containsAll(summary.citedRecordIds())) {
            summary = deterministicSummary(hits);
        }

        Map<String, CaseBlueprint.Clue> clues = blueprint.clues().stream()
                .collect(Collectors.toMap(CaseBlueprint.Clue::clueId, Function.identity()));
        List<CaseBlueprint.Clue> newlyDiscovered = new ArrayList<>();
        for (HybridEvidenceSearchService.SearchHit hit : hits) {
            if (!summary.citedRecordIds().contains(hit.record().recordId())) {
                continue;
            }
            for (String clueId : hit.record().revealsClueIds()) {
                CaseBlueprint.Clue clue = clues.get(clueId);
                if (clue != null
                        && clue.acquisition().type() == CaseBlueprint.AcquisitionType.RAG_QUERY
                        && session.evidenceInventory()
                                .containsAll(clue.acquisition().requiredClueIds())
                        && session.evidenceInventory().add(clueId)) {
                    newlyDiscovered.add(clue);
                }
            }
        }
        return new AssistantQueryResponse(
                summary.answer(),
                summary.citedRecordIds(),
                summary.suggestedQueries(),
                newlyDiscovered.stream().map(PublicClueView::from).toList()
        );
    }

    private RagSummary summarize(
            String question,
            List<HybridEvidenceSearchService.SearchHit> hits
    ) {
        if (hits.isEmpty()) {
            return new RagSummary(
                    "현재 검색 가능한 확정 기록에서 관련 항목을 찾지 못했습니다.",
                    List.of(),
                    List.of("인물 이름과 시각을 함께 검색해 보세요", "장소와 작업 종류를 함께 검색해 보세요")
            );
        }
        if (!properties.enabled() || properties.offlineMode()
                || properties.apiKey() == null || properties.apiKey().isBlank()) {
            return deterministicSummary(hits);
        }
        try {
            List<CaseBlueprint.EvidenceRecord> records = hits.stream()
                    .map(hit -> hit.record())
                    .toList();
            RagSummary response = gateway.generateStructured(
                    AiPurpose.RAG_SUMMARY,
                    "rag-summary-v1",
                    new StructuredPrompt(
                            "검색 결과에 있는 사실만 한국어로 요약하라. citedRecordIds는 제공된 recordId만 사용하라.",
                            objectMapper.writeValueAsString(Map.of(
                                    "question", question,
                                    "records", records
                            ))
                    ),
                    schemas.get("rag_summary"),
                    RagSummary.class
            );
            Set<String> validIds = hits.stream()
                    .map(hit -> hit.record().recordId())
                    .collect(Collectors.toSet());
            return validIds.containsAll(response.citedRecordIds())
                    ? response
                    : deterministicSummary(hits);
        } catch (Exception exception) {
            return deterministicSummary(hits);
        }
    }

    private RagSummary deterministicSummary(List<HybridEvidenceSearchService.SearchHit> hits) {
        List<CaseBlueprint.EvidenceRecord> records = hits.stream()
                .map(HybridEvidenceSearchService.SearchHit::record)
                .toList();
        String answer = records.stream()
                .map(record -> record.timestamp() + " " + record.title() + ": " + record.body())
                .collect(Collectors.joining(" "));
        return new RagSummary(
                answer,
                records.stream().map(CaseBlueprint.EvidenceRecord::recordId).toList(),
                records.stream()
                        .flatMap(record -> record.searchTerms().stream())
                        .distinct()
                        .limit(2)
                        .map(term -> term + " 관련 기록을 더 보여줘")
                        .toList()
        );
    }

    public record RagSummary(
            String answer,
            List<String> citedRecordIds,
            List<String> suggestedQueries
    ) {}

    public record AssistantQueryResponse(
            String answer,
            List<String> citedRecordIds,
            List<String> suggestedQueries,
            List<PublicClueView> newlyDiscoveredClues
    ) {}
}
