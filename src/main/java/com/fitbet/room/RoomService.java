package com.fitbet.room;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitbet.common.exception.BusinessException;
import com.fitbet.common.exception.ErrorCode;
import com.fitbet.streak.Streak;
import com.fitbet.streak.StreakRepository;
import com.fitbet.user.User;
import com.fitbet.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoomService {

    private static final int MAX_INVITE_CODE_ATTEMPTS = 10;

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final StreakRepository streakRepository;
    private final UserRepository userRepository;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final Clock clock;

    /**
     * 방 생성 = Room + RoomMember(HOST) + Streak 3개 INSERT.
     * 하나라도 실패하면 "방장 없는 방" 같은 반쪽 데이터가 남지 않도록 한 트랜잭션으로 묶는다.
     */
    @Transactional
    public Room createRoom(Long userId, String title, Integer penaltyAmount) {
        User host = getUser(userId);
        Room room = roomRepository.save(Room.create(title, penaltyAmount, issueInviteCode()));
        addMember(RoomMember.host(host, room, LocalDateTime.now(clock)));
        return room;
    }

    /** 초대 코드로 참여. 중복 참여는 앱 체크 + DB UNIQUE 두 겹으로 막는다. */
    @Transactional
    public RoomMember join(Long userId, String inviteCode) {
        User user = getUser(userId);
        Room room = roomRepository.findByInviteCode(inviteCode.toUpperCase())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_CODE_NOT_FOUND));

        // 1차 방어: 대부분의 중복 요청은 여기서 친절한 409로 끝난다
        if (roomMemberRepository.existsByUserIdAndRoomId(user.getId(), room.getId())) {
            throw new BusinessException(ErrorCode.ALREADY_JOINED);
        }
        return addMember(RoomMember.member(user, room, LocalDateTime.now(clock)));
    }

    private RoomMember addMember(RoomMember member) {
        try {
            // 2차 방어: 동시에 두 요청이 1차 체크를 통과해도 UNIQUE(user_id, room_id)가 막는다.
            // saveAndFlush로 INSERT를 즉시 실행해야 제약 위반을 이 try 안에서 잡을 수 있다.
            roomMemberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ALREADY_JOINED);
        }
        streakRepository.save(Streak.start(member.getUser(), member.getRoom()));
        return member;
    }

    /** 트랜잭션 안에서 DTO로 바꿔서 반환 — open-in-view=false라 밖에서 LAZY 필드를 건드리면 에러 */
    @Transactional(readOnly = true)
    public List<MyRoomSummary> findMyRooms(Long userId) {
        return roomMemberRepository.findAllWithRoomByUserId(userId).stream()
                .map(rm -> new MyRoomSummary(rm.getRoom().getId(), rm.getRoom().getTitle(),
                        rm.getRoom().getPenaltyAmount(), rm.getRole()))
                .toList();
    }

    public record MyRoomSummary(Long roomId, String title, int penaltyAmount, RoomRole role) {
    }

    @Transactional(readOnly = true)
    public boolean isMember(Long userId, Long roomId) {
        return roomMemberRepository.existsByUserIdAndRoomId(userId, roomId);
    }

    private String issueInviteCode() {
        // 31^6 ≈ 8.8억 조합이라 충돌은 드물지만 0은 아니므로 재시도
        for (int i = 0; i < MAX_INVITE_CODE_ATTEMPTS; i++) {
            String code = inviteCodeGenerator.generate();
            if (!roomRepository.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new BusinessException(ErrorCode.INVITE_CODE_GENERATION_FAILED);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
