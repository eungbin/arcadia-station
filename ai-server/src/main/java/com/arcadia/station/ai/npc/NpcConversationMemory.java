package com.arcadia.station.ai.npc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * AI 서버 인메모리 세션 안에서 NPC별 최근 심문 대화를 보관한다.
 *
 * <p>외부 제공자별 ChatMemory 기능에 의존하지 않는다. 그래야 Gemini/OpenAI 전환 시에도
 * 동일한 화이트리스트·응답 검증 규칙을 적용할 수 있고, 어떤 대화가 프롬프트에 들어가는지
 * 서버가 명확히 통제할 수 있다. AI 서버 자체가 인메모리 세션을 쓰므로, 서버를 재시작하면
 * 이 메모리도 해당 세션과 함께 사라진다.</p>
 */
@Component
public class NpcConversationMemory {

    private final Map<ConversationKey, Conversation> conversations = new ConcurrentHashMap<>();

    /** 같은 세션·NPC의 동시 턴이 앞뒤 순서를 바꾸지 않도록 직렬화한다. */
    public <T> T inConversation(String sessionId, String characterId, Supplier<T> action) {
        Conversation conversation = conversations.computeIfAbsent(
                new ConversationKey(sessionId, characterId),
                ignored -> new Conversation()
        );
        synchronized (conversation) {
            return action.get();
        }
    }

    public List<Turn> recent(String sessionId, String characterId, int maximumTurns) {
        Conversation conversation = conversations.get(new ConversationKey(sessionId, characterId));
        if (conversation == null || maximumTurns <= 0) {
            return List.of();
        }
        synchronized (conversation) {
            int skip = Math.max(0, conversation.turns.size() - maximumTurns);
            List<Turn> result = new ArrayList<>(Math.min(maximumTurns, conversation.turns.size()));
            int index = 0;
            for (Turn turn : conversation.turns) {
                if (index++ >= skip) {
                    result.add(turn);
                }
            }
            return List.copyOf(result);
        }
    }

    /** 검증을 통과한 최종 응답만 기록한다. */
    public void append(String sessionId, String characterId, Turn turn, int maximumTurns) {
        if (maximumTurns <= 0) {
            return;
        }
        Conversation conversation = conversations.computeIfAbsent(
                new ConversationKey(sessionId, characterId),
                ignored -> new Conversation()
        );
        synchronized (conversation) {
            conversation.turns.addLast(turn);
            while (conversation.turns.size() > maximumTurns) {
                conversation.turns.removeFirst();
            }
        }
    }

    public record Turn(
            String question,
            String dialogue,
            String emotion,
            List<String> presentedClueIds,
            List<String> revealedFactIds
    ) {
        public Turn {
            presentedClueIds = List.copyOf(presentedClueIds);
            revealedFactIds = List.copyOf(revealedFactIds);
        }
    }

    private record ConversationKey(String sessionId, String characterId) {}

    private static final class Conversation {
        private final Deque<Turn> turns = new ArrayDeque<>();
    }
}
