package com.arcadia.station.ai.validation.checks;

import com.arcadia.station.ai.casegen.CaseBlueprint;
import com.arcadia.station.ai.template.MysteryRuleTemplate;
import com.arcadia.station.ai.template.WorldTemplate;
import com.arcadia.station.ai.validation.CaseBlueprintCheck;
import com.arcadia.station.ai.validation.ValidationIssue;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 플레이어가 읽는 사건 문장에 내부 시스템 코드가 섞이는 생성을 재시도시킨다. */
@Component
@Order(70)
public class PlayerFacingNarrativeCheck implements CaseBlueprintCheck {

    private static final Pattern INTERNAL_CODE =
            Pattern.compile("(?<![A-Z0-9_])[A-Z]+(?:_[A-Z0-9]+)+(?![A-Z0-9_])");
    private static final Pattern INTERNAL_HYPHEN_ID =
            Pattern.compile("(?<![A-Z0-9-])(?:FACT|CLUE|RECORD|RED|EVT)(?:-[A-Z0-9]+)+(?![A-Z0-9-])");

    @Override
    public List<ValidationIssue> validate(
            WorldTemplate world,
            MysteryRuleTemplate rules,
            CaseBlueprint blueprint
    ) {
        List<ValidationIssue> issues = new ArrayList<>();
        Set<String> rosterIds = playerVisibleRosterIds(world);
        check(issues, rosterIds, "$.title", blueprint.title());
        check(issues, rosterIds, "$.briefing", blueprint.briefing());
        check(issues, rosterIds, "$.truthSummary", blueprint.truthSummary());
        check(issues, rosterIds, "$.method.fictionalSummary", blueprint.method().fictionalSummary());
        check(issues, rosterIds, "$.method.victimCondition", blueprint.method().victimCondition());
        blueprint.timeline().forEach(event -> check(
                issues,
                rosterIds,
                "$.timeline[" + event.eventId() + "].summary",
                event.summary()
        ));
        blueprint.facts().forEach(fact -> check(
                issues,
                rosterIds,
                "$.facts[" + fact.factId() + "].statement",
                fact.statement()
        ));
        blueprint.alibis().forEach(alibi -> {
            check(issues, rosterIds,
                    "$.alibis[" + alibi.characterId() + "].initialClaim", alibi.initialClaim());
            check(issues, rosterIds,
                    "$.alibis[" + alibi.characterId() + "].actualWhereabouts", alibi.actualWhereabouts());
        });
        blueprint.clues().forEach(clue -> {
            check(issues, rosterIds, "$.clues[" + clue.clueId() + "].title", clue.title());
            check(issues, rosterIds, "$.clues[" + clue.clueId() + "].playerText", clue.playerText());
        });
        blueprint.evidenceRecords().forEach(record -> {
            check(issues, rosterIds,
                    "$.evidenceRecords[" + record.recordId() + "].title", record.title());
            check(issues, rosterIds,
                    "$.evidenceRecords[" + record.recordId() + "].body", record.body());
        });
        blueprint.npcKnowledge().forEach(knowledge -> knowledge.recommendedQuestionTopics().forEach(topic ->
                check(
                        issues,
                        rosterIds,
                        "$.npcKnowledge[" + knowledge.characterId() + "].recommendedQuestionTopics",
                        topic
                )
        ));
        blueprint.redHerrings().forEach(redHerring -> check(
                issues,
                rosterIds,
                "$.redHerrings[" + redHerring.redHerringId() + "].presentation",
                redHerring.presentation()
        ));
        blueprint.solution().nonCulpritExclusions().forEach(exclusion -> check(
                issues,
                rosterIds,
                "$.solution.nonCulpritExclusions[" + exclusion.characterId() + "].reason",
                exclusion.reason()
        ));
        return List.copyOf(issues);
    }

    private Set<String> playerVisibleRosterIds(WorldTemplate world) {
        return java.util.stream.Stream.of(
                        world.characters().stream().map(WorldTemplate.CharacterDefinition::id),
                        world.locations().stream().map(WorldTemplate.LocationDefinition::id),
                        world.systems().stream().map(WorldTemplate.StationSystemDefinition::id)
                )
                .flatMap(stream -> stream)
                .collect(Collectors.toUnmodifiableSet());
    }

    private void check(
            List<ValidationIssue> issues,
            Set<String> rosterIds,
            String path,
            String value
    ) {
        if (value == null) {
            return;
        }
        var matcher = INTERNAL_CODE.matcher(value);
        if (matcher.find()) {
            issues.add(ValidationIssue.of(
                    "PLAYER_FACING_INTERNAL_CODE",
                    path,
                    matcher.group()
            ));
            return;
        }
        Matcher hyphenMatcher = INTERNAL_HYPHEN_ID.matcher(value);
        if (hyphenMatcher.find()) {
            issues.add(ValidationIssue.of(
                    "PLAYER_FACING_INTERNAL_CODE",
                    path,
                    hyphenMatcher.group()
            ));
            return;
        }
        String rosterId = rosterIds.stream()
                .filter(id -> containsToken(value, id))
                .findFirst()
                .orElse(null);
        if (rosterId != null) {
            issues.add(ValidationIssue.of(
                    "PLAYER_FACING_INTERNAL_ID",
                    path,
                    rosterId
            ));
        }
    }

    private boolean containsToken(String value, String token) {
        return Pattern.compile(
                "(?<![A-Za-z0-9_])" + Pattern.quote(token) + "(?![A-Za-z0-9_])"
        ).matcher(value).find();
    }
}
