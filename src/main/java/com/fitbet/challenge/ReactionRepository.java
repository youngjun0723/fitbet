package com.fitbet.challenge;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {

    /**
     * DTO projection: 엔티티 대신 필요한 컬럼 3개만 바로 record로 받는다.
     * 피드 글이 몇 개든 IN 절 하나로 한 번에 조회 (글마다 조회하면 N+1).
     */
    @Query("""
            select new com.fitbet.challenge.ReactionView(r.challengeLog.id, r.user.id, r.type)
            from Reaction r
            where r.challengeLog.id in :logIds
            """)
    List<ReactionView> findViewsByLogIds(@Param("logIds") Collection<Long> logIds);
}
