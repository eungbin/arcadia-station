package com.arcadia.station.ai.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import javax.net.ssl.SSLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Safe, human-readable evidence that an external AI HTTP request was attempted.
 * Prompts, response bodies, API keys, and exception messages are deliberately omitted.
 */
public final class AiProviderDiagnostics {

    private static final Logger LOGGER = LoggerFactory.getLogger("ARC_AI_CASE_AUDIT");

    private AiProviderDiagnostics() {}

    public static void requestStarted(
            String provider,
            String operation,
            AiPurpose purpose,
            String model,
            String endpoint
    ) {
        try {
            LOGGER.info(
                    "[AI-API][REQUEST] event=ai_provider_request_started provider={} "
                            + "operation={} purpose={} model={} endpoint={}",
                    safeValue(provider),
                    safeValue(operation),
                    purpose,
                    safeValue(model),
                    safeValue(endpoint)
            );
        } catch (RuntimeException ignored) {
            // Diagnostics must never block an AI request.
        }
    }

    public static void requestSucceeded(
            String provider,
            String operation,
            AiPurpose purpose,
            String model,
            int httpStatus,
            Instant startedAt,
            long inputTokens,
            long outputTokens
    ) {
        try {
            LOGGER.info(
                    "[AI-API][SUCCESS] event=ai_provider_request_succeeded provider={} "
                            + "operation={} purpose={} model={} httpStatus={} durationMs={} "
                            + "inputTokens={} outputTokens={}",
                    safeValue(provider),
                    safeValue(operation),
                    purpose,
                    safeValue(model),
                    httpStatus,
                    durationMillis(startedAt),
                    inputTokens,
                    outputTokens
            );
        } catch (RuntimeException ignored) {
            // Diagnostics must never change a successful AI result.
        }
    }

    public static void requestFailed(
            String provider,
            String operation,
            AiPurpose purpose,
            String model,
            int observedHttpStatus,
            Instant startedAt,
            Throwable failure
    ) {
        try {
            FailureView view = failureView(failure, observedHttpStatus);
            LOGGER.warn(
                    "[AI-API][FAILURE] event=ai_provider_request_failed provider={} "
                            + "operation={} purpose={} model={} httpStatus={} "
                            + "failureCategory={} exceptionType={} rootCauseType={} "
                            + "durationMs={}",
                    safeValue(provider),
                    safeValue(operation),
                    purpose,
                    safeValue(model),
                    view.httpStatus(),
                    view.category(),
                    failure.getClass().getSimpleName(),
                    view.rootCauseType(),
                    durationMillis(startedAt)
            );
        } catch (RuntimeException ignored) {
            // Diagnostics must never replace the original provider failure.
        }
    }

    static FailureView failureView(Throwable failure, int observedHttpStatus) {
        int httpStatus = observedHttpStatus;
        Throwable current = failure;
        Throwable rootCause = failure;
        boolean timeout = false;
        boolean network = false;
        boolean invalidResponse = false;
        boolean authentication = false;
        int remainingDepth = 20;
        while (current != null && remainingDepth-- > 0) {
            rootCause = current;
            if (current instanceof RestClientResponseException responseException) {
                httpStatus = responseException.getStatusCode().value();
                String responseBody = responseException.getResponseBodyAsString()
                        .toLowerCase(Locale.ROOT);
                authentication = authentication
                        || responseBody.contains("api_key_invalid")
                        || responseBody.contains("api key not valid")
                        || responseBody.contains("invalid api key")
                        || responseBody.contains("unauthenticated");
            }
            if (current instanceof SocketTimeoutException) {
                timeout = true;
            }
            if (current instanceof ResourceAccessException
                    || current instanceof ConnectException
                    || current instanceof UnknownHostException
                    || current instanceof SSLException) {
                network = true;
            }
            if (current instanceof JsonProcessingException
                    || current instanceof IllegalStateException) {
                invalidResponse = true;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return new FailureView(
                httpStatus,
                failureCategory(
                        httpStatus,
                        timeout,
                        network,
                        invalidResponse,
                        authentication
                ),
                rootCause.getClass().getSimpleName()
        );
    }

    private static String failureCategory(
            int httpStatus,
            boolean timeout,
            boolean network,
            boolean invalidResponse,
            boolean authentication
    ) {
        if (httpStatus == 401 || authentication) {
            return "AUTHENTICATION";
        }
        if (httpStatus == 400 || httpStatus == 422) {
            return "INVALID_REQUEST";
        }
        if (httpStatus == 403) {
            return "PERMISSION";
        }
        if (httpStatus == 404) {
            return "MODEL_OR_ENDPOINT_NOT_FOUND";
        }
        if (httpStatus == 408 || httpStatus == 504 || timeout) {
            return "TIMEOUT";
        }
        if (httpStatus == 429) {
            return "RATE_LIMITED";
        }
        if (httpStatus >= 500) {
            return "PROVIDER_UNAVAILABLE";
        }
        if (network) {
            return "NETWORK";
        }
        if (httpStatus >= 200 && httpStatus < 300 && invalidResponse) {
            return "INVALID_RESPONSE";
        }
        return "UNKNOWN";
    }

    private static long durationMillis(Instant startedAt) {
        return Math.max(0, Duration.between(startedAt, Instant.now()).toMillis());
    }

    private static String safeValue(String value) {
        if (value == null) {
            return "unknown";
        }
        StringBuilder normalized = new StringBuilder();
        value.codePoints().forEach(codePoint -> {
            int characterType = Character.getType(codePoint);
            if (Character.isISOControl(codePoint)
                    || characterType == Character.FORMAT
                    || characterType == Character.LINE_SEPARATOR
                    || characterType == Character.PARAGRAPH_SEPARATOR) {
                normalized.append('_');
            } else {
                normalized.appendCodePoint(codePoint);
            }
        });
        String normalizedValue = normalized.toString().toLowerCase(Locale.ROOT);
        int codePointCount = normalizedValue.codePointCount(0, normalizedValue.length());
        int endIndex = normalizedValue.offsetByCodePoints(
                0,
                Math.min(codePointCount, 128)
        );
        return normalizedValue.substring(0, endIndex);
    }

    record FailureView(int httpStatus, String category, String rootCauseType) {}
}
