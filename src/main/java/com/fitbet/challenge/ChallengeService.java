package com.fitbet.challenge;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.fitbet.common.exception.BusinessException;
import com.fitbet.common.exception.ErrorCode;
import com.fitbet.common.storage.StorageService;
import com.fitbet.room.RoomMember;
import com.fitbet.room.RoomMemberRepository;
import com.fitbet.streak.Streak;
import com.fitbet.streak.StreakRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChallengeService {

    private final RoomMemberRepository roomMemberRepository;
    private final ChallengeLogRepository challengeLogRepository;
    private final StreakRepository streakRepository;
    private final StorageService storageService;
    private final ImageFileValidator imageFileValidator;
    private final Clock clock;

    /**
     * PRD 5.1 인증 업로드: 파일 저장 → ChallengeLog INSERT → Streak 갱신.
     * DB 작업은 한 트랜잭션. 파일은 DB가 아니라 롤백되지 않으므로, 롤백되면 직접 지운다(보상 처리).
     */
    @Transactional
    public UploadResult upload(Long userId, Long roomId, MultipartFile photo, String memo) {
        // 1. 요청 검증: 방 멤버인지 + 이미지 형식/크기 (파일을 디스크에 쓰기 전에 싼 검사부터)
        RoomMember member = roomMemberRepository.findWithUserAndRoom(userId, roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_ROOM_MEMBER));
        imageFileValidator.validate(photo);

        // 2. 오늘 이미 인증했는지 (1차 방어)
        LocalDate today = LocalDate.now(clock);
        if (challengeLogRepository.existsByUserIdAndRoomIdAndLogDate(userId, roomId, today)) {
            throw new BusinessException(ErrorCode.ALREADY_VERIFIED_TODAY);
        }

        // 3. 파일 저장 + 롤백 시 파일 삭제 예약
        String photoUrl = storageService.store(photo);
        deleteFileOnRollback(photoUrl);

        // 4. ChallengeLog INSERT (2차 방어: 동시 업로드는 UNIQUE 위반 → 409)
        ChallengeLog log;
        try {
            log = challengeLogRepository.saveAndFlush(
                    ChallengeLog.of(member.getUser(), member.getRoom(), today, photoUrl, memo));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ALREADY_VERIFIED_TODAY);
        }

        // 5. Streak 갱신 — 엔티티 값만 바꾸면 커밋 시 JPA가 UPDATE를 날린다 (변경 감지, dirty checking)
        Streak streak = streakRepository.findByUserIdAndRoomId(userId, roomId)
                .orElseGet(() -> streakRepository.save(Streak.start(member.getUser(), member.getRoom())));
        streak.recordVerification(today);

        return new UploadResult(log.getId(), today, photoUrl, memo,
                streak.getCurrentStreak(), streak.getMaxStreak());
    }

    private void deleteFileOnRollback(String photoUrl) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    storageService.delete(photoUrl);
                }
            }
        });
    }

    public record UploadResult(Long logId, LocalDate logDate, String photoUrl, String memo,
                               int currentStreak, int maxStreak) {
    }
}
