package com.eunoia.analysis;

import com.eunoia.analysis.domain.EmotionAnalysisResult;
import com.eunoia.completion.client.StructuredPromptClient;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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

    // @ApplicationModuleListener는 비동기 실행이라, 분석이 끝날 때까지 조회 API를 직접 폴링
    // (프론트가 실제로 하게 될 방식과 동일한 방식으로 대기)
    private void waitForAnalysisReady(MockHttpSession session, Long entryId) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            MvcResult result = mockMvc.perform(get("/api/v1/analyses/by-entry/{entryId}", entryId).session(session))
                    .andReturn();
            if (result.getResponse().getStatus() == 200) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("분석 결과가 제한 시간 안에 준비되지 않았습니다.");
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
}
