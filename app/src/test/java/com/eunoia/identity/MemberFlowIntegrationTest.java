package com.eunoia.identity;

import com.eunoia.identity.domain.Member;
import com.eunoia.identity.domain.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;


import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
public class MemberFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    //mock테스트 전용 회원가입 메서드
    private void signup(String email, String password, String nickname, int age, String gender) throws Exception {
        mockMvc.perform(post("/api/v1/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"email":"%s","password":"%s","nickname":"%s","age":%d,"gender":"%s"}
                        """.formatted(email, password, nickname, age, gender)))
                .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE members SET status = 'ACTIVE' WHERE email = ?", email);
    }

    @Test
    @DisplayName("회원가입 후 로그인하면 세션이 발급되고, 비밀번호는 평문이 아닌 해시로 저장된다.")
    void signupThenLogin_issuesSessionAndPersistsHashedPassword() throws Exception {
        signup("test@test.com", "rawPassword1!", "하나", 20, "FEMALE");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                    .param("username", "test@test.com")
                    .param("password", "rawPassword1!"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("test@test.com"))
                .andExpect(jsonPath("$.data.nickname").value("하나"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        Member saved = memberRepository.findByEmail("test@test.com").orElseThrow();
        assertThat(saved.getPassword()).isNotEqualTo("rawPassword1!");
        assertThat(passwordEncoder.matches("rawPassword1!", saved.getPassword())).isTrue();
    }

    @Test
    @DisplayName("로그인한 후에는 인증된 세션으로 내 정보를 조회할 수 있고, 로그아웃하면 같은 세션으로는 접근할 수 없다.")
    void accessProtectedApi_withoutLogin_returns401() throws Exception {
        signup("logout@test.com", "rawPassword1!", "로그아웃", 20, "MALE");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .param("username", "logout@test.com")
                        .param("password", "rawPassword1!"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(get("/api/v1/members/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("logout@test.com"));

        mockMvc.perform(post("/api/v1/auth/logout").session(session))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/members/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그인 상태에서 내 정보를 조회하면 role을 포함한 전체 정보를 반환한다.")
    void getMe_withLoggedInSession_returnsFullMemberInfo() throws Exception {
        signup("me@test.com", "rawPassword1!", "내정보", 25, "FEMALE");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .param("username", "me@test.com")
                        .param("password", "rawPassword1!"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(get("/api/v1/members/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("me@test.com"))
                .andExpect(jsonPath("$.data.nickname").value("내정보"))
                .andExpect(jsonPath("$.data.age").value(25))
                .andExpect(jsonPath("$.data.gender").value("FEMALE"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    @DisplayName("로그인하지 않은 상태로 내 정보를 조회하면 401을 반환한다.")
    void getMe_withoutLogin_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("존재하지 않는 이메일로 로그인하면 401과 실패 응답을 반환한다.")
    void login_withUnknownEmail_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                    .param("username", "nobody@test.com")
                    .param("password", "rawPassword1!"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}
