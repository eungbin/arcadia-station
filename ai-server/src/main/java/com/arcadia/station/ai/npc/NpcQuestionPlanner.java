package com.arcadia.station.ai.npc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 매 턴에 안전한 질문 후보 풀을 만든다.
 *
 * <p>질문 문구를 모델이 자유롭게 발명하게 두면 아직 공개되지 않은 사실이 버튼에서 새어 나갈
 * 수 있다. 이 플래너는 현재 대화 상태에 맞는 문구만 서버에서 만들고, 모델은 그중 두 개를
 * 고르는 역할만 맡는다.</p>
 */
@Component
public class NpcQuestionPlanner {

    public List<NpcTurnContext.QuestionCandidate> plan(
            List<String> configuredTopics,
            List<NpcConversationMemory.Turn> history,
            List<String> presentedClueIds
    ) {
        Map<String, NpcTurnContext.QuestionCandidate> candidates = new LinkedHashMap<>();
        if (!history.isEmpty()) {
            NpcConversationMemory.Turn latest = history.getLast();
            if (!latest.presentedClueIds().isEmpty() || !presentedClueIds.isEmpty()) {
                add(candidates, "FOLLOW_UP_EVIDENCE",
                        "방금 제시한 기록과 당신의 진술이 어떻게 양립하는지 설명해 주십시오.");
            }
            add(candidates, "FOLLOW_UP_TIMELINE",
                    "방금 답변을 시간 순서대로 다시 설명해 주십시오.");
            if (!latest.revealedFactIds().isEmpty()) {
                add(candidates, "FOLLOW_UP_DETAIL",
                        "방금 인정한 내용에서 누가, 언제, 무엇을 했는지 구체적으로 설명해 주십시오.");
            } else {
                add(candidates, "FOLLOW_UP_SUPPORT",
                        "그 주장을 뒷받침할 기록이나 목격자가 있습니까?");
            }
        }

        Set<String> asked = history.stream()
                .map(NpcConversationMemory.Turn::question)
                .map(this::normalize)
                .collect(Collectors.toSet());
        addUnaskedConfigured(candidates, configuredTopics, asked);
        // 모든 기본 질문을 이미 사용했다면, 첫 턴과 동일한 버튼으로 되돌아가지는 않되
        // 질문 풀이 비지 않도록 가장 관련 있는 기본 주제도 다시 후보로 둔다.
        if (candidates.size() < 2) {
            addConfigured(candidates, configuredTopics, "REVISIT_TOPIC");
        }
        if (candidates.size() < 2) {
            add(candidates, "SAFE_FOLLOW_UP_TIMELINE",
                    "사건 당시의 동선을 시간 순서대로 설명해 주십시오.");
        }
        if (candidates.size() < 2) {
            add(candidates, "SAFE_FOLLOW_UP_RECORD",
                    "당신의 주장을 확인할 수 있는 기록은 무엇입니까?");
        }
        return List.copyOf(candidates.values());
    }

    private void addConfigured(
            Map<String, NpcTurnContext.QuestionCandidate> candidates,
            List<String> topics,
            String prefix
    ) {
        for (int index = 0; index < topics.size(); index++) {
            add(candidates, prefix + "-" + (index + 1), topics.get(index));
        }
    }

    private void addUnaskedConfigured(
            Map<String, NpcTurnContext.QuestionCandidate> candidates,
            List<String> topics,
            Set<String> asked
    ) {
        for (int index = 0; index < topics.size(); index++) {
            String topic = topics.get(index);
            if (!asked.contains(normalize(topic))) {
                add(candidates, "TOPIC-" + (index + 1), topic);
            }
        }
    }

    private void add(
            Map<String, NpcTurnContext.QuestionCandidate> candidates,
            String topicId,
            String label
    ) {
        candidates.putIfAbsent(topicId, new NpcTurnContext.QuestionCandidate(topicId, label));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
