package com.fitbet.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    LOGIN_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "방을 찾을 수 없습니다."),
    INVITE_CODE_NOT_FOUND(HttpStatus.NOT_FOUND, "유효하지 않은 초대 코드입니다."),
    ALREADY_JOINED(HttpStatus.CONFLICT, "이미 참여한 방입니다."),
    INVITE_CODE_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "초대 코드 생성에 실패했습니다."),

    NOT_ROOM_MEMBER(HttpStatus.FORBIDDEN, "방 멤버만 이용할 수 있습니다."),
    ALREADY_VERIFIED_TODAY(HttpStatus.CONFLICT, "오늘은 이미 인증했습니다."),
    INVALID_IMAGE(HttpStatus.BAD_REQUEST, "jpg, png, heic 이미지만 올릴 수 있습니다."),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "사진은 10MB 이하만 올릴 수 있습니다."),
    FILE_STORE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "사진 저장에 실패했습니다.");

    private final HttpStatus status;
    private final String message;
}
