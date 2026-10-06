package com.fitbet.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import com.fitbet.challenge.ReactionService.ReactionResult;
import com.fitbet.common.exception.ErrorCode;
import com.fitbet.room.Room;
import com.fitbet.room.RoomService;
import com.fitbet.user.User;
import com.fitbet.user.UserRepository;

@SpringBootTest
@Transactional
class ReactionServiceTest {

    @Autowired ReactionService reactionService;
    @Autowired ReactionRepository reactionRepository;
    @Autowired ChallengeService challengeService;
    @Autowired RoomService roomService;
    @Autowired UserRepository userRepository;

    User host, minsu, seoyeon;
    Long minsuLogId;

    @BeforeEach
    void setUp() {
        host = userRepository.save(User.create("영진"));
        minsu = userRepository.save(User.create("민수"));
        seoyeon = userRepository.save(User.create("서연"));
        Room room = roomService.createRoom(host.getId(), "헬창들의 모임", null);
        roomService.join(minsu.getId(), room.getInviteCode());
        roomService.join(seoyeon.getId(), room.getInviteCode());

        var photo = new MockMultipartFile("photo", "a.jpg", "image/jpeg", new byte[]{1});
        minsuLogId = challengeService.upload(minsu.getId(), room.getId(), photo, null).logId();
    }

    @Test
    void 친구_인증글에_인정을_남긴다() {
        ReactionResult result = reactionService.react(host.getId(), minsuLogId, ReactionType.APPROVE);

        assertThat(result.approveCount()).isEqualTo(1);
        assertThat(result.doubtCount()).isZero();
        assertThat(result.myReaction()).isEqualTo(ReactionType.APPROVE);
    }

    @Test
    void 다시_누르면_새로_만들지_않고_종류만_바꾼다_1인_1리액션() {
        reactionService.react(host.getId(), minsuLogId, ReactionType.APPROVE);

        ReactionResult result = reactionService.react(host.getId(), minsuLogId, ReactionType.DOUBT);

        assertThat(reactionRepository.count()).isEqualTo(1); // 행은 그대로 1개
        assertThat(result.approveCount()).isZero();
        assertThat(result.doubtCount()).isEqualTo(1);
    }

    @Test
    void 같은_리액션을_또_눌러도_카운트는_그대로() {
        reactionService.react(host.getId(), minsuLogId, ReactionType.APPROVE);
        ReactionResult result = reactionService.react(host.getId(), minsuLogId, ReactionType.APPROVE);

        assertThat(result.approveCount()).isEqualTo(1);
    }

    @Test
    void 여러_친구의_리액션이_종류별로_집계된다() {
        reactionService.react(host.getId(), minsuLogId, ReactionType.APPROVE);
        ReactionResult result = reactionService.react(seoyeon.getId(), minsuLogId, ReactionType.DOUBT);

        assertThat(result.approveCount()).isEqualTo(1);
        assertThat(result.doubtCount()).isEqualTo(1);
        assertThat(result.myReaction()).isEqualTo(ReactionType.DOUBT); // "내" 리액션은 요청자 기준
    }

    @Test
    void 본인_인증글에는_리액션_불가() {
        assertThatThrownBy(() -> reactionService.react(minsu.getId(), minsuLogId, ReactionType.APPROVE))
                .extracting("errorCode").isEqualTo(ErrorCode.CANNOT_REACT_TO_OWN);
    }

    @Test
    void 방_멤버가_아니면_리액션_불가() {
        User outsider = userRepository.save(User.create("외부인"));

        assertThatThrownBy(() -> reactionService.react(outsider.getId(), minsuLogId, ReactionType.DOUBT))
                .extracting("errorCode").isEqualTo(ErrorCode.NOT_ROOM_MEMBER);
    }

    @Test
    void 없는_인증글이면_CHALLENGE_NOT_FOUND() {
        assertThatThrownBy(() -> reactionService.react(host.getId(), 999_999L, ReactionType.APPROVE))
                .extracting("errorCode").isEqualTo(ErrorCode.CHALLENGE_NOT_FOUND);
    }
}
