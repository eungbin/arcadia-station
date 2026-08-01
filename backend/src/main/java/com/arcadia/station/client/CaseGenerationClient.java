package com.arcadia.station.client;

import com.arcadia.station.client.dto.CaseGenerationAck;
import com.arcadia.station.client.dto.CaseGenerationStatus;

/**
 * AI 서버 POST/GET /internal/v1/cases 계약(스펙 4장)의 게이트웨이.
 */
public interface CaseGenerationClient {
    CaseGenerationAck requestCase(String aiCaseRequestId, String seed);

    CaseGenerationStatus pollStatus(String aiCaseRequestId);
}
