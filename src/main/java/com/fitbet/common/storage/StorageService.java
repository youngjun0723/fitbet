package com.fitbet.common.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 저장소 추상화 (PRD 3).
 * 지금은 LocalStorageService(로컬 디스크)지만, 나중에 S3StorageService로 갈아끼워도
 * 이 인터페이스에만 의존하는 서비스 코드는 바뀌지 않는다.
 */
public interface StorageService {

    /** 파일을 저장하고, 브라우저에서 접근할 수 있는 URL을 돌려준다. */
    String store(MultipartFile file);

    /** store()가 돌려준 URL의 파일을 지운다. 없으면 조용히 넘어간다. */
    void delete(String url);
}
