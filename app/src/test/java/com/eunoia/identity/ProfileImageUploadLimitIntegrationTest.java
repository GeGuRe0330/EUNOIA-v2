package com.eunoia.identity;

import com.eunoia.identity.domain.Gender;
import com.eunoia.identity.domain.Member;
import com.eunoia.identity.domain.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ProfileImageUploadLimitIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    private static final String BOUNDARY = "eunoia-test-boundary";

    @LocalServerPort
    private int port;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private String xsrfToken() {
        return cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("XSRF-TOKEN 쿠키가 없습니다."));
    }

    private void login(String email, String password) throws Exception {
        memberRepository.save(Member.register(email, passwordEncoder.encode(password), "용량", 20, Gender.NONE));
        jdbcTemplate.update("UPDATE members SET status = 'ACTIVE' WHERE email = ?", email);

        client.send(HttpRequest.newBuilder(uri("/api/v1/members/me")).GET().build(), HttpResponse.BodyHandlers.discarding());

        HttpResponse<String> login = client.send(HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("X-XSRF-TOKEN", xsrfToken())
                        .POST(HttpRequest.BodyPublishers.ofString("username=" + email + "&password=" + password))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> putProfileImage(byte[] content) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"photo.jpg\"\r\n"
                + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));

        return client.send(HttpRequest.newBuilder(uri("/api/v1/members/me/profile-image"))
                        .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                        .header("X-XSRF-TOKEN", xsrfToken())
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static byte[] smallJpeg() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB), "jpeg", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("실제 HTTP로 — 정상 업로드는 200이고, 10MB를 넘는 업로드는 연결이 끊기지 않고 413 + 해요체 문구로 응답한다.")
    void overLimitUpload_returns413WithKoreanMessage() throws Exception {
        login("limit@test.com", "rawPassword1!");

        HttpResponse<String> ok = putProfileImage(smallJpeg());
        assertThat(ok.statusCode()).isEqualTo(200);
        assertThat(ok.body()).contains("profileImageId");

        byte[] tooLarge = new byte[12 * 1024 * 1024];
        Arrays.fill(tooLarge, (byte) 1);
        HttpResponse<String> rejected = putProfileImage(tooLarge);

        assertThat(rejected.statusCode()).isEqualTo(413);
        assertThat(rejected.body()).contains("사진은 10MB까지 올릴 수 있어요.");
    }
}
