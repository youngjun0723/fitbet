package com.fitbet.challenge;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.fitbet.common.exception.ErrorCode;

class ImageFileValidatorTest {

    ImageFileValidator validator = new ImageFileValidator();

    @ParameterizedTest
    @CsvSource({
            "a.jpg, image/jpeg",
            "a.JPEG, image/jpeg",
            "a.png, image/png",
            "a.heic, image/heic",
    })
    void 허용된_이미지는_통과(String filename, String contentType) {
        var file = new MockMultipartFile("photo", filename, contentType, new byte[]{1, 2, 3});

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({
            "a.gif, image/gif",          // 허용 안 된 확장자
            "a.exe, image/jpeg",         // 확장자 위장
            "a.jpg, text/html",          // MIME 위장
            "noext, image/jpeg",         // 확장자 없음
    })
    void 허용되지_않은_파일은_INVALID_IMAGE(String filename, String contentType) {
        var file = new MockMultipartFile("photo", filename, contentType, new byte[]{1, 2, 3});

        assertThatThrownBy(() -> validator.validate(file))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_IMAGE);
    }

    @Test
    void 빈_파일은_INVALID_IMAGE() {
        var file = new MockMultipartFile("photo", "a.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> validator.validate(file))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_IMAGE);
    }

    @Test
    void 크기가_10MB를_넘으면_FILE_TOO_LARGE() {
        var file = new MockMultipartFile("photo", "a.jpg", "image/jpeg",
                new byte[(int) ImageFileValidator.MAX_SIZE + 1]);

        assertThatThrownBy(() -> validator.validate(file))
                .extracting("errorCode").isEqualTo(ErrorCode.FILE_TOO_LARGE);
    }
}
