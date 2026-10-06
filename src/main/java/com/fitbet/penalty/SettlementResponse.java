package com.fitbet.penalty;

import java.time.LocalDate;
import java.util.List;

import com.fitbet.penalty.SettlementFormatter.RankedPenalty;

/** GET /api/rooms/{roomId}/settlement 응답 (PRD 6.3 정산 탭) */
public record SettlementResponse(
        Long roomId,
        String title,
        String hostName,
        boolean meHost,
        LocalDate periodFrom,  // 미정산 벌금의 첫 날짜 (없으면 null)
        LocalDate periodTo,    // 미정산 벌금의 마지막 날짜 → 정산 완료 요청의 until로 그대로 사용
        long totalPool,
        List<RankedPenalty> penaltyRanking,
        List<StreakRank> streakRanking,
        String guideText
) {

    public record StreakRank(Long userId, String username, int currentStreak, int maxStreak) {
    }
}
