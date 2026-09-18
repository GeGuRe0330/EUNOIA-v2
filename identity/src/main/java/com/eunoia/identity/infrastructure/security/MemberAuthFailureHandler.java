package com.eunoia.identity.infrastructure.security;

import com.eunoia.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class MemberAuthFailureHandler implements AuthenticationFailureHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        if (exception instanceof UsernameNotFoundException || exception instanceof BadCredentialsException) {
            objectMapper.writeValue(response.getWriter(),
                    ApiResponse.fail(new ApiResponse.ApiError("이메일 또는 비밀번호가 일치하지 않습니다.", null)));
            return;
        }

        objectMapper.writeValue(response.getWriter(),
                ApiResponse.fail(new ApiResponse.ApiError("로그인에 실패했습니다.", null)));
    }
}
