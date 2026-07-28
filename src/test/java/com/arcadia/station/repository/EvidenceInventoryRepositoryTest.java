package com.arcadia.station.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.arcadia.station.domain.EvidenceInventory;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EvidenceInventoryRepositoryTest {

    @Autowired
    private EvidenceInventoryRepository evidenceInventoryRepository;

    @Test
    void 발견한_단서와_사실_집합이_그대로_저장_조회된다() {
        EvidenceInventory inventory = new EvidenceInventory("game_test_101");
        inventory.getDiscoveredClueIds().add("CLUE-TRIGGER-LOG");
        inventory.getRevealedFactIds().add("FACT-TRIGGER");
        inventory.getPresentedClueIdsByCharacter().add("CLUE-SETUP-PANEL");
        inventory.setWrongDeductionAttempts(1);

        evidenceInventoryRepository.saveAndFlush(inventory);

        EvidenceInventory found = evidenceInventoryRepository.findById("game_test_101").orElseThrow();
        assertThat(found.getDiscoveredClueIds()).isEqualTo(Set.of("CLUE-TRIGGER-LOG"));
        assertThat(found.getRevealedFactIds()).isEqualTo(Set.of("FACT-TRIGGER"));
        assertThat(found.getPresentedClueIdsByCharacter()).isEqualTo(Set.of("CLUE-SETUP-PANEL"));
        assertThat(found.getWrongDeductionAttempts()).isEqualTo(1);
    }
}
