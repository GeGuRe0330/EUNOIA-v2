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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
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
    // (프론트가 실제로 하게 될 방식과 동일한 방식으로 대기)
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

        mockMvc.perform(get("/api/v1/analyses/{entryId}", entryId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entryId").value(entryId))
                .andExpect(jsonPath("$.data.emotionDetected").value("평온"))
                .andExpect(jsonPath("$.data.warmMessages.length()").value(3));
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

        mockMvc.perform(get("/api/v1/analyses/{entryId}", entryId).session(otherSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("존재하지 않는 분석 결과를 조회하면 404를 반환한다.")
    void getByEntryId_withNonExistentEntryId_returns404() throws Exception {
        MockHttpSession session = signupAndLogin("notfound3@test.com", "rawPassword1!", "없음3", 20, "FEMALE");

        mockMvc.perform(get("/api/v1/analyses/{entryId}", 999999L).session(session))
                .andExpect(status().isNotFound());
    }
}
