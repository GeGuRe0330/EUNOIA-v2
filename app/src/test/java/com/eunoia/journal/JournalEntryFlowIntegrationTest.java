package com.eunoia.journal;

import com.eunoia.journal.event.EmotionEntryDeleted;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
                    assertThat(e.entryId()).isEqualTo(entryId.longValue());
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
    @DisplayName("본인 글을 삭제하면 행은 남고 삭제 시각이 기록되며, 이후 단건·목록 조회에서 사라지고 삭제 이벤트가 발행된다.")
    void delete_withOwnerSession_softDeletesAndHidesEntry(ApplicationEvents events) throws Exception {
        MockHttpSession session = signupAndLogin("delete@test.com", "rawPassword1!", "삭제", 20, "FEMALE");
        Long entryId = writeEntry(session, "지울 글", "2026-09-20");
        writeEntry(session, "남길 글", "2026-09-21");

        mockMvc.perform(delete("/api/v1/emotion-entries/{id}", entryId)
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        // 소프트 삭제: 행은 남고 deleted_at이 채워졌는지 DB에서 직접 확인(@SQLRestriction을 거치지 않는 JDBC 조회)
        // — delete()의 @Transactional이 빠지면 UPDATE가 나가지 않아 여기서 실패한다
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT deleted_at FROM emotion_entries WHERE id = ?", entryId);
        assertThat(row.get("deleted_at")).isNotNull();

        mockMvc.perform(get("/api/v1/emotion-entries/{id}", entryId).session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").value("존재하지 않는 감정글이에요."));

        // 목록 엔드포인트는 records 모듈 소유(⑯) — 응답은 페이지 객체
        mockMvc.perform(get("/api/v1/emotion-entries").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].content").value("남길 글"));

        assertThat(events.stream(EmotionEntryDeleted.class))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.entryId()).isEqualTo(entryId);
                    assertThat(e.deletedAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("이미 삭제한 글을 다시 삭제하면 없는 글과 같은 404를 반환한다.")
    void delete_twice_returns404() throws Exception {
        MockHttpSession session = signupAndLogin("delete2@test.com", "rawPassword1!", "삭제2", 20, "FEMALE");
        Long entryId = writeEntry(session, "지울 글", "2026-09-20");

        mockMvc.perform(delete("/api/v1/emotion-entries/{id}", entryId).session(session).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/emotion-entries/{id}", entryId).session(session).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").value("존재하지 않는 감정글이에요."));
    }

    @Test
    @DisplayName("다른 회원의 글은 삭제할 수 없고, 글은 그대로 남는다.")
    void delete_withOtherMemberSession_returns403() throws Exception {
        MockHttpSession ownerSession = signupAndLogin("delete-owner@test.com", "rawPassword1!", "주인", 20, "FEMALE");
        Long entryId = writeEntry(ownerSession, "남의 손이 닿으면 안 되는 글", "2026-09-20");
        MockHttpSession otherSession = signupAndLogin("delete-other@test.com", "rawPassword1!", "타인삭제", 20, "MALE");

        mockMvc.perform(delete("/api/v1/emotion-entries/{id}", entryId).session(otherSession).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.message").value("해당 감정글에 대한 접근 권한이 없어요."));

        mockMvc.perform(get("/api/v1/emotion-entries/{id}", entryId).session(ownerSession))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CSRF 토큰 없이 삭제하면 403이고 글은 그대로 남는다.")
    void delete_withoutCsrf_returns403() throws Exception {
        MockHttpSession session = signupAndLogin("delete-csrf@test.com", "rawPassword1!", "토큰없음", 20, "FEMALE");
        Long entryId = writeEntry(session, "지울 글", "2026-09-20");

        mockMvc.perform(delete("/api/v1/emotion-entries/{id}", entryId).session(session))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/emotion-entries/{id}", entryId).session(session))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인하지 않으면 삭제할 수 없다.")
    void delete_withoutLogin_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/emotion-entries/{id}", 1L).with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
