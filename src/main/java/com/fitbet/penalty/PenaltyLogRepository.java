package com.fitbet.penalty;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PenaltyLogRepository extends JpaRepository<PenaltyLog, Long> {

    /** 미정산 벌금 풀 합계. 행을 다 가져와 자바에서 더하지 않고 DB가 SUM 한 숫자 하나만 받는다. */
    @Query("""
            select coalesce(sum(p.amount), 0) from PenaltyLog p
            where p.room.id = :roomId and p.settled = false
            """)
    long sumUnsettledAmount(@Param("roomId") Long roomId);

    /**
     * PRD 5.3 정산 리포트: 멤버별 미정산 벌금 합계·일수. 인덱스 idx_penalty_room_settled(room_id, is_settled) 사용.
     * 벌금 많은 순 → 같으면 닉네임 순 (순위가 매번 같게 나오도록 정렬 기준을 끝까지 정해 둔다)
     */
    @Query("""
            select new com.fitbet.penalty.PenaltySummary(
                u.id, u.username, sum(p.amount), count(p), min(p.targetDate), max(p.targetDate))
            from PenaltyLog p join p.user u
            where p.room.id = :roomId and p.settled = false
            group by u.id, u.username
            order by sum(p.amount) desc, u.username asc
            """)
    List<PenaltySummary> summarizeUnsettled(@Param("roomId") Long roomId);

    /**
     * 정산 완료: UPDATE 한 방으로 일괄 처리 (엔티티를 하나씩 settle() 하면 N번 UPDATE).
     * 벌크 연산은 영속성 컨텍스트(1차 캐시)를 거치지 않으므로 clearAutomatically로 캐시를 비워
     * 같은 트랜잭션에서 오래된 값(settled=false)을 읽는 일을 막는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update PenaltyLog p set p.settled = true
            where p.room.id = :roomId and p.settled = false and p.targetDate <= :until
            """)
    int settleUntil(@Param("roomId") Long roomId, @Param("until") LocalDate until);
}
