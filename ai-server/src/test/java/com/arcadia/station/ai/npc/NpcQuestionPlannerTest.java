package com.arcadia.station.ai.npc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class NpcQuestionPlannerTest {

    private final NpcQuestionPlanner planner = new NpcQuestionPlanner();

    @Test
    void beginsWithConfiguredTopicsBeforeThereIsConversationHistory() {
        List<NpcTurnContext.QuestionCandidate> candidates = planner.plan(
                List.of("안전 점검 예약의 목적", "진단 실행 시각"),
                List.of(),
                List.of()
        );

        assertThat(candidates)
                .extracting(NpcTurnContext.QuestionCandidate::topicId)
                .containsExactly("TOPIC-1", "TOPIC-2");
    }

    @Test
    void changesCandidatesToEvidenceAndTimelineFollowUpsAfterAturn() {
        List<NpcTurnContext.QuestionCandidate> candidates = planner.plan(
                List.of("안전 점검 예약의 목적", "진단 실행 시각"),
                List.of(new NpcConversationMemory.Turn(
                        "안전 점검 예약의 목적",
                        "통상적인 점검이었습니다.",
                        "CALM",
                        List.of(),
                        List.of()
                )),
                List.of("CLUE-SETUP-PANEL")
        );

        assertThat(candidates)
                .extracting(NpcTurnContext.QuestionCandidate::topicId)
                .startsWith("FOLLOW_UP_EVIDENCE", "FOLLOW_UP_TIMELINE");
        assertThat(candidates)
                .extracting(NpcTurnContext.QuestionCandidate::label)
                .doesNotContain("안전 점검 예약의 목적");
    }
}
