package com.arcadia.station.client;

/**
 * AI 서버가 404 SESSION_NOT_FOUND를 반환했을 때 던진다(5.5/6.4절 — AI 서버 세션이 메모리 전용이라
 * 재시작 시 사라질 수 있음). 실제 클라이언트 구현체(8번 작업)가 던지고, 프록시 서비스가 13장의
 * 우아한 실패로 변환한다.
 */
public class AiSessionLostException extends RuntimeException {
    public AiSessionLostException(String message) {
        super(message);
    }
}
