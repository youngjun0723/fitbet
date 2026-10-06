package com.fitbet.penalty;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PenaltyLogRepository extends JpaRepository<PenaltyLog, Long> {

    /** 미정산 벌금 풀 합계. 행을 다 가져와 자바에서 더하지 않고 DB가 SUM 한 숫자 하나만 받는다. */
    @Query("""
            select coalesce(sum(p.amount), 0) from PenaltyLog p
            where p.room.id = :roomId and p.settled = false
            """)
    long sumUnsettledAmount(@Param("roomId") Long roomId);
}
