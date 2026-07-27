package com.arcadia.station.game.domain;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class EvidenceInventory {

    private final Set<String> discoveredClueIds = new LinkedHashSet<>();

    public synchronized boolean add(String clueId) {
        return discoveredClueIds.add(clueId);
    }

    public synchronized Set<String> addAll(Collection<String> clueIds) {
        Set<String> added = new LinkedHashSet<>();
        clueIds.forEach(id -> {
            if (discoveredClueIds.add(id)) {
                added.add(id);
            }
        });
        return Set.copyOf(added);
    }

    public synchronized boolean contains(String clueId) {
        return discoveredClueIds.contains(clueId);
    }

    public synchronized boolean containsAll(Collection<String> clueIds) {
        return discoveredClueIds.containsAll(clueIds);
    }

    public synchronized Set<String> snapshot() {
        return Set.copyOf(discoveredClueIds);
    }
}
