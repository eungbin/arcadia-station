package com.arcadia.station.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.arcadia.station.ai.casegen.CaseBlueprint.EvidenceRole;
import com.arcadia.station.ai.template.ArcadiaLocationRoster;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class FrontendIntegrationContractTest {

    @Autowired
    private FrontendIntegrationContractRepository contracts;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void sharesStableIdsAndCoversTheFinalTheoryRoles() {
        FrontendIntegrationContract contract = contracts.contract();

        assertThat(contract.npcCharacterIds())
                .containsEntry("NPC_JUNHO", "JUNHO")
                .containsEntry("NPC_SOPHIA", "SOPHIA");
        assertThat(contract.investigationObjects())
                .containsKeys("CO_BODY", "EN_LIFE_SUPPORT", "MD_MEDICAL_TERMINAL");
        assertThat(contract.investigationObjects().values())
                .extracting(FrontendIntegrationContract.InvestigationObjectRoute::locationId)
                .allMatch(ArcadiaLocationRoster::contains);
        assertThat(contract.investigationObjects().values())
                .allMatch(route -> route.mode()
                        == FrontendIntegrationContract.InspectionMode.EXPLORE);
        assertThat(contract.investigationObjects().values())
                .filteredOn(FrontendIntegrationContract.InvestigationObjectRoute::clueRequired)
                .hasSize(10);
        assertThat(contract.investigationObjects().values().stream()
                .filter(FrontendIntegrationContract.InvestigationObjectRoute::clueRequired)
                .map(FrontendIntegrationContract.InvestigationObjectRoute::locationId)
                .collect(java.util.stream.Collectors.toSet()))
                .isEqualTo(ArcadiaLocationRoster.ID_SET);
        assertThat(contract.investigationObjects().get("EN_LIFE_SUPPORT").locationId())
                .isEqualTo("ENGINEERING_BAY");
        assertThat(contract.investigationObjects().get("CM_SECURITY_ARCHIVE").locationId())
                .isEqualTo("COMMUNICATIONS_CENTER");
        assertThat(
                contract.theoryFields().values().stream()
                        .flatMap(java.util.Collection::stream)
                        .collect(java.util.stream.Collectors.toSet())
        ).isEqualTo(Set.of(
                EvidenceRole.SETUP,
                EvidenceRole.TRIGGER,
                EvidenceRole.OPPORTUNITY,
                EvidenceRole.MOTIVE
        ));
    }

    @Test
    void exposesTheVersionedContractWithoutPrivateCaseData() throws Exception {
        mockMvc.perform(get("/api/v1/integration/frontend-contract"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("1.2.0"))
                .andExpect(jsonPath("$.npcCharacterIds.NPC_JUNHO").value("JUNHO"))
                .andExpect(jsonPath("$.investigationObjects.EN_LIFE_SUPPORT.mode")
                        .value("EXPLORE"))
                .andExpect(jsonPath("$.investigationObjects.CO_BODY.clueRequired")
                        .value(true))
                .andExpect(jsonPath("$.culpritId").doesNotExist());
    }
}
