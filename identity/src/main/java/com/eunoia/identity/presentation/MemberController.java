package com.eunoia.identity.presentation;

import com.eunoia.common.security.AuthenticatedPrincipal;
import com.eunoia.identity.application.MemberService;
import com.eunoia.identity.presentation.dto.MemberPasswordChangeRequest;
import com.eunoia.identity.presentation.dto.MemberProfileUpdateRequest;
import com.eunoia.identity.presentation.dto.MemberResponse;
import com.eunoia.identity.presentation.dto.MemberSignupRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/members")
@Tag(name = "회원", description = "회원가입 관련 API")
public class MemberController {

    private final MemberService memberService;

    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "이메일/비밀번호/닉네임/나이/성별을 받아 신규 회원으로 등록한다.")
    public MemberResponse signup(@Valid @RequestBody MemberSignupRequest request) {
        return MemberResponse.from(memberService.register(request.toCommand()));
    }

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회", description = "로그인한 회원 자신의 정보를 조회한다.")
    public MemberResponse getMe(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return MemberResponse.from(memberService.getMe(principal.getMemberId()));
    }

    @PatchMapping("/me")
    @Operation(summary = "프로필 수정", description = "로그인한 회원의 닉네임·나이·성별을 한번에 바꾼다. (이메일은 제외)")
    public MemberResponse updateProfile(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @Valid @RequestBody MemberProfileUpdateRequest request
            ) {
        return MemberResponse.from(memberService.updateProfile(principal.getMemberId(), request.toCommand()));
    }

    @PutMapping("/me/password")
    @Operation(summary = "비밀번호 변경", description = "현재 비밀번호를 확인한 뒤 새 비밀번호로 바꾼다.(바꾼 후 세션은 유지)")
    public void changePassword(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @Valid @RequestBody MemberPasswordChangeRequest request
    ) {
        memberService.changePassword(principal.getMemberId(), request.toCommand());
    }
}
