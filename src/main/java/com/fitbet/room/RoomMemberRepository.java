package com.fitbet.room;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {

    boolean existsByUserIdAndRoomId(Long userId, Long roomId);

    long countByRoomId(Long roomId);

    /**
     * 멤버십 확인 + User/Room 엔티티 확보를 쿼리 1번으로.
     * fetch join이 없으면 LAZY라서 getUser(), getRoom() 접근 때 SELECT가 각각 더 나간다.
     */
    @Query("""
            select rm from RoomMember rm
            join fetch rm.user
            join fetch rm.room
            where rm.user.id = :userId and rm.room.id = :roomId
            """)
    Optional<RoomMember> findWithUserAndRoom(@Param("userId") Long userId, @Param("roomId") Long roomId);
}
