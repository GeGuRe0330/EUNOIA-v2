package com.eunoia.identity.application;

import com.eunoia.common.exception.BusinessException;
import com.eunoia.identity.application.dto.MemberInfo;
import com.eunoia.identity.domain.InvalidProfileImageException;
import com.eunoia.identity.domain.Member;
import com.eunoia.identity.domain.MemberRepository;
import com.eunoia.identity.domain.ProfileImageProcessor;
import com.eunoia.identity.domain.ProfileImageStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileImageService {

    private final MemberRepository memberRepository;
    private final ProfileImageProcessor profileImageProcessor;
    private final ProfileImageStorage profileImageStorage;

    @Transactional
    public MemberInfo upload(Long memberId, byte[] original) {
        Member member = getMember(memberId);
        byte[] jpeg = toProfileJpeg(original);

        UUID oldImageId = member.getProfileImageId();
        UUID newImageId = UUID.randomUUID();
        profileImageStorage.save(newImageId, jpeg);
        afterCompletion(committed -> deleteQuietly(committed ? oldImageId : newImageId));

        member.changeProfileImage(newImageId);
        return MemberInfo.from(member);
    }

    @Transactional
    public MemberInfo remove(Long memberId) {
        Member member = getMember(memberId);
        UUID oldImageId = member.getProfileImageId();

        member.removeProfileImage();
        afterCompletion(committed -> {
            if (committed) {
                deleteQuietly(oldImageId);
            }
        });
        return MemberInfo.from(member);
    }

    @Transactional(readOnly = true)
    public byte[] read(Long memberId, String imageId) {
        UUID requested = parseOrNotFound(imageId);
        Member member = getMember(memberId);
        if (!requested.equals(member.getProfileImageId())) {
            throw notFound();
        }
        return profileImageStorage.read(requested).orElseThrow(this::notFound);
    }

    private byte[] toProfileJpeg(byte[] original) {
        try {
            return profileImageProcessor.toProfileJpeg(original);
        } catch (InvalidProfileImageException e) {
            log.debug("[400] 프로필 이미지 거절: {}", e.getMessage());
            throw new BusinessException(HttpStatus.BAD_REQUEST, "올릴 수 없는 사진이에요.");
        }
    }

    private void afterCompletion(Consumer<Boolean> action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                action.accept(status == STATUS_COMMITTED);
            }
        });
    }

    private void deleteQuietly(UUID imageId) {
        if (imageId == null) {
            return;
        }
        try {
            profileImageStorage.delete(imageId);
        } catch (RuntimeException e) {
            log.warn("프로필 이미지 파일 삭제 실패(고아 파일로 남음): {}", imageId, e);
        }
    }

    private UUID parseOrNotFound(String imageId) {
        try {
            return UUID.fromString(imageId);
        } catch (IllegalArgumentException e) {
            throw notFound();
        }
    }

    private BusinessException notFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "프로필 이미지를 찾을 수 없어요.");
    }

    private Member getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 회원이에요."));
    }
}
