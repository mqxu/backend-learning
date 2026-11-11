package com.campus.activity.controller.app;

import com.campus.activity.common.Result;
import com.campus.activity.common.UserContext;
import com.campus.activity.dto.CheckinRequest;
import com.campus.activity.service.CheckinService;
import com.campus.activity.vo.CheckinResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app/checkin")
@RequiredArgsConstructor
public class AppCheckinController {

    private final CheckinService checkinService;

    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public Result<CheckinResultVO> checkin(@Valid @RequestBody CheckinRequest request) {
        return Result.ok(checkinService.checkin(UserContext.get(), request));
    }
}
