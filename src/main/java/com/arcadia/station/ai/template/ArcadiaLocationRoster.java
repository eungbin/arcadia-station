package com.arcadia.station.ai.template;

import java.util.List;
import java.util.Set;

public final class ArcadiaLocationRoster {

    public static final String COMMANDER_OFFICE = "COMMANDER_OFFICE";
    public static final String DEPUTY_COMMANDER_OFFICE = "DEPUTY_COMMANDER_OFFICE";
    public static final String CENTRAL_HUB = "CENTRAL_HUB";
    public static final String MEDICAL_BAY = "MEDICAL_BAY";
    public static final String ENGINEERING_BAY = "ENGINEERING_BAY";
    public static final String COMMUNICATIONS_CENTER = "COMMUNICATIONS_CENTER";
    public static final String CARGO_BAY = "CARGO_BAY";
    public static final String COMMON_AREA = "COMMON_AREA";

    public static final List<String> IDS = List.of(
            COMMANDER_OFFICE,
            DEPUTY_COMMANDER_OFFICE,
            CENTRAL_HUB,
            MEDICAL_BAY,
            ENGINEERING_BAY,
            COMMUNICATIONS_CENTER,
            CARGO_BAY,
            COMMON_AREA
    );
    public static final Set<String> ID_SET = Set.copyOf(IDS);

    private ArcadiaLocationRoster() {}

    public static boolean contains(String locationId) {
        return ID_SET.contains(locationId);
    }
}
