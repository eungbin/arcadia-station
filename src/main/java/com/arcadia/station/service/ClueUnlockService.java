package com.arcadia.station.service;

import com.arcadia.station.domain.caseblueprint.Clue;
import java.util.List;

public interface ClueUnlockService {
    List<Clue> exploreLocation(String sessionId, String locationId);

    List<Clue> resolveConnectClues(String sessionId);
}
