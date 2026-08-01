package com.arcadia.station.ai.npc;

import com.arcadia.station.ai.casegen.CaseBlueprint;
import com.arcadia.station.ai.template.TemplateRepository;
import com.arcadia.station.ai.template.WorldTemplate;
import com.arcadia.station.game.application.GameSessionService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class NpcContextFactory {

    private final GameSessionService sessions;
    private final TemplateRepository templates;

    public NpcContextFactory(
            GameSessionService sessions,
            TemplateRepository templates
    ) {
        this.sessions = sessions;
        this.templates = templates;
    }

    public NpcTurnContext create(
            String sessionId,
            String characterId,
            String question,
            List<String> presentedClueIds
    ) {
        CaseBlueprint blueprint = sessions.requireFrozenCase(sessionId).blueprint();
        WorldTemplate.CharacterDefinition character = templates.world().characters().stream()
                .filter(candidate -> candidate.id().equals(characterId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown characterId: " + characterId));
        CaseBlueprint.NpcKnowledge knowledge = blueprint.npcKnowledge().stream()
                .filter(candidate -> candidate.characterId().equals(characterId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No interrogation context for characterId: " + characterId
                ));
        // In split deployment the game backend owns discovery state for EXPLORE/CONNECT.
        // The authenticated request is authoritative; this server only rejects foreign IDs.
        Set<String> caseClueIds = blueprint.clues().stream()
                .map(CaseBlueprint.Clue::clueId)
                .collect(Collectors.toSet());
        if (!caseClueIds.containsAll(presentedClueIds)) {
            throw new IllegalArgumentException(
                    "Presented clues must belong to the frozen case"
            );
        }

        Map<String, CaseBlueprint.Fact> facts = blueprint.facts().stream()
                .collect(Collectors.toMap(CaseBlueprint.Fact::factId, Function.identity()));
        Set<String> presented = new LinkedHashSet<>(presentedClueIds);
        Set<String> revealable = knowledge.revealPolicies().stream()
                .filter(policy -> presented.containsAll(policy.requiredPresentedClueIds()))
                .map(CaseBlueprint.RevealPolicy::factId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> allowedIds = new LinkedHashSet<>(knowledge.initialClaimFactIds());
        knowledge.knownFactIds().stream()
                .filter(id -> !knowledge.concealedFactIds().contains(id))
                .forEach(allowedIds::add);
        allowedIds.addAll(revealable);
        allowedIds.addAll(knowledge.allowedLieFactIds());

        List<NpcTurnContext.AllowedFact> allowedFacts = allowedIds.stream()
                .map(facts::get)
                .filter(java.util.Objects::nonNull)
                .map(fact -> new NpcTurnContext.AllowedFact(
                        fact.factId(),
                        fact.statement(),
                        fact.truthValue()
                ))
                .toList();
        List<NpcTurnContext.QuestionCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < knowledge.recommendedQuestionTopics().size(); index++) {
            candidates.add(new NpcTurnContext.QuestionCandidate(
                    "TOPIC-" + (index + 1),
                    knowledge.recommendedQuestionTopics().get(index)
            ));
        }
        return new NpcTurnContext(
                sessionId,
                character.id(),
                character.displayName(),
                character.occupation(),
                character.personalityTraits(),
                question,
                List.copyOf(presentedClueIds),
                allowedFacts,
                List.copyOf(revealable),
                List.copyOf(candidates)
        );
    }
}
