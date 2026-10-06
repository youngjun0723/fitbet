package com.fitbet.room;

import java.util.List;
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

    /** 대시보드: 방 멤버 전원 + 닉네임 + 방 정보. 멤버 수와 상관없이 쿼리 1번. */
    @Query("""
            select rm from RoomMember rm
            join fetch rm.user
            join fetch rm.room
            where rm.room.id = :roomId
            order by rm.joinedAt
            """)
    List<RoomMember> findAllWithUserByRoomId(@Param("roomId") Long roomId);

    /** 랜딩: 내가 참여한 방 목록 */
    @Query("""
            select rm from RoomMember rm
            join fetch rm.room
            where rm.user.id = :userId
            order by rm.joinedAt desc
            """)
    List<RoomMember> findAllWithRoomByUserId(@Param("userId") Long userId);
}
