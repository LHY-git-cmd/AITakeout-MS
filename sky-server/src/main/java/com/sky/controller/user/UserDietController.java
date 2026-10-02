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

    /**
     * 查询当前用户的饮食档案（身体数据、饮食偏好、健康目标等）
     *
     * @return 用户饮食档案详情
     */
    @GetMapping("/profile")
    public Result<UserDietProfileVO> profile() {
        return Result.success(service.getProfile(BaseContext.getCurrentId()));
    }

    /**
     * 保存或更新当前用户的饮食档案
     *
     * @param body 饮食档案数据
     * @return 保存后的饮食档案详情
     */
    @PutMapping("/profile")
    public Result<UserDietProfileVO> saveProfile(@Valid @RequestBody UserDietProfileDTO body) {
        return Result.success(service.saveProfile(BaseContext.getCurrentId(), body, MDC.get("trace_id")));
    }

    /**
     * 删除当前用户的饮食档案
     *
     * @return 操作结果
     */
    @DeleteMapping("/profile")
    public Result<Void> deleteProfile() {
        service.deleteProfile(BaseContext.getCurrentId(), MDC.get("trace_id"));
        return Result.success();
    }

    /**
     * 根据用户饮食档案生成个性化饮食推荐，支持幂等性防重复请求
     *
     * @param idempotencyKey 幂等性键，防止重复生成
     * @param body           推荐请求参数（如目标日期、餐次等）
     * @return 饮食推荐结果
     */
    @PostMapping("/recommendations")
    public Result<DietRecommendationVO> recommend(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody DietRecommendationDTO body) {
        return Result.success(service.recommend(BaseContext.getCurrentId(), idempotencyKey,
                body, MDC.get("trace_id")));
    }

    /**
     * 对饮食推荐结果提交反馈（喜欢/不喜欢/实际食用等），用于优化后续推荐
     *
     * @param recommendationId 推荐记录ID
     * @param body             反馈内容（JSON格式）
     * @return 操作结果
     */
    @PostMapping("/recommendations/{recommendationId}/feedback")
    public Result<Void> feedback(@PathVariable String recommendationId,
                                 @RequestBody Map<String, Object> body) {
        service.feedback(BaseContext.getCurrentId(), recommendationId, body);
        return Result.success();
    }
}