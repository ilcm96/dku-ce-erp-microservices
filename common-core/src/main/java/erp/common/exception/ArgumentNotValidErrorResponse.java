package erp.common.exception;

import java.util.List;

public record ArgumentNotValidErrorResponse(String errorCodeName, String errorMessage, List<FieldErrorDetail> errors) {
    public static ArgumentNotValidErrorResponse of(ErrorCode errorCode, List<FieldErrorDetail> errors) {
        return new ArgumentNotValidErrorResponse(errorCode.name(), errorCode.getMessage(), errors);
    }
}
