package com.eunoia.identity.application;

import com.eunoia.common.exception.BusinessException;
import com.eunoia.identity.application.dto.MemberInfo;
import com.eunoia.identity.domain.Gender;
import com.eunoia.identity.domain.InvalidProfileImageException;
import com.eunoia.identity.domain.Member;
import com.eunoia.identity.domain.MemberRepository;
import com.eunoia.identity.domain.ProfileImageStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileImageServiceTest {

    private static final byte[] PROCESSED = {1, 2, 3};

    @Mock
    private MemberRepository memberRepository;

    private InMemoryStorage storage;
    private ProfileImageService profileImageService;
    private Member member;

    @BeforeEach
    void setUp() {
        storage = new InMemoryStorage();
        profileImageService = new ProfileImageService(memberRepository, original -> {
            if (original == null || original.length == 0) {
                throw new InvalidProfileImageException("빈 파일입니다.");
            }
            return PROCESSED;
        }, storage);
        member = Member.register("a@test.com", "encoded", "개구리", 27, Gender.NONE);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    @DisplayName("업로드하면 처리된 이미지를 새 키로 저장하고 회원의 키를 바꾼다.")
    void upload_savesProcessedImageAndChangesKey() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        MemberInfo result = profileImageService.upload(1L, new byte[]{9});

        UUID newId = result.profileImageId();
        assertThat(newId).isNotNull();
        assertThat(member.getProfileImageId()).isEqualTo(newId);
        assertThat(storage.files.get(newId)).containsExactly(PROCESSED);
    }

    @Test
    @DisplayName("교체 후 커밋되면 옛 파일만 지운다.")
    void upload_onCommit_deletesOldFile() {
        UUID oldId = UUID.randomUUID();
        storage.files.put(oldId, new byte[]{7});
        member.changeProfileImage(oldId);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        UUID newId = profileImageService.upload(1L, new byte[]{9}).profileImageId();
        complete(TransactionSynchronization.STATUS_COMMITTED);

        assertThat(storage.files).containsOnlyKeys(newId);
    }

    @Test
    @DisplayName("교체 중 롤백되면 방금 쓴 새 파일을 지우고 옛 파일은 남긴다.")
    void upload_onRollback_deletesNewFile() {
        UUID oldId = UUID.randomUUID();
        storage.files.put(oldId, new byte[]{7});
        member.changeProfileImage(oldId);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        profileImageService.upload(1L, new byte[]{9});
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(storage.files).containsOnlyKeys(oldId);
    }

    @Test
    @DisplayName("쓸 수 없는 이미지는 400 \"올릴 수 없는 사진이에요.\"이고 아무것도 저장하지 않는다.")
    void upload_withInvalidImage_throws400() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> profileImageService.upload(1L, new byte[0]))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).isEqualTo("올릴 수 없는 사진이에요.");
                });
        assertThat(storage.files).isEmpty();
        assertThat(member.getProfileImageId()).isNull();
    }

    @Test
    @DisplayName("삭제하면 키를 비우고 커밋 후 파일을 지운다. 이미지가 없어도 성공한다(멱등).")
    void remove_clearsKeyAndDeletesFileAfterCommit() {
        UUID oldId = UUID.randomUUID();
        storage.files.put(oldId, new byte[]{7});
        member.changeProfileImage(oldId);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        MemberInfo result = profileImageService.remove(1L);
        assertThat(result.profileImageId()).isNull();
        assertThat(storage.files).containsKey(oldId);
        complete(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(storage.files).isEmpty();

        assertThat(profileImageService.remove(1L).profileImageId()).isNull();
    }

    @Test
    @DisplayName("현재 키로 조회하면 이미지를 주고, 남의 키·형식이 틀린 키·파일이 없는 경우는 404다.")
    void read_onlyCurrentImage() {
        UUID currentId = UUID.randomUUID();
        storage.files.put(currentId, PROCESSED);
        member.changeProfileImage(currentId);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        assertThat(profileImageService.read(1L, currentId.toString())).containsExactly(PROCESSED);
        assertNotFound(() -> profileImageService.read(1L, UUID.randomUUID().toString()));
        assertNotFound(() -> profileImageService.read(1L, "not-a-uuid"));

        storage.files.clear();
        assertNotFound(() -> profileImageService.read(1L, currentId.toString()));
    }

    private void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private void complete(int status) {
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(status));
    }

    private static class InMemoryStorage implements ProfileImageStorage {
        private final Map<UUID, byte[]> files = new HashMap<>();

        @Override
        public void save(UUID imageId, byte[] content) {
            files.put(imageId, content);
        }

        @Override
        public Optional<byte[]> read(UUID imageId) {
            return Optional.ofNullable(files.get(imageId));
        }

        @Override
        public void delete(UUID imageId) {
            files.remove(imageId);
        }
    }
}
