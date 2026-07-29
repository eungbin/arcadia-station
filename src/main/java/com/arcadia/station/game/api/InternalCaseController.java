package com.arcadia.station.game.api;

import com.arcadia.station.ai.casegen.CaseBlueprint;
import com.arcadia.station.ai.casegen.FrozenCaseBlueprint;
import com.arcadia.station.ai.casegen.GenerationSource;
import com.arcadia.station.game.application.GameSessionService;
import com.arcadia.station.game.domain.GameSession;
import com.arcadia.station.game.domain.SessionState;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/cases")
public class InternalCaseController {

    private final GameSessionService sessions;
    private final InternalApiKeyGuard internalApiKey;

    public InternalCaseController(
            GameSessionService sessions,
            InternalApiKeyGuard internalApiKey
    ) {
        this.sessions = sessions;
        this.internalApiKey = internalApiKey;
    }

    @PostMapping
    public ResponseEntity<InternalCaseAccepted> create(
            @RequestHeader(value = "X-Internal-AI-Key", required = false) String key,
            @RequestBody InternalCaseRequest request
    ) {
        internalApiKey.requireValid(key);
        GameSession session = sessions.createSession(request.sessionId(), request.seed());
        InternalCaseAccepted response = new InternalCaseAccepted(
                session.sessionId(),
                SessionState.CREATING,
                "/internal/v1/cases/" + session.sessionId()
        );
        return ResponseEntity.accepted()
                .location(URI.create(response.statusUrl()))
                .body(response);
    }

    @GetMapping("/{sessionId}")
    public InternalCaseStatus get(
            @RequestHeader(value = "X-Internal-AI-Key", required = false) String key,
            @PathVariable String sessionId
    ) {
        internalApiKey.requireValid(key);
        GameSession session = sessions.requireSession(sessionId);
        GenerationPayload payload = session.frozenCase() == null
                ? null
                : GenerationPayload.from(session.frozenCase());
        return new InternalCaseStatus(
                session.sessionId(),
                session.state(),
                payload,
                session.failureCode()
        );
    }

    public record InternalCaseRequest(String sessionId, String seed) {}

    public record InternalCaseAccepted(
            String sessionId,
            SessionState status,
            String statusUrl
    ) {}

    public record InternalCaseStatus(
            String sessionId,
            SessionState status,
            GenerationPayload generation,
            String errorCode
    ) {}

    public record GenerationPayload(
            String blueprintId,
            String seed,
            TemplateVersion worldTemplate,
            TemplateVersion ruleTemplate,
            String blueprintSha256,
            int generationAttemptCount,
            GenerationSource generationSource,
            String model,
            String promptVersion,
            Instant createdAt,
            Instant frozenAt,
            CaseBlueprint caseBlueprint
    ) {
        static GenerationPayload from(FrozenCaseBlueprint frozen) {
            return new GenerationPayload(
                    frozen.blueprintId(),
                    frozen.seed(),
                    new TemplateVersion(
                            frozen.worldTemplateId(),
                            frozen.worldTemplateVersion()
                    ),
                    new TemplateVersion(
                            frozen.ruleTemplateId(),
                            frozen.ruleTemplateVersion()
                    ),
                    frozen.blueprintSha256(),
                    frozen.generationAttemptCount(),
                    frozen.generationSource(),
                    frozen.model(),
                    frozen.promptVersion(),
                    frozen.createdAt(),
                    frozen.frozenAt(),
                    frozen.blueprint()
            );
        }
    }

    public record TemplateVersion(String id, String version) {}
}
