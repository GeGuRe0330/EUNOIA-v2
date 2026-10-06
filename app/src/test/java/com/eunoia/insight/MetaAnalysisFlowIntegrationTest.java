package com.eunoia.insight;

import com.eunoia.analysis.domain.EmotionAnalysisResult;
import com.eunoia.completion.client.StructuredPromptClient;
import com.eunoia.insight.application.MetaAnalysisTimeoutSweeper;
import com.eunoia.insight.domain.MetaAnalysisAiResponse;
import com.eunoia.insight.domain.MetaAnalysisContent;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
public class MetaAnalysisFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MetaAnalysisTimeoutSweeper timeoutSweeper;

    // 실제 OpenAI를 호출하지 않도록 completion의 구조화 호출 헬퍼를 대체
    @MockitoBean
    private StructuredPromptClient structuredPromptClient;

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
        throw new AssertionError("분석 결과가 제한 시간 안에 준비되지 않았습니다.");
    }

    private void stubAnalysisResponse() {
        EmotionAnalysisResult stub = new EmotionAnalysisResult(
                "평온", "평온,안정", "요약", "흐름", "감정요약", 80.0, 90, "충분히 선명해요",
                List.of("문장1", "문장2", "문장3"));
        when(structuredPromptClient.call(anyString(), eq(EmotionAnalysisResult.class))).thenReturn(stub);
    }

    private MetaAnalysisAiResponse metaStub() {
        return new MetaAnalysisAiResponse(
                new MetaAnalysisContent.Outer("겉모습 요약", List.of("키워드"), List.of(), List.of(), List.of(), List.of(), List.of()),
                new MetaAnalysisContent.Inner("내면 요약", List.of("키워드"), List.of(), List.of(), List.of(), "", ""),
                new MetaAnalysisAiResponse.ClarityNarrative(List.of("이유"), List.of(), List.of("다음 제안")));
    }

    private void stubMetaAnalysisResponse() {
        when(structuredPromptClient.call(anyString(), eq(MetaAnalysisAiResponse.class))).thenReturn(metaStub());
    }

    // 메타분석 GPT 호출을 반환된 래치가 풀릴 때까지 붙잡는다(PROCESSING 상태를 유지하려고). calls는 호출 횟수.
    private CountDownLatch stubMetaAnalysisHolding(AtomicInteger calls) {
        CountDownLatch release = new CountDownLatch(1);
        MetaAnalysisAiResponse stub = metaStub();
        when(structuredPromptClient.call(anyString(), eq(MetaAnalysisAiResponse.class))).thenAnswer(invocation -> {
            calls.incrementAndGet();
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("테스트가 GPT 응답을 풀어주지 않았습니다.");
            }
            return stub;
        });
        return release;
    }

    // 메타분석 생성이 끝날 때까지(generationStatus가 PROCESSING이 아니게 될 때까지) 최신 조회를 폴링 — 프론트가 하게 될 방식
    private void waitForGeneration(MockHttpSession session) throws Exception {
        long deadline = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < deadline) {
            MvcResult result = mockMvc.perform(get("/api/v1/meta-analyses/latest").session(session)).andReturn();
            String generationStatus = JsonPath.read(result.getResponse().getContentAsString(), "$.data.generationStatus");
            if (!"PROCESSING".equals(generationStatus)) {
                return;
            }
            Thread.sleep(150);
        }
        throw new AssertionError("메타분석 생성이 제한 시간 안에 끝나지 않았습니다.");
    }

    private int metaRowCount(String email) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM meta_analysis_results r JOIN members m ON r.member_id = m.id WHERE m.email = ?
                """, Integer.class, email);
    }

    private void seedTenDays(MockHttpSession session) throws Exception {
        LocalDate start = LocalDate.now().minusDays(9);
        for (int i = 0; i < 10; i++) {
            Long entryId = writeEntry(session, "오늘의 기록 " + i, start.plusDays(i).toString());
            waitForAnalysisReady(session, entryId);
        }
    }

    @Test
    @DisplayName("10일치 감정 기록이 쌓이면 메타분석을 생성할 수 있고, 이후 최신 조회에서도 확인할 수 있다.")
    void tenDaysOfEntries_thenGenerateMetaAnalysis_andRetrieveLatest() throws Exception {
        stubAnalysisResponse();
        stubMetaAnalysisResponse();

        MockHttpSession session = signupAndLogin("meta@test.com", "rawPassword1!", "메타러", 20, "FEMALE");

        LocalDate start = LocalDate.now().minusDays(9);
        for (int i = 0; i < 10; i++) {
            LocalDate entryDate = start.plusDays(i);
            Long entryId = writeEntry(session, "오늘의 기록 " + i, entryDate.toString());
            waitForAnalysisReady(session, entryId);
        }

        mockMvc.perform(post("/api/v1/meta-analyses").session(session).with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.generationStatus").value("PROCESSING"));
        waitForGeneration(session);

        mockMvc.perform(get("/api/v1/meta-analyses/latest").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.content.outer.summary").value("겉모습 요약"));

        mockMvc.perform(get("/api/v1/meta-analyses").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].content.outer.summary").value("겉모습 요약"));
    }

    @Test
    @DisplayName("기록이 10일치보다 적으면 PREPARING 상태로 조회되고, 생성 요청도 거부된다.")
    void fewerThanTenDaysOfEntries_returnsPreparing_andGenerateIsRejected() throws Exception {
        stubAnalysisResponse();

        MockHttpSession session = signupAndLogin("preparing@test.com", "rawPassword1!", "준비중", 20, "FEMALE");

        LocalDate start = LocalDate.now().minusDays(2);
        for (int i = 0; i < 3; i++) {
            LocalDate entryDate = start.plusDays(i);
            Long entryId = writeEntry(session, "오늘의 기록 " + i, entryDate.toString());
            waitForAnalysisReady(session, entryId);
        }

        mockMvc.perform(get("/api/v1/meta-analyses/latest").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PREPARING"))
                .andExpect(jsonPath("$.data.currentCount").value(3));

        mockMvc.perform(post("/api/v1/meta-analyses").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PREPARING"));

        verifyNoMetaAnalysisCall();
    }

    // 같은 회원·같은 날 결과 행을 JDBC로 하나 더 만든다(기존 attempt 1 행의 내용을 복사, 필요하면 요약만 바꿈)
    private void insertExtraAttempt(String email, int attemptNo, String status, String summaryOrNull) {
        String content = switch (status) {
            case "SUCCESS" -> "JSON_SET(content, '$.outer.summary', '" + summaryOrNull + "')";
            default -> "NULL"; // PROCESSING·FAILED 행은 내용이 없다
        };
        String failureReason = "FAILED".equals(status) ? "'테스트 실패'" : "NULL";
        String selectedIds = "PROCESSING".equals(status) ? "JSON_ARRAY(1, 2, 3)" : "NULL";
        jdbcTemplate.update("""
                INSERT INTO meta_analysis_results
                    (created_at, updated_at, based_on_count, content, excluded_entry_count, member_id, period_end,
                     period_start, generation_status, attempt_no, selected_entry_ids, failure_reason)
                SELECT NOW(6), NOW(6), r.based_on_count, %s, r.excluded_entry_count, r.member_id, r.period_end,
                       r.period_start, '%s', %d, %s, %s
                  FROM meta_analysis_results r JOIN members m ON r.member_id = m.id
                 WHERE m.email = ? AND r.attempt_no = 1
                """.formatted(content.replace("content", "r.content"), status, attemptNo, selectedIds, failureReason), email);
    }

    private Long attemptRowId(String email, int attemptNo) {
        return jdbcTemplate.queryForObject("""
                SELECT r.id FROM meta_analysis_results r JOIN members m ON r.member_id = m.id
                 WHERE m.email = ? AND r.attempt_no = ?
                """, Long.class, email, attemptNo);
    }

    private MockHttpSession tenDaysWithGeneratedMeta(String email, String nickname) throws Exception {
        stubAnalysisResponse();
        stubMetaAnalysisResponse();
        MockHttpSession session = signupAndLogin(email, "rawPassword1!", nickname, 20, "FEMALE");
        LocalDate start = LocalDate.now().minusDays(9);
        for (int i = 0; i < 10; i++) {
            Long entryId = writeEntry(session, "오늘의 기록 " + i, start.plusDays(i).toString());
            waitForAnalysisReady(session, entryId);
        }
        mockMvc.perform(post("/api/v1/meta-analyses").session(session).with(csrf()))
                .andExpect(status().isAccepted());
        waitForGeneration(session);
        return session;
    }

    @Test
    @DisplayName("처리 중·실패 행은 최신 조회와 이력에 나타나지 않고, 같은 날 여러 시도는 attemptNo 내림차순으로 이력에 나온다.")
    void latestAndHistory_ignoreNonSuccessRows_andOrderSameDayAttemptsDescending() throws Exception {
        String email = "attempts@test.com";
        MockHttpSession session = tenDaysWithGeneratedMeta(email, "시도들");

        insertExtraAttempt(email, 2, "SUCCESS", "두 번째 시도");
        insertExtraAttempt(email, 3, "FAILED", null);
        insertExtraAttempt(email, 4, "PROCESSING", null);

        // 최신 조회: 가장 최근 SUCCESS(attempt 2), 실패(3)·처리 중(4) 행은 무시
        mockMvc.perform(get("/api/v1/meta-analyses/latest").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.generationStatus").value("PROCESSING")) // 오늘 마지막 시도(attempt 4)가 처리 중
                .andExpect(jsonPath("$.data.content.outer.summary").value("두 번째 시도"));

        // 이력: SUCCESS만 2개(attempt 2, 1), 같은 날 최신 시도가 위, 응답에 행 id 포함
        Long attempt1 = attemptRowId(email, 1);
        Long attempt2 = attemptRowId(email, 2);
        mockMvc.perform(get("/api/v1/meta-analyses").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(attempt2))
                .andExpect(jsonPath("$.data[0].content.outer.summary").value("두 번째 시도"))
                .andExpect(jsonPath("$.data[1].id").value(attempt1))
                .andExpect(jsonPath("$.data[1].content.outer.summary").value("겉모습 요약"));
    }

    @Test
    @DisplayName("최신 행이 처리 중이어도 재생성 가드는 최신 SUCCESS 행과 비교한다(내용 없는 행을 읽다 NPE가 나지 않음).")
    void regenerationGuard_comparesWithLatestSuccess_evenWhenNewestRowIsProcessing() throws Exception {
        String email = "guard-processing@test.com";
        MockHttpSession session = tenDaysWithGeneratedMeta(email, "가드");

        insertExtraAttempt(email, 2, "PROCESSING", null);

        mockMvc.perform(post("/api/v1/meta-analyses").session(session).with(csrf()))
                .andExpect(status().isAccepted()) // 오늘 마지막 시도가 처리 중이라 진행 중 상태가 함께 간다
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.generationStatus").value("PROCESSING"))
                .andExpect(jsonPath("$.data.content.outer.summary").value("겉모습 요약"));

        // 구성이 같아 GPT를 다시 부르지 않았다(처음 생성 1번뿐)
        org.mockito.Mockito.verify(structuredPromptClient, org.mockito.Mockito.times(1))
                .call(any(), eq(MetaAnalysisAiResponse.class));
    }

    @Test
    @DisplayName("생성을 요청하면 202와 PROCESSING이 즉시 오고 이전 결과는 없으며, GPT가 끝나면 최신 조회에서 결과와 함께 완료로 바뀐다.")
    void generate_returns202Processing_thenCompletesAndIsReadable() throws Exception {
        stubAnalysisResponse();
        AtomicInteger gptCalls = new AtomicInteger();
        CountDownLatch release = stubMetaAnalysisHolding(gptCalls);
        String email = "async-first@test.com";
        MockHttpSession session = signupAndLogin(email, "rawPassword1!", "비동기", 20, "FEMALE");
        seedTenDays(session);

        try {
            mockMvc.perform(post("/api/v1/meta-analyses").session(session).with(csrf()))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.data.generationStatus").value("PROCESSING"))
                    .andExpect(jsonPath("$.data.content").doesNotExist());
            mockMvc.perform(get("/api/v1/meta-analyses/latest").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.generationStatus").value("PROCESSING"))
                    .andExpect(jsonPath("$.data.content").doesNotExist());
            mockMvc.perform(get("/api/v1/meta-analyses").session(session))
                    .andExpect(jsonPath("$.data.length()").value(0)); // 진행 중 행은 이력에 없다
        } finally {
            release.countDown();
        }

        waitForGeneration(session);
        mockMvc.perform(get("/api/v1/meta-analyses/latest").session(session))
                .andExpect(jsonPath("$.data.generationStatus").doesNotExist())
                .andExpect(jsonPath("$.data.content.outer.summary").value("겉모습 요약"));
        mockMvc.perform(get("/api/v1/meta-analyses").session(session))
                .andExpect(jsonPath("$.data.length()").value(1));
        org.assertj.core.api.Assertions.assertThat(gptCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("생성이 실패하면 FAILED와 고정 문구가 조회되고, 다시 요청하면 새 시도 행(attemptNo 2)이 만들어져 성공할 수 있다.")
    void generate_afterFailure_retriesWithNewAttemptRow() throws Exception {
        stubAnalysisResponse();
        when(structuredPromptClient.call(anyString(), eq(MetaAnalysisAiResponse.class)))
                .thenThrow(new RuntimeException("내부 예외 상세 메시지"));
        String email = "async-retry@test.com";
        MockHttpSession session = signupAndLogin(email, "rawPassword1!", "재시도", 20, "FEMALE");
        seedTenDays(session);

        mockMvc.perform(post("/api/v1/meta-analyses").session(session).with(csrf()))
                .andExpect(status().isAccepted());
        waitForGeneration(session);

        MvcResult failed = mockMvc.perform(get("/api/v1/meta-analyses/latest").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.generationStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.generationReason").value("메타분석 생성에 실패했어요."))
                .andExpect(jsonPath("$.data.content").doesNotExist())
                .andReturn();
        org.assertj.core.api.Assertions.assertThat(failed.getResponse().getContentAsString()).doesNotContain("내부 예외 상세 메시지");

        stubMetaAnalysisResponse();
        mockMvc.perform(post("/api/v1/meta-analyses").session(session).with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.generationStatus").value("PROCESSING"));
        waitForGeneration(session);

        mockMvc.perform(get("/api/v1/meta-analyses/latest").session(session))
                .andExpect(jsonPath("$.data.generationStatus").doesNotExist())
                .andExpect(jsonPath("$.data.content.outer.summary").value("겉모습 요약"));
        org.assertj.core.api.Assertions.assertThat(metaRowCount(email)).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForList("""
                SELECT r.generation_status FROM meta_analysis_results r JOIN members m ON r.member_id = m.id
                 WHERE m.email = ? ORDER BY r.attempt_no
                """, String.class, email)).containsExactly("FAILED", "SUCCESS");
        mockMvc.perform(get("/api/v1/meta-analyses").session(session))
                .andExpect(jsonPath("$.data.length()").value(1)); // 이력에는 성공한 시도만
    }

    @Test
    @DisplayName("같은 회원이 동시에 두 번 요청해도 GPT는 한 번만 호출되고 행은 하나만 만들어지며, 두 요청 모두 202 PROCESSING을 받는다.")
    void generate_concurrentRequests_callGptOnlyOnce() throws Exception {
        stubAnalysisResponse();
        AtomicInteger gptCalls = new AtomicInteger();
        CountDownLatch release = stubMetaAnalysisHolding(gptCalls);
        String email = "async-concurrent@test.com";
        MockHttpSession session = signupAndLogin(email, "rawPassword1!", "동시", 20, "FEMALE");
        seedTenDays(session);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return mockMvc.perform(post("/api/v1/meta-analyses").session(session).with(csrf())).andReturn();
                }));
            }
            go.countDown();
            for (Future<MvcResult> future : futures) {
                MvcResult result = future.get(15, TimeUnit.SECONDS);
                org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus()).isEqualTo(202);
                org.assertj.core.api.Assertions.assertThat(JsonPath.<String>read(
                        result.getResponse().getContentAsString(), "$.data.generationStatus")).isEqualTo("PROCESSING");
            }
        } finally {
            release.countDown();
            pool.shutdown();
        }

        waitForGeneration(session);
        org.assertj.core.api.Assertions.assertThat(gptCalls.get()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(metaRowCount(email)).isEqualTo(1);
    }

    @Test
    @DisplayName("구성이 그대로면 다시 요청해도 새 시도를 만들지 않고 200으로 기존 결과를 돌려주며 GPT를 다시 부르지 않는다.")
    void generate_whenSelectionUnchanged_returns200WithoutNewAttempt() throws Exception {
        String email = "async-guard@test.com";
        MockHttpSession session = tenDaysWithGeneratedMeta(email, "가드적중");

        mockMvc.perform(post("/api/v1/meta-analyses").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.generationStatus").doesNotExist())
                .andExpect(jsonPath("$.data.content.outer.summary").value("겉모습 요약"));

        org.assertj.core.api.Assertions.assertThat(metaRowCount(email)).isEqualTo(1);
        org.mockito.Mockito.verify(structuredPromptClient, org.mockito.Mockito.times(1))
                .call(any(), eq(MetaAnalysisAiResponse.class));
    }

    // 같은 회원·같은 날 시도 행을 JDBC로 하나 더 만든다(PROCESSING처럼 내용 없는 행). 생성 시각은 JVM 시각으로 직접 지정 —
    // 컨테이너 DB의 NOW()는 UTC라 앱이 쓰는 시각과 어긋나 sweeper 임계 비교에 쓸 수 없다.
    private void insertAttemptRow(String email, int attemptNo, String status, LocalDateTime createdAt) {
        jdbcTemplate.update("""
                INSERT INTO meta_analysis_results
                    (created_at, updated_at, based_on_count, content, excluded_entry_count, member_id, period_end,
                     period_start, generation_status, attempt_no, selected_entry_ids, failure_reason)
                SELECT ?, ?, r.based_on_count, NULL, r.excluded_entry_count, r.member_id, r.period_end,
                       r.period_start, ?, ?, JSON_ARRAY(1, 2, 3), NULL
                  FROM meta_analysis_results r JOIN members m ON r.member_id = m.id
                 WHERE m.email = ? AND r.attempt_no = 1
                """, createdAt, createdAt, status, attemptNo, email);
    }

    private String attemptColumn(String column, String email, int attemptNo) {
        return jdbcTemplate.queryForObject("""
                SELECT r.%s FROM meta_analysis_results r JOIN members m ON r.member_id = m.id
                 WHERE m.email = ? AND r.attempt_no = ?
                """.formatted(column), String.class, email, attemptNo);
    }

    @Test
    @DisplayName("sweeper는 3분 넘은 PROCESSING 시도만 FAILED로 확정하고, 최근 PROCESSING·성공 시도는 건드리지 않는다.")
    void sweeper_failsOnlyStuckProcessingAttempts() throws Exception {
        String email = "sweeper-select@test.com";
        tenDaysWithGeneratedMeta(email, "스위퍼선택"); // 시도 1: SUCCESS

        insertAttemptRow(email, 2, "PROCESSING", LocalDateTime.now().minusMinutes(10)); // 멈춘 시도
        insertAttemptRow(email, 3, "PROCESSING", LocalDateTime.now());                  // 방금 시작한 시도

        timeoutSweeper.failStuckGenerations();

        org.assertj.core.api.Assertions.assertThat(attemptColumn("generation_status", email, 1)).isEqualTo("SUCCESS");
        org.assertj.core.api.Assertions.assertThat(attemptColumn("generation_status", email, 2)).isEqualTo("FAILED");
        org.assertj.core.api.Assertions.assertThat(attemptColumn("failure_reason", email, 2)).isEqualTo("처리 시간 초과");
        org.assertj.core.api.Assertions.assertThat(attemptColumn("generation_status", email, 3)).isEqualTo("PROCESSING");
    }

    @Test
    @DisplayName("멈춘 시도를 sweeper가 FAILED로 확정하면 이후 도착한 GPT 결과는 버려지고, 사용자는 다시 요청해 새 시도로 성공할 수 있다.")
    void sweeper_thenLateResultIsDiscarded_andRetryStartsNewAttempt() throws Exception {
        stubAnalysisResponse();
        AtomicInteger gptCalls = new AtomicInteger();
        CountDownLatch release = stubMetaAnalysisHolding(gptCalls);
        String email = "sweeper-late@test.com";
        MockHttpSession session = signupAndLogin(email, "rawPassword1!", "스위퍼늦은결과", 20, "FEMALE");
        seedTenDays(session);

        mockMvc.perform(post("/api/v1/meta-analyses").session(session).with(csrf()))
                .andExpect(status().isAccepted());
        // GPT가 붙잡힌 채 시간이 흐른 것처럼 시도 1의 생성 시각을 10분 전으로 되돌린다
        jdbcTemplate.update("""
                UPDATE meta_analysis_results r JOIN members m ON r.member_id = m.id
                   SET r.created_at = ? WHERE m.email = ? AND r.attempt_no = 1
                """, LocalDateTime.now().minusMinutes(10), email);

        timeoutSweeper.failStuckGenerations();

        mockMvc.perform(get("/api/v1/meta-analyses/latest").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.generationStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.generationReason").value("메타분석 생성에 실패했어요."));

        release.countDown();
        Thread.sleep(700); // 워커가 뒤늦게 결과를 기록하려 했지만 이미 FAILED라 버려질 시간
        org.assertj.core.api.Assertions.assertThat(attemptColumn("generation_status", email, 1)).isEqualTo("FAILED");
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject("""
                SELECT r.content IS NULL FROM meta_analysis_results r JOIN members m ON r.member_id = m.id
                 WHERE m.email = ? AND r.attempt_no = 1
                """, Boolean.class, email)).isTrue(); // 늦게 온 결과가 반영되지 않았다

        // 사용자가 다시 요청 — 오늘 마지막 시도가 FAILED라 새 시도(2)가 만들어지고 성공한다(래치가 풀려 GPT는 바로 응답)
        mockMvc.perform(post("/api/v1/meta-analyses").session(session).with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.generationStatus").value("PROCESSING"));
        waitForGeneration(session);

        mockMvc.perform(get("/api/v1/meta-analyses/latest").session(session))
                .andExpect(jsonPath("$.data.generationStatus").doesNotExist())
                .andExpect(jsonPath("$.data.content.outer.summary").value("겉모습 요약"));
        org.assertj.core.api.Assertions.assertThat(attemptColumn("generation_status", email, 2)).isEqualTo("SUCCESS");
        org.assertj.core.api.Assertions.assertThat(gptCalls.get()).isEqualTo(2);
    }

    private void verifyNoMetaAnalysisCall() {
        org.mockito.Mockito.verify(structuredPromptClient, org.mockito.Mockito.never())
                .call(any(), eq(MetaAnalysisAiResponse.class));
    }
}
