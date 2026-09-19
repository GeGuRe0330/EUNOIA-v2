package com.eunoia.identity.presentation;

import com.eunoia.identity.application.AdminMemberService;
import com.eunoia.identity.presentation.dto.PendingMemberResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/members")
@Tag(name = "관리자-회원", description = "회원 승인 관련 관리자 API")
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    @GetMapping("/pending")
    @Operation(summary = "승인대기 회원 목록 조회", description = "상태가 PENDING인 회원 목록을 조회한다.")
    public List<PendingMemberResponse> pendingMembers() {
        return adminMemberService.getPendingMembers().stream()
                .map(PendingMemberResponse::from)
                .toList();
    }

    @PatchMapping("/{memberId}/approve")
    @Operation(summary = "회원 승인", description = "PENDING 상태 회원을 ACTIVE로 전환한다.")
    public void approve(@PathVariable Long memberId) {
        adminMemberService.approve(memberId);
    }
}
