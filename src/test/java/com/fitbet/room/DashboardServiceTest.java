package com.fitbet.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import com.fitbet.challenge.ChallengeLog;
import com.fitbet.challenge.ChallengeLogRepository;
import com.fitbet.challenge.ChallengeService;
import com.fitbet.challenge.Reaction;
import com.fitbet.challenge.ReactionRepository;
import com.fitbet.challenge.ReactionType;
import com.fitbet.common.exception.ErrorCode;
import com.fitbet.penalty.PenaltyLog;
import com.fitbet.penalty.PenaltyLogRepository;
import com.fitbet.room.DashboardResponse.FeedItem;
import com.fitbet.room.DashboardResponse.MemberStatus;
import com.fitbet.support.MutableClock;
import com.fitbet.user.User;
import com.fitbet.user.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class DashboardServiceTest {

    static final LocalDateTime YESTERDAY = LocalDateTime.of(2026, 10, 5, 10, 0);
    static final LocalDateTime TODAY = LocalDateTime.of(2026, 10, 6, 9, 0);

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(YESTERDAY);
        }
    }

    @Autowired DashboardService dashboardService;
    @Autowired RoomService roomService;
    @Autowired ChallengeService challengeService;
    @Autowired UserRepository userRepository;
    @Autowired ChallengeLogRepository challengeLogRepository;
    @Autowired ReactionRepository reactionRepository;
    @Autowired PenaltyLogRepository penaltyLogRepository;
    @Autowired MutableClock clock;
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;

    User host;   // 영진 (방장)
    User minsu;  // 오늘 인증함
    User jihun;  // 오늘 미인증
    User seoyeon; // 오늘 참여 (벌금 면제)
    Room room;

    @BeforeEach
    void setUp() {
        clock.setTo(YESTERDAY);
        host = userRepository.save(User.create("영진"));
        minsu = userRepository.save(User.create("민수"));
        jihun = userRepository.save(User.create("지훈"));
        seoyeon = userRepository.save(User.create("서연"));

        room = roomService.createRoom(host.getId(), "헬창들의 모임", null);
        roomService.join(minsu.getId(), room.getInviteCode());
        roomService.join(jihun.getId(), room.getInviteCode());

        clock.setTo(TODAY); // ---- 하루 지남
        roomService.join(seoyeon.getId(), room.getInviteCode());
        challengeService.upload(minsu.getId(), room.getId(), photo(), "하체 데이");
    }

    @Test
    void 미인증자_목록에는_오늘_인증_안_한_멤버만_있고_오늘_참여자는_면제_표시() {
        DashboardResponse d = dashboardService.getDashboard(host.getId(), room.getId());

        assertThat(d.missedMembers())
                .extracting(MemberStatus::username, MemberStatus::joinedToday)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("영진", false),
                        org.assertj.core.groups.Tuple.tuple("지훈", false),
                        org.assertj.core.groups.Tuple.tuple("서연", true));
        assertThat(d.room().memberCount()).isEqualTo(4);
    }

    @Test
    void 오늘_피드에는_작성자_Streak과_리액션_카운트가_붙는다() {
        ChallengeLog log = challengeLogRepository.findFeed(room.getId(), TODAY.toLocalDate()).get(0);
        reactionRepository.save(Reaction.of(log, jihun, ReactionType.APPROVE));
        reactionRepository.save(Reaction.of(log, host, ReactionType.APPROVE));
        reactionRepository.save(Reaction.of(log, seoyeon, ReactionType.DOUBT));

        DashboardResponse d = dashboardService.getDashboard(host.getId(), room.getId());

        assertThat(d.feed()).hasSize(1);
        FeedItem item = d.feed().get(0);
        assertThat(item.username()).isEqualTo("민수");
        assertThat(item.memo()).isEqualTo("하체 데이");
        assertThat(item.currentStreak()).isEqualTo(1);
        assertThat(item.approveCount()).isEqualTo(2);
        assertThat(item.doubtCount()).isEqualTo(1);
        assertThat(item.mine()).isFalse();
    }

    @Test
    void 내_정보는_보는_사람_기준() {
        DashboardResponse asHost = dashboardService.getDashboard(host.getId(), room.getId());
        DashboardResponse asMinsu = dashboardService.getDashboard(minsu.getId(), room.getId());

        assertThat(asHost.me().role()).isEqualTo(RoomRole.HOST);
        assertThat(asHost.me().verifiedToday()).isFalse();

        assertThat(asMinsu.me().role()).isEqualTo(RoomRole.MEMBER);
        assertThat(asMinsu.me().verifiedToday()).isTrue();
        assertThat(asMinsu.me().currentStreak()).isEqualTo(1);
        assertThat(asMinsu.feed().get(0).mine()).isTrue();
    }

    @Test
    void 벌금_풀은_미정산_벌금만_합산() {
        penaltyLogRepository.save(PenaltyLog.of(jihun, room, LocalDate.of(2026, 10, 5)));
        penaltyLogRepository.save(PenaltyLog.of(host, room, LocalDate.of(2026, 10, 5)));
        PenaltyLog settled = PenaltyLog.of(jihun, room, LocalDate.of(2026, 10, 4));
        settled.settle();
        penaltyLogRepository.save(settled);

        DashboardResponse d = dashboardService.getDashboard(host.getId(), room.getId());

        assertThat(d.penaltyPool()).isEqualTo(2000);
    }

    @Test
    void 마감_시각은_오늘_23시59분59초_서울_기준() {
        DashboardResponse d = dashboardService.getDashboard(host.getId(), room.getId());

        assertThat(d.today()).isEqualTo(LocalDate.of(2026, 10, 6));
        assertThat(d.deadline()).isEqualTo(OffsetDateTime.of(2026, 10, 6, 23, 59, 59, 0, ZoneOffset.ofHours(9)));
    }

    @Test
    void 방_멤버가_아니면_NOT_ROOM_MEMBER() {
        User outsider = userRepository.save(User.create("외부인"));

        assertThatThrownBy(() -> dashboardService.getDashboard(outsider.getId(), room.getId()))
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_ROOM_MEMBER);
    }

    @Test
    void 멤버와_인증글이_늘어나도_쿼리_수는_그대로다_N플러스1_없음() {
        long queriesWith4Members = countQueries(() -> dashboardService.getDashboard(host.getId(), room.getId()));

        // 멤버 6명 추가 + 전원 오늘 인증 + 리액션
        for (int i = 1; i <= 6; i++) {
            User u = userRepository.save(User.create("추가멤버" + i));
            roomService.join(u.getId(), room.getInviteCode());
            challengeService.upload(u.getId(), room.getId(), photo(), "메모" + i);
        }
        ChallengeLog anyLog = challengeLogRepository.findFeed(room.getId(), TODAY.toLocalDate()).get(0);
        reactionRepository.save(Reaction.of(anyLog, host, ReactionType.APPROVE));

        long queriesWith10Members = countQueries(() -> dashboardService.getDashboard(host.getId(), room.getId()));

        assertThat(queriesWith10Members).isEqualTo(queriesWith4Members);
        assertThat(queriesWith10Members).isLessThanOrEqualTo(5); // 멤버, 피드, Streak, 리액션, 벌금 SUM
    }

    /** 1차 캐시를 비운 뒤 실제로 DB에 날아간 SQL 개수를 센다 */
    private long countQueries(Runnable action) {
        em.flush();
        em.clear();
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        action.run();
        return stats.getPrepareStatementCount();
    }

    private MockMultipartFile photo() {
        return new MockMultipartFile("photo", "workout.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }
}
