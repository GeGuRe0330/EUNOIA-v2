package com.eunoia.common.response;

import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 컨트롤러가 반환한 DTO를 {@link ApiResponse#ok}로 {success: true, data, error: null} 봉투에 담는다.
 * 실패 응답은 {@link com.eunoia.common.exception.GlobalExceptionHandler}가 {@link ApiResponse#fail}로
 * 직접 만들어 반환하므로 여기서는 별도 변환이 필요없다.(이미 ApiResponse인 경우 그대로 흘려보낼 뿐)
 * ProblemDetail을 추가로 흘려보내는건, GlobalExceptionHandler의 손이 닿지 않는 곳에서 ProblemDetail이
 * 새어 나오더라도 success: true로 잘못 감싸지 않기 위한 방어코드.
 *
 * <p>basePackages를 우리 코드로 한정한다.
 * - 그렇지 않으면 springdoc(OpenAPI) 같은 서드 파티 컨트롤러의 응답까지 감싸버려서 문제가 생김.
 */
@RestControllerAdvice(basePackages = "com.eunoia")
public class ApiResponseWrappingAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public @Nullable Object beforeBodyWrite(@Nullable Object body, MethodParameter returnType, MediaType selectedContentType,
                                            Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                            ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ApiResponse<?> || body instanceof ProblemDetail) {
            return body;
        }
        return ApiResponse.ok(body);
    }
}
