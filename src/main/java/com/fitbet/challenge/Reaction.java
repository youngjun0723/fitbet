package com.fitbet.challenge;

import com.fitbet.common.entity.BaseTimeEntity;
import com.fitbet.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "reaction",
        uniqueConstraints = @UniqueConstraint(name = "uk_reaction_log_user",
                columnNames = {"challenge_log_id", "user_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reaction extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "challenge_log_id", nullable = false)
    private ChallengeLog challengeLog;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReactionType type;

    private Reaction(ChallengeLog challengeLog, User user, ReactionType type) {
        this.challengeLog = challengeLog;
        this.user = user;
        this.type = type;
    }

    public static Reaction of(ChallengeLog challengeLog, User user, ReactionType type) {
        return new Reaction(challengeLog, user, type);
    }

    /** 1인 1리액션이지만 변경은 가능 (PRD 2.5) — 새 행을 만들지 않고 type만 바꾼다. */
    public void changeType(ReactionType type) {
        this.type = type;
    }
}
