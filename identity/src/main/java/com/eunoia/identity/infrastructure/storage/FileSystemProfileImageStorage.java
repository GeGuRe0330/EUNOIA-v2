package com.eunoia.identity.infrastructure.storage;

import com.eunoia.identity.domain.ProfileImageStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

@Component
public class FileSystemProfileImageStorage implements ProfileImageStorage {

    private final Path baseDir;

    public FileSystemProfileImageStorage(@Value("${eunoia.profile-image.dir}") String dir) {
        this.baseDir = Path.of(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new UncheckedIOException("프로필 이미지 디렉터리를 만들 수 없습니다: " + baseDir, e);
        }
    }

    @Override
    public void save(UUID imageId, byte[] content) {
        Path target = resolve(imageId);
        Path temp = baseDir.resolve(imageId + ".tmp");

        try {
            Files.write(temp, content);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("프로필 이미지를 저장할 수 없습니다: " + imageId, e);
        }
    }

    @Override
    public Optional<byte[]> read(UUID imageId) {
        try {
            return Optional.of(Files.readAllBytes(resolve(imageId)));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("프로필 이미지를 읽을 수 없습니다: " + imageId, e);
        }
    }

    @Override
    public void delete(UUID imageId) {
        try {
            Files.deleteIfExists(resolve(imageId));
        } catch (IOException e) {
            throw new UncheckedIOException("프로필 이미지를 삭제할 수 없습니다: " + imageId, e);
        }
    }

    private Path resolve(UUID imageId) {
        Path path = baseDir.resolve(imageId + ".jpg").normalize();
        if (!path.getParent().equals(baseDir)) {
            throw new IllegalStateException("저장 디렉터리 밖의 경로입니다: " + path);
        }
        return path;
    }
}
