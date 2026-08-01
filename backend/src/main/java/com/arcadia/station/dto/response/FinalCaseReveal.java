package com.arcadia.station.dto.response;

import com.arcadia.station.domain.caseblueprint.Alibi;
import com.arcadia.station.domain.caseblueprint.Fact;
import com.arcadia.station.domain.caseblueprint.Method;
import com.arcadia.station.domain.caseblueprint.Solution;
import com.arcadia.station.domain.caseblueprint.TimelineEvent;
import java.util.List;

/**
 * 판정 완료(COMPLETED) 이후에만 공개되는 사건의 전체 진실 재구성(9.3절).
 * 이 시점부터는 10장의 비공개 필드 제약이 더 이상 적용되지 않는다.
 */
public record FinalCaseReveal(
    String sessionId,
    String culpritId,
    String truthSummary,
    Method method,
    List<TimelineEvent> timeline,
    List<Fact> facts,
    List<Alibi> alibis,
    Solution solution
) {}
