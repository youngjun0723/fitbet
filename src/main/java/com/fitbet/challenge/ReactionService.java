package com.fitbet.challenge;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitbet.common.exception.BusinessException;
import com.fitbet.common.exception.ErrorCode;
import com.fitbet.room.RoomMember;
import com.fitbet.room.RoomMemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReactionService {

    private final ChallengeLogRepository challengeLogRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final ReactionRepository reactionRepository;

    /**
     * PRD 2.5: 인정/의심 리액션 등록·변경. 1인 1리액션이므로 이미 있으면 새로 만들지 않고 type만 바꾼다(upsert).
     * PRD 2.6 MVP 제안대로 의심 리액션은 "표시만" 하고 인증을 무효화하지 않는다.
     */
    @Transactional
    public ReactionResult react(Long userId, Long logId, ReactionType type) {
        ChallengeLog log = challengeLogRepository.findWithUserAndRoom(logId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
        RoomMember me = roomMemberRepository.findWithUserAndRoom(userId, log.getRoom().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_ROOM_MEMBER));
        if (log.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.CANNOT_REACT_TO_OWN);
        }

        reactionRepository.findByChallengeLogIdAndUserId(logId, userId)
                .ifPresentOrElse(
                        existing -> existing.changeType(type), // 변경 감지로 UPDATE
                        () -> insert(Reaction.of(log, me.getUser(), type)));

        // JPQL 실행 직전 Hibernate가 자동 flush 하므로 방금 바꾼 값까지 포함해 집계된다
        List<ReactionView> reactions = reactionRepository.findViewsByLogIds(List.of(logId));
        long approve = reactions.stream().filter(r -> r.type() == ReactionType.APPROVE).count();
        return new ReactionResult(logId, approve, reactions.size() - approve, type);
    }

    private void insert(Reaction reaction) {
        try {
            reactionRepository.saveAndFlush(reaction);
        } catch (DataIntegrityViolationException e) {
            // 같은 사람이 버튼을 동시에 두 번 눌러 둘 다 "아직 없음"으로 판단한 경우 → UNIQUE가 막음
            throw new BusinessException(ErrorCode.REACTION_CONFLICT);
        }
    }

    public record ReactionResult(Long logId, long approveCount, long doubtCount, ReactionType myReaction) {
    }
}
