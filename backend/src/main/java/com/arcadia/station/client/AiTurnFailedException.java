package com.arcadia.station.client;

/**
 * AI 서버 호출이 세션 유실(404) 외의 이유로 실패했을 때 던진다 — 예: AI 서버 자체의 내부 검증
 * 실패(4xx), 서버 오류(5xx), 타임아웃/연결 실패. 13장: "NPC/RAG 호출이 실패(404 포함)하면
 * 게임 진행 자체를 막지 않는다"에 따라, 이 경우도 게임을 중단시키지 않고 안전 응답으로 대체한다.
 * AiSessionLostException과 달리 AI_SESSION_LOST 플래그는 남기지 않는다(원인이 다르므로).
 */
public class AiTurnFailedException extends RuntimeException {
    public AiTurnFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
