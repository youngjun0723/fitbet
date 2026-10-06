package com.fitbet.room;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RoomRepository extends JpaRepository<Room, Long> {

    Optional<Room> findByInviteCode(String inviteCode);

    boolean existsByInviteCode(String inviteCode);

    /** 마감 스케줄러: 엔티티 전체가 아니라 id만 필요하다 */
    @Query("select r.id from Room r order by r.id")
    List<Long> findAllIds();
}
