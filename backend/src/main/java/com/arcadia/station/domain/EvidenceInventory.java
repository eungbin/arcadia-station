package com.arcadia.station.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
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

    // 심문에서 NPC에게 제시한 단서 (스펙 3.4절). "characterId::clueId" 형태로 인코딩해서
    // NPC별로 구분한다 — 한 용의자에게 보여준 단서가 다른 용의자에게 보여준 것으로 잘못 인정되면 안 된다.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "evidence_presented_clues", joinColumns = @JoinColumn(name = "session_id"))
    @Column(name = "clue_id")
    private Set<String> presentedClueIdsByCharacter = new HashSet<>();

    private int wrongDeductionAttempts;

    public EvidenceInventory(String sessionId) {
        this.sessionId = sessionId;
    }

    private static final String CHARACTER_CLUE_SEPARATOR = "::";

    public void recordPresentedClues(String characterId, Collection<String> clueIds) {
        for (String clueId : clueIds) {
            presentedClueIdsByCharacter.add(characterId + CHARACTER_CLUE_SEPARATOR + clueId);
        }
    }

    public Set<String> getPresentedClueIds(String characterId) {
        String prefix = characterId + CHARACTER_CLUE_SEPARATOR;
        return presentedClueIdsByCharacter.stream()
                .filter(entry -> entry.startsWith(prefix))
                .map(entry -> entry.substring(prefix.length()))
                .collect(Collectors.toSet());
    }
}
