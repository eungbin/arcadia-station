package com.arcadia.station.ai.npc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class NpcConversationMemoryTest {

    @Test
    void retainsOnlyTheConfiguredRecentTurnsAndKeepsNpcConversationsSeparate() {
        NpcConversationMemory memory = new NpcConversationMemory();
        memory.append("session-a", "SOPHIA", turn("첫 질문"), 2);
        memory.append("session-a", "SOPHIA", turn("둘째 질문"), 2);
        memory.append("session-a", "SOPHIA", turn("셋째 질문"), 2);

        assertThat(memory.recent("session-a", "SOPHIA", 8))
                .extracting(NpcConversationMemory.Turn::question)
                .containsExactly("둘째 질문", "셋째 질문");
        assertThat(memory.recent("session-a", "MAYA", 8)).isEmpty();
        assertThat(memory.recent("other-session", "SOPHIA", 8)).isEmpty();
    }

    private NpcConversationMemory.Turn turn(String question) {
        return new NpcConversationMemory.Turn(
                question,
                "검증된 답변",
                "CALM",
                List.of(),
                List.of()
        );
    }
}
