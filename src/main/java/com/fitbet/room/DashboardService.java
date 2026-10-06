package com.fitbet.room;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitbet.challenge.ChallengeLog;
import com.fitbet.challenge.ChallengeLogRepository;
import com.fitbet.challenge.ReactionRepository;
import com.fitbet.challenge.ReactionType;
import com.fitbet.challenge.ReactionView;
import com.fitbet.common.exception.BusinessException;
import com.fitbet.common.exception.ErrorCode;
import com.fitbet.penalty.PenaltyLogRepository;
import com.fitbet.room.DashboardResponse.FeedItem;
import com.fitbet.room.DashboardResponse.Me;
import com.fitbet.room.DashboardResponse.MemberStatus;
import com.fitbet.room.DashboardResponse.RoomInfo;
import com.fitbet.streak.Streak;
import com.fitbet.streak.StreakRepository;

import lombok.RequiredArgsConstructor;

/**
 * 대시보드 조회. 멤버가 3명이든 30명이든 쿼리 수는 고정이다 (PRD 8: N+1 방지).
 *   ① 멤버+닉네임  ② 오늘 피드+작성자  ③ 방 Streak 전체  ④ 피드 리액션(IN 절)  ⑤ 벌금 풀 SUM
 * 각각을 "한 번에" 가져온 뒤, 조립은 메모리(Map)에서 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 조회 전용: 변경 감지용 스냅샷을 만들지 않아 가볍고, 실수로 수정해도 반영되지 않는다
public class DashboardService {

    private final RoomMemberRepository roomMemberRepository;
    private final ChallengeLogRepository challengeLogRepository;
    private final StreakRepository streakRepository;
    private final ReactionRepository reactionRepository;
    private final PenaltyLogRepository penaltyLogRepository;
    private final Clock clock;

    public DashboardResponse getDashboard(Long userId, Long roomId) {
        LocalDate today = LocalDate.now(clock);

        // ① 멤버 목록 — 이 안에 내가 없으면 방 멤버가 아니다 (멤버십 체크를 별도 쿼리 없이)
        List<RoomMember> members = roomMemberRepository.findAllWithUserByRoomId(roomId);
        RoomMember myMembership = members.stream()
                .filter(m -> m.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_ROOM_MEMBER));
        Room room = myMembership.getRoom(); // ①에서 fetch join 했으므로 추가 쿼리 없음

        // ② 오늘 피드, ③ Streak
        List<ChallengeLog> feedLogs = challengeLogRepository.findFeed(roomId, today);
        Map<Long, Streak> streakByUserId = streakRepository.findAllByRoomId(roomId).stream()
                .collect(Collectors.toMap(s -> s.getUser().getId(), Function.identity()));

        // ④ 리액션 — 피드가 비어 있으면 쿼리 자체를 생략
        List<Long> logIds = feedLogs.stream().map(ChallengeLog::getId).toList();
        Map<Long, List<ReactionView>> reactionsByLogId = logIds.isEmpty()
                ? Map.of()
                : reactionRepository.findViewsByLogIds(logIds).stream()
                        .collect(Collectors.groupingBy(ReactionView::logId));

        Set<Long> verifiedUserIds = feedLogs.stream()
                .map(cl -> cl.getUser().getId())
                .collect(Collectors.toSet());

        List<MemberStatus> missed = members.stream()
                .filter(m -> !verifiedUserIds.contains(m.getUser().getId()))
                .map(m -> new MemberStatus(
                        m.getUser().getId(),
                        m.getUser().getUsername(),
                        currentStreakOf(streakByUserId, m.getUser().getId()),
                        m.getJoinedAt().toLocalDate().equals(today)))
                .toList();

        List<FeedItem> feed = feedLogs.stream()
                .map(cl -> toFeedItem(cl, userId, streakByUserId, reactionsByLogId))
                .toList();

        Streak myStreak = streakByUserId.get(userId);
        Me me = new Me(userId, myMembership.getUser().getUsername(), myMembership.getRole(),
                verifiedUserIds.contains(userId),
                myStreak == null ? 0 : myStreak.getCurrentStreak(),
                myStreak == null ? 0 : myStreak.getMaxStreak());

        RoomInfo roomInfo = new RoomInfo(room.getId(), room.getTitle(), room.getPenaltyAmount(),
                room.getInviteCode(), members.size());

        // PRD 2.1: 마감은 deadlineTime(23:59)의 59초까지
        OffsetDateTime deadline = today.atTime(room.getDeadlineTime().withSecond(59))
                .atZone(clock.getZone()).toOffsetDateTime();

        return new DashboardResponse(roomInfo, me, today, deadline, OffsetDateTime.now(clock),
                penaltyLogRepository.sumUnsettledAmount(roomId), // ⑤
                missed, feed);
    }

    private FeedItem toFeedItem(ChallengeLog cl, Long myId, Map<Long, Streak> streakByUserId,
                                Map<Long, List<ReactionView>> reactionsByLogId) {
        List<ReactionView> reactions = reactionsByLogId.getOrDefault(cl.getId(), List.of());
        long approve = reactions.stream().filter(r -> r.type() == ReactionType.APPROVE).count();
        long doubt = reactions.size() - approve;
        Long authorId = cl.getUser().getId();
        return new FeedItem(cl.getId(), authorId, cl.getUser().getUsername(), cl.getPhotoUrl(), cl.getMemo(),
                cl.getCreatedAt(), currentStreakOf(streakByUserId, authorId),
                approve, doubt, authorId.equals(myId));
    }

    private int currentStreakOf(Map<Long, Streak> streakByUserId, Long userId) {
        Streak streak = streakByUserId.get(userId);
        return streak == null ? 0 : streak.getCurrentStreak();
    }
}
