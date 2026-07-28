package com.arcadia.station.client;

import com.arcadia.station.client.dto.NpcTurnResult;
import java.util.List;

/**
 * AI 서버 POST {AI_SERVER_BASE_URL}/api/v1/sessions/{sessionId}/interrogations/{characterId}/turns 계약(5장)의 게이트웨이.
 */
public interface InterrogationClient {
    NpcTurnResult ask(String sessionId, String characterId, String question, List<String> presentedClueIds);
}
