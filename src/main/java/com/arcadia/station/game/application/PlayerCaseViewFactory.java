package com.arcadia.station.game.application;

import com.arcadia.station.ai.casegen.CaseBlueprint;
import com.arcadia.station.ai.template.TemplateRepository;
import com.arcadia.station.ai.template.WorldTemplate;
import com.arcadia.station.game.api.dto.PlayerCaseView;
import com.arcadia.station.game.api.dto.PublicClueView;
import com.arcadia.station.game.domain.GameSession;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PlayerCaseViewFactory {

    private final TemplateRepository templates;

    public PlayerCaseViewFactory(TemplateRepository templates) {
        this.templates = templates;
    }

    public PlayerCaseView create(GameSession session) {
        if (session.frozenCase() == null) {
            throw new SessionNotReadyException(session.sessionId(), session.state());
        }
        WorldTemplate world = templates.world();
        CaseBlueprint blueprint = session.frozenCase().blueprint();
        Map<String, CaseBlueprint.Clue> clues = blueprint.clues().stream()
                .collect(Collectors.toMap(CaseBlueprint.Clue::clueId, Function.identity()));
        return new PlayerCaseView(
                session.sessionId(),
                session.state(),
                blueprint.title(),
                blueprint.briefing(),
                new PlayerCaseView.WorldView(
                        world.setting().name(),
                        world.setting().summary(),
                        world.setting().publicFacts()
                ),
                world.characters().stream()
                        .filter(WorldTemplate.CharacterDefinition::suspect)
                        .map(character -> new PlayerCaseView.SuspectView(
                                character.id(),
                                character.displayName(),
                                character.occupation(),
                                character.publicProfile(),
                                character.personalityTraits()
                        ))
                        .toList(),
                world.locations().stream()
                        .map(location -> new PlayerCaseView.LocationView(
                                location.id(),
                                location.displayName(),
                                location.publicDescription()
                        ))
                        .toList(),
                session.evidenceInventory().snapshot().stream()
                        .map(clues::get)
                        .filter(java.util.Objects::nonNull)
                        .sorted(Comparator.comparing(CaseBlueprint.Clue::clueId))
                        .map(PublicClueView::from)
                        .toList(),
                session.remainingAttempts(templates.rules().finalReportRules().maxWrongSubmissions())
        );
    }
}
