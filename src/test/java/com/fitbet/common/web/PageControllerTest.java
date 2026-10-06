package com.fitbet.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
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
class PageControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RoomService roomService;

    User host;
    Room room;

    @BeforeEach
    void setUp() {
        host = userRepository.save(User.create("영진"));
        room = roomService.createRoom(host.getId(), "헬창들의 모임", null);
    }

    @Test
    void 로그인_전_랜딩은_닉네임_입력폼() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(containsString("login-form")));
    }

    @Test
    void 로그인_후_랜딩에는_내_방_목록() throws Exception {
        mockMvc.perform(get("/").session(sessionOf(host)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("헬창들의 모임")))
                .andExpect(content().string(containsString("방장")));
    }

    @Test
    void 로그인_없이_보호된_페이지에_오면_원래_주소를_next로_들고_로그인으로() throws Exception {
        mockMvc.perform(get("/rooms/new"))
                .andExpect(redirectedUrl("/?next=%2Frooms%2Fnew"));
        // .param()은 getQueryString()을 채우지 않는다 → 실제 브라우저처럼 URL에 쿼리스트링을 직접 쓴다
        mockMvc.perform(get("/join?code=ABC234"))
                .andExpect(redirectedUrl("/?next=%2Fjoin%3Fcode%3DABC234"));
        mockMvc.perform(get("/rooms/{id}", room.getId()))
                .andExpect(redirectedUrl("/?next=%2Frooms%2F" + room.getId()));
    }

    @Test
    void 초대_링크의_코드가_입력칸에_미리_채워진다() throws Exception {
        mockMvc.perform(get("/join").param("code", "ABC234").session(sessionOf(host)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"ABC234\"")));
    }

    @Test
    void 대시보드는_멤버만_볼_수_있다() throws Exception {
        mockMvc.perform(get("/rooms/{id}", room.getId()).session(sessionOf(host)))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attribute("roomId", room.getId()));

        User outsider = userRepository.save(User.create("외부인"));
        mockMvc.perform(get("/rooms/{id}", room.getId()).session(sessionOf(outsider)))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void 정산_탭도_멤버만_볼_수_있다() throws Exception {
        mockMvc.perform(get("/rooms/{id}/settlement", room.getId()).session(sessionOf(host)))
                .andExpect(status().isOk())
                .andExpect(view().name("settlement"))
                .andExpect(content().string(containsString("정산 완료 처리")));

        User outsider = userRepository.save(User.create("외부인"));
        mockMvc.perform(get("/rooms/{id}/settlement", room.getId()).session(sessionOf(outsider)))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void 외부_주소로의_next는_버린다_오픈_리다이렉트_방지() throws Exception {
        mockMvc.perform(get("/").param("next", "//evil.com"))
                .andExpect(model().attribute("next", nullValue()));

        assertThat(PageController.safeNext("/rooms/1")).isEqualTo("/rooms/1");
        assertThat(PageController.safeNext("https://evil.com")).isNull();
        assertThat(PageController.safeNext("//evil.com")).isNull();
        assertThat(PageController.safeNext("/\\evil.com")).isNull();
        assertThat(PageController.safeNext(null)).isNull();
    }

    private MockHttpSession sessionOf(User user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(LoginUserIdArgumentResolver.SESSION_KEY, user.getId());
        return session;
    }
}
