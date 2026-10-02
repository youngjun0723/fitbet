package com.fitbet.challenge;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fitbet.common.auth.LoginUserIdArgumentResolver;
import com.fitbet.room.Room;
import com.fitbet.room.RoomService;
import com.fitbet.user.User;
import com.fitbet.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChallengeApiControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RoomService roomService;

    User minsu;
    Room room;

    @BeforeEach
    void setUp() {
        minsu = userRepository.save(User.create("민수"));
        room = roomService.createRoom(minsu.getId(), "헬창들의 모임", null);
    }

    @Test
    void 사진과_메모로_인증하면_201() throws Exception {
        mockMvc.perform(multipart("/api/rooms/{roomId}/challenges", room.getId())
                        .file(photo("workout.jpg", "image/jpeg"))
                        .param("memo", "  오운완  ")
                        .session(sessionOf(minsu)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memo").value("오운완"))
                .andExpect(jsonPath("$.currentStreak").value(1));
    }

    @Test
    void 사진_없이_보내면_400() throws Exception {
        mockMvc.perform(multipart("/api/rooms/{roomId}/challenges", room.getId())
                        .param("memo", "사진 깜빡")
                        .session(sessionOf(minsu)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 메모가_100자를_넘으면_400() throws Exception {
        mockMvc.perform(multipart("/api/rooms/{roomId}/challenges", room.getId())
                        .file(photo("workout.jpg", "image/jpeg"))
                        .param("memo", "가".repeat(101))
                        .session(sessionOf(minsu)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 이미지가_아니면_400() throws Exception {
        mockMvc.perform(multipart("/api/rooms/{roomId}/challenges", room.getId())
                        .file(photo("virus.exe", "application/octet-stream"))
                        .session(sessionOf(minsu)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
    }

    @Test
    void 다른_방_멤버가_아니면_403() throws Exception {
        User outsider = userRepository.save(User.create("외부인"));

        mockMvc.perform(multipart("/api/rooms/{roomId}/challenges", room.getId())
                        .file(photo("workout.jpg", "image/jpeg"))
                        .session(sessionOf(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ROOM_MEMBER"));
    }

    private MockMultipartFile photo(String filename, String contentType) {
        return new MockMultipartFile("photo", filename, contentType, new byte[]{1, 2, 3});
    }

    private MockHttpSession sessionOf(User user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(LoginUserIdArgumentResolver.SESSION_KEY, user.getId());
        return session;
    }
}
