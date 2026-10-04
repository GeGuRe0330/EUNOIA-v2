package com.eunoia.identity;

import com.eunoia.identity.domain.ProfileImageStorage;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
public class ProfileImageFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProfileImageStorage profileImageStorage;

    private MockHttpSession signupAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/members/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"email":"%s","password":"rawPassword1!","nickname":"사진","age":20,"gender":"NONE"}
                        """.formatted(email)))
                .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE members SET status = 'ACTIVE' WHERE email = ?", email);

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .param("username", email)
                        .param("password", "rawPassword1!"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) loginResult.getRequest().getSession(false);
    }

    private UUID upload(MockHttpSession session, byte[] content) throws Exception {
        MvcResult result = mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/members/me/profile-image")
                        .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", content))
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.profileImageId");
        return UUID.fromString(id);
    }

    private static byte[] jpeg(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("업로드하면 내 정보에 새 키가 보이고, 그 키로 512×512 JPEG를 캐시·보안 헤더와 함께 받는다.")
    void upload_thenMeAndImageAreServed() throws Exception {
        MockHttpSession session = signupAndLogin("upload@test.com");

        UUID imageId = upload(session, jpeg(800, 600));

        mockMvc.perform(get("/api/v1/members/me").session(session))
                .andExpect(jsonPath("$.data.profileImageId").value(imageId.toString()));

        MvcResult image = mockMvc.perform(get("/api/v1/members/me/profile-image/{id}", imageId).session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Cache-Control", containsString("max-age=31536000")))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(header().string("Cache-Control", containsString("immutable")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();

        BufferedImage served = ImageIO.read(new ByteArrayInputStream(image.getResponse().getContentAsByteArray()));
        assertThat(served.getWidth()).isEqualTo(512);
        assertThat(served.getHeight()).isEqualTo(512);
    }

    @Test
    @DisplayName("교체하면 옛 키는 404가 되고 옛 파일은 지워진다.")
    void replace_oldImageIsGone() throws Exception {
        MockHttpSession session = signupAndLogin("replace@test.com");
        UUID oldId = upload(session, jpeg(400, 400));

        UUID newId = upload(session, jpeg(300, 500));

        assertThat(newId).isNotEqualTo(oldId);
        mockMvc.perform(get("/api/v1/members/me/profile-image/{id}", oldId).session(session))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/members/me/profile-image/{id}", newId).session(session))
                .andExpect(status().isOk());
        assertThat(profileImageStorage.read(oldId)).isEmpty();
        assertThat(profileImageStorage.read(newId)).isPresent();
    }

    @Test
    @DisplayName("남의 이미지 키, 형식이 틀린 키, 파일이 사라진 현재 키는 모두 404다.")
    void read_notFoundCases() throws Exception {
        MockHttpSession owner = signupAndLogin("owner-img@test.com");
        UUID ownerImageId = upload(owner, jpeg(200, 200));
        MockHttpSession other = signupAndLogin("other-img@test.com");

        mockMvc.perform(get("/api/v1/members/me/profile-image/{id}", ownerImageId).session(other))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/members/me/profile-image/{id}", "not-a-uuid").session(owner))
                .andExpect(status().isNotFound());

        profileImageStorage.delete(ownerImageId);
        mockMvc.perform(get("/api/v1/members/me/profile-image/{id}", ownerImageId).session(owner))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("되돌리면 키가 null이 되고 파일이 지워지며, 다시 되돌려도 200이다.")
    void remove_isIdempotent() throws Exception {
        MockHttpSession session = signupAndLogin("remove@test.com");
        UUID imageId = upload(session, jpeg(200, 200));

        mockMvc.perform(delete("/api/v1/members/me/profile-image").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImageId").doesNotExist());
        assertThat(profileImageStorage.read(imageId)).isEmpty();
        mockMvc.perform(get("/api/v1/members/me").session(session))
                .andExpect(jsonPath("$.data.profileImageId").doesNotExist());

        mockMvc.perform(delete("/api/v1/members/me/profile-image").session(session).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("이미지가 아닌 파일이나 file 파트가 없는 요청은 400 \"올릴 수 없는 사진이에요.\"이다.")
    void upload_invalid_returns400() throws Exception {
        MockHttpSession session = signupAndLogin("invalid-img@test.com");
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg'></svg>".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/members/me/profile-image")
                        .file(new MockMultipartFile("file", "fake.jpg", "image/jpeg", svg))
                        .session(session).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("올릴 수 없는 사진이에요."));
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/members/me/profile-image")
                        .session(session).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("올릴 수 없는 사진이에요."));
        mockMvc.perform(get("/api/v1/members/me").session(session))
                .andExpect(jsonPath("$.data.profileImageId").doesNotExist());
    }

    @Test
    @DisplayName("CSRF 토큰 없이 업로드·삭제하면 403, 로그인 없이 조회하면 401이다.")
    void securityRules() throws Exception {
        MockHttpSession session = signupAndLogin("security-img@test.com");

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/members/me/profile-image")
                        .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpeg(100, 100)))
                        .session(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/members/me/profile-image").session(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/members/me/profile-image/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
