package com.eunoia.identity.infrastructure.image;

import com.eunoia.identity.domain.InvalidProfileImageException;
import com.eunoia.identity.domain.ProfileImageProcessor;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

@Component
public class ImageIoProfileImageProcessor implements ProfileImageProcessor {

    static final int OUTPUT_SIZE = 512;
    static final int MAX_SOURCE_DIMENSION = 4096; // 압축 폭탄 방지 — 디코드 전에 헤더로 확인
    private static final float JPEG_QUALITY = 0.9f;

    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    @Override
    public byte[] toProfileJpeg(byte[] original) {
        if (original == null || original.length == 0) {
            throw new InvalidProfileImageException("빈 파일입니다.");
        }
        // ImageIO는 GIF·BMP 등도 읽으므로, 디코드를 맡기기 전에 실제 내용(시그니처)으로 JPEG·PNG만 통과
        if (!startsWith(original, JPEG_SIGNATURE) && !startsWith(original, PNG_SIGNATURE)) {
            throw new InvalidProfileImageException("JPEG·PNG가 아닙니다.");
        }
        BufferedImage source = decodeWithLimit(original);
        return encodeJpeg(cropCenterAndResize(source));
    }

    private BufferedImage decodeWithLimit(byte[] original) {
        try(ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new InvalidProfileImageException("읽을 수 없는 이미지입니다.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width > MAX_SOURCE_DIMENSION ||  height > MAX_SOURCE_DIMENSION) {
                    throw new InvalidProfileImageException("이미지가 너무 큽니다: " + width + "x" + height);
                }

                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new InvalidProfileImageException("깨진 이미지입니다.", e);
        }
    }

    // 가운데 정사각형을 잘라 OUTPUT_SIZE로 다시 그린다 — 새 이미지에 그리므로 원본의 메타데이터(EXIF)는 따라오지 않는다
    private BufferedImage cropCenterAndResize(BufferedImage source) {
        int side = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - side) / 2;
        int y = (source.getHeight() - side) / 2;

        BufferedImage target = new BufferedImage(OUTPUT_SIZE, OUTPUT_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setColor(Color.WHITE); // PNG 투명 영역 → 흰 배경(JPEG엔 알파가 없음, 프론트 캔버스와 같은 결과)
            g.fillRect(0, 0, OUTPUT_SIZE, OUTPUT_SIZE);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, OUTPUT_SIZE, OUTPUT_SIZE, x, y, x + side, y + side, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private byte[] encodeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), param); // 메타데이터 null → EXIF 없음
        } catch (IOException e) {
            throw new IllegalStateException("JPEG 인코딩에 실패했습니다.", e); // 사용자 파일 문제가 아닌 서버 문제 — 500
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private boolean startsWith(byte[] data, byte[] signature) {
        return data.length >= signature.length
                && Arrays.equals(Arrays.copyOf(data, signature.length), signature);
    }
}
