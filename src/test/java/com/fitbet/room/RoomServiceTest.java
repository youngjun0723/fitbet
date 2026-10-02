package com.fitbet.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import com.fitbet.common.exception.BusinessException;
import com.fitbet.common.exception.ErrorCode;
import com.fitbet.streak.StreakRepository;
import com.fitbet.user.User;
import com.fitbet.user.UserRepository;

@SpringBootTest
@Transactional // 각 테스트가 끝나면 롤백 → 테스트끼리 데이터가 섞이지 않음
class RoomServiceTest {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 21, 30);

    /** 실제 Clock 대신 고정된 Clock을 주입 → "지금"이 항상 2026-10-02 21:30 */
    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        }
    }

    @Autowired RoomService roomService;
    @Autowired RoomMemberRepository roomMemberRepository;
    @Autowired StreakRepository streakRepository;
    @Autowired UserRepository userRepository;
    @MockitoSpyBean InviteCodeGenerator inviteCodeGenerator;

    User host;
    User friend;

    @BeforeEach
    void setUp() {
        host = userRepository.save(User.create("영진"));
        friend = userRepository.save(User.create("민수"));
    }

    @Test
    void 방을_만들면_생성자가_HOST로_참여하고_Streak이_0으로_생긴다() {
        Room room = roomService.createRoom(host.getId(), "헬창들의 모임", null);

        assertThat(room.getInviteCode()).hasSize(6);
        assertThat(room.getPenaltyAmount()).isEqualTo(Room.DEFAULT_PENALTY_AMOUNT);
        assertThat(roomMemberRepository.existsByUserIdAndRoomId(host.getId(), room.getId())).isTrue();

        RoomMember hostMember = roomMemberRepository.findAll().get(0);
        assertThat(hostMember.getRole()).isEqualTo(RoomRole.HOST);
        assertThat(hostMember.getJoinedAt()).isEqualTo(NOW); // Clock 주입 덕분에 시각까지 검증 가능

        assertThat(streakRepository.findByUserIdAndRoomId(host.getId(), room.getId()))
                .hasValueSatisfying(s -> {
                    assertThat(s.getCurrentStreak()).isZero();
                    assertThat(s.getMaxStreak()).isZero();
                    assertThat(s.getLastVerifiedDate()).isNull();
                });
    }

    @Test
    void 초대_코드로_참여하면_MEMBER가_된다_소문자_입력도_허용() {
        Room room = roomService.createRoom(host.getId(), "헬창들의 모임", 2000);

        RoomMember member = roomService.join(friend.getId(), room.getInviteCode().toLowerCase());

        assertThat(member.getRole()).isEqualTo(RoomRole.MEMBER);
        assertThat(roomMemberRepository.countByRoomId(room.getId())).isEqualTo(2);
        assertThat(streakRepository.findByUserIdAndRoomId(friend.getId(), room.getId())).isPresent();
    }

    @Test
    void 이미_참여한_방에_다시_참여하면_ALREADY_JOINED() {
        Room room = roomService.createRoom(host.getId(), "헬창들의 모임", null);
        roomService.join(friend.getId(), room.getInviteCode());

        assertThatThrownBy(() -> roomService.join(friend.getId(), room.getInviteCode()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_JOINED);
    }

    @Test
    void 방장이_자기_방_코드로_참여해도_ALREADY_JOINED() {
        Room room = roomService.createRoom(host.getId(), "헬창들의 모임", null);

        assertThatThrownBy(() -> roomService.join(host.getId(), room.getInviteCode()))
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_JOINED);
    }

    @Test
    void 없는_초대_코드면_INVITE_CODE_NOT_FOUND() {
        assertThatThrownBy(() -> roomService.join(friend.getId(), "ZZZZZZ"))
                .extracting("errorCode").isEqualTo(ErrorCode.INVITE_CODE_NOT_FOUND);
    }

    @Test
    void 초대_코드가_이미_쓰이고_있으면_다시_뽑는다() {
        // 첫 방은 AAAAAA, 두 번째 방 생성 시 AAAAAA(충돌) → BBBBBB 순서로 나오게 조작
        given(inviteCodeGenerator.generate()).willReturn("AAAAAA", "AAAAAA", "BBBBBB");

        roomService.createRoom(host.getId(), "첫 번째 방", null);
        Room second = roomService.createRoom(host.getId(), "두 번째 방", null);

        assertThat(second.getInviteCode()).isEqualTo("BBBBBB");
    }
}
