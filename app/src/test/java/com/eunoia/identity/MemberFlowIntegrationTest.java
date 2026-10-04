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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"email":"%s","password":"%s","nickname":"%s","age":%d,"gender":"%s"}
                        """.formatted(email, password, nickname, age, gender)))
                .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE members SET status = 'ACTIVE' WHERE email = ?", email);
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .param("username", email)
                        .param("password", password))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) loginResult.getRequest().getSession(false);
    }

    private void changePassword(MockHttpSession session, String currentPassword, String newPassword) throws Exception {
        mockMvc.perform(put("/api/v1/members/me/password")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"currentPassword":"%s","newPassword":"%s"}
                        """.formatted(currentPassword, newPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("회원가입 후 로그인하면 세션이 발급되고, 비밀번호는 평문이 아닌 해시로 저장된다.")
    void signupThenLogin_issuesSessionAndPersistsHashedPassword() throws Exception {
        signup("test@test.com", "rawPassword1!", "하나", 20, "FEMALE");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                    .with(csrf())
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
                        .with(csrf())
                        .param("username", "logout@test.com")
                        .param("password", "rawPassword1!"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(get("/api/v1/members/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("logout@test.com"));

        mockMvc.perform(post("/api/v1/auth/logout").session(session).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/members/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그인 상태에서 내 정보를 조회하면 role을 포함한 전체 정보를 반환한다.")
    void getMe_withLoggedInSession_returnsFullMemberInfo() throws Exception {
        signup("me@test.com", "rawPassword1!", "내정보", 25, "FEMALE");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
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
                    .with(csrf())
                    .param("username", "nobody@test.com")
                    .param("password", "rawPassword1!"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("CSRF 토큰 없이 상태 변경 요청을 보내면 403이 반환된다.")
    void csrfProtection_blocksRequestWithoutToken() throws Exception {
        // 실제 쿠키 발급 + 더블서브밋 헤더 왕복까지 검증하는 테스트는 의도적으로 두지 않음 —
        // spring-security-test의 .with(csrf())가 같은 테스트 실행(공유 Spring 컨텍스트) 안에서
        // CsrfTokenRepository를 테스트 전용 세션 기반 저장소로 전역 교체해버려, 이 프로젝트처럼
        // 다른 통합 테스트들이 .with(csrf())를 광범위하게 쓰는 상황에서는 MockMvc로 신뢰성 있게
        // 검증할 수 없음이 실제로 확인됨(디버깅 기록은 branch-work-history/12 참고).
        // 토큰이 아예 없을 때 거부되는지(=CSRF 보호가 실제로 켜져 있는지)는 저장소 종류와 무관하게
        // 항상 성립하므로 이것만 검증. 실제 쿠키+헤더 왕복은 프론트 연동 시점에 브라우저로 실증.
        mockMvc.perform(post("/api/v1/auth/login")
                    .param("username", "csrf@test.com")
                    .param("password", "rawPassword1!"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("요청 형식이 잘못되면(JSON 파싱 실패) 영어 범용 문구 대신 한국어 문구로 400을 반환한다.")
    void signup_withMalformedJson_returnsKoreanGenericMessage() throws Exception {
        // age에 숫자로 변환 불가능한 값을 보내면 Integer 역직렬화 자체가 실패한다(HttpMessageNotReadableException).
        // (빈 문자열("")은 Jackson이 null로 코어스해 @NotNull 검증 쪽으로 빠지므로 재현에 부적합 — 실제 확인함)
        mockMvc.perform(post("/api/v1/members/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"email":"malformed@test.com","password":"rawPassword1!","nickname":"닉네임","age":"abc","gender":"FEMALE"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.message").value("요청 형식이 올바르지 않아요."));
    }

    @Test
    @DisplayName("이메일 형식이 잘못되면 범용 문구와 함께 필드별 검증 메시지(errors)가 보존된다.")
    void signup_withInvalidEmailFormat_returnsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/members/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"email":"not-an-email","password":"rawPassword1!","nickname":"닉네임","age":20,"gender":"FEMALE"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.message").value("잘못된 요청이에요."))
                .andExpect(jsonPath("$.error.errors[0].field").value("email"))
                .andExpect(jsonPath("$.error.errors[0].message").exists());
    }

    @Test
    @DisplayName("회원가입·로그인·내 정보 응답에 가입 시각(createdAt)이 포함된다.")
    void memberResponses_includeCreatedAt() throws Exception {
        mockMvc.perform(post("/api/v1/members/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"email":"created@test.com","password":"rawPassword1!","nickname":"가입시각","age":20,"gender":"NONE"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());
        jdbcTemplate.update("UPDATE members SET status = 'ACTIVE' WHERE email = ?", "created@test.com");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .param("username", "created@test.com")
                        .param("password", "rawPassword1!"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(get("/api/v1/members/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("프로필을 수정하면 응답과 이후 내 정보 조회에 새 닉네임·나이·성별이 반영된다(이메일은 그대로).")
    void updateProfile_changesProfile_andIsVisibleInMe() throws Exception {
        signup("profile@test.com", "rawPassword1!", "개구리", 27, "NONE");
        MockHttpSession session = login("profile@test.com", "rawPassword1!");

        mockMvc.perform(patch("/api/v1/members/me")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"nickname":"두꺼비","gender":"FEMALE","age":30}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("두꺼비"))
                .andExpect(jsonPath("$.data.age").value(30))
                .andExpect(jsonPath("$.data.gender").value("FEMALE"));

        // 세션 스냅샷이 아니라 DB에서 다시 읽는 내 정보에 반영됐는지(= 변경 감지로 실제 저장됐는지)
        mockMvc.perform(get("/api/v1/members/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("profile@test.com"))
                .andExpect(jsonPath("$.data.nickname").value("두꺼비"))
                .andExpect(jsonPath("$.data.age").value(30))
                .andExpect(jsonPath("$.data.gender").value("FEMALE"));
    }

    @Test
    @DisplayName("프로필 수정 값이 잘못되면 400과 필드별 오류(errors)를 반환하고 프로필은 바뀌지 않는다.")
    void updateProfile_withInvalidValues_returnsFieldErrors() throws Exception {
        signup("invalidprofile@test.com", "rawPassword1!", "그대로", 27, "NONE");
        MockHttpSession session = login("invalidprofile@test.com", "rawPassword1!");

        mockMvc.perform(patch("/api/v1/members/me").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"nickname":"두꺼비","gender":"FEMALE","age":-1}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.errors[0].field").value("age"));
        mockMvc.perform(patch("/api/v1/members/me").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"nickname":" ","gender":"FEMALE","age":30}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.errors[0].field").value("nickname"));
        mockMvc.perform(patch("/api/v1/members/me").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"nickname":"두꺼비","age":30}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.errors[0].field").value("gender"));

        mockMvc.perform(get("/api/v1/members/me").session(session))
                .andExpect(jsonPath("$.data.nickname").value("그대로"))
                .andExpect(jsonPath("$.data.age").value(27));
    }

    @Test
    @DisplayName("회원가입도 음수 나이는 프로필 수정과 같은 필드별 오류(errors)로 400을 반환한다.")
    void signup_withNegativeAge_returnsFieldError() throws Exception {
        mockMvc.perform(post("/api/v1/members/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"email":"negative@test.com","password":"rawPassword1!","nickname":"음수","age":-1,"gender":"FEMALE"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.errors[0].field").value("age"));
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 401이 아닌 400 + 해요체 문구이고, 세션과 기존 비밀번호는 그대로다.")
    void changePassword_withWrongCurrentPassword_returns400AndKeepsSession() throws Exception {
        signup("wrongpw@test.com", "rawPassword1!", "틀림", 20, "MALE");
        MockHttpSession session = login("wrongpw@test.com", "rawPassword1!");

        mockMvc.perform(put("/api/v1/members/me/password")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"currentPassword":"notMyPassword","newPassword":"newPassword1!"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("지금 쓰는 비밀번호가 맞지 않아요."));

        // 로그인 화면으로 쫓겨나지 않음 — 같은 세션이 여전히 유효
        mockMvc.perform(get("/api/v1/members/me").session(session))
                .andExpect(status().isOk());
        login("wrongpw@test.com", "rawPassword1!");
    }

    @Test
    @DisplayName("비밀번호를 바꾸면 세션은 유지되고, 같은 세션에서 연달아 바꿔도 되며, 새 비밀번호로만 로그인된다.")
    void changePassword_twiceInSameSession_thenOnlyLatestPasswordWorks() throws Exception {
        signup("changepw@test.com", "firstPassword1!", "변경", 20, "FEMALE");
        MockHttpSession session = login("changepw@test.com", "firstPassword1!");

        changePassword(session, "firstPassword1!", "secondPassword1!");
        mockMvc.perform(get("/api/v1/members/me").session(session))
                .andExpect(status().isOk());

        // 같은 세션에서 두 번째 변경 — 세션 스냅샷의 낡은 해시(first)가 아니라 DB의 최신 해시(second)와 비교해야 성공
        changePassword(session, "secondPassword1!", "thirdPassword1!");

        login("changepw@test.com", "thirdPassword1!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .param("username", "changepw@test.com")
                        .param("password", "firstPassword1!"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .param("username", "changepw@test.com")
                        .param("password", "secondPassword1!"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CSRF 토큰 없이 프로필 수정·비밀번호 변경을 보내면 403이다.")
    void profileAndPasswordChange_withoutCsrf_returns403() throws Exception {
        signup("csrfprofile@test.com", "rawPassword1!", "토큰", 20, "NONE");
        MockHttpSession session = login("csrfprofile@test.com", "rawPassword1!");

        mockMvc.perform(patch("/api/v1/members/me").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"nickname":"두꺼비","gender":"FEMALE","age":30}
                        """))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/members/me/password").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"currentPassword":"rawPassword1!","newPassword":"newPassword1!"}
                        """))
                .andExpect(status().isForbidden());
    }
}
