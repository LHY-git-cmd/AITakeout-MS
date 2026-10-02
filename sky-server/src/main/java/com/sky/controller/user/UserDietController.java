package com.sky.controller.user;

import com.sky.context.BaseContext;
import com.sky.dto.DietRecommendationDTO;
import com.sky.dto.UserDietProfileDTO;
import com.sky.result.Result;
import com.sky.service.diet.DietRecommendationService;
import com.sky.vo.DietRecommendationVO;
import com.sky.vo.UserDietProfileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 用户饮食档案、推荐和反馈 API。 */
@RestController
@RequestMapping("/user/diet")
@RequiredArgsConstructor
public class UserDietController {
    private final DietRecommendationService service;

    @GetMapping("/profile")
    public Result<UserDietProfileVO> profile() {
        return Result.success(service.getProfile(BaseContext.getCurrentId()));
    }

    @PutMapping("/profile")
    public Result<UserDietProfileVO> saveProfile(@Valid @RequestBody UserDietProfileDTO body) {
        return Result.success(service.saveProfile(BaseContext.getCurrentId(), body, MDC.get("trace_id")));
    }

    @DeleteMapping("/profile")
    public Result<Void> deleteProfile() {
        service.deleteProfile(BaseContext.getCurrentId(), MDC.get("trace_id"));
        return Result.success();
    }

    @PostMapping("/recommendations")
    public Result<DietRecommendationVO> recommend(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody DietRecommendationDTO body) {
        return Result.success(service.recommend(BaseContext.getCurrentId(), idempotencyKey,
                body, MDC.get("trace_id")));
    }

    @PostMapping("/recommendations/{recommendationId}/feedback")
    public Result<Void> feedback(@PathVariable String recommendationId,
                                 @RequestBody Map<String, Object> body) {
        service.feedback(BaseContext.getCurrentId(), recommendationId, body);
        return Result.success();
    }
}
