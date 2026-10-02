package com.fitbet.common.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** 컨트롤러마다 try-catch 하지 않고, 예외 → HTTP 응답 변환을 한 곳에서 처리한다. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        return toResponse(e.getErrorCode());
    }

    /** @RequestBody(JSON)와 @ModelAttribute(폼/멀티파트) 검증 실패 모두 BindException 계열이다. */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleValidation(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse(ErrorCode.INVALID_INPUT.getMessage());
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(new ErrorResponse(ErrorCode.INVALID_INPUT.name(), message));
    }

    /** spring.servlet.multipart.max-file-size 초과 시 컨트롤러에 도달하기 전에 터지는 예외 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return toResponse(ErrorCode.FILE_TOO_LARGE);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException e) {
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(new ErrorResponse(ErrorCode.INVALID_INPUT.name(), e.getRequestPartName() + ": 필수입니다."));
    }

    private ResponseEntity<ErrorResponse> toResponse(ErrorCode code) {
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code));
    }
}
