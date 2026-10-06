package com.fitbet.scheduler;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fitbet.penalty.PenaltyLog;
import com.fitbet.penalty.PenaltyLogRepository;
import com.fitbet.room.RoomMember;
import com.fitbet.room.RoomMemberRepository;
import com.fitbet.streak.Streak;
import com.fitbet.streak.StreakRepository;

import lombok.RequiredArgsConstructor;

/**
 * 방 하나의 하루를 마감한다. 트랜잭션 단위 = 방 1개.
 *
 * DailyClosingService와 다른 클래스(빈)로 분리한 이유:
 * 같은 클래스 안에서 this.close()로 부르면 Spring 프록시를 거치지 않아 @Transactional이 무시된다(self-invocation).
 */
@Component
@RequiredArgsConstructor
public class RoomDayCloser {

    private final RoomMemberRepository roomMemberRepository;
    private final PenaltyLogRepository penaltyLogRepository;
    private final StreakRepository streakRepository;

    /** @return 이번에 벌금을 부과한 인원 수 */
    @Transactional
    public int close(Long roomId, LocalDate targetDate) {
        List<RoomMember> missed = roomMemberRepository.findMissedMembers(roomId, targetDate);
        if (missed.isEmpty()) {
            return 0;
        }

        // 멤버별로 Streak을 하나씩 조회하면 N+1 → 방의 Streak을 한 번에 가져와 Map으로
        Map<Long, Streak> streakByUserId = streakRepository.findAllByRoomId(roomId).stream()
                .collect(Collectors.toMap(s -> s.getUser().getId(), Function.identity()));

        for (RoomMember member : missed) {
            penaltyLogRepository.save(PenaltyLog.of(member.getUser(), member.getRoom(), targetDate));
            Streak streak = streakByUserId.get(member.getUser().getId());
            if (streak != null) {
                streak.reset(); // 변경 감지 → 커밋 시 UPDATE
            }
        }
        return missed.size();
    }
}
