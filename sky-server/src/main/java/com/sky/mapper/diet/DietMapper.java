package com.sky.mapper.diet;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 个性化饮食数据访问层；结构化事实和执行轨迹统一从 MySQL 读取。 */
@Mapper
public interface DietMapper {
    @Select("select region_code regionCode,dietary_pattern dietaryPattern,consent_version consentVersion,consented_at consentedAt,version from user_diet_profile where user_id=#{userId} and status='ACTIVE' and deleted_at is null")
    Map<String, Object> getProfile(long userId);

    @Select("select constraint_type type,constraint_code code,severity,source_type sourceType from user_diet_constraint where user_id=#{userId} and (effective_until is null or effective_until>now()) order by constraint_type,constraint_code")
    List<Map<String, Object>> listConstraints(long userId);

    @Select("select goal_code code,priority from user_health_goal where user_id=#{userId} and (effective_until is null or effective_until>now()) order by priority desc,goal_code")
    List<Map<String, Object>> listGoals(long userId);

    @Insert("""
            insert into user_diet_profile(user_id,region_code,dietary_pattern,consent_version,consented_at,status,version)
            values(#{userId},#{regionCode},#{dietaryPattern},#{consentVersion},now(),'ACTIVE',0)
            on duplicate key update region_code=values(region_code),dietary_pattern=values(dietary_pattern),
              consent_version=values(consent_version),consented_at=now(),status='ACTIVE',deleted_at=null,version=version+1
            """)
    int upsertProfile(@Param("userId") long userId, @Param("regionCode") String regionCode,
                      @Param("dietaryPattern") String dietaryPattern,
                      @Param("consentVersion") String consentVersion);

    @Delete("delete from user_diet_constraint where user_id=#{userId}")
    int deleteConstraints(long userId);

    @Insert("insert into user_diet_constraint(user_id,constraint_type,constraint_code,severity,source_type) values(#{userId},#{type},#{code},#{severity},#{sourceType})")
    int insertConstraint(@Param("userId") long userId, @Param("type") String type,
                         @Param("code") String code, @Param("severity") String severity,
                         @Param("sourceType") String sourceType);

    @Delete("delete from user_health_goal where user_id=#{userId}")
    int deleteGoals(long userId);

    @Insert("insert into user_health_goal(user_id,goal_code,priority) values(#{userId},#{code},#{priority})")
    int insertGoal(@Param("userId") long userId, @Param("code") String code,
                   @Param("priority") int priority);

    @Update("update user_diet_profile set status='DELETED',deleted_at=now(),version=version+1 where user_id=#{userId} and deleted_at is null")
    int deleteProfile(long userId);

    @Insert("insert into user_diet_profile_audit(user_id,action,detail_json,trace_id) values(#{userId},#{action},cast(#{detailJson} as json),#{traceId})")
    int insertProfileAudit(@Param("userId") long userId, @Param("action") String action,
                           @Param("detailJson") String detailJson, @Param("traceId") String traceId);

    @Select("select * from dish_nutrition_profile where dish_id=#{dishId} order by profile_version desc limit 1")
    Map<String, Object> getLatestNutrition(long dishId);

    @Insert("""
            insert into dish_recipe_version(dish_id,recipe_version,status,created_by)
            values(#{dishId},#{recipeVersion},'DRAFT',#{operator})
            on duplicate key update update_time=now()
            """)
    int ensureRecipe(@Param("dishId") long dishId, @Param("recipeVersion") int recipeVersion,
                     @Param("operator") long operator);

    @Insert("""
            insert into dish_nutrition_profile(dish_id,profile_version,recipe_version,serving_size_g,
              energy_kcal,protein_g,fat_g,carbohydrate_g,dietary_fiber_g,sugar_g,sodium_mg,purine_mg,
              source_type,source_reference,calculation_method,verification_status,data_completeness,uncertainty_note)
            values(#{dishId},#{profileVersion},#{recipeVersion},#{servingSizeG},#{energyKcal},#{proteinG},
              #{fatG},#{carbohydrateG},#{dietaryFiberG},#{sugarG},#{sodiumMg},#{purineMg},#{sourceType},
              #{sourceReference},#{calculationMethod},'DRAFT',#{dataCompleteness},#{uncertaintyNote})
            on duplicate key update recipe_version=values(recipe_version),serving_size_g=values(serving_size_g),
              energy_kcal=values(energy_kcal),protein_g=values(protein_g),fat_g=values(fat_g),
              carbohydrate_g=values(carbohydrate_g),dietary_fiber_g=values(dietary_fiber_g),
              sugar_g=values(sugar_g),sodium_mg=values(sodium_mg),purine_mg=values(purine_mg),
              source_type=values(source_type),source_reference=values(source_reference),
              calculation_method=values(calculation_method),verification_status='DRAFT',
              data_completeness=values(data_completeness),uncertainty_note=values(uncertainty_note),version=version+1
            """)
    int upsertNutrition(Map<String, Object> value);

    @Delete("delete from dish_ingredient where dish_id=#{dishId} and recipe_version=#{recipeVersion}")
    int deleteDishIngredients(@Param("dishId") long dishId, @Param("recipeVersion") int recipeVersion);

    @Insert("insert into food_ingredient(ingredient_code,name) values(#{code},#{name}) on duplicate key update name=values(name)")
    int upsertIngredient(@Param("code") String code, @Param("name") String name);

    @Select("select id from food_ingredient where ingredient_code=#{code}")
    Long getIngredientId(String code);

    @Insert("insert into dish_ingredient(dish_id,recipe_version,ingredient_id,amount_g,role_type,replaceable) values(#{dishId},#{recipeVersion},#{ingredientId},#{amountG},#{roleType},#{replaceable})")
    int insertDishIngredient(@Param("dishId") long dishId, @Param("recipeVersion") int recipeVersion,
                             @Param("ingredientId") long ingredientId,
                             @Param("amountG") BigDecimal amountG,
                             @Param("roleType") String roleType,
                             @Param("replaceable") boolean replaceable);

    @Delete("delete from dish_allergen_declaration where dish_id=#{dishId} and recipe_version=#{recipeVersion}")
    int deleteDishAllergens(@Param("dishId") long dishId, @Param("recipeVersion") int recipeVersion);

    @Insert("insert into dish_allergen_declaration(dish_id,recipe_version,allergen_code,declaration_status,source_reference) values(#{dishId},#{recipeVersion},#{code},#{status},#{sourceReference})")
    int insertDishAllergen(@Param("dishId") long dishId, @Param("recipeVersion") int recipeVersion,
                           @Param("code") String code, @Param("status") String status,
                           @Param("sourceReference") String sourceReference);

    @Update("""
            update dish_nutrition_profile set verification_status='VERIFIED',verified_by=#{operator},
              verified_at=now(),effective_from=coalesce(effective_from,now()),version=version+1
            where dish_id=#{dishId} and profile_version=#{profileVersion} and verification_status='DRAFT'
            """)
    int verifyNutrition(@Param("dishId") long dishId, @Param("profileVersion") int profileVersion,
                        @Param("operator") long operator);

    @Update("update dish_recipe_version set status='VERIFIED',verified_by=#{operator},effective_from=coalesce(effective_from,now()) where dish_id=#{dishId} and recipe_version=#{recipeVersion}")
    int verifyRecipe(@Param("dishId") long dishId, @Param("recipeVersion") int recipeVersion,
                     @Param("operator") long operator);

    @Select("select count(*) from setmeal where id=#{setmealId}")
    int countSetmeal(long setmealId);

    @Select("""
            select count(sd.dish_id) componentCount,count(n.id) verifiedCount,
              sum(n.energy_kcal*sd.copies) energyKcal,sum(n.protein_g*sd.copies) proteinG,
              sum(n.fat_g*sd.copies) fatG,sum(n.carbohydrate_g*sd.copies) carbohydrateG,
              sum(n.dietary_fiber_g*sd.copies) dietaryFiberG,sum(n.sugar_g*sd.copies) sugarG,
              sum(n.sodium_mg*sd.copies) sodiumMg
            from setmeal_dish sd left join dish_nutrition_profile n on n.id=(
              select n2.id from dish_nutrition_profile n2 where n2.dish_id=sd.dish_id
                and (n2.verification_status='VERIFIED' or (#{allowSimulated}=true and n2.verification_status='SIMULATED'))
                and (n2.effective_from is null or n2.effective_from<=now())
                and (n2.effective_until is null or n2.effective_until>now())
              order by n2.profile_version desc limit 1)
            where sd.setmeal_id=#{setmealId}
            """)
    Map<String, Object> aggregateSetmealNutrition(@Param("setmealId") long setmealId,
                                                   @Param("allowSimulated") boolean allowSimulated);

    @Select("""
            select sd.dish_id dishId,sd.copies,n.profile_version profileVersion,
              a.allergen_code allergenCode,a.declaration_status declarationStatus
            from setmeal_dish sd join dish_nutrition_profile n on n.id=(
              select n2.id from dish_nutrition_profile n2 where n2.dish_id=sd.dish_id
                and (n2.verification_status='VERIFIED' or (#{allowSimulated}=true and n2.verification_status='SIMULATED'))
                order by n2.profile_version desc limit 1)
            left join dish_allergen_declaration a on a.dish_id=sd.dish_id and a.recipe_version=n.recipe_version
            where sd.setmeal_id=#{setmealId}
            """)
    List<Map<String, Object>> listSetmealSources(@Param("setmealId") long setmealId,
                                                 @Param("allowSimulated") boolean allowSimulated);

    @Select("select coalesce(max(snapshot_version),0) from setmeal_nutrition_snapshot where setmeal_id=#{setmealId}")
    int getMaxSetmealSnapshotVersion(long setmealId);

    @Insert("""
            insert into setmeal_nutrition_snapshot(setmeal_id,snapshot_version,composition_hash,energy_kcal,
              protein_g,fat_g,carbohydrate_g,dietary_fiber_g,sugar_g,sodium_mg,allergens_json,
              source_versions_json,verification_status)
            values(#{setmealId},#{snapshotVersion},#{compositionHash},#{energyKcal},#{proteinG},#{fatG},
              #{carbohydrateG},#{dietaryFiberG},#{sugarG},#{sodiumMg},cast(#{allergensJson} as json),
              cast(#{sourceVersionsJson} as json),#{verificationStatus})
            """)
    int insertSetmealSnapshot(Map<String, Object> value);

    List<Map<String, Object>> listRecommendationCandidates(@Param("budget") BigDecimal budget,
                                                            @Param("allowSimulated") boolean allowSimulated);
    List<Map<String, Object>> listCandidateAllergens(@Param("dishIds") List<Long> dishIds);
    List<Map<String, Object>> listCandidateIngredients(@Param("dishIds") List<Long> dishIds);

    List<Map<String, Object>> listCandidateAdaptations(@Param("dishIds") List<Long> dishIds);
    List<Map<String, Object>> listSeasonalMatches(@Param("dishIds") List<Long> dishIds,
                                                  @Param("regionCode") String regionCode,
                                                  @Param("month") int month);

    @Select("select rule_set_id ruleSetId,rule_code ruleCode,rule_version ruleVersion from diet_rule_set where status='PUBLISHED' and (effective_from is null or effective_from<=now()) and (effective_until is null or effective_until>now())")
    List<Map<String, Object>> listActiveRuleSets();

    @Select("""
            select rs.rule_set_id ruleSetId,rs.rule_code ruleCode,rs.rule_version ruleVersion,
              r.target_field targetField,r.operator,r.comparison_value comparisonValue,
              r.action,r.score_delta scoreDelta,r.reason_code reasonCode,r.message
            from diet_rule_set rs join diet_rule r on r.rule_set_id=rs.rule_set_id
            where rs.status='PUBLISHED' and (rs.effective_from is null or rs.effective_from<=now())
              and (rs.effective_until is null or rs.effective_until>now())
            order by r.priority,r.id
            """)
    List<Map<String, Object>> listActiveRules();

    @Select("select * from diet_rule_set order by create_time desc")
    List<Map<String, Object>> listRuleSets();

    @Select("select * from diet_rule_set where rule_set_id=#{ruleSetId}")
    Map<String, Object> getRuleSet(String ruleSetId);

    @Select("select coalesce(max(rule_version),0) from diet_rule_set where rule_code=#{ruleCode}")
    int getMaxRuleVersion(String ruleCode);

    @Insert("insert into diet_rule_set(rule_set_id,rule_code,name,applicable_population,rule_version,status,created_by) values(#{ruleSetId},#{ruleCode},#{name},#{applicablePopulation},#{ruleVersion},'DRAFT',#{createdBy})")
    int insertRuleSet(Map<String, Object> value);

    @Insert("insert into diet_rule(rule_set_id,rule_id,target_field,operator,comparison_value,action,score_delta,priority,reason_code,message) values(#{ruleSetId},#{ruleId},#{targetField},#{operator},#{comparisonValue},#{action},#{scoreDelta},#{priority},#{reasonCode},#{message})")
    int insertRule(Map<String, Object> value);

    @Insert("insert into diet_rule_source(rule_set_id,source_title,source_url,source_section,published_date,review_due_date) values(#{ruleSetId},#{sourceTitle},#{sourceUrl},#{sourceSection},#{publishedDate},#{reviewDueDate})")
    int insertRuleSource(Map<String, Object> value);

    @Select("select count(*) from diet_rule where rule_set_id=#{ruleSetId}")
    int countRules(String ruleSetId);

    @Select("select count(*) from diet_rule_source where rule_set_id=#{ruleSetId} and review_due_date>=curdate()")
    int countCurrentSources(String ruleSetId);

    @Update("update diet_rule_set set status='VALIDATED' where rule_set_id=#{ruleSetId} and status='DRAFT'")
    int validateRuleSet(String ruleSetId);

    @Update("update diet_rule_set set status=#{target},published_by=#{operator},published_at=case when #{target}='PUBLISHED' then now() else published_at end,effective_from=case when #{target}='PUBLISHED' then coalesce(effective_from,now()) else effective_from end where rule_set_id=#{ruleSetId} and status=#{expected}")
    int changeRuleSetStatus(@Param("ruleSetId") String ruleSetId, @Param("expected") String expected,
                            @Param("target") String target, @Param("operator") long operator);

    @Update("update diet_rule_set set status='SUPERSEDED',effective_until=now() where rule_code=#{ruleCode} and rule_set_id<>#{ruleSetId} and status='PUBLISHED'")
    int supersedePublishedRuleSets(@Param("ruleCode") String ruleCode,
                                   @Param("ruleSetId") String ruleSetId);

    @Insert("insert into diet_rule_review(rule_set_id,reviewer_id,decision,comment) values(#{ruleSetId},#{operator},#{decision},#{comment})")
    int insertRuleReview(@Param("ruleSetId") String ruleSetId, @Param("operator") long operator,
                         @Param("decision") String decision, @Param("comment") String comment);

    @Insert("insert into diet_rule_publish_audit(rule_set_id,operator_id,action,from_status,to_status) values(#{ruleSetId},#{operator},#{action},#{fromStatus},#{toStatus})")
    int insertRuleAudit(@Param("ruleSetId") String ruleSetId, @Param("operator") long operator,
                        @Param("action") String action, @Param("fromStatus") String fromStatus,
                        @Param("toStatus") String toStatus);

    @Insert("""
            insert into diet_recommendation_session(recommendation_id,user_id,idempotency_key,scene,risk_level,
              request_hash,request_json,rule_versions_json,status,trace_id)
            values(#{recommendationId},#{userId},#{idempotencyKey},#{scene},#{riskLevel},#{requestHash},
              cast(#{requestJson} as json),cast(#{ruleVersionsJson} as json),#{status},#{traceId})
            """)
    int insertRecommendationSession(Map<String, Object> value);

    @Select("select recommendation_id recommendationId,status,risk_level riskLevel from diet_recommendation_session where user_id=#{userId} and idempotency_key=#{idempotencyKey}")
    Map<String, Object> getRecommendationByKey(@Param("userId") long userId,
                                                @Param("idempotencyKey") String idempotencyKey);

    @Insert("""
            insert into diet_recommendation_candidate(recommendation_id,product_type,product_id,eligible,score,
              reason_codes_json,score_detail_json,data_versions_json)
            values(#{recommendationId},#{productType},#{productId},#{eligible},#{score},
              cast(#{reasonCodesJson} as json),cast(#{scoreDetailJson} as json),cast(#{dataVersionsJson} as json))
            """)
    int insertCandidateTrace(Map<String, Object> value);

    @Select("select product_type productType,product_id productId,eligible,score,reason_codes_json reasonCodesJson,score_detail_json scoreDetailJson from diet_recommendation_candidate where recommendation_id=#{recommendationId} order by eligible desc,score desc,id")
    List<Map<String, Object>> listCandidateTrace(String recommendationId);

    @Insert("""
            insert ignore into diet_recommendation_feedback(recommendation_id,user_id,product_type,product_id,feedback_type,reason_code)
            values(#{recommendationId},#{userId},#{productType},#{productId},#{feedbackType},#{reasonCode})
            """)
    int insertFeedback(Map<String, Object> value);

    @Select("""
            select (select count(*) from dish) totalDishes,
              (select count(distinct dish_id) from dish_nutrition_profile where verification_status='VERIFIED') verifiedNutritionDishes,
              (select count(distinct dish_id) from dish_nutrition_profile where verification_status='SIMULATED') simulatedNutritionDishes,
              (select count(*) from dish_allergen_declaration where declaration_status='UNKNOWN') unknownAllergenDeclarations,
              (select count(*) from diet_rule_set where status='PUBLISHED') publishedRuleSets,
              (select count(*) from diet_recommendation_session) recommendationRequests,
              (select count(*) from diet_recommendation_session where status='NO_MATCH') noMatchRequests
            """)
    Map<String, Object> qualityDashboard();
}
