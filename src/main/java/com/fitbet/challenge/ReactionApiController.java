package com.fitbet.challenge;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fitbet.challenge.ReactionService.ReactionResult;
import com.fitbet.common.auth.LoginUserId;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ReactionApiController {

    private final ReactionService reactionService;

    /** PRD 7: 인정/의심 리액션 등록·변경. {"type": "APPROVE" | "DOUBT"} */
    @PostMapping("/api/challenges/{logId}/reactions")
    public ReactionResult react(@LoginUserId Long userId,
                                @PathVariable Long logId,
                                @Valid @RequestBody ReactRequest request) {
        return reactionService.react(userId, logId, request.type());
    }

    public record ReactRequest(@NotNull ReactionType type) {
    }
}
