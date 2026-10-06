package com.fitbet.room;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import com.fitbet.challenge.ReactionType;

/** GET /api/rooms/{roomId}/dashboard 응답 (PRD 6.1 홈 대시보드에 필요한 데이터 전부) */
public record DashboardResponse(
        RoomInfo room,
        Me me,
        LocalDate today,
        OffsetDateTime deadline,   // 마감 타이머 목표 시각
        OffsetDateTime serverNow,  // 브라우저 시계가 틀려도 서버 기준으로 타이머를 맞추기 위해
        long penaltyPool,
        List<MemberStatus> missedMembers,
        List<FeedItem> feed
) {

    public record RoomInfo(Long roomId, String title, int penaltyAmount, String inviteCode, int memberCount) {
    }

    public record Me(Long userId, String username, RoomRole role, boolean verifiedToday,
                     int currentStreak, int maxStreak) {
    }

    /** joinedToday = 오늘 참여한 멤버는 오늘 벌금 면제 (PRD 2.6 MVP 제안: 참여 다음 날부터 적용) */
    public record MemberStatus(Long userId, String username, int currentStreak, boolean joinedToday) {
    }

    public record FeedItem(Long logId, Long userId, String username, String photoUrl, String memo,
                           LocalDateTime createdAt, int currentStreak,
                           long approveCount, long doubtCount, boolean mine,
                           ReactionType myReaction) { // 내가 남긴 리액션 (없으면 null)
    }
}
