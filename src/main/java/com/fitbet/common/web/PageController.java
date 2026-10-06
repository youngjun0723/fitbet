package com.fitbet.common.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;

import com.fitbet.common.auth.LoginUserIdArgumentResolver;
import com.fitbet.room.RoomService;
import com.fitbet.user.UserService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 화면(HTML) 라우팅 전용 컨트롤러. 데이터는 각 페이지의 JS가 /api/** 를 호출해서 채운다.
 * - @RestController(JSON)와 달리 @Controller는 반환값을 "템플릿 이름"으로 해석한다.
 * - 화면은 401 JSON 대신 로그인 페이지로 redirect 해야 하므로 @LoginUserId 대신 required=false로 직접 받는다.
 */
@Controller
@RequiredArgsConstructor
public class PageController {

    private static final String LOGIN_KEY = LoginUserIdArgumentResolver.SESSION_KEY;

    private final UserService userService;
    private final RoomService roomService;

    /** PRD 6.4: 랜딩 & 닉네임 로그인. 로그인 상태면 내 방 목록. */
    @GetMapping("/")
    public String landing(@SessionAttribute(name = LOGIN_KEY, required = false) Long userId,
                          @RequestParam(required = false) String next,
                          Model model) {
        model.addAttribute("next", safeNext(next));
        Optional<String> username = (userId == null) ? Optional.empty() : userService.findUsername(userId);
        username.ifPresent(name -> {
            model.addAttribute("username", name);
            model.addAttribute("myRooms", roomService.findMyRooms(userId));
        });
        return "index";
    }

    @GetMapping("/rooms/new")
    public String newRoom(@SessionAttribute(name = LOGIN_KEY, required = false) Long userId,
                          HttpServletRequest request) {
        if (userId == null) {
            return redirectToLogin(request);
        }
        return "room-new";
    }

    /** 초대 링크 /join?code=ABC234 로 들어오면 코드가 미리 채워진다 (PRD 1.3) */
    @GetMapping("/join")
    public String join(@SessionAttribute(name = LOGIN_KEY, required = false) Long userId,
                       @RequestParam(required = false) String code,
                       HttpServletRequest request, Model model) {
        if (userId == null) {
            return redirectToLogin(request);
        }
        model.addAttribute("code", code);
        return "join";
    }

    /** PRD 6.1 홈 대시보드 */
    @GetMapping("/rooms/{roomId}")
    public String dashboard(@SessionAttribute(name = LOGIN_KEY, required = false) Long userId,
                            @PathVariable Long roomId,
                            HttpServletRequest request, Model model) {
        if (userId == null) {
            return redirectToLogin(request);
        }
        if (!roomService.isMember(userId, roomId)) {
            return "redirect:/";
        }
        model.addAttribute("roomId", roomId);
        return "dashboard";
    }

    /** 로그인 후 원래 보던 페이지로 돌아오도록 현재 주소를 next 파라미터로 넘긴다. */
    private String redirectToLogin(HttpServletRequest request) {
        String current = request.getRequestURI()
                + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        return "redirect:/?next=" + URLEncoder.encode(current, StandardCharsets.UTF_8);
    }

    /**
     * 오픈 리다이렉트 방지: next=https://evil.com 이나 //evil.com 같은 "외부 주소"는 버린다.
     * 우리 사이트 내부 경로(/로 시작, //로 시작하지 않음)만 허용.
     */
    static String safeNext(String next) {
        if (next == null || !next.startsWith("/") || next.startsWith("//") || next.contains("\\")) {
            return null;
        }
        return next;
    }
}
