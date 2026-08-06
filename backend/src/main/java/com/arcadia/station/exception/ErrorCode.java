package com.arcadia.station.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "세션을 찾을 수 없습니다."),
    INVALID_SESSION_STATE(HttpStatus.CONFLICT, "지금은 이 요청을 처리할 수 없는 세션 상태입니다."),
    SESSION_NOT_READY(HttpStatus.CONFLICT, "사건이 아직 동결되지 않았습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청이 올바르지 않습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
