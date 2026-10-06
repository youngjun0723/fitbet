package com.fitbet.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import com.fitbet.challenge.ChallengeService;
import com.fitbet.penalty.PenaltyLog;
import com.fitbet.penalty.PenaltyLogRepository;
import com.fitbet.room.Room;
import com.fitbet.room.RoomService;
import com.fitbet.scheduler.DailyClosingService.ClosingResult;
import com.fitbet.streak.Streak;
import com.fitbet.streak.StreakRepository;
import com.fitbet.support.MutableClock;
import com.fitbet.user.User;
import com.fitbet.user.UserRepository;

import jakarta.persistence.EntityManager;

/**
 * 시나리오 (방 "헬창들의 모임", 벌금 2,000원)
 *   10/5 영진(방장)·민수·지훈 참여, 지훈만 10/5 인증
 *   10/6 서연 참여, 민수만 10/6 인증
 *   10/7 00:00:05 → 10/6 마감
 */
@SpringBootTest
@Transactional
class DailyClosingServiceTest {

    static final LocalDate OCT5 = LocalDate.of(2026, 10, 5);
    static final LocalDate OCT6 = LocalDate.of(2026, 10, 6);
    static final LocalDate OCT7 = LocalDate.of(2026, 10, 7);

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(OCT5.atTime(10, 0));
        }
    }

    @Autowired DailyClosingService dailyClosingService;
    @Autowired RoomService roomService;
    @Autowired ChallengeService challengeService;
    @Autowired UserRepository userRepository;
    @Autowired PenaltyLogRepository penaltyLogRepository;
    @Autowired StreakRepository streakRepository;
    @Autowired MutableClock clock;
    @Autowired EntityManager em;

    User host, minsu, jihun, seoyeon;
    Room room;

    @BeforeEach
    void setUp() {
        clock.setTo(OCT5.atTime(10, 0));
        host = userRepository.save(User.create("영진"));
        minsu = userRepository.save(User.create("민수"));
        jihun = userRepository.save(User.create("지훈"));
        seoyeon = userRepository.save(User.create("서연"));

        room = roomService.createRoom(host.getId(), "헬창들의 모임", 2000);
        roomService.join(minsu.getId(), room.getInviteCode());
        roomService.join(jihun.getId(), room.getInviteCode());
        challengeService.upload(jihun.getId(), room.getId(), photo(), null); // 지훈 10/5 인증 → streak 1

        clock.setTo(OCT6.atTime(9, 0));
        roomService.join(seoyeon.getId(), room.getInviteCode());
        challengeService.upload(minsu.getId(), room.getId(), photo(), null); // 민수 10/6 인증

        clock.setTo(OCT7.atTime(0, 0, 5));
    }

    @Test
    void 미인증_멤버에게만_방_벌금액_스냅샷으로_벌금을_부과한다() {
        ClosingResult result = dailyClosingService.closeDay(OCT6);

        assertThat(result.penalizedCount()).isEqualTo(2);
        assertThat(penaltyLogRepository.findAll())
                .extracting(p -> p.getUser().getUsername(), PenaltyLog::getTargetDate, PenaltyLog::getAmount)
                .containsExactlyInAnyOrder(
                        tuple("영진", OCT6, 2000),
                        tuple("지훈", OCT6, 2000));
    }

    @Test
    void 참여_당일은_면제되고_다음_날부터_벌금이_붙는다() {
        dailyClosingService.closeDay(OCT6);
        assertThat(penaltiesOf(seoyeon)).isZero(); // 10/6 참여 → 10/6은 면제

        dailyClosingService.closeDay(OCT7);
        assertThat(penaltiesOf(seoyeon)).isEqualTo(1); // 10/7은 부과
    }

    @Test
    void 미인증자의_Streak은_0이_되지만_최고기록은_남는다() {
        dailyClosingService.closeDay(OCT6);
        em.flush();
        em.clear();

        Streak jihunStreak = streakOf(jihun);
        assertThat(jihunStreak.getCurrentStreak()).isZero();
        assertThat(jihunStreak.getMaxStreak()).isEqualTo(1);

        Streak minsuStreak = streakOf(minsu); // 인증한 사람은 그대로
        assertThat(minsuStreak.getCurrentStreak()).isEqualTo(1);
    }

    @Test
    void 스케줄러가_두_번_돌아도_벌금은_한_번만_멱등성() {
        dailyClosingService.closeDay(OCT6);
        ClosingResult second = dailyClosingService.closeDay(OCT6);

        assertThat(second.penalizedCount()).isZero();
        assertThat(second.failedRoomIds()).isEmpty();
        assertThat(penaltyLogRepository.count()).isEqualTo(2);
    }

    @Test
    void 방마다_따로_판정한다_다른_방_인증은_인정되지_않는다() {
        Room otherRoom = createRoomAt(OCT5, minsu, "러닝 크루"); // 민수 방장, 벌금 1,000원

        dailyClosingService.closeDay(OCT6);

        // 민수는 헬창방엔 인증했지만 러닝 크루엔 안 함 → 러닝 크루에서만 1,000원
        assertThat(penaltyLogRepository.findAll())
                .filteredOn(p -> p.getUser().getId().equals(minsu.getId()))
                .extracting(p -> p.getRoom().getId(), PenaltyLog::getAmount)
                .containsExactly(tuple(otherRoom.getId(), 1000));
    }

    @Test
    void 인증하면_다음_날_Streak은_1부터_다시_시작() {
        dailyClosingService.closeDay(OCT6); // 지훈 streak 0

        clock.setTo(OCT7.atTime(8, 0));
        var result = challengeService.upload(jihun.getId(), room.getId(), photo(), null);

        assertThat(result.currentStreak()).isEqualTo(1);
        assertThat(result.maxStreak()).isEqualTo(1);
    }

    private Room createRoomAt(LocalDate date, User host, String title) {
        clock.setTo(date.atTime(10, 0));
        Room created = roomService.createRoom(host.getId(), title, null);
        clock.setTo(OCT7.atTime(0, 0, 5));
        return created;
    }

    private long penaltiesOf(User user) {
        return penaltyLogRepository.findAll().stream()
                .filter(p -> p.getUser().getId().equals(user.getId()))
                .count();
    }

    private Streak streakOf(User user) {
        return streakRepository.findByUserIdAndRoomId(user.getId(), room.getId()).orElseThrow();
    }

    private MockMultipartFile photo() {
        return new MockMultipartFile("photo", "workout.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }
}
