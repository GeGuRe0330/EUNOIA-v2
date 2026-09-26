package com.eunoia.identity.application;

import com.eunoia.common.exception.BusinessException;
import com.eunoia.identity.application.dto.PendingMemberInfo;
import com.eunoia.identity.domain.Member;
import com.eunoia.identity.domain.MemberRepository;
import com.eunoia.identity.domain.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminMemberService {

    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public List<PendingMemberInfo> getPendingMembers() {
        return memberRepository.findByStatus(Status.PENDING).stream()
                .map(PendingMemberInfo::from)
                .toList();
    }

    @Transactional
    public void approve(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 회원이에요."));

        try {
            member.approve();
        } catch (IllegalStateException e) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 승인된 회원이에요.");
        }
    }
}
