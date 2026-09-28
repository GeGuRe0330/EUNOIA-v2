package com.eunoia.journal.presentation;

import com.eunoia.common.security.AuthenticatedPrincipal;
import com.eunoia.journal.application.EmotionEntryService;
import com.eunoia.journal.presentation.dto.EmotionEntryResponse;
import com.eunoia.journal.presentation.dto.EmotionEntryWriteRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/emotion-entries")
@Tag(name = "감정일기", description = "감정일기 관련 API")
public class EmotionEntryController {

    private final EmotionEntryService emotionEntryService;

    @PostMapping
    @Operation(summary = "감정일기 작성", description = "로그인한 회원 명의로 감정일기를 작성한다.")
    public EmotionEntryResponse write(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @Valid @RequestBody EmotionEntryWriteRequest request
    ) {
        return EmotionEntryResponse.from(emotionEntryService.write(request.toCommand(principal.getMemberId())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "감정일기 단건 조회", description = "본인이 작성한 감정일기를 단건 조회한다.")
    public EmotionEntryResponse getById(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable Long id
    ) {
        return EmotionEntryResponse.from(emotionEntryService.getById(id, principal.getMemberId()));
    }

    @GetMapping
    @Operation(summary = "감정일기 목록 조회", description = "본인이 작성한 감정일기 목록을 최신순으로 조회한다.")
    public List<EmotionEntryResponse> getMyEntries(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return emotionEntryService.getMyEntries(principal.getMemberId()).stream()
                .map(EmotionEntryResponse::from)
                .toList();
    }
}
