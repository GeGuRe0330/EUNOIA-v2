package com.eunoia.insight;

import com.eunoia.analysis.domain.EmotionAnalysisResult;
import com.eunoia.completion.client.StructuredPromptClient;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
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

    // 실제 OpenAI를 호출하지 않도록 completion의 구조화 호출 헬퍼를 대체
    @MockitoBean
    private StructuredPromptClient structuredPromptClient;

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

    private Long writeEntry(MockHttpSession session, String content, String entryDate) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/emotion-entries")
                        .session(session)
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
    private void waitForAnalysisReady(MockHttpSession session, Long entryId) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            MvcResult result = mockMvc.perform(get("/api/v1/analyses/{entryId}", entryId).session(session))
                    .andReturn();
            if (result.getResponse().getStatus() == 200) {
                return;
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

    private void stubMetaAnalysisResponse() {
        MetaAnalysisAiResponse stub = new MetaAnalysisAiResponse(
                new MetaAnalysisContent.Outer("겉모습 요약", List.of("키워드"), List.of(), List.of(), List.of(), List.of(), List.of()),
                new MetaAnalysisContent.Inner("내면 요약", List.of("키워드"), List.of(), List.of(), List.of(), "", ""),
                new MetaAnalysisAiResponse.ClarityNarrative(List.of("이유"), List.of(), List.of("다음 제안")));
        when(structuredPromptClient.call(anyString(), eq(MetaAnalysisAiResponse.class))).thenReturn(stub);
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

        mockMvc.perform(post("/api/v1/meta-analyses").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.content.outer.summary").value("겉모습 요약"))
                .andExpect(jsonPath("$.data.content.inner.summary").value("내면 요약"));

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

        mockMvc.perform(post("/api/v1/meta-analyses").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PREPARING"));

        verifyNoMetaAnalysisCall();
    }

    private void verifyNoMetaAnalysisCall() {
        org.mockito.Mockito.verify(structuredPromptClient, org.mockito.Mockito.never())
                .call(any(), eq(MetaAnalysisAiResponse.class));
    }
}
