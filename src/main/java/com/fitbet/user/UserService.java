package com.fitbet.user;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /** 닉네임 기반 간단 로그인 (PRD 6.4): 있으면 그 사용자, 없으면 새로 가입. */
    @Transactional
    public User loginOrRegister(String username) {
        return userRepository.findByUsername(username)
                .orElseGet(() -> userRepository.save(User.create(username)));
    }

    @Transactional(readOnly = true)
    public Optional<String> findUsername(Long userId) {
        return userRepository.findById(userId).map(User::getUsername);
    }
}
