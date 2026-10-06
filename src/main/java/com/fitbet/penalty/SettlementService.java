package com.fitbet.penalty;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitbet.common.exception.BusinessException;
import com.fitbet.common.exception.ErrorCode;
import com.fitbet.penalty.SettlementFormatter.RankedPenalty;
import com.fitbet.penalty.SettlementResponse.StreakRank;
import com.fitbet.room.RoomMember;
import com.fitbet.room.RoomMemberRepository;
import com.fitbet.room.RoomRole;
import com.fitbet.streak.Streak;
import com.fitbet.streak.StreakRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SettlementService {

    private final RoomMemberRepository roomMemberRepository;
    private final PenaltyLogRepository penaltyLogRepository;
    private final StreakRepository streakRepository;

    /** PRD 5.3 정산 리포트. 쿼리 3번(멤버, 벌금 집계, Streak) — 멤버 수와 무관. */
    @Transactional(readOnly = true)
    public SettlementResponse getReport(Long userId, Long roomId) {
        List<RoomMember> members = roomMemberRepository.findAllWithUserByRoomId(roomId);
        RoomMember me = members.stream()
                .filter(m -> m.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_ROOM_MEMBER));
        RoomMember host = members.stream()
                .filter(m -> m.getRole() == RoomRole.HOST)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("방장이 없는 방: " + roomId));

        List<PenaltySummary> summaries = penaltyLogRepository.summarizeUnsettled(roomId);
        List<RankedPenalty> ranked = SettlementFormatter.rank(summaries, host.getUser().getId());
        LocalDate from = summaries.stream().map(PenaltySummary::firstDate).min(Comparator.naturalOrder()).orElse(null);
        LocalDate to = summaries.stream().map(PenaltySummary::lastDate).max(Comparator.naturalOrder()).orElse(null);
        long pool = ranked.stream().mapToLong(RankedPenalty::total).sum();

        String title = me.getRoom().getTitle();
        String hostName = host.getUser().getUsername();
        return new SettlementResponse(roomId, title, hostName, me.getRole() == RoomRole.HOST,
                from, to, pool, ranked, streakRanking(members, roomId),
                SettlementFormatter.guideText(title, hostName, from, to, ranked));
    }

    /**
     * 정산 완료 (HOST 전용). until = 방장이 화면에서 본 리포트의 마지막 날짜.
     * 리포트를 보고 버튼을 누르는 사이에 스케줄러가 새 벌금을 만들 수 있는데,
     * until까지만 정산하면 "보지도 못한 벌금"이 정산되는 일을 막을 수 있다.
     */
    @Transactional
    public SettleResult settle(Long userId, Long roomId, LocalDate until) {
        RoomMember me = roomMemberRepository.findWithUserAndRoom(userId, roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_ROOM_MEMBER));
        if (me.getRole() != RoomRole.HOST) {
            throw new BusinessException(ErrorCode.HOST_ONLY);
        }
        int settledCount = penaltyLogRepository.settleUntil(roomId, until);
        return new SettleResult(settledCount, penaltyLogRepository.sumUnsettledAmount(roomId));
    }

    private List<StreakRank> streakRanking(List<RoomMember> members, Long roomId) {
        Map<Long, Streak> streakByUserId = streakRepository.findAllByRoomId(roomId).stream()
                .collect(Collectors.toMap(s -> s.getUser().getId(), Function.identity()));
        return members.stream()
                .map(m -> {
                    Streak s = streakByUserId.get(m.getUser().getId());
                    return new StreakRank(m.getUser().getId(), m.getUser().getUsername(),
                            s == null ? 0 : s.getCurrentStreak(), s == null ? 0 : s.getMaxStreak());
                })
                .sorted(Comparator.comparingInt(StreakRank::currentStreak).reversed()
                        .thenComparing(Comparator.comparingInt(StreakRank::maxStreak).reversed())
                        .thenComparing(StreakRank::username, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public record SettleResult(int settledCount, long remainingPool) {
    }
}
