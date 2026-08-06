package com.arcadia.station.ai.presentation;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 내부 식별자를 플레이어 화면에 노출하지 않기 위한 최종 표시 계층. */
@Component
public class PlayerFacingTextFormatter {

    private static final Pattern INTERNAL_SNAKE_CASE =
            Pattern.compile("(?<![A-Z0-9_])[A-Z]+(?:_[A-Z0-9]+)+(?![A-Z0-9_])");
    private static final Pattern INTERNAL_HYPHEN_ID =
            Pattern.compile("(?<![A-Z0-9-])(?:FACT|CLUE|RECORD|RED|EVT)(?:-[A-Z0-9]+)+(?![A-Z0-9-])");
    private static final Pattern RECORD_REFERENCE =
            Pattern.compile("\\s*\\(?\\s*(?:REC(?:ORD)?[-_]?\\d+|EVT[-_]?\\d+)\\s*\\)?");

    private static final Map<String, String> DISPLAY_NAMES = displayNames();

    /**
     * 모델 출력과 고정 fallback 모두에 적용한다. 알 수 없는 내부 snake_case 값은 내용을
     * 추측해서 번역하지 않고 플레이어에게 의미 있는 중립 표현으로 감춘다.
     */
    public String format(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String result = value;
        for (Map.Entry<String, String> entry : DISPLAY_NAMES.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, String> item) -> item.getKey().length())
                        .reversed())
                .toList()) {
            result = result.replaceAll(
                    "(?<![A-Za-z0-9_])" + Pattern.quote(entry.getKey()) + "(?![A-Za-z0-9_])",
                    entry.getValue()
            );
        }
        result = RECORD_REFERENCE.matcher(result).replaceAll("");
        result = INTERNAL_HYPHEN_ID.matcher(result).replaceAll("해당 기록");
        result = INTERNAL_SNAKE_CASE.matcher(result).replaceAll("해당 시스템 작업");
        return result
                .replace("**", "")
                .replace("`", "")
                .replaceAll("[ \\t]{2,}", " ")
                .replaceAll(" *\\n *", "\n")
                .trim();
    }

    private static Map<String, String> displayNames() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("SCHEDULE_HEALTH_SAFETY_CHECK", "의료 안전 점검 예약");
        names.put("RUN_MAINTENANCE_DIAGNOSTIC", "정비 진단");
        names.put("RUN_SAFETY_DIAGNOSTIC", "안전 진단");
        names.put("REVIEW_STATION_AUDIT", "정거장 감사 검토");
        names.put("READ_ACCESS_LOG", "출입 기록 조회");
        names.put("READ_PATIENT_RECORD", "의료 기록 조회");
        names.put("READ_SIGNAL_STATUS", "통신 상태 조회");
        names.put("QUEUE_OUTBOUND_MESSAGE", "외부 메시지 대기 등록");
        names.put("READ_DOCKING_SCHEDULE", "도킹 일정 조회");
        names.put("APPROVE_RESOURCE_PLAN", "자원 배분 승인");
        names.put("READ_WORK_ORDER", "정비 작업 지시 조회");
        names.put("CLOSE_WORK_ORDER", "정비 작업 종료");
        names.put("RUN_HEALTH_AUDIT", "건강 기록 점검");
        names.put("READ_MANIFEST", "화물 목록 조회");
        names.put("UPDATE_STORAGE_SLOT", "화물 보관 위치 변경");
        names.put("READ_STATUS", "상태 조회");
        names.put("DELIVERY_FAILED_EXTERNAL_COPY_SAVED", "외부 전송은 실패했지만 사본은 보존됨");
        names.put("COMMANDER_OFFICE", "사령관실");
        names.put("DEPUTY_COMMANDER_OFFICE", "부사령관 집무실");
        names.put("COMMUNICATIONS_CENTER", "통신실");
        names.put("ENGINEERING_BAY", "엔지니어링 구역");
        names.put("MEDICAL_BAY", "의무실");
        names.put("CENTRAL_HUB", "중앙 허브");
        names.put("CARGO_BAY", "화물칸");
        names.put("COMMON_AREA", "공용 구역");
        names.put("LIFE_SUPPORT", "생명유지 시스템");
        names.put("MEDICAL_RECORDS", "의료 기록 시스템");
        names.put("COMMAND_SYSTEM", "지휘 시스템");
        names.put("SECURITY_SYSTEM", "보안 시스템");
        names.put("MAINTENANCE_SYSTEM", "정비 시스템");
        names.put("COMMUNICATION_SYSTEM", "통신 시스템");
        names.put("CARGO_SYSTEM", "화물 시스템");
        names.put("DOCKING_SYSTEM", "도킹 시스템");
        names.put("ACCESS_LOG", "출입 기록");
        names.put("COMMAND_LOG", "시스템 기록");
        names.put("SOPHIA", "소피아");
        names.put("JUNHO", "백준호");
        names.put("KASIM", "카심");
        names.put("MAYA", "마야");
        names.put("YUNA", "유나");
        names.put("VICTIM", "피해자");
        return Map.copyOf(names);
    }
}
