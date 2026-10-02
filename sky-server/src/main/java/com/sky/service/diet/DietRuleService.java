package com.sky.service.diet;

import com.sky.exception.AgentBusinessException;
import com.sky.mapper.diet.DietMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 饮食规则版本的创建、校验、发布、下线和回滚服务。 */
@Service
@RequiredArgsConstructor
public class DietRuleService {
    private static final Set<String> OPERATORS = Set.of("EQ", "IN", "CONTAINS", "LT", "LTE", "GT", "GTE", "UNKNOWN");
    private static final Set<String> ACTIONS = Set.of("EXCLUDE", "WARN", "BOOST", "PENALIZE", "REQUIRE_CLARIFICATION");
    private final DietMapper mapper;

    public List<Map<String, Object>> list() { return mapper.listRuleSets(); }

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> create(Map<String, Object> body, long operator) {
        String code = code(body.get("rule_code"));
        String name = required(body.get("name"), "规则集名称");
        String population = required(body.get("applicable_population"), "适用人群");
        List<Map<String, Object>> rules = (List<Map<String, Object>>) body.getOrDefault("rules", List.of());
        List<Map<String, Object>> sources = (List<Map<String, Object>>) body.getOrDefault("sources", List.of());
        if (rules.isEmpty()) throw new AgentBusinessException("规则集至少包含一条规则");
        if (sources.isEmpty()) throw new AgentBusinessException("规则集必须绑定权威来源");
        String id = "diet-rule-" + UUID.randomUUID().toString().replace("-", "");
        int version = mapper.getMaxRuleVersion(code) + 1;
        mapper.insertRuleSet(Map.of("ruleSetId", id, "ruleCode", code, "name", name,
                "applicablePopulation", population, "ruleVersion", version, "createdBy", operator));
        for (Map<String, Object> rule : rules) {
            String op = code(rule.get("operator")); String action = code(rule.get("action"));
            if (!OPERATORS.contains(op)) throw new AgentBusinessException("规则操作符不受支持");
            if (!ACTIONS.contains(action)) throw new AgentBusinessException("规则动作不受支持");
            rule.put("ruleSetId", id); rule.put("ruleId", UUID.randomUUID().toString().replace("-", ""));
            rule.put("targetField", code(rule.get("target_field"))); rule.put("operator", op);
            rule.put("comparisonValue", rule.get("comparison_value")); rule.put("action", action);
            rule.put("scoreDelta", rule.get("score_delta")); rule.put("priority", rule.getOrDefault("priority", 100));
            rule.put("reasonCode", code(rule.get("reason_code")));
            rule.put("message", required(rule.get("message"), "规则说明"));
            mapper.insertRule(rule);
        }
        for (Map<String, Object> source : sources) {
            source.put("ruleSetId", id); source.put("sourceTitle", required(source.get("source_title"), "来源标题"));
            source.put("sourceUrl", required(source.get("source_url"), "来源地址"));
            source.put("sourceSection", source.get("source_section")); source.put("publishedDate", source.get("published_date"));
            source.put("reviewDueDate", source.getOrDefault("review_due_date", LocalDate.now().plusYears(1).toString()));
            mapper.insertRuleSource(source);
        }
        mapper.insertRuleAudit(id, operator, "CREATE", null, "DRAFT");
        return mapper.getRuleSet(id);
    }

    @Transactional
    public void validate(String id, long operator) {
        require(id);
        if (mapper.countRules(id) == 0 || mapper.countCurrentSources(id) == 0) {
            throw new AgentBusinessException("规则或有效权威来源不完整");
        }
        if (mapper.validateRuleSet(id) != 1) throw new AgentBusinessException("只有草稿规则可以校验");
        mapper.insertRuleReview(id, operator, "VALIDATED", "结构与来源校验通过");
        mapper.insertRuleAudit(id, operator, "VALIDATE", "DRAFT", "VALIDATED");
    }

    @Transactional
    public void publish(String id, long operator) {
        Map<String, Object> value = require(id);
        if (mapper.changeRuleSetStatus(id, "VALIDATED", "PUBLISHED", operator) != 1) {
            throw new AgentBusinessException("只有已校验规则可以发布");
        }
        mapper.supersedePublishedRuleSets(String.valueOf(value.get("rule_code")), id);
        mapper.insertRuleAudit(id, operator, "PUBLISH", "VALIDATED", "PUBLISHED");
    }

    @Transactional
    public void offline(String id, long operator) {
        require(id);
        if (mapper.changeRuleSetStatus(id, "PUBLISHED", "OFFLINE", operator) != 1) {
            throw new AgentBusinessException("只有已发布规则可以下线");
        }
        mapper.insertRuleAudit(id, operator, "OFFLINE", "PUBLISHED", "OFFLINE");
    }

    @Transactional
    public void rollback(String id, long operator) {
        Map<String, Object> value = require(id);
        if (mapper.changeRuleSetStatus(id, "OFFLINE", "PUBLISHED", operator) != 1) {
            throw new AgentBusinessException("只有已下线规则可以回滚上线");
        }
        mapper.supersedePublishedRuleSets(String.valueOf(value.get("rule_code")), id);
        mapper.insertRuleAudit(id, operator, "ROLLBACK", "OFFLINE", "PUBLISHED");
    }

    private Map<String, Object> require(String id) {
        Map<String, Object> value = mapper.getRuleSet(id);
        if (value == null) throw new AgentBusinessException("规则集不存在");
        return value;
    }

    private String code(Object value) {
        String text = required(value, "规则编码").toUpperCase(Locale.ROOT);
        if (!text.matches("[A-Z0-9_\\-]{1,64}")) throw new AgentBusinessException("规则编码格式不正确");
        return text;
    }

    private String required(Object value, String label) {
        String text = value == null ? "" : String.valueOf(value).trim();
        if (text.isEmpty()) throw new AgentBusinessException(label + "不能为空");
        return text;
    }
}
