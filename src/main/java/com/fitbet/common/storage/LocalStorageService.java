package com.fitbet.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.fitbet.common.exception.BusinessException;
import com.fitbet.common.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class LocalStorageService implements StorageService {

    public static final String URL_PREFIX = "/uploads/";

    private final Path root;

    public LocalStorageService(@Value("${fitbet.storage.local-dir}") String localDir) {
        this.root = Path.of(localDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("업로드 폴더를 만들 수 없습니다: " + root, e);
        }
    }

    @Override
    public String store(MultipartFile file) {
        // 사용자가 보낸 파일명은 쓰지 않는다: 한글/공백/중복/경로 조작(../) 문제를 UUID로 한 번에 회피
        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        String filename = UUID.randomUUID() + "." + ext.toLowerCase();
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, root.resolve(filename));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_STORE_FAILED);
        }
        return URL_PREFIX + filename;
    }

    @Override
    public void delete(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) {
            return;
        }
        Path target = root.resolve(url.substring(URL_PREFIX.length())).normalize();
        if (!target.startsWith(root)) { // 업로드 폴더 밖의 파일은 절대 지우지 않는다
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            // 보상 삭제 실패로 요청 전체를 실패시키진 않는다. 고아 파일은 로그로 추적.
            log.warn("업로드 파일 삭제 실패: {}", target, e);
        }
    }
}
