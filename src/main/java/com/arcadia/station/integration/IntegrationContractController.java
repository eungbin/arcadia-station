package com.arcadia.station.integration;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integration")
public class IntegrationContractController {

    private final FrontendIntegrationContractRepository contracts;

    public IntegrationContractController(
            FrontendIntegrationContractRepository contracts
    ) {
        this.contracts = contracts;
    }

    @GetMapping("/frontend-contract")
    public FrontendIntegrationContract frontendContract() {
        return contracts.contract();
    }
}
