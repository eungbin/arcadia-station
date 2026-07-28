package com.arcadia.station.client;

import com.arcadia.station.client.dto.AssistantQueryResult;

/**
 * AI 서버 POST {AI_SERVER_BASE_URL}/api/v1/sessions/{sessionId}/assistant/queries 계약(6장)의 게이트웨이.
 */
public interface AssistantClient {
    AssistantQueryResult query(String sessionId, String question);
}
