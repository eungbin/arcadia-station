package com.arcadia.station.client;

import com.arcadia.station.client.dto.AssistantQueryResult;

/**
 * AI 서버 POST {AI_SERVER_BASE_URL}/api/v1/sessions/{aiCaseRequestId}/assistant/queries 계약(6장)의 게이트웨이.
 * AI 서버 회신(2026-07-29) 3.1절: 경로의 세션 키는 사건 생성 때 쓴 aiCaseRequestId여야 한다(플레이어의 sessionId 아님).
 */
public interface AssistantClient {
    AssistantQueryResult query(String aiCaseRequestId, String question);
}
