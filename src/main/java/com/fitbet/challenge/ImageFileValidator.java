package com.fitbet.challenge;

import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.fitbet.common.exception.BusinessException;
import com.fitbet.common.exception.ErrorCode;

/** PRD 5.1-1: 이미지(jpg/png/heic)이고 10MB 이하인지. 확장자와 MIME 타입을 둘 다 본다. */
@Component
public class ImageFileValidator {

    static final long MAX_SIZE = 10L * 1024 * 1024;
    static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "heic");
    static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/heic", "image/heif");

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE);
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (ext == null || !ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE);
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE);
        }
    }
}
