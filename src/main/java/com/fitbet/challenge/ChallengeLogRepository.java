package com.fitbet.challenge;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChallengeLogRepository extends JpaRepository<ChallengeLog, Long> {

    boolean existsByUserIdAndRoomIdAndLogDate(Long userId, Long roomId, LocalDate logDate);

    /** 대시보드 "오늘 피드". idx_challenge_room_date(room_id, log_date) 인덱스를 탄다. */
    @Query("""
            select cl from ChallengeLog cl
            join fetch cl.user
            where cl.room.id = :roomId and cl.logDate = :logDate
            order by cl.createdAt desc
            """)
    List<ChallengeLog> findFeed(@Param("roomId") Long roomId, @Param("logDate") LocalDate logDate);
}
