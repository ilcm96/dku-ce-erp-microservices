package erp.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    INVALID_JSON(HttpStatus.BAD_REQUEST, "유효하지 않은 JSON 형식입니다."),
    INVALID_ENUM(HttpStatus.BAD_REQUEST, "유효하지 않은 Enum 값입니다."),
    DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT, "데이터 무결성 위반입니다."),

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증되지 않았습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    AUTH_TOKEN_MISSING(HttpStatus.UNAUTHORIZED, "인증 토큰이 없습니다."),
    AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류입니다."),

    // Employee Service
    EMPLOYEE_NOT_FOUND(HttpStatus.NOT_FOUND, "직원 정보를 찾을 수 없습니다."),
    EMPLOYEE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 직원입니다."),

    // Approval Request
    APPROVAL_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "결재 요청을 찾을 수 없습니다."),
    APPROVAL_REQUEST_INVALID_STEP(HttpStatus.BAD_REQUEST, "결재 단계가 올바르지 않습니다."),
    APPROVAL_SELF_APPROVAL_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "자기 자신을 결재자로 지정할 수 없습니다."),
    APPROVAL_APPROVER_NOT_ELIGIBLE(HttpStatus.BAD_REQUEST, "결재자로 지정할 수 없는 사용자입니다."),

    // Approval Processing
    APPROVAL_PROCESS_NOT_FOUND(HttpStatus.NOT_FOUND, "결재 처리 정보를 찾을 수 없습니다."),
    APPROVAL_PROCESS_INVALID_STATUS(HttpStatus.BAD_REQUEST, "결재 상태가 올바르지 않습니다."),
    APPROVAL_PROCESS_CONFLICT(HttpStatus.CONFLICT, "결재 처리 중 충돌이 발생했습니다. 다시 시도해주세요."),

    // Notification
    NOTIFICATION_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림 세션을 찾을 수 없습니다."),
    ;

    private final HttpStatus httpStatus;
    private final String message;
}
