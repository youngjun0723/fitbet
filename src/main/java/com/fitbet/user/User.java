package com.fitbet.user;

import com.fitbet.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users") // user는 H2/MySQL 예약어라 복수형 테이블명 사용
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA용 기본 생성자. 외부에서 빈 객체 생성은 막는다
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String username;

    @Column(length = 255)
    private String profileImageUrl;

    private User(String username) {
        this.username = username;
    }

    public static User create(String username) {
        return new User(username);
    }
}
