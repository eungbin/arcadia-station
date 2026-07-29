package com.arcadia.station.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AI 서버 회신(2026-07-29) 3.2절: 한 NPC에게 제시한 단서가 다른 NPC에게 제시한 것으로
 * 잘못 인정되면 안 된다 — presentedClueIdsByCharacter는 characterId별로 격리돼야 한다.
 */
class EvidenceInventoryTest {

    @Test
    void 서로_다른_NPC에게_제시한_단서는_서로_섞이지_않는다() {
        EvidenceInventory inventory = new EvidenceInventory("game_test");

        inventory.recordPresentedClues("SOPHIA", List.of("CLUE-TRIGGER-LOG"));
        inventory.recordPresentedClues("MARCUS", List.of("CLUE-MOTIVE-MESSAGE"));

        assertThat(inventory.getPresentedClueIds("SOPHIA")).containsExactly("CLUE-TRIGGER-LOG");
        assertThat(inventory.getPresentedClueIds("MARCUS")).containsExactly("CLUE-MOTIVE-MESSAGE");
    }

    @Test
    void 같은_NPC에게_여러_턴에_걸쳐_제시한_단서는_누적된다() {
        EvidenceInventory inventory = new EvidenceInventory("game_test");

        inventory.recordPresentedClues("SOPHIA", List.of("CLUE-TRIGGER-LOG"));
        inventory.recordPresentedClues("SOPHIA", List.of("CLUE-MOTIVE-MESSAGE"));

        assertThat(inventory.getPresentedClueIds("SOPHIA"))
                .containsExactlyInAnyOrder("CLUE-TRIGGER-LOG", "CLUE-MOTIVE-MESSAGE");
    }

    @Test
    void 아직_아무것도_제시하지_않은_NPC는_빈_목록이다() {
        EvidenceInventory inventory = new EvidenceInventory("game_test");

        assertThat(inventory.getPresentedClueIds("SOPHIA")).isEmpty();
    }
}
