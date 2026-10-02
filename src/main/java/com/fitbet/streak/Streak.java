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
}
