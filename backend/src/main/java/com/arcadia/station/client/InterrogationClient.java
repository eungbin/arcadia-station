package com.arcadia.station.client;

import com.arcadia.station.client.dto.NpcTurnResult;
import java.util.List;

/**
 * AI 서버 POST {AI_SERVER_BASE_URL}/api/v1/sessions/{aiCaseRequestId}/interrogations/{characterId}/turns 계약(5장)의 게이트웨이.
 * AI 서버 회신(2026-07-29) 3.1절: 경로의 세션 키는 사건 생성 때 쓴 aiCaseRequestId여야 한다(플레이어의 sessionId 아님).
 */
public interface InterrogationClient {
    /**
     * @param presentedClueIds 이번 턴에 새로 제시한 단서뿐 아니라, 이 세션에서 이 characterId에게 지금까지
     *                         제시한 단서 ID 전체(누적)를 보내야 한다 — AI 서버가 턴 이력을 자체적으로
     *                         누적하지 않기 때문이다(AI 서버 회신 3.2절).
     */
    NpcTurnResult ask(String aiCaseRequestId, String characterId, String question, List<String> presentedClueIds);
}
