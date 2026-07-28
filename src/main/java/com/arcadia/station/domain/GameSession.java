package com.arcadia.station.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * sessionId(프론트가 아는 ID)와 aiCaseRequestId(AI 서버가 아는 ID)를 분리해서 보관한다.
 * AI 서버는 같은 sessionId로 재요청하면 409를 반환하므로, 재시도 시 aiCaseRequestId만 새로 발급한다(스펙 3.1/4.5절).
 */
@Entity
@Table(name = "game_sessions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameSession {

    @Id
    @Column(nullable = false, updatable = false)
    private String sessionId;

    @Column(nullable = false)
    private String aiCaseRequestId;

    @Column(nullable = false)
    private int caseRequestAttemptCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionState state;

    private String worldTemplateId;
    private String worldTemplateVersion;
    private String ruleTemplateId;
    private String ruleTemplateVersion;
    private String blueprintId;
    private String blueprintSha256;
    private String generationSource;

    // 4.2절: READY 응답과 함께 오는 메타데이터. 3.1절 필드 목록에는 없지만 저장 대상으로 명시됨.
    private Integer generationAttemptCount;
    private String model;
    private String promptVersion;

    // 4.4절: "완료 응답 원문(JSON)과 blueprintSha256을 함께 저장한다" — CaseBlueprint 원문 보관용.
    @Lob
    private String caseBlueprintJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant frozenAt;

    public GameSession(String sessionId, String aiCaseRequestId, Instant createdAt) {
        this.sessionId = sessionId;
        this.aiCaseRequestId = aiCaseRequestId;
        this.caseRequestAttemptCount = 1;
        this.state = SessionState.CREATING;
        this.createdAt = createdAt;
    }
}
