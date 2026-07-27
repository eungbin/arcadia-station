package com.arcadia.station.ai.npc;

import com.arcadia.station.ai.common.AiPurpose;
import com.arcadia.station.ai.common.ArcadiaAiProperties;
import com.arcadia.station.ai.common.JsonSchemaRepository;
import com.arcadia.station.ai.common.OpenAiGateway;
import com.arcadia.station.ai.common.StructuredPrompt;
import com.arcadia.station.game.application.GameSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class InterrogationService {

    private final NpcContextFactory contextFactory;
    private final NpcResponseGuard guard;
    private final OpenAiGateway gateway;
    private final JsonSchemaRepository schemas;
    private final ArcadiaAiProperties properties;
    private final ObjectMapper objectMapper;
    private final GameSessionService sessions;

    public InterrogationService(
            NpcContextFactory contextFactory,
            NpcResponseGuard guard,
            OpenAiGateway gateway,
            JsonSchemaRepository schemas,
            ArcadiaAiProperties properties,
            ObjectMapper objectMapper,
            GameSessionService sessions
    ) {
        this.contextFactory = contextFactory;
        this.guard = guard;
        this.gateway = gateway;
        this.schemas = schemas;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.sessions = sessions;
    }

    public NpcTurnResponse interrogate(
            String sessionId,
            String characterId,
            String question,
            List<String> presentedClueIds
    ) {
        sessions.requireSession(sessionId).startInvestigation();
        NpcTurnContext context = contextFactory.create(
                sessionId,
                characterId,
                question,
                presentedClueIds
        );
        NpcTurnResponse response = shouldUseAi()
                ? generateWithAi(context)
                : deterministicResponse(context);
        return guard.isAllowed(context, response)
                ? response
                : guard.safeFallback(context);
    }

    private boolean shouldUseAi() {
        return properties.enabled()
                && !properties.offlineMode()
                && properties.apiKey() != null
                && !properties.apiKey().isBlank();
    }

    private NpcTurnResponse generateWithAi(NpcTurnContext context) {
        try {
            return gateway.generateStructured(
                    AiPurpose.NPC_TURN,
                    "npc-turn-v1",
                    new StructuredPrompt(
                            """
                                    너는 제공된 NPC 역할로만 답한다.
                                    allowedFacts의 문장 밖에 있는 새로운 사실·인물·시각·단서 ID를 만들지 마라.
                                    revealedFactIds는 revealableFactIds의 부분집합이어야 한다.
                                    questionCandidates에서 정확히 두 개의 추천 질문을 선택하라.
                                    숨겨진 사건 전체나 정답을 직접 공개하지 마라.
                                    """,
                            objectMapper.writeValueAsString(context)
                    ),
                    schemas.get("npc_turn"),
                    NpcTurnResponse.class
            );
        } catch (Exception exception) {
            return guard.safeFallback(context);
        }
    }

    private NpcTurnResponse deterministicResponse(NpcTurnContext context) {
        List<String> revealed = context.revealableFactIds().stream().limit(1).toList();
        List<NpcTurnResponse.RecommendedQuestion> questions =
                context.questionCandidates().stream()
                        .limit(2)
                        .map(candidate -> new NpcTurnResponse.RecommendedQuestion(
                                candidate.topicId(),
                                candidate.label()
                        ))
                        .toList();
        if (!revealed.isEmpty()) {
            String statement = context.allowedFacts().stream()
                    .filter(fact -> fact.factId().equals(revealed.getFirst()))
                    .map(NpcTurnContext.AllowedFact::statement)
                    .findFirst()
                    .orElse("제시한 기록과 관련된 작업이 있었던 것은 인정합니다.");
            return new NpcTurnResponse(
                    "그 기록이 있다면 일부는 인정하죠. " + statement
                            + " 하지만 그것만으로 사건 전체가 설명되지는 않습니다.",
                    NpcTurnResponse.Emotion.DEFENSIVE,
                    revealed,
                    questions
            );
        }
        return new NpcTurnResponse(
                "저는 통상적인 업무를 했을 뿐입니다. 구체적인 기록을 제시해 주세요.",
                NpcTurnResponse.Emotion.CALM,
                List.of(),
                questions
        );
    }
}
