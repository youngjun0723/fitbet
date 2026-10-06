package com.fitbet.streak;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StreakRepository extends JpaRepository<Streak, Long> {

    Optional<Streak> findByUserIdAndRoomId(Long userId, Long roomId);

    List<Streak> findAllByRoomId(Long roomId);
}
