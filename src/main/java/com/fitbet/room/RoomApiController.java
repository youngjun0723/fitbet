package com.fitbet.room;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fitbet.common.auth.LoginUserId;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomApiController {

    private final RoomService roomService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse create(@LoginUserId Long userId, @Valid @RequestBody CreateRoomRequest request) {
        Room room = roomService.createRoom(userId, request.title().strip(), request.penaltyAmount());
        return RoomResponse.of(room, RoomRole.HOST);
    }

    @PostMapping("/join")
    public RoomResponse join(@LoginUserId Long userId, @Valid @RequestBody JoinRoomRequest request) {
        RoomMember member = roomService.join(userId, request.inviteCode());
        return RoomResponse.of(member.getRoom(), member.getRole());
    }

    public record CreateRoomRequest(
            @NotBlank @Size(max = 50) String title,
            @Min(100) @Max(100_000) Integer penaltyAmount // null이면 기본 1,000원
    ) {
    }

    public record JoinRoomRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9]{6}$", message = "6자리 영문/숫자여야 합니다.") String inviteCode
    ) {
    }

    /** 엔티티를 그대로 반환하지 않고 DTO로 변환: LAZY 프록시 직렬화 문제 + API 스펙이 엔티티 변경에 끌려가지 않게. */
    public record RoomResponse(Long roomId, String title, int penaltyAmount, String inviteCode, RoomRole myRole) {
        static RoomResponse of(Room room, RoomRole role) {
            return new RoomResponse(room.getId(), room.getTitle(), room.getPenaltyAmount(), room.getInviteCode(), role);
        }
    }
}
