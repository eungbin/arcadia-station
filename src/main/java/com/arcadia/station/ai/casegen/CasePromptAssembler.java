package com.arcadia.station.ai.casegen;

import com.arcadia.station.ai.common.StructuredPrompt;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CasePromptAssembler {

    private static final String SYSTEM_PROMPT = """
            너는 허구적 우주 정거장 미스터리의 사건 설계자다.
            제공된 인물·장소·시스템·권한만 사용하라.
            범인은 SOPHIA로 고정한다.
            구체 수법, 시간표, 알리바이, 단서 문구와 로그는 이번 seed에 맞게 새로 설계하라.
            살해 방법 템플릿에서 고르지 말고 등록된 세계 요소의 새로운 조합을 작성하라.
            모든 필수 추리 축에 증거를 배치하고 전체 핵심 단서로 범인이 한 명만 남게 하라.
            핵심 단서는 EXPLORE, RAG_QUERY, CONNECT 또는 증거 제시 기반 INTERROGATE로
            결정적으로 획득 가능해야 한다.
            현실에서 재현 가능한 유해 절차, 수치, 실행 가능한 코드나 명령을 쓰지 말라.
            사실·단서·기록·이벤트 ID는 사건 안에서 유일해야 한다.
            한국어로 작성하고 주어진 JSON Schema 외 필드를 출력하지 말라.
            """;

    private final ObjectMapper objectMapper;

    public CasePromptAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public StructuredPrompt assemble(CaseGenerationRequest request) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("sessionId", request.sessionId());
        context.put("seed", request.seed());
        context.put("worldTemplate", request.world());
        context.put("mysteryRuleTemplate", request.rules());
        context.put(
                "previousValidationErrorCodes",
                request.previousIssues().stream().map(issue -> issue.code()).distinct().toList()
        );
        try {
            return new StructuredPrompt(
                    SYSTEM_PROMPT,
                    objectMapper.writeValueAsString(context)
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot assemble case generation prompt", exception);
        }
    }
}
