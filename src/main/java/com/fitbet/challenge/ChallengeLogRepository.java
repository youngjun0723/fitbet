package com.fitbet.challenge;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChallengeLogRepository extends JpaRepository<ChallengeLog, Long> {

    boolean existsByUserIdAndRoomIdAndLogDate(Long userId, Long roomId, LocalDate logDate);

    /** 리액션: 글의 방(멤버십 확인)과 작성자(본인 글 확인)를 한 번에 */
    @Query("""
            select cl from ChallengeLog cl
            join fetch cl.user
            join fetch cl.room
            where cl.id = :id
            """)
    Optional<ChallengeLog> findWithUserAndRoom(@Param("id") Long id);

    /** 대시보드 "오늘 피드". idx_challenge_room_date(room_id, log_date) 인덱스를 탄다. */
    @Query("""
            select cl from ChallengeLog cl
            join fetch cl.user
            where cl.room.id = :roomId and cl.logDate = :logDate
            order by cl.createdAt desc
            """)
    List<ChallengeLog> findFeed(@Param("roomId") Long roomId, @Param("logDate") LocalDate logDate);
}
