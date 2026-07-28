package com.arcadia.station.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 12.2절: 공모전 심사 기간 동안 Render 무료 웹 서비스가 15분 유휴 슬립에 들어가지 않도록
 * 자기 자신의 공개 URL에 주기적으로 핑을 보낸다. KEEP_ALIVE_ENABLED=true일 때만 동작한다.
 */
@Component
@ConditionalOnProperty(prefix = "arcadia.keep-alive", name = "enabled", havingValue = "true")
public class KeepAliveScheduler {

    private static final Logger log = LoggerFactory.getLogger(KeepAliveScheduler.class);

    private final RestClient restClient = RestClient.create();
    private final String targetUrl;

    public KeepAliveScheduler(@Value("${arcadia.keep-alive.target-url}") String targetUrl) {
        this.targetUrl = targetUrl;
    }

    @Scheduled(fixedRate = 600_000) // 10분마다 — Render의 15분 슬립 기준보다 여유를 둔 값
    public void ping() {
        try {
            restClient.get()
                    .uri(targetUrl + "/actuator/health")
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("keep-alive ping 실패: {}", e.getMessage());
        }
    }
}
