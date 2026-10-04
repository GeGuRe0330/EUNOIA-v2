package com.eunoia.records;

import com.eunoia.analysis.domain.EmotionAnalysisResult;
import com.eunoia.completion.client.StructuredPromptClient;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
public class RecordsFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 실제 OpenAI를 호출하지 않도록 completion의 구조화 호출 헬퍼를 대체
    @MockitoBean
    private StructuredPromptClient structuredPromptClient;

    private static final Pattern SCORE_MARKER = Pattern.compile("#점수(\\d+)");

    // 일기 본문의 표시로 분석 결과를 조절한다 — "#실패"면 GPT 실패(FAILED), "#점수NN"이면 그 점수로 SUCCESS(기본 50)
    @BeforeEach
    void stubAnalysis() {
        when(structuredPromptClient.call(anyString(), any())).thenAnswer(invocation -> {
            String prompt = invocation.getArgument(0, String.class);
            if (prompt.contains("#실패")) {
                throw new RuntimeException("GPT 호출 실패");
            }
            Matcher matcher = SCORE_MARKER.matcher(prompt);
            double score = matcher.find() ? Double.parseDouble(matcher.group(1)) : 50.0;
            return new EmotionAnalysisResult("평온", "평온,안정", "요약", "흐름", "감정요약", score, 90, "충분함",
                    List.of("문장1", "문장2", "문장3"));
        });
    }

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

    // 글을 쓰고 비동기 분석(SUCCESS 또는 FAILED)이 저장될 때까지 기다린다
    private Long writeAnalyzedEntry(MockHttpSession session, String content, String entryDate) throws Exception {
        Long entryId = writeEntry(session, content, entryDate);
        waitUntil(() -> countRows("SELECT COUNT(*) FROM emotion_analyses WHERE entry_id = ?", entryId) == 1,
                "분석이 제한 시간 안에 저장되지 않았습니다. entryId=" + entryId);
        return entryId;
    }

    // "분석 처리 중"(분석 행이 아직 없음) 상태를 만든다 — 분석 완료를 기다린 뒤 행을 지운다
    private Long writeEntryWithoutAnalysis(MockHttpSession session, String content, String entryDate) throws Exception {
        Long entryId = writeAnalyzedEntry(session, content, entryDate);
        jdbcTemplate.update("DELETE FROM emotion_analyses WHERE entry_id = ?", entryId);
        return entryId;
    }

    // 글을 지우고, 비동기 삭제 구독이 분석까지 소프트 삭제할 때까지 기다린다
    private void deleteEntryAndWaitForCascade(MockHttpSession session, Long entryId) throws Exception {
        mockMvc.perform(delete("/api/v1/emotion-entries/{id}", entryId).session(session).with(csrf()))
                .andExpect(status().isOk());
        waitUntil(() -> countRows("SELECT COUNT(*) FROM emotion_analyses WHERE entry_id = ? AND deleted_at IS NOT NULL", entryId) == 1,
                "삭제된 글의 분석이 제한 시간 안에 소프트 삭제되지 않았습니다. entryId=" + entryId);
    }

    private int countRows(String sql, Long entryId) {
        return jdbcTemplate.queryForObject(sql, Integer.class, entryId);
    }

    private void waitUntil(BooleanSupplier condition, String message) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError(message);
    }

    private int readInt(MvcResult result, String path) throws Exception {
        Number value = JsonPath.read(result.getResponse().getContentAsString(), path);
        return value.intValue();
    }

    // JournalEntryFlowIntegrationTest.getMyEntries_returnsOnlyOwnEntries에서 이전(⑯ — 목록 엔드포인트가 records로 이동)
    @Test
    @DisplayName("목록을 조회하면 본인이 작성한 감정일기만 반환한다.")
    void getEntries_returnsOnlyOwnEntries() throws Exception {
        MockHttpSession session = signupAndLogin("list@test.com", "rawPassword1!", "목록", 20, "FEMALE");
        writeEntry(session, "첫째 날", "2026-09-19");
        writeEntry(session, "둘째 날", "2026-09-20");
        MockHttpSession otherSession = signupAndLogin("otherlist@test.com", "rawPassword1!", "타인목록", 20, "MALE");
        writeEntry(otherSession, "남의 글", "2026-09-20");

        mockMvc.perform(get("/api/v1/emotion-entries").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[*].content", containsInAnyOrder("첫째 날", "둘째 날")));
    }

    @Test
    @DisplayName("조회 기간은 시작·종료일을 포함하고, entryDate 내림차순으로 페이지를 나누며 마지막 페이지에서 hasNext가 false다.")
    void getEntries_withPeriod_includesBoundariesAndPaginates() throws Exception {
        MockHttpSession session = signupAndLogin("period@test.com", "rawPassword1!", "기간", 20, "FEMALE");
        writeAnalyzedEntry(session, "9일(범위 밖)", "2026-09-09");
        writeAnalyzedEntry(session, "10일(시작일)", "2026-09-10");
        writeAnalyzedEntry(session, "15일", "2026-09-15");
        writeAnalyzedEntry(session, "20일(종료일)", "2026-09-20");
        writeAnalyzedEntry(session, "21일(범위 밖)", "2026-09-21");

        mockMvc.perform(get("/api/v1/emotion-entries").session(session)
                        .param("from", "2026-09-10").param("to", "2026-09-20").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].entryDate").value("2026-09-20"))
                .andExpect(jsonPath("$.data.items[1].entryDate").value("2026-09-15"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        mockMvc.perform(get("/api/v1/emotion-entries").session(session)
                        .param("from", "2026-09-10").param("to", "2026-09-20").param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].entryDate").value("2026-09-10"))
                .andExpect(jsonPath("$.data.hasNext").value(false));

        // 한쪽만 보내도 된다
        mockMvc.perform(get("/api/v1/emotion-entries").session(session).param("from", "2026-09-20"))
                .andExpect(jsonPath("$.data.items[*].entryDate", containsInAnyOrder("2026-09-20", "2026-09-21")));
        mockMvc.perform(get("/api/v1/emotion-entries").session(session).param("to", "2026-09-09"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].entryDate").value("2026-09-09"));
    }

    @Test
    @DisplayName("감정 태그는 SUCCESS 분석의 대표 감정이고, 분석이 FAILED이거나 아직 없으면 null이다.")
    void getEntries_emotionDetected_onlyFromSuccessfulAnalysis() throws Exception {
        MockHttpSession session = signupAndLogin("emotion@test.com", "rawPassword1!", "감정", 20, "FEMALE");
        writeAnalyzedEntry(session, "성공한 글", "2026-09-03");
        writeAnalyzedEntry(session, "실패한 글 #실패", "2026-09-02");
        writeEntryWithoutAnalysis(session, "처리 중인 글", "2026-09-01");

        mockMvc.perform(get("/api/v1/emotion-entries").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].emotionDetected").value("평온"))
                .andExpect(jsonPath("$.data.items[1].emotionDetected").value(nullValue()))
                .andExpect(jsonPath("$.data.items[2].emotionDetected").value(nullValue()));
    }

    @Test
    @DisplayName("글이 없는 신규 회원은 목록 [], 요약 0, 캘린더 days []를 받는다(404 아님).")
    void newMember_returnsEmptyResults() throws Exception {
        MockHttpSession session = signupAndLogin("empty@test.com", "rawPassword1!", "신규", 20, "FEMALE");

        mockMvc.perform(get("/api/v1/emotion-entries").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false));
        mockMvc.perform(get("/api/v1/emotion-entries/summary").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalEntryCount").value(0))
                .andExpect(jsonPath("$.data.monthEntryCount").value(0));
        mockMvc.perform(get("/api/v1/emotion-entries/calendar").param("yearMonth", "2026-09").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearMonth").value("2026-09"))
                .andExpect(jsonPath("$.data.days.length()").value(0));
    }

    @Test
    @DisplayName("조회 기간의 시작이 종료보다 늦거나 페이지 정보가 범위를 벗어나면 해요체 문구로 400을 응답한다.")
    void getEntries_withInvalidRange_returns400() throws Exception {
        MockHttpSession session = signupAndLogin("range@test.com", "rawPassword1!", "범위", 20, "FEMALE");

        mockMvc.perform(get("/api/v1/emotion-entries").session(session).param("from", "2026-09-20").param("to", "2026-09-10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("조회 기간의 시작 날짜가 종료 날짜보다 늦어요."));
        mockMvc.perform(get("/api/v1/emotion-entries").session(session).param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("페이지 정보가 올바르지 않아요."));
        mockMvc.perform(get("/api/v1/emotion-entries").session(session).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("페이지 정보가 올바르지 않아요."));
    }

    @Test
    @DisplayName("요약은 전체 글 수와 이번 달 글 수를 세고, 삭제된 글은 둘 다에서 뺀다.")
    void getSummary_countsTotalAndThisMonth_excludingDeleted() throws Exception {
        MockHttpSession session = signupAndLogin("summary@test.com", "rawPassword1!", "요약", 20, "FEMALE");
        String thisMonth = LocalDate.now().toString();
        String otherMonth = YearMonth.now().minusMonths(2).atDay(1).toString();
        writeAnalyzedEntry(session, "이번 달 1", thisMonth);
        Long deleted = writeAnalyzedEntry(session, "이번 달 2(삭제)", thisMonth);
        writeAnalyzedEntry(session, "이번 달 3(실패) #실패", thisMonth);
        writeAnalyzedEntry(session, "지난 달", otherMonth);
        deleteEntryAndWaitForCascade(session, deleted);

        mockMvc.perform(get("/api/v1/emotion-entries/summary").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalEntryCount").value(3))
                .andExpect(jsonPath("$.data.monthEntryCount").value(2));
    }

    @Test
    @DisplayName("캘린더는 글이 있는 날만 날짜 오름차순으로, 글 수(분석 무관)와 SUCCESS 평균 점수(없으면 null)를 주고 삭제된 글은 뺀다.")
    void getCalendar_countsPerDay_averagesSuccessOnly_excludingDeleted() throws Exception {
        MockHttpSession session = signupAndLogin("calendar@test.com", "rawPassword1!", "캘린더", 20, "FEMALE");
        writeAnalyzedEntry(session, "10일 첫 글 #점수40", "2026-09-10");
        writeAnalyzedEntry(session, "10일 둘째 글 #점수60", "2026-09-10");
        Long deletedSameDay = writeAnalyzedEntry(session, "10일 셋째 글(삭제) #점수0", "2026-09-10");
        writeAnalyzedEntry(session, "12일 실패한 글 #실패", "2026-09-12");
        writeEntryWithoutAnalysis(session, "15일 처리 중인 글", "2026-09-15");
        Long deletedOnlyEntry = writeAnalyzedEntry(session, "20일 유일한 글(삭제)", "2026-09-20");
        writeAnalyzedEntry(session, "다른 달 글", "2026-10-01");
        deleteEntryAndWaitForCascade(session, deletedSameDay);
        deleteEntryAndWaitForCascade(session, deletedOnlyEntry);

        mockMvc.perform(get("/api/v1/emotion-entries/calendar").param("yearMonth", "2026-09").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearMonth").value("2026-09"))
                .andExpect(jsonPath("$.data.days.length()").value(3))
                .andExpect(jsonPath("$.data.days[0].date").value("2026-09-10"))
                .andExpect(jsonPath("$.data.days[0].entryCount").value(2))
                .andExpect(jsonPath("$.data.days[0].averageScore").value(50.0)) // 삭제된 0점 글이 빠져야 (40+60)/2
                .andExpect(jsonPath("$.data.days[1].date").value("2026-09-12"))
                .andExpect(jsonPath("$.data.days[1].entryCount").value(1))
                .andExpect(jsonPath("$.data.days[1].averageScore").value(nullValue()))
                .andExpect(jsonPath("$.data.days[2].date").value("2026-09-15"))
                .andExpect(jsonPath("$.data.days[2].entryCount").value(1))
                .andExpect(jsonPath("$.data.days[2].averageScore").value(nullValue()));

        // 미래 달은 오류가 아니라 빈 결과
        mockMvc.perform(get("/api/v1/emotion-entries/calendar").param("yearMonth", "2099-01").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days.length()").value(0));
    }

    @Test
    @DisplayName("일치 규칙 — 캘린더의 그날 글 수 = 그날(from=to) 목록 글 수, 요약 전체 수 = 전체 목록 글 수(삭제 후에도).")
    void calendarListAndSummary_agreeAfterDeletion() throws Exception {
        MockHttpSession session = signupAndLogin("consistency@test.com", "rawPassword1!", "일치", 20, "FEMALE");
        writeAnalyzedEntry(session, "같은 날 1", "2026-09-10");
        writeAnalyzedEntry(session, "같은 날 2 #실패", "2026-09-10");
        writeEntryWithoutAnalysis(session, "같은 날 3(처리 중)", "2026-09-10");
        Long deleted = writeAnalyzedEntry(session, "같은 날 4(삭제)", "2026-09-10");
        writeAnalyzedEntry(session, "다른 날", "2026-09-11");
        deleteEntryAndWaitForCascade(session, deleted);

        int calendarCount = readInt(mockMvc.perform(get("/api/v1/emotion-entries/calendar")
                        .param("yearMonth", "2026-09").session(session)).andReturn(),
                "$.data.days[0].entryCount");
        int dayListCount = readInt(mockMvc.perform(get("/api/v1/emotion-entries")
                        .param("from", "2026-09-10").param("to", "2026-09-10").param("size", "50").session(session)).andReturn(),
                "$.data.items.length()");
        int totalListCount = readInt(mockMvc.perform(get("/api/v1/emotion-entries")
                        .param("size", "50").session(session)).andReturn(),
                "$.data.items.length()");
        int summaryTotal = readInt(mockMvc.perform(get("/api/v1/emotion-entries/summary").session(session)).andReturn(),
                "$.data.totalEntryCount");

        assertThat(calendarCount).isEqualTo(3);
        assertThat(dayListCount).isEqualTo(calendarCount);
        assertThat(summaryTotal).isEqualTo(4);
        assertThat(totalListCount).isEqualTo(summaryTotal);
    }

    @Test
    @DisplayName("요청 값 형식이 틀리면 Spring 기본(영어) 문구 대신 해요체 문구와 필드 이름으로 400을 응답한다.")
    void invalidFormat_returnsKoreanMessage() throws Exception {
        MockHttpSession session = signupAndLogin("format@test.com", "rawPassword1!", "형식", 20, "FEMALE");

        mockMvc.perform(get("/api/v1/emotion-entries/calendar").param("yearMonth", "2026-13").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("요청 값의 형식이 올바르지 않아요."))
                .andExpect(jsonPath("$.error.errors[0].field").value("yearMonth"));
        mockMvc.perform(get("/api/v1/emotion-entries").param("from", "2026-02-30").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("요청 값의 형식이 올바르지 않아요."))
                .andExpect(jsonPath("$.error.errors[0].field").value("from"));
        mockMvc.perform(get("/api/v1/emotion-entries").param("page", "abc").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.errors[0].field").value("page"));
        // common 핸들러라 다른 모듈에도 적용 — journal 단건 조회의 경로 변수
        mockMvc.perform(get("/api/v1/emotion-entries/{id}", "abc").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("요청 값의 형식이 올바르지 않아요."));
    }

    @Test
    @DisplayName("캘린더에 yearMonth가 없으면 해요체 문구와 파라미터 이름으로 400을 응답한다.")
    void calendar_withoutYearMonth_returnsKoreanMessage() throws Exception {
        MockHttpSession session = signupAndLogin("missing@test.com", "rawPassword1!", "누락", 20, "FEMALE");

        mockMvc.perform(get("/api/v1/emotion-entries/calendar").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("필요한 요청 값이 빠졌어요."))
                .andExpect(jsonPath("$.error.errors[0].field").value("yearMonth"));
    }
}
