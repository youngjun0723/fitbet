package com.fitbet.challenge;

import java.time.LocalDate;

import com.fitbet.common.entity.BaseTimeEntity;
import com.fitbet.room.Room;
import com.fitbet.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "challenge_log",
        // 하루 1인증의 최후 방어선: 동시 요청이 앱 체크를 통과해도 DB가 막는다
        uniqueConstraints = @UniqueConstraint(name = "uk_challenge_user_room_date",
                columnNames = {"user_id", "room_id", "log_date"}),
        // 대시보드 "이 방의 오늘 피드" 조회용 (room_id가 선두 컬럼이어야 이 조건에서 인덱스를 탄다)
        indexes = @Index(name = "idx_challenge_room_date", columnList = "room_id, log_date"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChallengeLog extends BaseTimeEntity {

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
    private LocalDate logDate;

    @Column(nullable = false, length = 255)
    private String photoUrl;

    @Column(length = 100)
    private String memo;

    private ChallengeLog(User user, Room room, LocalDate logDate, String photoUrl, String memo) {
        this.user = user;
        this.room = room;
        this.logDate = logDate;
        this.photoUrl = photoUrl;
        this.memo = memo;
    }

    public static ChallengeLog of(User user, Room room, LocalDate logDate, String photoUrl, String memo) {
        return new ChallengeLog(user, room, logDate, photoUrl, memo);
    }
}
