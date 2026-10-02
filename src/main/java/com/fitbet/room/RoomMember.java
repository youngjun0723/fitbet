package com.fitbet.room;

import java.time.LocalDateTime;

import com.fitbet.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/**
 * User ↔ Room 다대다(N:M)를 풀어주는 연결 엔티티.
 * @ManyToMany 대신 엔티티로 만든 이유: role, joinedAt 같은 추가 컬럼이 필요하기 때문.
 */
@Getter
@Entity
@Table(name = "room_member",
        uniqueConstraints = @UniqueConstraint(name = "uk_room_member_user_room", columnNames = {"user_id", "room_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Enumerated(EnumType.STRING) // ORDINAL(숫자)로 저장하면 enum 순서가 바뀔 때 데이터가 깨진다
    @Column(nullable = false, length = 10)
    private RoomRole role;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    private RoomMember(User user, Room room, RoomRole role, LocalDateTime joinedAt) {
        this.user = user;
        this.room = room;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public static RoomMember host(User user, Room room, LocalDateTime joinedAt) {
        return new RoomMember(user, room, RoomRole.HOST, joinedAt);
    }

    public static RoomMember member(User user, Room room, LocalDateTime joinedAt) {
        return new RoomMember(user, room, RoomRole.MEMBER, joinedAt);
    }
}
