package com.fitbet.room;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/** 6자리 초대 코드 생성기. 헷갈리는 문자(0/O, 1/I/L)는 뺐다. */
@Component
public class InviteCodeGenerator {

    static final String CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int LENGTH = 6;

    // Random은 예측 가능 → 초대 코드처럼 "추측되면 안 되는 값"에는 SecureRandom
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
