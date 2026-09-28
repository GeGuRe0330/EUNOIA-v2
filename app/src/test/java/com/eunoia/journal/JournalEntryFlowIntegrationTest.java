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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"email":"%s","password":"%s","nickname":"%s","age":%d,"gender":"%s"}
                        """.formatted(email, password, nickname, age, gender)))
                .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE members SET status = 'ACTIVE' WHERE email = ?", email);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .param("username", email)
                        .param("password", password))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) loginResult.getRequest().getSession(false);
    }

    private Long writeEntry(MockHttpSession session, String content, String entryDate) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/emotion-entries")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"content":"%s","entryDate":"%s"}
                        """.formatted(content, entryDate)))
                .andExpect(status().isOk())
                .andReturn();
        Number entryId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return entryId.longValue();
    }

    @Test
    @DisplayName("로그인한 회원이 감정일기를 작성하면 저장되고 EmotionEntryRecorded 이벤트가 발행된다.")
    void write_withAuthenticatedSession_persistsEntryAndPublishesEvent(ApplicationEvents events) throws Exception {
        MockHttpSession session = signupAndLogin("journal@test.com", "rawPassword1!", "일기러", 20, "FEMALE");

        MvcResult result = mockMvc.perform(post("/api/v1/emotion-entries")
                        .session(session)
                        .with(csrf())
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
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"content":"오늘은 맑았다","entryDate":"2026-09-20"}
                        """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("본인이 작성한 감정일기를 단건 조회할 수 있다.")
    void getById_withOwnerSession_returnsEntry() throws Exception {
        MockHttpSession session = signupAndLogin("owner@test.com", "rawPassword1!", "본인", 20, "FEMALE");
        Long entryId = writeEntry(session, "오늘은 맑았다", "2026-09-20");

        mockMvc.perform(get("/api/v1/emotion-entries/{id}", entryId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("오늘은 맑았다"));
    }

    @Test
    @DisplayName("다른 회원이 작성한 감정일기는 조회할 수 없다.")
    void getById_withOtherMemberSession_returns403() throws Exception {
        MockHttpSession ownerSession = signupAndLogin("owner2@test.com", "rawPassword1!", "본인2", 20, "FEMALE");
        Long entryId = writeEntry(ownerSession, "오늘은 맑았다", "2026-09-20");
        MockHttpSession otherSession = signupAndLogin("other@test.com", "rawPassword1!", "타인", 20, "MALE");

        mockMvc.perform(get("/api/v1/emotion-entries/{id}", entryId).session(otherSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("존재하지 않는 감정일기를 조회하면 404를 반환한다.")
    void getById_withNonExistentId_returns404() throws Exception {
        MockHttpSession session = signupAndLogin("notfound@test.com", "rawPassword1!", "없음", 20, "FEMALE");

        mockMvc.perform(get("/api/v1/emotion-entries/{id}", 999999L).session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("목록을 조회하면 본인이 작성한 감정일기만 반환한다.")
    void getMyEntries_returnsOnlyOwnEntries() throws Exception {
        MockHttpSession session = signupAndLogin("list@test.com", "rawPassword1!", "목록", 20, "FEMALE");
        writeEntry(session, "첫째 날", "2026-09-19");
        writeEntry(session, "둘째 날", "2026-09-20");
        MockHttpSession otherSession = signupAndLogin("otherlist@test.com", "rawPassword1!", "타인목록", 20, "MALE");
        writeEntry(otherSession, "남의 글", "2026-09-20");

        mockMvc.perform(get("/api/v1/emotion-entries").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[*].content", org.hamcrest.Matchers.containsInAnyOrder("첫째 날", "둘째 날")));
    }
}
