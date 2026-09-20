package com.eunoia.journal;

import com.eunoia.journal.event.EmotionEntryRecorded;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
public class JournalEntryFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    //mock테스트 전용 회원가입+로그인 메서드
    private MockHttpSession signupAndLogin(String email, String password, String nickname, int age, String gender) throws Exception {
        mockMvc.perform(post("/api/v1/members/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"email":"%s","password":"%s","nickname":"%s","age":%d,"gender":"%s"}
                        """.formatted(email, password, nickname, age, gender)))
                .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE members SET status = 'ACTIVE' WHERE email = ?", email);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .param("username", email)
                        .param("password", password))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) loginResult.getRequest().getSession(false);
    }

    @Test
    @DisplayName("로그인한 회원이 감정일기를 작성하면 저장되고 EmotionEntryRecorded 이벤트가 발행된다.")
    void write_withAuthenticatedSession_persistsEntryAndPublishesEvent(ApplicationEvents events) throws Exception {
        MockHttpSession session = signupAndLogin("journal@test.com", "rawPassword1!", "일기러", 20, "FEMALE");

        MvcResult result = mockMvc.perform(post("/api/v1/emotion-entries")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"content":"오늘은 맑았다","entryDate":"2026-09-20"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("오늘은 맑았다"))
                .andExpect(jsonPath("$.data.entryDate").value("2026-09-20"))
                .andReturn();

        Number entryId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT member_id, content, entry_date FROM emotion_entries WHERE id = ?", entryId.longValue());
        assertThat(row.get("content")).isEqualTo("오늘은 맑았다");

        assertThat(events.stream(EmotionEntryRecorded.class))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.content()).isEqualTo("오늘은 맑았다");
                    assertThat(e.entryDate()).isEqualTo(LocalDate.of(2026, 9, 20));
                });
    }

    @Test
    @DisplayName("로그인하지 않으면 감정일기를 작성할 수 없다.")
    void write_withoutLogin_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/emotion-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"content":"오늘은 맑았다","entryDate":"2026-09-20"}
                        """))
                .andExpect(status().isUnauthorized());
    }
}
