package com.eunoia.common.exception;

import com.eunoia.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
 * <p>사용자에게 보여도 되는 검수된 메시지는 {@link BusinessException}을 통해서만 나간다.
 * {@code IllegalArgumentException}/{@code IllegalStateException}은 도메인/애플리케이션 계층의
 * 방어적 검증(내부 불변식)에도 쓰이고 있어, 원문을 그대로 노출하지 않고 범용 문구로 응답 —
 * 실제 원인은 로그에만 남긴다.
 *
 * <p>성공 응답은 이 클래스가 아니라
 * {@link com.eunoia.common.response.ApiResponseWrappingAdvice}가 만든다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String GENERIC_BAD_REQUEST_MESSAGE = "잘못된 요청이에요.";
    private static final String GENERIC_SERVER_ERROR_MESSAGE = "서버에 오류가 발생했어요.";

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
        return fail(HttpStatus.BAD_REQUEST, GENERIC_BAD_REQUEST_MESSAGE, errors);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("[{}] {}", HttpStatus.BAD_REQUEST.value(), e.getMessage());
        return fail(HttpStatus.BAD_REQUEST, GENERIC_BAD_REQUEST_MESSAGE);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException e) {
        log.error("[{}] {}", HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage(), e);
        return fail(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_SERVER_ERROR_MESSAGE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("[{}] 분기되지 않은 오류", HttpStatus.INTERNAL_SERVER_ERROR.value(), e);
        return fail(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_SERVER_ERROR_MESSAGE);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        List<ApiResponse.ApiError.FieldErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiResponse.ApiError.FieldErrorDetail(fe.getField(), fe.getDefaultMessage()))
                .toList();
        ApiResponse<Void> body = ApiResponse.fail(new ApiResponse.ApiError(GENERIC_BAD_REQUEST_MESSAGE, errors));
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                   HttpHeaders headers,
                                                                   HttpStatusCode status,
                                                                   WebRequest request) {
        log.warn("[{}] {}", HttpStatus.BAD_REQUEST.value(), ex.getMessage());
        ApiResponse<Void> body = ApiResponse.fail(new ApiResponse.ApiError("요청 형식이 올바르지 않아요.", null));
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
