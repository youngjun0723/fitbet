package com.fitbet.user;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fitbet.common.auth.LoginUserIdArgumentResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserApiController {

    private final UserService userService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        User user = userService.loginOrRegister(request.username().strip());
        httpRequest.getSession(true).setAttribute(LoginUserIdArgumentResolver.SESSION_KEY, user.getId());
        return new LoginResponse(user.getId(), user.getUsername());
    }

    public record LoginRequest(@NotBlank @Size(max = 30) String username) {
    }

    public record LoginResponse(Long userId, String username) {
    }
}
