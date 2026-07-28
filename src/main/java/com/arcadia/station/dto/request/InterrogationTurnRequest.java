package com.arcadia.station.dto.request;

import java.util.List;

public record InterrogationTurnRequest(String question, List<String> presentedClueIds) {}
