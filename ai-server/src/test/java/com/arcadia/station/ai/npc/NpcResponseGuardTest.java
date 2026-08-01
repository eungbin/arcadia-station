package com.arcadia.station.ai.npc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class NpcResponseGuardTest {

    private final NpcResponseGuard guard = new NpcResponseGuard();

    @Test
    void rejectsEarlySecretDisclosure() {
        NpcTurnContext context = context(List.of("FACT-ALLOWED"));
        NpcTurnResponse response = response(List.of("FACT-SECRET"));

        assertThat(guard.isAllowed(context, response)).isFalse();
    }

    @Test
    void acceptsAllowedFactAndWhitelistedQuestionTopics() {
        NpcTurnContext context = context(List.of("FACT-ALLOWED"));
        NpcTurnResponse response = response(List.of("FACT-ALLOWED"));

        assertThat(guard.isAllowed(context, response)).isTrue();
    }

    private NpcTurnContext context(List<String> revealable) {
        return new NpcTurnContext(
                "session",
                "SOPHIA",
                "소피아",
                "의무관",
                List.of("침착함"),
                "질문",
                List.of("CLUE-1"),
                List.of(new NpcTurnContext.AllowedFact(
                        "FACT-ALLOWED",
                        "허용된 사실",
                        true
                )),
                revealable,
                List.of(
                        new NpcTurnContext.QuestionCandidate("TOPIC-1", "첫 질문"),
                        new NpcTurnContext.QuestionCandidate("TOPIC-2", "둘째 질문")
                )
        );
    }

    private NpcTurnResponse response(List<String> facts) {
        return new NpcTurnResponse(
                "답변",
                NpcTurnResponse.Emotion.CALM,
                facts,
                List.of(
                        new NpcTurnResponse.RecommendedQuestion("TOPIC-1", "첫 질문"),
                        new NpcTurnResponse.RecommendedQuestion("TOPIC-2", "둘째 질문")
                )
        );
    }
}
