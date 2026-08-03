package com.arcadia.station.service;

import com.arcadia.station.domain.caseblueprint.Clue;
import java.util.List;

public interface ClueUnlockService {
    List<Clue> exploreLocation(String sessionId, String locationId, String objectHint);

    /**
     * 6장 RAG 프록시가 newlyDiscoveredClues로 제안한 clueId 후보를 검증 후 해금한다.
     * 등록되지 않았거나 acquisition.type이 RAG_QUERY가 아닌 clueId는 무시한다.
     */
    List<Clue> unlockRagClues(String sessionId, List<String> candidateClueIds);

    List<Clue> resolveConnectClues(String sessionId);
}
