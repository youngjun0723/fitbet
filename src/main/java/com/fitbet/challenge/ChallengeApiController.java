package com.fitbet.challenge;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fitbet.challenge.ChallengeService.UploadResult;
import com.fitbet.common.auth.LoginUserId;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ChallengeApiController {

    private final ChallengeService challengeService;

    /** multipart/form-data: photo(파일) + memo(텍스트). JSON이 아니므로 @RequestBody 대신 @ModelAttribute. */
    @PostMapping(value = "/api/rooms/{roomId}/challenges", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UploadResult upload(@LoginUserId Long userId,
                               @PathVariable Long roomId,
                               @Valid @ModelAttribute UploadForm form) {
        String memo = (form.memo() == null || form.memo().isBlank()) ? null : form.memo().strip();
        return challengeService.upload(userId, roomId, form.photo(), memo);
    }

    public record UploadForm(@NotNull MultipartFile photo, @Size(max = 100) String memo) {
    }
}
