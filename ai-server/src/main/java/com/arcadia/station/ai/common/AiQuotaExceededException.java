package com.arcadia.station.ai.common;

import java.util.Locale;
import org.springframework.web.client.RestClientResponseException;

public class AiQuotaExceededException extends RuntimeException {

    public AiQuotaExceededException(String message) {
        super(message);
    }

    public AiQuotaExceededException(String message, Throwable cause) {
        super(message, cause);
    }

    public static boolean isQuotaFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof AiQuotaExceededException) {
                return true;
            }
            if (current instanceof RestClientResponseException responseException) {
                if (responseException.getStatusCode().value() == 429) {
                    return true;
                }
                String body = responseException.getResponseBodyAsString()
                        .toLowerCase(Locale.ROOT);
                if (body.contains("resource_exhausted")
                        || body.contains("quota exceeded")
                        || body.contains("quota_exceeded")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
