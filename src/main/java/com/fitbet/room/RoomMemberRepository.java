package com.fitbet.room;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    /**
     * 마감 대상: targetDate에 인증하지 않은 멤버 (PRD 5.2).
     * - 참여 당일 면제: joinedAt < targetDate 00:00 (DATE(joined_at) 함수 대신 범위 비교 → 인덱스 사용 가능)
     * - 이미 벌금이 있으면 제외: 스케줄러가 두 번 돌아도 같은 결과 (멱등성 1차 방어, 2차는 UNIQUE)
     */
    @Query("""
            select rm from RoomMember rm
            join fetch rm.user
            join fetch rm.room
            where rm.room.id = :roomId
              and rm.joinedAt < :startOfTargetDate
              and not exists (
                  select 1 from ChallengeLog cl
                  where cl.user = rm.user and cl.room = rm.room and cl.logDate = :targetDate)
              and not exists (
                  select 1 from PenaltyLog p
                  where p.user = rm.user and p.room = rm.room and p.targetDate = :targetDate)
            """)
    List<RoomMember> findMissedMembers(@Param("roomId") Long roomId,
                                       @Param("targetDate") LocalDate targetDate,
                                       @Param("startOfTargetDate") LocalDateTime startOfTargetDate);

    default List<RoomMember> findMissedMembers(Long roomId, LocalDate targetDate) {
        return findMissedMembers(roomId, targetDate, targetDate.atStartOfDay());
    }

    /** 랜딩: 내가 참여한 방 목록 */
    @Query("""
            select rm from RoomMember rm
            join fetch rm.room
            where rm.user.id = :userId
            order by rm.joinedAt desc
            """)
    List<RoomMember> findAllWithRoomByUserId(@Param("userId") Long userId);
}
