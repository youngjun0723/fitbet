package com.fitbet.common.exception;

import lombok.Getter;

/** 도메인 규칙 위반을 표현하는 예외. ErrorCode가 HTTP 상태와 메시지를 결정한다. */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
