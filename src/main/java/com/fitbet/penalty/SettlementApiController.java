package com.fitbet.penalty;

import java.time.LocalDate;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fitbet.common.auth.LoginUserId;
import com.fitbet.penalty.SettlementService.SettleResult;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rooms/{roomId}/settlement")
@RequiredArgsConstructor
public class SettlementApiController {

    private final SettlementService settlementService;

    @GetMapping
    public SettlementResponse report(@LoginUserId Long userId, @PathVariable Long roomId) {
        return settlementService.getReport(userId, roomId);
    }

    /** 정산 완료 (HOST 전용). {"until": "2026-10-06"} — 리포트의 periodTo를 그대로 보낸다. */
    @PatchMapping
    public SettleResult settle(@LoginUserId Long userId, @PathVariable Long roomId,
                               @Valid @RequestBody SettleRequest request) {
        return settlementService.settle(userId, roomId, request.until());
    }

    public record SettleRequest(@NotNull LocalDate until) {
    }
}
