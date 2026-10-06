package com.eunoia.analysis;

import com.eunoia.analysis.application.EmotionAnalysisTimeoutSweeper;
import com.eunoia.analysis.domain.EmotionAnalysisRepository;
import com.eunoia.analysis.domain.EmotionAnalysisResult;
import com.eunoia.completion.client.StructuredPromptClient;
import com.eunoia.journal.query.EmotionEntryQueryApi;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
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
public class EmotionAnalysisFlowIntegrationTest {

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

    @Autowired
    private EmotionEntryQueryApi emotionEntryQueryApi;

    @Autowired
    private EmotionAnalysisTimeoutSweeper timeoutSweeper;

    // 일기 작성과 같은 트랜잭션에서 PROCESSING 행을 저장하는 동기 리스너의 실패를 흉내 내기 위해 spy로 감싼다(평소엔 실제 동작 그대로)
    @MockitoSpyBean
    private EmotionAnalysisRepository emotionAnalysisRepository;

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

    // 분석은 비동기 실행이라, PROCESSING이 끝날 때까지(SUCCESS 또는 FAILED) 조회 API를 직접 폴링
    // (프론트가 실제로 하게 될 방식과 동일한 방식으로 대기)
    private void waitForAnalysisReady(MockHttpSession session, Long entryId) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            MvcResult result = mockMvc.perform(get("/api/v1/analyses/by-entry/{entryId}", entryId).session(session))
                    .andReturn();
            if (result.getResponse().getStatus() == 200) {
                String status = JsonPath.read(result.getResponse().getContentAsString(), "$.data.status");
                if (!"PROCESSING".equals(status)) {
                    return;
                }
            }
            Thread.sleep(200);
        }
        throw new AssertionError("분석이 제한 시간 안에 끝나지 않았습니다.");
    }

    private EmotionAnalysisResult defaultStub() {
        return new EmotionAnalysisResult(
                "평온", "평온,안정", "요약", "흐름", "감정요약", 80.0, 90, "충분함",
                List.of("문장1", "문장2", "문장3"));
    }

    // 앞의 firstCount번은 바로 stub을 돌려주고, 그 뒤의 GPT 호출은 반환된 래치가 풀릴 때까지 붙잡는다(PROCESSING 상태를 유지하려고)
    private CountDownLatch stubGptHoldingAfter(int firstCount, EmotionAnalysisResult stub) {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        when(structuredPromptClient.call(anyString(), any())).thenAnswer(invocation -> {
            if (calls.incrementAndGet() > firstCount && !release.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("테스트가 GPT 응답을 풀어주지 않았습니다.");
            }
            return stub;
        });
        return release;
    }

    // @SQLRestriction을 거치지 않는 JDBC 조회 — 소프트 삭제된 분석 행도 보인다
    private String analysisStatus(Long entryId) {
        return jdbcTemplate.queryForObject("SELECT status FROM emotion_analyses WHERE entry_id = ?", String.class, entryId);
    }

    private String analysisEmotionDetected(Long entryId) {
        return jdbcTemplate.queryForObject("SELECT emotion_detected FROM emotion_analyses WHERE entry_id = ?", String.class, entryId);
    }

    private void deleteEntry(MockHttpSession session, Long entryId) throws Exception {
        mockMvc.perform(delete("/api/v1/emotion-entries/{id}", entryId).session(session).with(csrf()))
                .andExpect(status().isOk());
    }

    // 삭제 구독(@ApplicationModuleListener)도 비동기라, DB 상태가 조건을 만족할 때까지 폴링
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

    // @SQLRestriction을 거치지 않는 JDBC 조회 — 소프트 삭제된 분석 행도 보인다
    private boolean isAnalysisSoftDeleted(Long entryId) {
        List<Object> deletedAts = jdbcTemplate.queryForList(
                "SELECT deleted_at FROM emotion_analyses WHERE entry_id = ?", Object.class, entryId);
        return deletedAts.size() == 1 && deletedAts.get(0) != null;
    }

    @Test
    @DisplayName("일기를 작성하면 자동으로 분석이 실행되고, 완료되면 조회할 수 있다.")
    void write_thenAnalysisCompletesAutomatically_andIsReadable() throws Exception {
        EmotionAnalysisResult stub = new EmotionAnalysisResult(
                "평온", "평온,안정", "요약", "흐름", "감정요약", 80.0, 90, "충분함",
                List.of("문장1", "문장2", "문장3"));
        when(structuredPromptClient.call(anyString(), any())).thenReturn(stub);

        MockHttpSession session = signupAndLogin("analysis@test.com", "rawPassword1!", "분석러", 20, "FEMALE");
        Long entryId = writeEntry(session, "오늘은 맑았다", "2026-09-20");

        waitForAnalysisReady(session, entryId);

        mockMvc.perform(get("/api/v1/analyses/by-entry/{entryId}", entryId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entryId").value(entryId))
                .andExpect(jsonPath("$.data.emotionDetected").value("평온"))
                .andExpect(jsonPath("$.data.warmMessages.length()").value(3));
    }

    @Test
    @DisplayName("GPT 호출이 실패하면 FAILED 상태로 저장되고, 조회 시 고정 문구로 응답하며 내부 예외 메시지는 노출되지 않는다.")
    void writeEntry_whenAnalysisFails_returnsFailedStatusWithFixedReason() throws Exception {
        when(structuredPromptClient.call(anyString(), any()))
                .thenThrow(new RuntimeException("내부 예외 상세 메시지"));

        MockHttpSession session = signupAndLogin("failure@test.com", "rawPassword1!", "실패러", 20, "FEMALE");
        Long entryId = writeEntry(session, "오늘은 흐렸다", "2026-09-20");

        waitForAnalysisReady(session, entryId);

        MvcResult result = mockMvc.perform(get("/api/v1/analyses/by-entry/{entryId}", entryId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.reason").value("감정 분석에 실패했어요."))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("내부 예외 상세 메시지");
    }

    @Test
    @DisplayName("본인이 작성하지 않은 일기의 분석 결과는 조회할 수 없다.")
    void getByEntryId_withOtherMemberSession_returns403() throws Exception {
        EmotionAnalysisResult stub = new EmotionAnalysisResult(
                "평온", "평온,안정", "요약", "흐름", "감정요약", 80.0, 90, "충분함",
                List.of("문장1", "문장2", "문장3"));
        when(structuredPromptClient.call(anyString(), any())).thenReturn(stub);

        MockHttpSession ownerSession = signupAndLogin("owner3@test.com", "rawPassword1!", "본인3", 20, "FEMALE");
        Long entryId = writeEntry(ownerSession, "오늘은 맑았다", "2026-09-20");
        waitForAnalysisReady(ownerSession, entryId);

        MockHttpSession otherSession = signupAndLogin("other3@test.com", "rawPassword1!", "타인3", 20, "MALE");

        mockMvc.perform(get("/api/v1/analyses/by-entry/{entryId}", entryId).session(otherSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("존재하지 않는 분석 결과를 조회하면 404를 반환한다.")
    void getByEntryId_withNonExistentEntryId_returns404() throws Exception {
        MockHttpSession session = signupAndLogin("notfound3@test.com", "rawPassword1!", "없음3", 20, "FEMALE");

        mockMvc.perform(get("/api/v1/analyses/by-entry/{entryId}", 999999L).session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("분석 결과가 하나도 없으면 최신 분석 조회는 200과 함께 data: null을 반환한다.")
    void getLatest_withNoAnalyses_returnsOkWithNullData() throws Exception {
        MockHttpSession session = signupAndLogin("nolatest@test.com", "rawPassword1!", "무기록", 20, "FEMALE");

        mockMvc.perform(get("/api/v1/analyses/latest").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("entryDate가 가장 최근인 일기의 분석을 최신 분석으로 조회한다(처리/생성 순서와 무관).")
    void getLatest_withMultipleAnalyses_returnsMostRecentEntryDate() throws Exception {
        EmotionAnalysisResult stub = new EmotionAnalysisResult(
                "평온", "평온,안정", "요약", "흐름", "감정요약", 80.0, 90, "충분함",
                List.of("문장1", "문장2", "문장3"));
        when(structuredPromptClient.call(anyString(), any())).thenReturn(stub);

        MockHttpSession session = signupAndLogin("latest@test.com", "rawPassword1!", "최신러", 20, "FEMALE");
        // 먼저 작성(=먼저 처리 완료)했지만 entryDate는 더 최근인 일기
        Long earlierWrittenButLaterDateId = writeEntry(session, "먼저 작성, 날짜는 더 최근", "2026-09-20");
        waitForAnalysisReady(session, earlierWrittenButLaterDateId);
        // 나중에 작성(=나중에 처리 완료)했지만 entryDate는 더 과거인 일기
        Long laterWrittenButEarlierDateId = writeEntry(session, "나중 작성, 날짜는 더 과거", "2026-09-10");
        waitForAnalysisReady(session, laterWrittenButEarlierDateId);

        mockMvc.perform(get("/api/v1/analyses/latest").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entryId").value(earlierWrittenButLaterDateId));
    }

    @Test
    @DisplayName("가장 최근 entryDate의 일기 분석이 FAILED여도 최신 분석 조회에 그대로 노출된다.")
    void getLatest_withMostRecentEntryFailed_returnsFailedStatus() throws Exception {
        EmotionAnalysisResult stub = new EmotionAnalysisResult(
                "평온", "평온,안정", "요약", "흐름", "감정요약", 80.0, 90, "충분함",
                List.of("문장1", "문장2", "문장3"));
        when(structuredPromptClient.call(anyString(), any()))
                .thenReturn(stub)
                .thenThrow(new RuntimeException("GPT 호출 실패"));

        MockHttpSession session = signupAndLogin("latestfailed@test.com", "rawPassword1!", "실패최신러", 20, "FEMALE");
        Long earlierEntryId = writeEntry(session, "먼저 일기(성공)", "2026-09-10");
        waitForAnalysisReady(session, earlierEntryId);
        Long laterEntryId = writeEntry(session, "나중 일기(실패)", "2026-09-20");
        waitForAnalysisReady(session, laterEntryId);

        mockMvc.perform(get("/api/v1/analyses/latest").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entryId").value(laterEntryId))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.reason").value("감정 분석에 실패했어요."));
    }

    @Test
    @DisplayName("감정 점수 목록을 entryDate 오름차순으로 조회한다.")
    void getScores_withMultipleAnalyses_returnsAscendingByEntryDate() throws Exception {
        EmotionAnalysisResult stub = new EmotionAnalysisResult(
                "평온", "평온,안정", "요약", "흐름", "감정요약", 80.0, 90, "충분함",
                List.of("문장1", "문장2", "문장3"));
        when(structuredPromptClient.call(anyString(), any())).thenReturn(stub);

        MockHttpSession session = signupAndLogin("scores@test.com", "rawPassword1!", "점수러", 20, "FEMALE");
        Long laterEntryId = writeEntry(session, "나중 일기", "2026-09-20");
        waitForAnalysisReady(session, laterEntryId);
        Long earlierEntryId = writeEntry(session, "먼저 일기", "2026-09-10");
        waitForAnalysisReady(session, earlierEntryId);

        mockMvc.perform(get("/api/v1/analyses/scores").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].entryId").value(earlierEntryId))
                .andExpect(jsonPath("$.data[0].entryDate").value("2026-09-10"))
                .andExpect(jsonPath("$.data[1].entryId").value(laterEntryId))
                .andExpect(jsonPath("$.data[1].entryDate").value("2026-09-20"));
    }

    @Test
    @DisplayName("FAILED 상태 분석은 감정 점수 목록에서 제외된다.")
    void getScores_withFailedAnalysis_excludesFailedFromList() throws Exception {
        EmotionAnalysisResult stub = new EmotionAnalysisResult(
                "평온", "평온,안정", "요약", "흐름", "감정요약", 80.0, 90, "충분함",
                List.of("문장1", "문장2", "문장3"));
        when(structuredPromptClient.call(anyString(), any()))
                .thenReturn(stub)
                .thenThrow(new RuntimeException("GPT 호출 실패"));

        MockHttpSession session = signupAndLogin("scoresfailed@test.com", "rawPassword1!", "점수실패러", 20, "FEMALE");
        Long successEntryId = writeEntry(session, "성공 일기", "2026-09-10");
        waitForAnalysisReady(session, successEntryId);
        Long failedEntryId = writeEntry(session, "실패 일기", "2026-09-20");
        waitForAnalysisReady(session, failedEntryId);

        mockMvc.perform(get("/api/v1/analyses/scores").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].entryId").value(successEntryId));
    }

    @Test
    @DisplayName("분석이 하나도 없으면 감정 점수 목록은 빈 배열을 반환한다.")
    void getScores_withNoAnalyses_returnsEmptyList() throws Exception {
        MockHttpSession session = signupAndLogin("noscores@test.com", "rawPassword1!", "무점수", 20, "FEMALE");

        mockMvc.perform(get("/api/v1/analyses/scores").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("일기를 삭제하면 그 분석도 소프트 삭제되어 단건·최신·점수 조회에서 빠진다.")
    void deleteEntry_cascadesToAnalysis_andExcludedFromQueries() throws Exception {
        EmotionAnalysisResult stub = new EmotionAnalysisResult(
                "평온", "평온,안정", "요약", "흐름", "감정요약", 80.0, 90, "충분함",
                List.of("문장1", "문장2", "문장3"));
        when(structuredPromptClient.call(anyString(), any())).thenReturn(stub);

        MockHttpSession session = signupAndLogin("cascade@test.com", "rawPassword1!", "연쇄삭제", 20, "FEMALE");
        Long keptEntryId = writeEntry(session, "남길 일기", "2026-09-10");
        waitForAnalysisReady(session, keptEntryId);
        Long deletedEntryId = writeEntry(session, "지울 일기(가장 최근)", "2026-09-20");
        waitForAnalysisReady(session, deletedEntryId);

        deleteEntry(session, deletedEntryId);
        waitUntil(() -> isAnalysisSoftDeleted(deletedEntryId), "삭제 이벤트를 받은 분석이 제한 시간 안에 소프트 삭제되지 않았습니다.");

        mockMvc.perform(get("/api/v1/analyses/by-entry/{entryId}", deletedEntryId).session(session))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/analyses/latest").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entryId").value(keptEntryId));
        mockMvc.perform(get("/api/v1/analyses/scores").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].entryId").value(keptEntryId));
    }

    @Test
    @DisplayName("일기 존재 확인은 삭제된 글과 남의 글을 없는 글로 본다(파생 쿼리에도 @SQLRestriction 적용).")
    void existsEntry_withDeletedOrOthersEntry_returnsFalse() throws Exception {
        EmotionAnalysisResult stub = new EmotionAnalysisResult(
                "평온", "평온,안정", "요약", "흐름", "감정요약", 80.0, 90, "충분함",
                List.of("문장1", "문장2", "문장3"));
        when(structuredPromptClient.call(anyString(), any())).thenReturn(stub);

        MockHttpSession session = signupAndLogin("exists@test.com", "rawPassword1!", "존재확인", 20, "FEMALE");
        Long entryId = writeEntry(session, "확인할 일기", "2026-09-20");
        Long memberId = jdbcTemplate.queryForObject(
                "SELECT member_id FROM emotion_entries WHERE id = ?", Long.class, entryId);

        assertThat(emotionEntryQueryApi.existsEntry(memberId, entryId)).isTrue();
        assertThat(emotionEntryQueryApi.existsEntry(memberId + 1000, entryId)).isFalse();

        deleteEntry(session, entryId);

        assertThat(emotionEntryQueryApi.existsEntry(memberId, entryId)).isFalse();
    }

    @Test
    @DisplayName("분석(GPT 호출) 도중 일기가 삭제되면, PROCESSING 행이 바로 소프트 삭제되고 GPT 결과는 반영되지 않는다.")
    void deleteEntry_duringAnalysis_softDeletesProcessingRow_andDiscardsResult() throws Exception {
        EmotionAnalysisResult stub = defaultStub();
        CountDownLatch gptStarted = new CountDownLatch(1);
        CountDownLatch releaseGpt = new CountDownLatch(1);
        // GPT 호출에 들어간 뒤(=분석 전 확인 통과 후) 테스트가 삭제를 끝낼 때까지 응답을 붙잡는다
        when(structuredPromptClient.call(anyString(), any())).thenAnswer(invocation -> {
            gptStarted.countDown();
            if (!releaseGpt.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("테스트가 GPT 응답을 풀어주지 않았습니다.");
            }
            return stub;
        });

        MockHttpSession session = signupAndLogin("race@test.com", "rawPassword1!", "경쟁", 20, "FEMALE");
        Long entryId = writeEntry(session, "분석 중에 지울 일기", "2026-09-20");
        assertThat(gptStarted.await(5, TimeUnit.SECONDS)).isTrue();

        deleteEntry(session, entryId); // 분석 행(PROCESSING)은 이미 있으므로 삭제 구독이 바로 소프트 삭제한다
        waitUntil(() -> isAnalysisSoftDeleted(entryId), "처리 중인 분석이 삭제 이벤트로 제한 시간 안에 소프트 삭제되지 않았습니다.");
        assertThat(analysisStatus(entryId)).isEqualTo("PROCESSING");

        releaseGpt.countDown();
        Thread.sleep(500); // 기록기가 소프트 삭제된 행을 찾지 못해 결과를 버릴 시간
        assertThat(analysisStatus(entryId)).isEqualTo("PROCESSING"); // 결과가 되살아나 반영되지 않았다
        assertThat(analysisEmotionDetected(entryId)).isNull();
        assertThat(isAnalysisSoftDeleted(entryId)).isTrue();
        mockMvc.perform(get("/api/v1/analyses/latest").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("긴 본문(1200자)도 저장되고 분석까지 완료되며, 분석은 journal에서 조회한 본문으로 GPT를 호출한다.")
    void writeLongEntry_isSavedAndAnalyzed() throws Exception {
        // event_publication.serialized_event는 ddl-auto 기본 VARCHAR(255) — 이벤트에 본문이 다시 들어가면 여기서 500(05.troubleshooting/30)
        EmotionAnalysisResult stub = new EmotionAnalysisResult(
                "평온", "평온,안정", "요약", "흐름", "감정요약", 80.0, 90, "충분함",
                List.of("문장1", "문장2", "문장3"));
        when(structuredPromptClient.call(anyString(), any())).thenReturn(stub);
        String longContent = "긴글시작-" + "오늘 하루를 천천히 돌아본다. ".repeat(70);
        assertThat(longContent.length()).isGreaterThan(1000);

        MockHttpSession session = signupAndLogin("long-entry@test.com", "rawPassword1!", "긴글", 20, "FEMALE");
        Long entryId = writeEntry(session, longContent, "2026-09-20");

        waitForAnalysisReady(session, entryId);
        mockMvc.perform(get("/api/v1/analyses/by-entry/{entryId}", entryId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
        verify(structuredPromptClient).call(contains("긴글시작-"), any());
    }

    @Test
    @DisplayName("일기를 작성한 직후에는 404가 아니라 200 + PROCESSING(결과 필드는 비어있음)이 조회된다.")
    void write_thenImmediateGet_returnsProcessing() throws Exception {
        CountDownLatch releaseGpt = stubGptHoldingAfter(0, defaultStub());

        MockHttpSession session = signupAndLogin("processing@test.com", "rawPassword1!", "처리중", 20, "FEMALE");
        Long entryId = writeEntry(session, "방금 쓴 일기", "2026-09-20");
        try {
            mockMvc.perform(get("/api/v1/analyses/by-entry/{entryId}", entryId).session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.entryId").value(entryId))
                    .andExpect(jsonPath("$.data.status").value("PROCESSING"))
                    .andExpect(jsonPath("$.data.emotionDetected").doesNotExist())
                    .andExpect(jsonPath("$.data.warmMessages").doesNotExist())
                    .andExpect(jsonPath("$.data.reason").doesNotExist());
        } finally {
            releaseGpt.countDown();
        }
        waitForAnalysisReady(session, entryId);
    }

    @Test
    @DisplayName("가장 최근 일기가 처리 중이면 /latest는 그 행을 건너뛰고 직전 분석을 반환하며, /scores에서도 제외된다.")
    void getLatestAndScores_whenMostRecentIsProcessing_skipsProcessing() throws Exception {
        CountDownLatch releaseGpt = stubGptHoldingAfter(1, defaultStub());

        MockHttpSession session = signupAndLogin("latestprocessing@test.com", "rawPassword1!", "처리중최신", 20, "FEMALE");
        Long earlierId = writeEntry(session, "먼저 쓴 일기(완료)", "2026-09-10");
        waitForAnalysisReady(session, earlierId);
        Long laterId = writeEntry(session, "나중에 쓴 일기(처리 중)", "2026-09-20");
        try {
            mockMvc.perform(get("/api/v1/analyses/by-entry/{entryId}", laterId).session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PROCESSING"));
            mockMvc.perform(get("/api/v1/analyses/latest").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.entryId").value(earlierId));
            mockMvc.perform(get("/api/v1/analyses/scores").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].entryId").value(earlierId));
        } finally {
            releaseGpt.countDown();
        }

        waitForAnalysisReady(session, laterId);
        mockMvc.perform(get("/api/v1/analyses/latest").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entryId").value(laterId));
    }

    @Test
    @DisplayName("멈춘 처리 sweeper는 5분 넘은 PROCESSING만 FAILED로 확정하고, 최근·성공·소프트 삭제된 행은 건드리지 않으며, 이후 도착한 결과는 버린다.")
    void sweeper_failsOnlyStuckProcessing_andLateResultIsDiscarded() throws Exception {
        CountDownLatch releaseGpt = stubGptHoldingAfter(1, defaultStub());

        MockHttpSession session = signupAndLogin("sweeper@test.com", "rawPassword1!", "스위퍼", 20, "FEMALE");
        Long successId = writeEntry(session, "성공한 글", "2026-09-10");
        waitForAnalysisReady(session, successId);
        Long stuckId = writeEntry(session, "멈춘 글", "2026-09-11");
        Long recentId = writeEntry(session, "최근 글", "2026-09-12");
        Long deletedStuckId = writeEntry(session, "삭제된 멈춘 글", "2026-09-13");

        LocalDateTime longAgo = LocalDateTime.now().minusMinutes(10);
        for (Long id : List.of(successId, stuckId, deletedStuckId)) {
            jdbcTemplate.update("UPDATE emotion_analyses SET created_at = ? WHERE entry_id = ?", longAgo, id);
        }
        jdbcTemplate.update("UPDATE emotion_analyses SET deleted_at = ? WHERE entry_id = ?", LocalDateTime.now(), deletedStuckId);

        try {
            timeoutSweeper.failStuckAnalyses();

            assertThat(analysisStatus(stuckId)).isEqualTo("FAILED");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT failure_reason FROM emotion_analyses WHERE entry_id = ?", String.class, stuckId))
                    .isEqualTo("처리 시간 초과");
            assertThat(analysisStatus(recentId)).isEqualTo("PROCESSING");          // 최근 글은 그대로
            assertThat(analysisStatus(successId)).isEqualTo("SUCCESS");            // 오래됐어도 성공은 그대로
            assertThat(analysisStatus(deletedStuckId)).isEqualTo("PROCESSING");    // 소프트 삭제된 행은 대상이 아님
        } finally {
            releaseGpt.countDown();
        }

        // 최근 글은 정상 완료되고, 이미 FAILED로 확정된 멈춘 글에는 늦게 도착한 결과가 반영되지 않는다
        waitUntil(() -> "SUCCESS".equals(analysisStatus(recentId)), "최근 글의 분석이 제한 시간 안에 완료되지 않았습니다.");
        Thread.sleep(300);
        assertThat(analysisStatus(stuckId)).isEqualTo("FAILED");
        assertThat(analysisEmotionDetected(stuckId)).isNull();
    }

    @Test
    @DisplayName("PROCESSING 행 저장이 실패하면 일기 저장도 롤백된다(같은 트랜잭션).")
    void write_whenProcessingRowSaveFails_rollsBackEntry() throws Exception {
        MockHttpSession session = signupAndLogin("rollback@test.com", "rawPassword1!", "롤백", 20, "FEMALE");
        doThrow(new RuntimeException("분석 행 저장 실패")).when(emotionAnalysisRepository).save(any());

        mockMvc.perform(post("/api/v1/emotion-entries")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"content":"롤백될 일기","entryDate":"2026-09-20"}
                        """))
                .andExpect(status().is5xxServerError());

        Integer entryCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM emotion_entries e JOIN members m ON e.member_id = m.id WHERE m.email = ?",
                Integer.class, "rollback@test.com");
        assertThat(entryCount).isZero();
    }
}
