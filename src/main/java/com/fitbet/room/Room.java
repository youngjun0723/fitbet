package com.fitbet.room;

import java.time.LocalTime;

import com.fitbet.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "room")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room extends BaseTimeEntity {

    public static final int DEFAULT_PENALTY_AMOUNT = 1000;
    public static final LocalTime DEFAULT_DEADLINE = LocalTime.of(23, 59);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String title;

    @Column(nullable = false)
    private int penaltyAmount;

    @Column(nullable = false)
    private LocalTime deadlineTime;

    @Column(nullable = false, unique = true, length = 6)
    private String inviteCode;

    private Room(String title, int penaltyAmount, String inviteCode) {
        this.title = title;
        this.penaltyAmount = penaltyAmount;
        this.deadlineTime = DEFAULT_DEADLINE;
        this.inviteCode = inviteCode;
    }

    public static Room create(String title, Integer penaltyAmount, String inviteCode) {
        int amount = (penaltyAmount == null) ? DEFAULT_PENALTY_AMOUNT : penaltyAmount;
        return new Room(title, amount, inviteCode);
    }
}
