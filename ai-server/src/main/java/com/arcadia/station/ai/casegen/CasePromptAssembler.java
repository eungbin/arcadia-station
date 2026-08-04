package com.arcadia.station.ai.casegen;

import com.arcadia.station.ai.common.StructuredPrompt;
import com.arcadia.station.integration.FrontendIntegrationContractRepository;
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
            핵심 단서는 EXPLORE, RAG_QUERY 또는 CONNECT로 결정적으로 획득 가능해야 한다.
            alibis에는 모든 용의자를 정확히 한 번씩 포함하고, alibis의 모든 characterId에
            대해 npcKnowledge를 생성하라. 각 npcKnowledge의 initialClaimFactIds에는 해당
            알리바이의 supportingFactIds 또는 contradictingFactIds에 연결된 사실을 하나 이상,
            recommendedQuestionTopics에는 질문 주제를 하나 이상 넣어라. 공개할 결정적 사실이
            없는 인물의 revealPolicies는 빈 배열이어도 된다.
            characterId는 worldTemplate.characters의 ID를 대소문자까지 그대로 사용하라.
            culpritId, alibis, npcKnowledge, solution.nonCulpritExclusions와
            clues.suspectEffects 사이에서 동일 인물을 다른 문자열로 표기하지 말라.
            locationId는 worldTemplate.locations에 제공된 8개 ID 문자열만 대소문자까지
            그대로 사용하라. method.setupAction, method.triggerAction, timeline,
            clues.acquisition과 evidenceRecords.metadata의 locationId에 새 장소나 별칭을
            만들지 말라.
            frontendInvestigationObjects에서 clueRequired=true인 모든 objectId마다
            EXPLORE 단서를 하나 이상 생성하라. 이 단서의 sourceType은 PHYSICAL_OBJECT,
            sourceId는 objectId와 정확히 같고 acquisition.locationId는 해당 object의
            locationId와 같아야 한다. clueRequired=false인 object에도 추가 단서를 만들 수
            있지만 등록되지 않은 sourceId를 새로 만들지 말라.
            현실에서 재현 가능한 유해 절차, 수치, 실행 가능한 코드나 명령을 쓰지 말라.
            사실·단서·기록·이벤트 ID는 사건 안에서 유일해야 한다.
            한국어로 작성하고 주어진 JSON Schema 외 필드를 출력하지 말라.
            """;

    private final ObjectMapper objectMapper;
    private final FrontendIntegrationContractRepository frontendContracts;

    public CasePromptAssembler(
            ObjectMapper objectMapper,
            FrontendIntegrationContractRepository frontendContracts
    ) {
        this.objectMapper = objectMapper;
        this.frontendContracts = frontendContracts;
    }

    public StructuredPrompt assemble(CaseGenerationRequest request) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("sessionId", request.sessionId());
        context.put("seed", request.seed());
        context.put("worldTemplate", request.world());
        context.put("mysteryRuleTemplate", request.rules());
        context.put(
                "frontendInvestigationObjects",
                frontendContracts.contract().investigationObjects()
        );
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
