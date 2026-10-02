package com.fitbet.streak;

import java.time.LocalDate;

import com.fitbet.room.Room;
import com.fitbet.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 멤버당 방별 1행. 방 참여 시점에 0으로 미리 만들어 두고, 인증/마감 때는 갱신만 한다. */
@Getter
@Entity
@Table(name = "streak",
        uniqueConstraints = @UniqueConstraint(name = "uk_streak_user_room", columnNames = {"user_id", "room_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Streak {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false)
    private int currentStreak;

    @Column(nullable = false)
    private int maxStreak;

    private LocalDate lastVerifiedDate;

    private Streak(User user, Room room) {
        this.user = user;
        this.room = room;
    }

    public static Streak start(User user, Room room) {
        return new Streak(user, room);
    }

    /**
     * PRD 5.1-5: 인증 성공 시 Streak 갱신.
     * 어제 인증했으면 이어서 +1, 아니면(최초/공백) 1부터 다시. maxStreak은 최고 기록만 갱신.
     * 규칙을 서비스가 아닌 엔티티 안에 두면 DB/Spring 없이 순수 자바 단위 테스트가 가능하다.
     */
    public void recordVerification(LocalDate today) {
        if (today.equals(lastVerifiedDate)) {
            return; // 같은 날 두 번 반영되지 않게 (1일 1인증은 DB가 막지만 도메인도 안전하게)
        }
        boolean continued = today.minusDays(1).equals(lastVerifiedDate);
        currentStreak = continued ? currentStreak + 1 : 1;
        maxStreak = Math.max(maxStreak, currentStreak);
        lastVerifiedDate = today;
    }

    /** PRD 2.3: 하루라도 실패하면 즉시 0. maxStreak은 유지. (M3 마감 스케줄러에서 사용) */
    public void reset() {
        currentStreak = 0;
    }
}
