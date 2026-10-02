package com.fitbet.challenge;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChallengeLogRepository extends JpaRepository<ChallengeLog, Long> {

    boolean existsByUserIdAndRoomIdAndLogDate(Long userId, Long roomId, LocalDate logDate);
}
