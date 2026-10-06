package com.fitbet.penalty;

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
@Table(name = "penalty_log",
        // 스케줄러가 두 번 돌아도 같은 날 벌금은 1건만 (멱등성)
        uniqueConstraints = @UniqueConstraint(name = "uk_penalty_user_room_date",
                columnNames = {"user_id", "room_id", "target_date"}),
        // 정산 쿼리: WHERE room_id = ? AND is_settled = false
        indexes = @Index(name = "idx_penalty_room_settled", columnList = "room_id, is_settled"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PenaltyLog extends BaseTimeEntity {

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
    private LocalDate targetDate;

    /** 부과 시점 Room.penaltyAmount 스냅샷. 방 설정이 나중에 바뀌어도 과거 벌금은 그대로. */
    @Column(nullable = false)
    private int amount;

    @Column(name = "is_settled", nullable = false)
    private boolean settled;

    private PenaltyLog(User user, Room room, LocalDate targetDate, int amount) {
        this.user = user;
        this.room = room;
        this.targetDate = targetDate;
        this.amount = amount;
    }

    public static PenaltyLog of(User user, Room room, LocalDate targetDate) {
        return new PenaltyLog(user, room, targetDate, room.getPenaltyAmount());
    }

    /** 방장이 "정산 완료" 처리 (PRD 5.3, M4에서 사용) */
    public void settle() {
        this.settled = true;
    }
}
