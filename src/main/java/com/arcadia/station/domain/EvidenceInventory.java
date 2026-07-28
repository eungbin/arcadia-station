package com.arcadia.station.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "evidence_inventories")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvidenceInventory {

    @Id
    @Column(nullable = false, updatable = false)
    private String sessionId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "evidence_discovered_clues", joinColumns = @JoinColumn(name = "session_id"))
    @Column(name = "clue_id")
    private Set<String> discoveredClueIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "evidence_revealed_facts", joinColumns = @JoinColumn(name = "session_id"))
    @Column(name = "fact_id")
    private Set<String> revealedFactIds = new HashSet<>();

    // 심문에서 NPC에게 제시한 단서 (스펙 3.4절)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "evidence_presented_clues", joinColumns = @JoinColumn(name = "session_id"))
    @Column(name = "clue_id")
    private Set<String> presentedClueIdsByCharacter = new HashSet<>();

    private int wrongDeductionAttempts;

    public EvidenceInventory(String sessionId) {
        this.sessionId = sessionId;
    }
}
