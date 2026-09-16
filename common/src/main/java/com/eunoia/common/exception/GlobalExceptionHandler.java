package com.eunoia.common.exception;

import com.eunoia.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * 모든 예외를 {@link ApiResponse#fail}의 {success: false, data: null, error} 봉투로 응답한다.
 * 우리가 직접 던진 예외(BusinessException 등)는 아래 @ExceptionHandler들이 바로 처리하고,
 * 부모인 ResponseEntityExceptionHandler가 기본 ProblemDetail을 만든 뒤 {@link #handleExceptionInternal}로
 * 넘기므로, 그 지점에서 한 번만 같은 봉투로 변환한다.
 *
 * <p>성공 응답은 이 클래스가 아니라
 * {@link com.eunoia.common.response.ApiResponseWrappingAdvice}가 만든다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        HttpStatus status = e.getStatus();
        if (e.getCause() != null) {
            log.warn("[{}] {}", status.value(), e.getMessage(), e);
        } else {
            log.warn("[{}] {}", status.value(), e.getMessage());
        }
        return fail(status, e.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        List<ApiResponse.ApiError.FieldErrorDetail> errors = e.getConstraintViolations().stream()
                .map(v -> new ApiResponse.ApiError.FieldErrorDetail(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        log.warn("[{}] {}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
        return fail(HttpStatus.BAD_REQUEST, "잘못된 요청입니다.", errors);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("[{}] {}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
        return fail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException e) {
        log.warn("[{}] {}", HttpStatus.CONFLICT.value(), e.getMessage());
        return fail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("[{}] 분기되지 않은 오류", HttpStatus.INTERNAL_SERVER_ERROR.value(), e);
        return fail(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        List<ApiResponse.ApiError.FieldErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiResponse.ApiError.FieldErrorDetail(fe.getField(), fe.getDefaultMessage()))
                .toList();
        ApiResponse<Void> body = ApiResponse.fail(new ApiResponse.ApiError("잘못된 요청입니다.", errors));
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex,
                                                             Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode statusCode,
                                                             WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            body = ApiResponse.fail(new ApiResponse.ApiError(problem.getDetail(), null));
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private ResponseEntity<ApiResponse<Void>> fail(@NonNull HttpStatus status, String message) {
        return fail(status, message, null);
    }

    private ResponseEntity<ApiResponse<Void>> fail(@NonNull HttpStatus status, String message,
                                                   List<ApiResponse.ApiError.FieldErrorDetail> errors) {
        return ResponseEntity.status(status).body(ApiResponse.fail(new ApiResponse.ApiError(message, errors)));
    }
}
