package com.eunoia.common.exception;

import lombok.Getter;
import lombok.NonNull;
import org.springframework.http.HttpStatus;

/**
 * 사용자에게 직접 노출 가능한 비즈니스 오류.
 * message에는 내부 식별자, 구현 상세, 민감 정보를 포함하지 않는다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(@NonNull HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
