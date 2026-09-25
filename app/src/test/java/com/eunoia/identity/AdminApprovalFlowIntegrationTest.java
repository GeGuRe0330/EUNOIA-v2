package com.eunoia.identity;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;


import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
public class AdminApprovalFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

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
    }

    private MockHttpSession loginSession(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .param("username", email)
                        .param("password", password))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    @DisplayName("PENDING 상태 회원은 로그인 할 수 없다.")
    void login_withPendingMember_return401() throws Exception {
        signup("pending@test.com", "rawPassword1!", "대기회원", 20, "FEMALE");

        mockMvc.perform(post("/api/v1/auth/login")
                    .with(csrf())
                    .param("username", "pending@test.com")
                    .param("password", "rawPassword1!"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ADMIN이 승인대기 목록을 조회하고 승인하면, 해당 회원은 이후 로그인할 수 있다.")
    void adminApprovesPendingMember_thenMemberCanLogin() throws Exception {
        signup("approve@test.com", "rawPassword1!", "승인대상", 20, "FEMALE");
        signup("admin@test.com", "adminPassword1!", "관리자", 20, "FEMALE");

        // DB 레벨에서 직접 권한 부여
        jdbcTemplate.update("UPDATE members SET role = 'ADMIN', status = 'ACTIVE' WHERE email = ?", "admin@test.com");

        MockHttpSession adminSession = loginSession("admin@test.com", "adminPassword1!");
        Long targetId = memberRepository.findByEmail("approve@test.com").orElseThrow().getId();

        mockMvc.perform(get("/api/v1/admin/members/pending").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].email", org.hamcrest.Matchers.hasItem("approve@test.com")));

        mockMvc.perform(patch("/api/v1/admin/members/{memberId}/approve", targetId).session(adminSession).with(csrf()))
                .andExpect(status().isOk());

        MockHttpSession approvedSession = loginSession("approve@test.com", "rawPassword1!");
        assertThat(approvedSession).isNotNull();
    }

    @Test
    @DisplayName("ADMIN이 아닌 회원은 관리자 API에 접근할 수 없다.")
    void nonAdminMember_cannotAccessAdminApi() throws Exception {
        signup("user@test.com", "rawPassword1!", "일반회원", 20, "FEMALE");
        jdbcTemplate.update("UPDATE members SET status = 'ACTIVE' WHERE email = ?", "user@test.com");
        MockHttpSession session = loginSession("user@test.com", "rawPassword1!");

        mockMvc.perform(get("/api/v1/admin/members/pending").session(session))
                .andExpect(status().isForbidden());
    }
}
