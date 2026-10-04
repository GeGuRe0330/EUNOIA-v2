package com.eunoia.common.exception;

import com.eunoia.common.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("BusinessException은 자신의 상태코드와 메시지를 그대로 응답한다.")
    void handleBusiness_returnsOwnStatusAndMessage() {
        BusinessException e = new BusinessException(HttpStatus.CONFLICT, "이미 가입된 이메일이에요.");

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusiness(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().error().message()).isEqualTo("이미 가입된 이메일이에요.");
    }

    @Test
    @DisplayName("IllegalArgumentException은 원문 대신 범용 문구로 400을 응답한다.")
    void handleIllegalArgument_returnsGenericBadRequestMessage() {
        IllegalArgumentException e = new IllegalArgumentException("memberId는 필수입니다.");

        ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgument(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error().message()).isEqualTo("잘못된 요청이에요.");
        assertThat(response.getBody().error().message()).doesNotContain("memberId");
    }

    @Test
    @DisplayName("IllegalStateException은 409가 아니라 500 + 범용 문구로 응답하고 원문은 노출하지 않는다.")
    void handleIllegalState_returns500WithGenericMessage() {
        IllegalStateException e = new IllegalStateException("선택된 일기의 원문을 찾을 수 없습니다. entryId=42");

        ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalState(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().error().message()).isEqualTo("서버에 오류가 발생했어요.");
        assertThat(response.getBody().error().message()).doesNotContain("entryId");
    }

    @Test
    @DisplayName("분기되지 않은 예외는 500 + 범용 문구로 응답한다.")
    void handleException_returns500WithGenericMessage() {
        RuntimeException e = new RuntimeException("아무 예외");

        ResponseEntity<ApiResponse<Void>> response = handler.handleException(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().error().message()).isEqualTo("서버에 오류가 발생했어요.");
    }

    @Test
    @DisplayName("요청 값 타입 변환에 실패하면 Spring 기본 문구 대신 해요체 문구와 필드 이름으로 400을 응답한다.")
    void handleTypeMismatch_returnsKoreanMessageWithFieldName() {
        MethodArgumentTypeMismatchException e = new MethodArgumentTypeMismatchException(
                "abc", YearMonth.class, "yearMonth", null, new IllegalArgumentException("parse failed"));

        ResponseEntity<Object> response = handler.handleTypeMismatch(
                e, new HttpHeaders(), HttpStatus.BAD_REQUEST, new ServletWebRequest(new MockHttpServletRequest()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiResponse<?> body = (ApiResponse<?>) response.getBody();
        assertThat(body.success()).isFalse();
        assertThat(body.error().message()).isEqualTo("요청 값의 형식이 올바르지 않아요.");
        assertThat(body.error().errors()).singleElement()
                .satisfies(detail -> assertThat(detail.field()).isEqualTo("yearMonth"));
    }

    @Test
    @DisplayName("필수 요청 파라미터가 없으면 Spring 기본 문구 대신 해요체 문구와 파라미터 이름으로 400을 응답한다.")
    void handleMissingServletRequestParameter_returnsKoreanMessageWithParameterName() {
        MissingServletRequestParameterException e = new MissingServletRequestParameterException("yearMonth", "YearMonth");

        ResponseEntity<Object> response = handler.handleMissingServletRequestParameter(
                e, new HttpHeaders(), HttpStatus.BAD_REQUEST, new ServletWebRequest(new MockHttpServletRequest()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiResponse<?> body = (ApiResponse<?>) response.getBody();
        assertThat(body.error().message()).isEqualTo("필요한 요청 값이 빠졌어요.");
        assertThat(body.error().errors()).singleElement()
                .satisfies(detail -> assertThat(detail.field()).isEqualTo("yearMonth"));
    }
}
