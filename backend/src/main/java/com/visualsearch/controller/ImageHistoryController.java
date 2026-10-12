
package com.visualsearch.controller;

import com.visualsearch.dto.BaseResponse;
import com.visualsearch.dto.response.ImageUploadHistoryResponse;
import com.visualsearch.entity.User;
import com.visualsearch.service.ImageHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageHistoryController {

    private final ImageHistoryService imageHistoryService;

    @GetMapping("/my-uploads")
    public BaseResponse<Page<ImageUploadHistoryResponse>> getMyUploads(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User currentUser
    ) {
        Page<ImageUploadHistoryResponse> result =
                imageHistoryService.getMyUploads(
                        currentUser.getId(),
                        page,
                        size
                );

        return BaseResponse.of(
                HttpStatus.OK.value(),
                "Lấy lịch sử upload thành công",
                result
        );
    }
}
