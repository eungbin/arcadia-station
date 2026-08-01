package com.arcadia.station.game.domain;

import com.arcadia.station.ai.casegen.FrozenCaseBlueprint;
import java.time.Instant;

public class GameSession {

    private final String sessionId;
    private final String seed;
    private final Instant createdAt;
    private final EvidenceInventory evidenceInventory = new EvidenceInventory();
    private volatile SessionState state;
    private volatile FrozenCaseBlueprint frozenCase;
    private volatile String failureCode;
    private int wrongSubmissions;

    public GameSession(String sessionId, String seed) {
        this.sessionId = sessionId;
        this.seed = seed;
        this.createdAt = Instant.now();
        this.state = SessionState.CREATING;
    }

    public String sessionId() {
        return sessionId;
    }

    public String seed() {
        return seed;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public SessionState state() {
        return state;
    }

    public FrozenCaseBlueprint frozenCase() {
        return frozenCase;
    }

    public EvidenceInventory evidenceInventory() {
        return evidenceInventory;
    }

    public String failureCode() {
        return failureCode;
    }

    public synchronized void markValidating() {
        requireState(SessionState.CREATING);
        state = SessionState.VALIDATING;
    }

    public synchronized void markReady(FrozenCaseBlueprint value) {
        if (state != SessionState.CREATING && state != SessionState.VALIDATING) {
            throw new IllegalStateException("Cannot freeze case from state " + state);
        }
        if (frozenCase != null) {
            throw new IllegalStateException("Case is already frozen");
        }
        frozenCase = value;
        state = SessionState.READY;
    }

    public synchronized void startInvestigation() {
        if (state == SessionState.READY || state == SessionState.BRIEFING) {
            state = SessionState.INVESTIGATION;
        }
    }

    public synchronized void startDeduction() {
        if (state == SessionState.READY
                || state == SessionState.BRIEFING
                || state == SessionState.INVESTIGATION
                || state == SessionState.DEDUCTION) {
            state = SessionState.DEDUCTION;
        }
    }

    public synchronized int registerWrongSubmission(int maximumAttempts) {
        wrongSubmissions++;
        return Math.max(0, maximumAttempts - wrongSubmissions);
    }

    public synchronized int remainingAttempts(int maximumAttempts) {
        return Math.max(0, maximumAttempts - wrongSubmissions);
    }

    public synchronized void complete() {
        state = SessionState.COMPLETED;
    }

    public synchronized void fail(String code) {
        failureCode = code;
        state = SessionState.FAILED;
    }

    private void requireState(SessionState expected) {
        if (state != expected) {
            throw new IllegalStateException("Expected " + expected + " but was " + state);
        }
    }
}
