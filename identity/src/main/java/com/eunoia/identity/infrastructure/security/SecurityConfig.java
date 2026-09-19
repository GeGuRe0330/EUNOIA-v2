package com.eunoia.identity.infrastructure.security;

import com.eunoia.common.response.ApiResponse;
import com.eunoia.identity.application.dto.MemberInfo;
import com.eunoia.identity.domain.Member;
import com.eunoia.identity.presentation.dto.MemberResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import tools.jackson.databind.ObjectMapper;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final MemberAuthFailureHandler authFailureHandler;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 세션( JSESSIONID ) 기반 인증이라 원칙적으로 CSRF 보호가 필요하지만,
                // 프론트엔드 연동 방식(SPA여부, 쿠키 정책)이 아직 정해지지 않아 개발 편의상 비활성화.
                // 실제 브라우저 클라이언트 연동 전 CSRF 토큰 전략을 반드시 재검토할 것.
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/members/signup", "/api/v1/auth/login", "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                request -> request.getRequestURI().startsWith("/api/")))
                .formLogin(form -> form
                        .loginProcessingUrl("/api/v1/auth/login")
                        .successHandler((request, response, authentication) -> {
                            response.setContentType("application/json;charset=UTF-8");
                            response.setStatus(HttpServletResponse.SC_OK);
                            Member member = ((CustomMemberDetails) authentication.getPrincipal()).getMember();
                            objectMapper.writeValue(response.getWriter(),
                                    ApiResponse.ok(MemberResponse.from(MemberInfo.from(member))));
                        })
                        .failureHandler(authFailureHandler))
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.setContentType("application/json;charset=UTF-8");
                            response.setStatus(HttpServletResponse.SC_OK);
                            objectMapper.writeValue(response.getWriter(), ApiResponse.ok(null));
                        })
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"));

        return http.build();
    }
}
