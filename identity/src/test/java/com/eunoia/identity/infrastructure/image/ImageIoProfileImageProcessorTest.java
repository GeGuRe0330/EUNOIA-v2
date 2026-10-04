package com.eunoia.identity.infrastructure.image;

import com.eunoia.identity.domain.InvalidProfileImageException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageIoProfileImageProcessorTest {

    private final ImageIoProfileImageProcessor processor = new ImageIoProfileImageProcessor();

    @Test
    @DisplayName("JPEG를 넣으면 512×512 JPEG가 나온다.")
    void jpeg_isNormalizedTo512Jpeg() throws IOException {
        byte[] result = processor.toProfileJpeg(encode(solid(800, 800, Color.BLUE), "jpeg"));

        BufferedImage image = decode(result);
        assertThat(image.getWidth()).isEqualTo(512);
        assertThat(image.getHeight()).isEqualTo(512);
        assertThat(isJpeg(result)).isTrue();
    }

    @Test
    @DisplayName("PNG도 받아서 JPEG로 바꾸고, 원본보다 작아도 512×512로 맞춘다.")
    void smallPng_isNormalizedTo512Jpeg() throws IOException {
        byte[] result = processor.toProfileJpeg(encode(solid(100, 100, Color.RED), "png"));

        BufferedImage image = decode(result);
        assertThat(image.getWidth()).isEqualTo(512);
        assertThat(image.getHeight()).isEqualTo(512);
        assertThat(isJpeg(result)).isTrue();
    }

    @Test
    @DisplayName("가로로 긴 이미지는 가운데 정사각형만 남는다(양옆이 잘린다).")
    void landscape_isCenterCropped() throws IOException {
        // 300×100: 왼쪽 100px 빨강 | 가운데 100px 초록 | 오른쪽 100px 파랑 → 가운데(초록)만 남아야 함
        BufferedImage source = new BufferedImage(300, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = source.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 100, 100);
        g.setColor(Color.GREEN);
        g.fillRect(100, 0, 100, 100);
        g.setColor(Color.BLUE);
        g.fillRect(200, 0, 100, 100);
        g.dispose();

        BufferedImage image = decode(processor.toProfileJpeg(encode(source, "png")));

        assertDominant(image, 20, 256, Color.GREEN);
        assertDominant(image, 491, 256, Color.GREEN);
    }

    @Test
    @DisplayName("세로로 긴 이미지는 가운데 정사각형만 남는다(위아래가 잘린다).")
    void portrait_isCenterCropped() throws IOException {
        BufferedImage source = new BufferedImage(100, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = source.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 100, 100);
        g.setColor(Color.GREEN);
        g.fillRect(0, 100, 100, 100);
        g.setColor(Color.BLUE);
        g.fillRect(0, 200, 100, 100);
        g.dispose();

        BufferedImage image = decode(processor.toProfileJpeg(encode(source, "png")));

        assertDominant(image, 256, 20, Color.GREEN);
        assertDominant(image, 256, 491, Color.GREEN);
    }

    @Test
    @DisplayName("PNG의 투명한 부분은 흰 배경이 된다.")
    void transparentPng_becomesWhite() throws IOException {
        BufferedImage transparent = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB); // 전부 투명

        BufferedImage image = decode(processor.toProfileJpeg(encode(transparent, "png")));

        Color center = new Color(image.getRGB(256, 256));
        assertThat(center.getRed()).isGreaterThan(240);
        assertThat(center.getGreen()).isGreaterThan(240);
        assertThat(center.getBlue()).isGreaterThan(240);
    }

    @Test
    @DisplayName("GIF는 ImageIO가 읽을 수 있어도 거절한다.")
    void gif_isRejected() throws IOException {
        byte[] gif = encode(solid(50, 50, Color.BLUE), "gif");

        assertThatThrownBy(() -> processor.toProfileJpeg(gif)).isInstanceOf(InvalidProfileImageException.class);
    }

    @Test
    @DisplayName("확장자만 이미지인 텍스트(위장 파일)·빈 파일·null은 거절한다.")
    void nonImage_isRejected() {
        byte[] text = "<svg xmlns='http://www.w3.org/2000/svg'></svg>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> processor.toProfileJpeg(text)).isInstanceOf(InvalidProfileImageException.class);
        assertThatThrownBy(() -> processor.toProfileJpeg(new byte[0])).isInstanceOf(InvalidProfileImageException.class);
        assertThatThrownBy(() -> processor.toProfileJpeg(null)).isInstanceOf(InvalidProfileImageException.class);
    }

    @Test
    @DisplayName("시그니처만 JPEG이고 내용이 깨진 파일은 거절한다.")
    void brokenJpeg_isRejected() throws IOException {
        byte[] jpeg = encode(solid(100, 100, Color.BLUE), "jpeg");
        byte[] broken = Arrays.copyOf(jpeg, 20); // 헤더 일부만 남김

        assertThatThrownBy(() -> processor.toProfileJpeg(broken)).isInstanceOf(InvalidProfileImageException.class);
    }

    @Test
    @DisplayName("가로나 세로가 상한(4096px)을 넘으면 디코드 전에 거절한다.")
    void tooLarge_isRejected() throws IOException {
        // 4097×1 — 픽셀 수는 작아도 한 변이 상한을 넘음
        byte[] wide = encode(solid(ImageIoProfileImageProcessor.MAX_SOURCE_DIMENSION + 1, 1, Color.BLUE), "png");

        assertThatThrownBy(() -> processor.toProfileJpeg(wide))
                .isInstanceOf(InvalidProfileImageException.class)
                .hasMessageContaining("너무 큽니다");
    }

    @Test
    @DisplayName("상한(4096px) 크기 그대로는 받는다.")
    void atLimit_isAccepted() throws IOException {
        byte[] atLimit = encode(solid(ImageIoProfileImageProcessor.MAX_SOURCE_DIMENSION, 1, Color.BLUE), "png");

        assertThat(decode(processor.toProfileJpeg(atLimit)).getWidth()).isEqualTo(512);
    }

    private static BufferedImage solid(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, out)).isTrue();
        return out.toByteArray();
    }

    private static BufferedImage decode(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    private static boolean isJpeg(byte[] bytes) {
        return bytes.length > 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
    }

    // JPEG 손실 압축이라 정확한 색 대신 "그 색 채널이 가장 강한지"로 확인
    private static void assertDominant(BufferedImage image, int x, int y, Color expected) {
        Color actual = new Color(image.getRGB(x, y));
        if (expected.equals(Color.GREEN)) {
            assertThat(actual.getGreen()).isGreaterThan(actual.getRed() + 100).isGreaterThan(actual.getBlue() + 100);
        } else {
            throw new IllegalArgumentException("테스트에서 쓰지 않는 색: " + expected);
        }
    }
}
