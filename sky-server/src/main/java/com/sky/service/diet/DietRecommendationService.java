package com.sky.service.diet;

import com.sky.dto.DietRecommendationDTO;
import com.sky.dto.DishNutritionDTO;
import com.sky.dto.UserDietProfileDTO;
import com.sky.vo.DietRecommendationVO;
import com.sky.vo.UserDietProfileVO;

import java.util.Map;

/** 个性化饮食业务边界。 */
public interface DietRecommendationService {
    UserDietProfileVO getProfile(long userId);
    UserDietProfileVO saveProfile(long userId, UserDietProfileDTO request, String traceId);
    void deleteProfile(long userId, String traceId);
    Map<String, Object> getNutrition(long dishId);
    void saveNutrition(long dishId, DishNutritionDTO request, long operator);
    void verifyNutrition(long dishId, int profileVersion, long operator);
    Map<String, Object> recalculateSetmealNutrition(long setmealId);
    DietRecommendationVO recommend(long userId, String idempotencyKey,
                                   DietRecommendationDTO request, String traceId);
    void feedback(long userId, String recommendationId, Map<String, Object> request);
    Map<String, Object> qualityDashboard();
}
