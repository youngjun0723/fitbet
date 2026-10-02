package com.fitbet.common;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import com.fitbet.challenge.ChallengeLog;
import com.fitbet.challenge.ChallengeLogRepository;
import com.fitbet.challenge.Reaction;
import com.fitbet.challenge.ReactionRepository;
import com.fitbet.challenge.ReactionType;
import com.fitbet.common.config.TimeConfig;
import com.fitbet.penalty.PenaltyLog;
import com.fitbet.penalty.PenaltyLogRepository;
import com.fitbet.room.Room;
import com.fitbet.room.RoomMember;
import com.fitbet.room.RoomMemberRepository;
import com.fitbet.room.RoomRepository;
import com.fitbet.streak.Streak;
import com.fitbet.streak.StreakRepository;
import com.fitbet.user.User;
import com.fitbet.user.UserRepository;

/**
 * "앱 체크를 뚫고 들어와도 DB가 막는다"를 검증한다.
 * 서비스 로직 없이 Repository로 직접 중복 INSERT를 시도해 UNIQUE 제약이 실제로 걸려 있는지 확인.
 */
@DataJpaTest // JPA 관련 빈만 띄우는 슬라이스 테스트 (빠름)
@Import(TimeConfig.class) // createdAt 자동 입력(Auditing)용
class UniqueConstraintTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 2);

    @Autowired UserRepository userRepository;
    @Autowired RoomRepository roomRepository;
    @Autowired RoomMemberRepository roomMemberRepository;
    @Autowired ChallengeLogRepository challengeLogRepository;
    @Autowired StreakRepository streakRepository;
    @Autowired PenaltyLogRepository penaltyLogRepository;
    @Autowired ReactionRepository reactionRepository;

    User user;
    Room room;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.create("민수"));
        room = roomRepository.save(Room.create("헬창들의 모임", null, "ABC234"));
    }

    @Test
    void 같은_방에_두_번_참여_불가() {
        roomMemberRepository.saveAndFlush(RoomMember.member(user, room, LocalDateTime.now()));

        assertThatThrownBy(() -> roomMemberRepository.saveAndFlush(RoomMember.member(user, room, LocalDateTime.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 같은_날_두_번_인증_불가() {
        challengeLogRepository.saveAndFlush(ChallengeLog.of(user, room, TODAY, "/a.jpg", "첫 인증"));

        assertThatThrownBy(() -> challengeLogRepository.saveAndFlush(ChallengeLog.of(user, room, TODAY, "/b.jpg", "또 인증")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 같은_날_벌금_두_번_부과_불가() {
        penaltyLogRepository.saveAndFlush(PenaltyLog.of(user, room, TODAY));

        assertThatThrownBy(() -> penaltyLogRepository.saveAndFlush(PenaltyLog.of(user, room, TODAY)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 멤버당_방별_Streak은_한_행() {
        streakRepository.saveAndFlush(Streak.start(user, room));

        assertThatThrownBy(() -> streakRepository.saveAndFlush(Streak.start(user, room)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 한_인증글에_한_사람은_리액션_하나() {
        User friend = userRepository.save(User.create("지훈"));
        ChallengeLog log = challengeLogRepository.save(ChallengeLog.of(user, room, TODAY, "/a.jpg", null));
        reactionRepository.saveAndFlush(Reaction.of(log, friend, ReactionType.APPROVE));

        assertThatThrownBy(() -> reactionRepository.saveAndFlush(Reaction.of(log, friend, ReactionType.DOUBT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 초대_코드는_방마다_달라야_한다() {
        assertThatThrownBy(() -> roomRepository.saveAndFlush(Room.create("다른 방", null, "ABC234")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
