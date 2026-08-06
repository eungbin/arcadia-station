package com.arcadia.station.ai.rag;

import com.arcadia.station.ai.casegen.CaseBlueprint;
import com.arcadia.station.ai.common.AiPurpose;
import com.arcadia.station.ai.common.ArcadiaAiProperties;
import com.arcadia.station.ai.common.JsonSchemaRepository;
import com.arcadia.station.ai.common.OpenAiGateway;
import com.arcadia.station.ai.common.StructuredPrompt;
import com.arcadia.station.ai.presentation.PlayerFacingTextFormatter;
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
    private final PlayerFacingTextFormatter playerText;

    public InvestigationAssistantService(
            GameSessionService sessions,
            HybridEvidenceSearchService searchService,
            OpenAiGateway gateway,
            JsonSchemaRepository schemas,
            ArcadiaAiProperties properties,
            ObjectMapper objectMapper,
            PlayerFacingTextFormatter playerText
    ) {
        this.sessions = sessions;
        this.searchService = searchService;
        this.gateway = gateway;
        this.schemas = schemas;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.playerText = playerText;
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
        RagSummary summary = summarize(question, hits, blueprint);
        if (!allowedRecordIds.containsAll(summary.citedRecordIds())) {
            summary = deterministicSummary(playerFacingHits(hits, blueprint));
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
                playerText.format(summary.answer()),
                summary.citedRecordIds(),
                summary.suggestedQueries(),
                newlyDiscovered.stream().map(PublicClueView::from).toList()
        );
    }

    private RagSummary summarize(
            String question,
            List<HybridEvidenceSearchService.SearchHit> hits,
            CaseBlueprint blueprint
    ) {
        if (hits.isEmpty()) {
            return new RagSummary(
                    "현재 검색 가능한 확정 기록에서 관련 항목을 찾지 못했습니다.",
                    List.of(),
                    List.of("인물 이름과 시각을 함께 검색해 보세요", "장소와 작업 종류를 함께 검색해 보세요")
            );
        }
        if (!properties.enabled() || properties.offlineMode()
                || !properties.hasActiveApiKey()) {
            return deterministicSummary(playerFacingHits(hits, blueprint));
        }
        try {
            List<PlayerFacingHit> records = playerFacingHits(hits, blueprint);
            Map<String, String> recordIdsByCitation = records.stream()
                    .collect(Collectors.toMap(
                            PlayerFacingHit::citationId,
                            PlayerFacingHit::recordId,
                            (first, ignored) -> first
                    ));
            RagDraft response = gateway.generateStructured(
                    AiPurpose.RAG_SUMMARY,
                    "rag-summary-v2",
                    new StructuredPrompt(
                            """
                                    너는 수사 보조관이다. records에 있는 공개 사실만 사용해 플레이어가 바로
                                    이해할 수 있는 한국어 요약을 작성하라. answer는 '핵심 정리:'로 시작하고
                                    2~4개의 짧은 번호 문장으로 쓴다. 먼저 확인된 사실을 말하고, 모순이
                                    확인되지 않았다면 '현재 기록만으로는 모순을 단정할 수 없다'고 분명히 말하라.
                                    내부 recordId, citationId, fact ID, 장소/인물/시스템 ID, 영문 enum,
                                    명령 코드, metadata, 코드 블록, 마크다운 표기를 answer에 절대 쓰지 마라.
                                    citedRecordIds에는 records의 citationId만 사용하라.
                                    """,
                            objectMapper.writeValueAsString(Map.of(
                                    "question", question,
                                    "records", records.stream().map(PlayerFacingHit::view).toList()
                            ))
                    ),
                    schemas.get("rag_summary"),
                    RagDraft.class
            );
            List<String> citedRecordIds = response.citedRecordIds().stream()
                    .map(recordIdsByCitation::get)
                    .toList();
            if (citedRecordIds.contains(null) || citedRecordIds.isEmpty()) {
                return deterministicSummary(records);
            }
            return new RagSummary(
                    playerText.format(response.answer()),
                    citedRecordIds,
                    response.suggestedQueries().stream().map(playerText::format).toList()
            );
        } catch (Exception exception) {
            return deterministicSummary(playerFacingHits(hits, blueprint));
        }
    }

    /** API 실패 시에도 원본 로그/메타데이터를 화면에 그대로 흘리지 않는다. */
    private RagSummary deterministicSummary(List<PlayerFacingHit> records) {
        String answer = "핵심 정리:\n" + records.stream()
                .limit(4)
                .map(record -> record.view().timestamp() + " — "
                        + String.join(" ", record.view().facts()))
                .collect(Collectors.joining("\n"))
                + "\n현재 기록만으로는 모순을 단정할 수 없습니다.";
        return new RagSummary(
                playerText.format(answer),
                records.stream().map(PlayerFacingHit::recordId).toList(),
                records.stream()
                        .flatMap(record -> record.view().facts().stream())
                        .distinct()
                        .limit(2)
                        .map(term -> playerText.format(term) + " 관련 기록을 더 보여줘")
                        .toList()
        );
    }

    private List<PlayerFacingHit> playerFacingHits(
            List<HybridEvidenceSearchService.SearchHit> hits,
            CaseBlueprint blueprint
    ) {
        Map<String, CaseBlueprint.Clue> clues = blueprint.clues().stream()
                .collect(Collectors.toMap(CaseBlueprint.Clue::clueId, Function.identity()));
        List<PlayerFacingHit> result = new ArrayList<>();
        for (int index = 0; index < hits.size(); index++) {
            CaseBlueprint.EvidenceRecord record = hits.get(index).record();
            List<String> facts = record.revealsClueIds().stream()
                    .map(clues::get)
                    .filter(java.util.Objects::nonNull)
                    .map(CaseBlueprint.Clue::playerText)
                    .map(playerText::format)
                    .filter(text -> !text.isBlank())
                    .distinct()
                    .toList();
            if (facts.isEmpty()) {
                facts = List.of(playerText.format(record.body()));
            }
            String citationId = Integer.toString(index + 1);
            result.add(new PlayerFacingHit(
                    record.recordId(),
                    citationId,
                    new PlayerFacingRagRecord(
                            citationId,
                            playerText.format(record.timestamp()),
                            playerText.format(record.title()),
                            facts
                    )
            ));
        }
        return List.copyOf(result);
    }

    public record RagSummary(
            String answer,
            List<String> citedRecordIds,
            List<String> suggestedQueries
    ) {}

    /** 모델 내부 응답. citedRecordIds는 화면에 노출되지 않는 짧은 citationId다. */
    public record RagDraft(
            String answer,
            List<String> citedRecordIds,
            List<String> suggestedQueries
    ) {}

    private record PlayerFacingHit(
            String recordId,
            String citationId,
            PlayerFacingRagRecord view
    ) {}

    /** 원본 metadata/recordId 없이 RAG 모델에 전달하는 공개용 레코드. */
    private record PlayerFacingRagRecord(
            String citationId,
            String timestamp,
            String title,
            List<String> facts
    ) {}

    public record AssistantQueryResponse(
            String answer,
            List<String> citedRecordIds,
            List<String> suggestedQueries,
            List<PublicClueView> newlyDiscoveredClues
    ) {}
}
