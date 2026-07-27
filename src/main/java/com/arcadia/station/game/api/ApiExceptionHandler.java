package com.arcadia.station.game.api;

import com.arcadia.station.game.application.SessionNotFoundException;
import com.arcadia.station.game.application.SessionNotReadyException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(SessionNotFoundException.class)
    ResponseEntity<ApiError> notFound(SessionNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(SessionNotReadyException.class)
    ResponseEntity<ApiError> notReady(SessionNotReadyException exception) {
        return response(HttpStatus.CONFLICT, "SESSION_NOT_READY", exception.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<ApiError> badRequest(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiError> conflict(IllegalStateException exception) {
        return response(HttpStatus.CONFLICT, "INVALID_SESSION_STATE", exception.getMessage());
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status,
            String code,
            String message
    ) {
        return ResponseEntity.status(status).body(
                new ApiError(Instant.now(), status.value(), code, message)
        );
    }

    public record ApiError(Instant timestamp, int status, String code, String message) {}
}
