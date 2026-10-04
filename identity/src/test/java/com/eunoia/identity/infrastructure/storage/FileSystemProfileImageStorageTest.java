package com.eunoia.identity.infrastructure.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FileSystemProfileImageStorageTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("저장한 바이트를 그대로 읽고, 지우면 더 이상 읽히지 않는다.")
    void saveReadDelete() {
        FileSystemProfileImageStorage storage = new FileSystemProfileImageStorage(tempDir.toString());
        UUID id = UUID.randomUUID();

        storage.save(id, new byte[]{1, 2, 3});
        assertThat(storage.read(id)).hasValueSatisfying(bytes -> assertThat(bytes).containsExactly(1, 2, 3));
        assertThat(tempDir.resolve(id + ".jpg")).exists();
        assertThat(tempDir.resolve(id + ".tmp")).doesNotExist(); // 임시 파일이 남지 않음

        storage.delete(id);
        assertThat(storage.read(id)).isEmpty();
    }

    @Test
    @DisplayName("같은 키로 다시 저장하면 내용이 교체된다.")
    void save_overwritesSameKey() {
        FileSystemProfileImageStorage storage = new FileSystemProfileImageStorage(tempDir.toString());
        UUID id = UUID.randomUUID();

        storage.save(id, new byte[]{1});
        storage.save(id, new byte[]{2, 2});

        assertThat(storage.read(id)).hasValueSatisfying(bytes -> assertThat(bytes).containsExactly(2, 2));
    }

    @Test
    @DisplayName("없는 이미지를 읽으면 빈 결과이고, 없는 이미지를 지워도 오류가 아니다(멱등).")
    void readAndDelete_missingImage() {
        FileSystemProfileImageStorage storage = new FileSystemProfileImageStorage(tempDir.toString());
        UUID id = UUID.randomUUID();

        assertThat(storage.read(id)).isEmpty();
        storage.delete(id); // 예외 없음
    }

    @Test
    @DisplayName("저장 디렉터리가 없으면 만든다.")
    void createsDirectory() {
        Path nested = tempDir.resolve("a/b/profile-images");

        new FileSystemProfileImageStorage(nested.toString());

        assertThat(nested).isDirectory();
    }
}
