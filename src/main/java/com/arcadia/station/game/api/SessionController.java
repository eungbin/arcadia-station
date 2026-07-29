package com.arcadia.station.game.api;

import com.arcadia.station.ai.npc.InterrogationService;
import com.arcadia.station.ai.npc.NpcTurnResponse;
import com.arcadia.station.ai.rag.InvestigationAssistantService;
import com.arcadia.station.game.api.dto.FinalCaseReveal;
import com.arcadia.station.game.api.dto.PlayerCaseView;
import com.arcadia.station.game.application.DeductionService;
import com.arcadia.station.game.application.ExplorationService;
import com.arcadia.station.game.application.FinalRevealService;
import com.arcadia.station.game.application.GameSessionService;
import com.arcadia.station.game.application.PlayerCaseViewFactory;
import com.arcadia.station.game.domain.GameSession;
import com.arcadia.station.game.domain.SessionState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final GameSessionService sessions;
    private final PlayerCaseViewFactory playerViews;
    private final ExplorationService exploration;
    private final InvestigationAssistantService assistant;
    private final InterrogationService interrogation;
    private final DeductionService deduction;
    private final FinalRevealService finalReveal;
    private final InternalApiKeyGuard internalApiKey;

    public SessionController(
            GameSessionService sessions,
            PlayerCaseViewFactory playerViews,
            ExplorationService exploration,
            InvestigationAssistantService assistant,
            InterrogationService interrogation,
            DeductionService deduction,
            FinalRevealService finalReveal,
            InternalApiKeyGuard internalApiKey
    ) {
        this.sessions = sessions;
        this.playerViews = playerViews;
        this.exploration = exploration;
        this.assistant = assistant;
        this.interrogation = interrogation;
        this.deduction = deduction;
        this.finalReveal = finalReveal;
        this.internalApiKey = internalApiKey;
    }

    @PostMapping
    public ResponseEntity<SessionAcceptedResponse> create(
            @RequestBody(required = false) CreateSessionRequest request
    ) {
        GameSession session = sessions.createSession(request == null ? null : request.seed());
        SessionAcceptedResponse response = new SessionAcceptedResponse(
                session.sessionId(),
                SessionState.CREATING,
                "/api/v1/sessions/" + session.sessionId() + "/status",
                "/api/v1/sessions/" + session.sessionId()
        );
        return ResponseEntity.accepted()
                .location(URI.create(response.statusUrl()))
                .body(response);
    }

    @GetMapping("/{sessionId}/status")
    public SessionStatusResponse status(@PathVariable String sessionId) {
        GameSession session = sessions.requireSession(sessionId);
        return new SessionStatusResponse(
                session.sessionId(),
                session.state(),
                session.frozenCase() != null
                        ? "/api/v1/sessions/" + sessionId
                        : null,
                session.state() == SessionState.FAILED
                        ? session.failureCode()
                        : null
        );
    }

    @GetMapping("/{sessionId}")
    public PlayerCaseView get(@PathVariable String sessionId) {
        return playerViews.create(sessions.requireSession(sessionId));
    }

    @PostMapping("/{sessionId}/explore")
    public ExplorationService.ExplorationResult explore(
            @PathVariable String sessionId,
            @Valid @RequestBody ExploreRequest request
    ) {
        return exploration.explore(sessionId, request.locationId());
    }

    @PostMapping("/{sessionId}/assistant/queries")
    public InvestigationAssistantService.AssistantQueryResponse queryAssistant(
            @RequestHeader(value = "X-Internal-AI-Key", required = false) String key,
            @PathVariable String sessionId,
            @Valid @RequestBody AssistantQueryRequest request
    ) {
        internalApiKey.requireValid(key);
        return assistant.query(sessionId, request.question());
    }

    @PostMapping("/{sessionId}/interrogations/{characterId}/turns")
    public NpcTurnResponse interrogate(
            @RequestHeader(value = "X-Internal-AI-Key", required = false) String key,
            @PathVariable String sessionId,
            @PathVariable String characterId,
            @Valid @RequestBody InterrogationRequest request
    ) {
        internalApiKey.requireValid(key);
        return interrogation.interrogate(
                sessionId,
                characterId,
                request.question(),
                request.presentedClueIds()
        );
    }

    @PostMapping("/{sessionId}/deductions")
    public DeductionService.DeductionResponse deduce(
            @PathVariable String sessionId,
            @Valid @RequestBody DeductionService.DeductionRequest request
    ) {
        return deduction.submit(sessionId, request);
    }

    @GetMapping("/{sessionId}/result")
    public FinalCaseReveal result(@PathVariable String sessionId) {
        return finalReveal.get(sessionId);
    }

    public record CreateSessionRequest(String seed) {}

    public record SessionAcceptedResponse(
            String sessionId,
            SessionState status,
            String statusUrl,
            String gameUrl
    ) {}

    public record SessionStatusResponse(
            String sessionId,
            SessionState status,
            String playerViewUrl,
            String errorCode
    ) {}

    public record ExploreRequest(@NotBlank String locationId) {}

    public record AssistantQueryRequest(@NotBlank String question) {}

    public record InterrogationRequest(
            @NotBlank String question,
            @NotNull List<String> presentedClueIds
    ) {}
}
