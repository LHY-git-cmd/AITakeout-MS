package com.sky.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.enumeration.AdminPermission;
import com.sky.enumeration.AdminRole;
import com.sky.enumeration.AgentActorType;
import com.sky.exception.AgentConfirmationConflictException;
import com.sky.exception.PermissionDeniedException;
import com.sky.mapper.AgentKnowledgeMapper;
import com.sky.mapper.AgentTaskMapper;
import com.sky.mapper.AgentToolAuditMapper;
import com.sky.mapper.AgentToolConfirmationMapper;
import com.sky.mapper.user.UserAgentTaskMapper;
import com.sky.mapper.user.UserAgentToolAuditMapper;
import com.sky.mapper.user.UserAgentToolConfirmationMapper;
import com.sky.context.BaseContext;
import com.sky.properties.AgentProperties;
import com.sky.result.PageResult;
import com.sky.service.*;
import com.sky.service.agent.UserAgentToolExecutor;
import com.sky.service.agent.AdminDishCreationService;
import com.sky.service.security.AdminAuthorizationService;
import com.sky.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * AI原子业务操作分发器。Python负责选择工具；本类不信任Python携带的身份，
 * 每次都通过task_id找回员工并按数据库当前角色重新鉴权。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentToolOperationServiceImpl implements AgentToolOperationService {
    private static final String SHOP_STATUS_KEY = "SHOP_STATUS";

    private final AgentTaskMapper taskMapper;
    private final AgentToolAuditMapper auditMapper;
    private final AgentToolConfirmationMapper confirmationMapper;
    private final AgentKnowledgeMapper knowledgeMapper;
    private final AdminAuthorizationService authorizationService;
    private final EmployeeService employeeService;
    private final OrderService orderService;
    private final DishService dishService;
    private final SetmealService setmealService;
    private final WorkspaceService workspaceService;
    private final ReportService reportService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AgentProperties agentProperties;
    private final ObjectMapper objectMapper;
    private AdminDishCreationService dishCreationService;

    /** 保留原构造契约，独立注入菜品批量创建服务。 */
    @Autowired
    public void setDishCreationService(AdminDishCreationService service) {
        this.dishCreationService = service;
    }

    private UserAgentToolExecutor userAgentToolExecutor;
    private UserAgentTaskMapper userTaskMapper;
    private UserAgentToolAuditMapper userAuditMapper;
    private UserAgentToolConfirmationMapper userConfirmationMapper;

    /** 注入用户工具执行器；保留显式方法便于隔离测试。 */
    @Autowired
    public void setUserAgentToolExecutor(UserAgentToolExecutor userAgentToolExecutor) {
        this.userAgentToolExecutor = userAgentToolExecutor;
    }

    /** 注入用户端独立任务与审计存储，不改变管理端构造契约。 */
    @Autowired
    public void setUserAgentStorage(UserAgentTaskMapper userTaskMapper,
                                    UserAgentToolAuditMapper userAuditMapper,
                                    UserAgentToolConfirmationMapper userConfirmationMapper) {
        this.userTaskMapper = userTaskMapper;
        this.userAuditMapper = userAuditMapper;
        this.userConfirmationMapper = userConfirmationMapper;
    }

    @Override
    public AgentToolOperationResponse execute(AgentToolOperationRequest request) {
        long started = System.nanoTime();
        String traceId = UUID.randomUUID().toString();
        AgentTask task = null;
        AdminRole role = null;
        Operation operation = Operation.fromCode(request.operation());
        AgentToolOperationResponse response;

        try {
            task = taskMapper.getByTaskId(request.taskId());
            if (task == null && userTaskMapper != null) {
                task = userTaskMapper.getByTaskId(request.taskId());
                if (task != null) {
                    task.setActorType(AgentActorType.USER.name());
                    task.setActorId(task.getUserId());
                    task.setActorRole("CUSTOMER");
                }
            }
            if (task == null) {
                response = error(request, "rejected", "TASK_NOT_FOUND", "任务不存在或已失效", traceId);
            } else if (AgentActorType.USER.name().equals(actorType(task))) {
                response = userAgentToolExecutor.execute(request, actorId(task), traceId);
            } else if (operation == null) {
                response = error(request, "rejected", "UNKNOWN_OPERATION", "不支持的原子操作", traceId);
            } else if (!isAdminActor(task)) {
                // 用户端工具将在独立目录开放，禁止普通用户继承任何管理端工具。
                response = error(request, "rejected", "ACTOR_TYPE_NOT_SUPPORTED",
                        "当前主体不能调用管理端工具", traceId);
            } else {
                Long actorId = actorId(task);
                role = authorizationService.resolveRole(actorId);
                authorizationService.require(role, operation.permission);
                validateArguments(operation, request.arguments());
                if (operation.write) {
                    response = error(request, "confirmation_required", "CONFIRMATION_REQUIRED",
                            "该操作需要管理员确认后才能执行", traceId);
                } else {
                    response = AgentToolOperationResponse.success(request.toolCallId(),
                            dispatchRead(operation, request.arguments(), actorId, role), traceId);
                }
            }
        } catch (PermissionDeniedException exception) {
            response = error(request, "rejected", "PERMISSION_DENIED", exception.getMessage(), traceId);
        } catch (IllegalArgumentException exception) {
            response = error(request, "rejected", "INVALID_ARGUMENT", exception.getMessage(), traceId);
        } catch (Exception exception) {
            log.error("AI原子操作执行失败, traceId={}, operation={}", traceId, request.operation(), exception);
            response = error(request, "failed", "OPERATION_FAILED", "业务操作执行失败", traceId);
        }

        saveAudit(request, task, role, operation, response,
                Duration.ofNanos(System.nanoTime() - started).toMillis(), traceId);
        return response;
    }

    @Override
    public AgentToolOperationResponse prepare(AgentToolOperationRequest request) {
        long started = System.nanoTime();
        String traceId = UUID.randomUUID().toString();
        AgentTask task = taskMapper.getByTaskId(request.taskId());
        if (task == null && userTaskMapper != null) {
            task = userTaskMapper.getByTaskId(request.taskId());
            if (task != null) {
                task.setActorType(AgentActorType.USER.name());
                task.setActorId(task.getUserId());
                task.setActorRole("CUSTOMER");
                return prepareUserConfirmation(request, task, started, traceId);
            }
        }
        Operation operation = Operation.fromCode(request.operation());
        AdminRole role = null;
        AgentToolOperationResponse response;
        try {
            if (task == null) throw new IllegalArgumentException("任务不存在或已失效");
            if (operation == null || !operation.write)
                throw new IllegalArgumentException("仅写操作可以创建确认凭证");
            requireAdminActor(task);
            Long actorId = actorId(task);
            role = authorizationService.resolveRole(actorId);
            authorizationService.require(role, operation.permission);
            validateArguments(operation, request.arguments());

            AgentToolConfirmation existing = confirmationMapper.getByTaskAndCall(
                    request.taskId(), request.toolCallId());
            if (existing == null) {
                String argumentsJson = objectMapper.writeValueAsString(request.arguments());
                AgentToolConfirmation value = AgentToolConfirmation.builder()
                        .confirmationId(UUID.randomUUID().toString().replace("-", ""))
                        .taskId(request.taskId()).toolCallId(request.toolCallId())
                        .employeeId(actorId).actorType(AgentActorType.ADMIN.name())
                        .actorId(actorId).actorRole(role.name())
                        .operation(operation.code).argumentsJson(argumentsJson)
                .argumentHash(argumentHash(request.arguments()))
                        .resourceVersion(resourceVersion(operation, request.arguments()))
                        .summary(summary(operation, request.arguments())).status("PENDING")
                        .expiresAt(LocalDateTime.now().plusSeconds(
                                agentProperties.getToolConfirmationTtlSeconds())).build();
                confirmationMapper.insertIgnore(value);
                existing = confirmationMapper.getByTaskAndCall(request.taskId(), request.toolCallId());
            }
            if (!existing.getArgumentHash().equals(argumentHash(request.arguments())))
                throw new IllegalArgumentException("工具调用ID已用于其他参数");
            if ("EXECUTED".equals(existing.getStatus())) {
                response = AgentToolOperationResponse.success(request.toolCallId(),
                        currentWriteResult(operation, request.arguments(), existing.getConfirmationId()), traceId);
                saveAudit(request, task, role, operation, response,
                        Duration.ofNanos(System.nanoTime() - started).toMillis(), traceId);
                return response;
            }
            response = AgentToolOperationResponse.error(request.toolCallId(),
                    "confirmation_required", "CONFIRMATION_REQUIRED",
                    "该操作需要管理员确认后才能执行", traceId);
            Map<String, Object> data = confirmationView(existing);
            response = new AgentToolOperationResponse(request.toolCallId(),
                    "confirmation_required", data, response.error(), traceId);
        } catch (PermissionDeniedException exception) {
            response = error(request, "rejected", "PERMISSION_DENIED", exception.getMessage(), traceId);
        } catch (IllegalArgumentException exception) {
            response = error(request, "rejected", "INVALID_ARGUMENT", exception.getMessage(), traceId);
        } catch (Exception exception) {
            log.error("创建AI工具确认失败, traceId={}", traceId, exception);
            response = error(request, "failed", "PREPARE_FAILED", "无法创建操作确认", traceId);
        }
        saveAudit(request, task, role, operation, response,
                Duration.ofNanos(System.nanoTime() - started).toMillis(), traceId);
        return response;
    }

    @Override
    public Map<String, Object> confirmationStatus(String confirmationId) {
        AgentToolConfirmation value = confirmationMapper.getByConfirmationId(confirmationId);
        if (value == null && userConfirmationMapper != null) {
            value = userConfirmationMapper.getByConfirmationId(confirmationId);
        }
        if (value == null) throw new IllegalArgumentException("确认凭证不存在");
        if (AgentActorType.USER.name().equals(value.getActorType())) {
            return confirmationView(value);
        }
        expireIfNeeded(value);
        return confirmationView(requiredConfirmation(confirmationId));
    }

    @Override
    public Map<String, Object> decideConfirmation(String confirmationId, String actorType,
                                                  Long actorId, boolean approved) {
        if (AgentActorType.USER.name().equals(actorType)) {
            if (userConfirmationMapper == null || userTaskMapper == null) {
                throw new PermissionDeniedException("用户确认服务不可用");
            }
            AgentToolConfirmation value = userConfirmationMapper.getByConfirmationId(confirmationId);
            AgentTask task = value == null ? null : userTaskMapper.getByTaskId(value.getTaskId());
            if (value == null || task == null || !Objects.equals(task.getUserId(), actorId)
                    || !Objects.equals(value.getActorId(), actorId)) {
                throw new PermissionDeniedException("只能确认本人发起的AI操作");
            }
            int changed = userConfirmationMapper.transitionByUser(
                    confirmationId, actorId, "PENDING", approved ? "CONFIRMED" : "REJECTED");
            if (changed == 0) throw new AgentConfirmationConflictException("确认凭证已处理或已过期");
            return confirmationView(userConfirmationMapper.getByConfirmationId(confirmationId));
        }
        AgentToolConfirmation value = requiredConfirmation(confirmationId);
        AgentTask task = taskMapper.getByTaskId(value.getTaskId());
        if (task == null || !Objects.equals(actorType(task), actorType)
                || !Objects.equals(actorId(task), actorId)
                || !Objects.equals(value.getActorType(), actorType)
                || !Objects.equals(value.getActorId(), actorId)) {
            throw new PermissionDeniedException("只能确认本人发起的AI操作");
        }
        Operation operation = Operation.fromCode(value.getOperation());
        requireAdminActor(task);
        AdminRole role = authorizationService.resolveRole(actorId);
        if (operation == null) throw new IllegalArgumentException("确认记录中的操作无效");
        authorizationService.require(role, operation.permission);
        expireIfNeeded(value);
        int changed = confirmationMapper.transitionByActor(confirmationId, actorType, actorId,
                "PENDING", approved ? "CONFIRMED" : "REJECTED");
        if (changed == 0) {
            AgentToolConfirmation current = requiredConfirmation(confirmationId);
            if (!(approved && "CONFIRMED".equals(current.getStatus()))
                    && !(!approved && "REJECTED".equals(current.getStatus()))) {
                throw new AgentConfirmationConflictException("确认凭证已处理或已过期，请重新发起操作");
            }
        }
        return confirmationView(requiredConfirmation(confirmationId));
    }

    @Override
    public AgentToolOperationResponse executeConfirmed(String confirmationId) {
        long started = System.nanoTime();
        String traceId = UUID.randomUUID().toString();
        AgentToolConfirmation userConfirmation = userConfirmationMapper == null ? null
                : userConfirmationMapper.getByConfirmationId(confirmationId);
        if (userConfirmation != null && AgentActorType.USER.name().equals(userConfirmation.getActorType())) {
            return executeUserConfirmed(userConfirmation, traceId, started);
        }
        AgentToolConfirmation confirmation = requiredConfirmation(confirmationId);
        AgentTask task = taskMapper.getByTaskId(confirmation.getTaskId());
        Operation operation = Operation.fromCode(confirmation.getOperation());
        AdminRole role = null;
        AgentToolOperationRequest auditRequest = new AgentToolOperationRequest(
                confirmationId, confirmation.getTaskId(), confirmation.getToolCallId(),
                confirmation.getOperation(), objectMapper.createObjectNode());
        try {
            JsonNode arguments = objectMapper.readTree(confirmation.getArgumentsJson());
            auditRequest = new AgentToolOperationRequest(confirmationId, confirmation.getTaskId(),
                    confirmation.getToolCallId(), confirmation.getOperation(), arguments);
            if (task == null || operation == null || !operation.write)
                throw new IllegalArgumentException("确认凭证关联的任务或操作无效");
            requireAdminActor(task);
            Long actorId = actorId(task);
            role = authorizationService.resolveRole(actorId);
            authorizationService.require(role, operation.permission);
            expireIfNeeded(confirmation);
            String currentStatus = requiredConfirmation(confirmationId).getStatus();
            if ("EXECUTED".equals(currentStatus)) {
                AgentToolOperationResponse response = AgentToolOperationResponse.success(
                        confirmation.getToolCallId(), currentWriteResult(operation, arguments, confirmationId), traceId);
                saveAudit(auditRequest, task, role, operation, response,
                        Duration.ofNanos(System.nanoTime() - started).toMillis(), traceId);
                return response;
            }
            if (!"CONFIRMED".equals(currentStatus)) {
                AgentToolOperationResponse response = error(auditRequest, "rejected",
                        "CONFIRMATION_REQUIRED", "操作尚未确认", traceId);
                saveAudit(auditRequest, task, role, operation, response,
                        Duration.ofNanos(System.nanoTime() - started).toMillis(), traceId);
                return response;
            }
            validateArguments(operation, arguments);
            if (!confirmation.getArgumentHash().equals(argumentHash(arguments)))
                return audited(auditRequest, task, role, operation, started, traceId,
                        error(auditRequest, "rejected", "ARGUMENT_CHANGED", "操作参数已发生变化", traceId));
            if (!confirmation.getResourceVersion().equals(resourceVersion(operation, arguments)))
                return audited(auditRequest, task, role, operation, started, traceId,
                        error(auditRequest, "rejected", "STALE_RESOURCE", "业务数据已变化，请重新发起操作", traceId));
            if (confirmationMapper.transition(confirmationId, "CONFIRMED", "EXECUTING") == 0)
                return audited(auditRequest, task, role, operation, started, traceId,
                        error(auditRequest, "rejected", "CONFIRMATION_USED", "确认凭证已被使用", traceId));
            BaseContext.setCurrentId(actorId);
            BaseContext.setCurrentRole(role.name());
            Object data;
            try {
                data = operation == Operation.DISH_CREATE
                        ? dishCreationService.create(confirmationId, arguments, actorId, confirmation.getResourceVersion())
                        : dispatchWrite(operation, arguments);
            } finally {
                BaseContext.removeCurrentId();
            }
            if (operation != Operation.DISH_CREATE && confirmationMapper.markExecuted(confirmationId) == 0)
                throw new IllegalStateException("无法完成确认凭证状态转换");
            AgentToolOperationResponse response = AgentToolOperationResponse.success(
                    confirmation.getToolCallId(), data, traceId);
            saveAudit(auditRequest, task, role, operation, response,
                    Duration.ofNanos(System.nanoTime() - started).toMillis(), traceId);
            return response;
        } catch (PermissionDeniedException exception) {
            confirmationMapper.transition(confirmationId, "EXECUTING", "CONFIRMED");
            AgentToolOperationResponse response = AgentToolOperationResponse.error(
                    confirmation.getToolCallId(), "rejected",
                    "PERMISSION_DENIED", exception.getMessage(), traceId);
            saveAudit(auditRequest, task, role, operation, response,
                    Duration.ofNanos(System.nanoTime() - started).toMillis(), traceId);
            return response;
        } catch (Exception exception) {
            confirmationMapper.transition(confirmationId, "EXECUTING", "CONFIRMED");
            log.error("执行已确认AI工具失败, confirmationId={}, traceId={}",
                    confirmationId, traceId, exception);
            AgentToolOperationResponse response = AgentToolOperationResponse.error(
                    confirmation.getToolCallId(), "failed",
                    "OPERATION_FAILED", "业务操作执行失败", traceId);
            saveAudit(auditRequest, task, role, operation, response,
                    Duration.ofNanos(System.nanoTime() - started).toMillis(), traceId);
            return response;
        }
    }

    /** 为普通用户创建高风险操作确认卡，不复用管理端权限和表。 */
    private AgentToolOperationResponse prepareUserConfirmation(AgentToolOperationRequest request,
                                                               AgentTask task, long started,
                                                               String traceId) {
        Operation operation = Operation.fromCode(request.operation());
        if (operation == null || !Set.of("user.order.cancel.request", "user.after_sale.submit")
                .contains(operation.code)) {
            return error(request, "rejected", "UNKNOWN_OPERATION", "用户工具不支持该写操作", traceId);
        }
        try {
            Map<String, Object> preview = userAgentToolExecutor.previewConfirmedOperation(
                    request, task.getUserId());
            AgentToolConfirmation existing = userConfirmationMapper.getByTaskAndCall(
                    request.taskId(), request.toolCallId());
            if (existing == null) {
                String argumentsJson = objectMapper.writeValueAsString(request.arguments());
                AgentToolConfirmation value = AgentToolConfirmation.builder()
                        .confirmationId(UUID.randomUUID().toString().replace("-", ""))
                        .taskId(request.taskId()).toolCallId(request.toolCallId())
                        .actorType(AgentActorType.USER.name()).actorId(task.getUserId())
                        .actorRole("CUSTOMER").operation(operation.code).argumentsJson(argumentsJson)
                        .argumentHash(argumentHash(request.arguments()))
                        .resourceVersion(String.valueOf(preview.get("resource_version")))
                        .summary(operation.code.equals("user.order.cancel.request")
                                ? "取消订单 " + request.arguments().path("order_id").asText()
                                : "提交订单售后 " + request.arguments().path("order_id").asText())
                        .status("PENDING")
                        .expiresAt(LocalDateTime.now().plusSeconds(
                                agentProperties.getToolConfirmationTtlSeconds())).build();
                userConfirmationMapper.insertIgnore(value);
                existing = userConfirmationMapper.getByTaskAndCall(request.taskId(), request.toolCallId());
            }
            if (!Objects.equals(existing.getArgumentHash(), argumentHash(request.arguments()))) {
                throw new IllegalArgumentException("工具调用ID已用于其他参数");
            }
            if ("EXECUTED".equals(existing.getStatus())) {
                return AgentToolOperationResponse.success(request.toolCallId(), preview, traceId);
            }
            Map<String, Object> data = new LinkedHashMap<>(preview);
            data.put("confirmation_id", existing.getConfirmationId());
            data.put("expires_at", existing.getExpiresAt());
            AgentToolOperationResponse response = new AgentToolOperationResponse(
                    request.toolCallId(), "confirmation_required", data,
                    Map.of("code", "CONFIRMATION_REQUIRED", "message", "请确认后继续"), traceId);
            saveAudit(request, task, null, operation, response,
                    Duration.ofNanos(System.nanoTime() - started).toMillis(), traceId);
            return response;
        } catch (Exception exception) {
            AgentToolOperationResponse response = error(request, "rejected", "PREPARE_FAILED",
                    exception.getMessage(), traceId);
            saveAudit(request, task, null, operation, response,
                    Duration.ofNanos(System.nanoTime() - started).toMillis(), traceId);
            return response;
        }
    }

    private AgentToolOperationResponse executeUserConfirmed(AgentToolConfirmation confirmation,
                                                             String traceId, long started) {
        AgentTask task = userTaskMapper.getByTaskId(confirmation.getTaskId());
        AgentToolOperationRequest request;
        try {
            request = new AgentToolOperationRequest(confirmation.getConfirmationId(),
                    confirmation.getTaskId(), confirmation.getToolCallId(), confirmation.getOperation(),
                    objectMapper.readTree(confirmation.getArgumentsJson()));
            if (task == null || !Objects.equals(task.getUserId(), confirmation.getActorId())) {
                throw new PermissionDeniedException("只能确认本人发起的AI操作");
            }
            AgentToolConfirmation current = userConfirmationMapper.getByConfirmationId(
                    confirmation.getConfirmationId());
            if (current.getExpiresAt() != null && current.getExpiresAt().isBefore(LocalDateTime.now())) {
                userConfirmationMapper.transition(confirmation.getConfirmationId(), "PENDING", "EXPIRED");
                return error(request, "rejected", "CONFIRMATION_EXPIRED", "操作确认已过期", traceId);
            }
            if ("EXECUTED".equals(current.getStatus())) {
                return AgentToolOperationResponse.success(request.toolCallId(), Map.of("replayed", true), traceId);
            }
            if (!"CONFIRMED".equals(current.getStatus())) {
                return error(request, "rejected", "CONFIRMATION_REQUIRED", "操作尚未确认", traceId);
            }
            if (userConfirmationMapper.transition(confirmation.getConfirmationId(), "CONFIRMED", "EXECUTING") == 0) {
                return error(request, "rejected", "CONFIRMATION_USED", "确认凭证已被使用", traceId);
            }
            AgentToolOperationResponse result = userAgentToolExecutor.executeConfirmed(
                    request, task.getUserId(), current.getResourceVersion(), traceId);
            if ("success".equals(result.status())) userConfirmationMapper.markExecuted(
                    confirmation.getConfirmationId());
            return result;
        } catch (Exception exception) {
            return error(new AgentToolOperationRequest(confirmation.getConfirmationId(),
                    confirmation.getTaskId(), confirmation.getToolCallId(), confirmation.getOperation(),
                    objectMapper.createObjectNode()), "failed", "EXECUTE_FAILED",
                    "用户操作执行失败", traceId);
        }
    }

    private AgentToolOperationResponse audited(AgentToolOperationRequest request, AgentTask task,
                                               AdminRole role, Operation operation, long started,
                                               String traceId, AgentToolOperationResponse response) {
        saveAudit(request, task, role, operation, response,
                Duration.ofNanos(System.nanoTime() - started).toMillis(), traceId);
        return response;
    }

    private Object dispatchRead(Operation operation, JsonNode args, Long employeeId, AdminRole role) {
        return switch (operation) {
            case EMPLOYEE_QUERY -> queryEmployees(args);
            case EMPLOYEE_DETAIL -> employee(employeeService.getById(requiredLong(args, "employee_id")));
            case KNOWLEDGE_BASE_QUERY -> queryKnowledgeBases(employeeId, role);
            case KNOWLEDGE_DOCUMENT_QUERY -> queryKnowledgeDocuments(args, employeeId, role);
            case ORDER_QUERY -> queryOrders(args);
            case ORDER_DETAIL -> order(orderService.details(requiredLong(args, "order_id")));
            case ORDER_STATISTICS -> orderService.statistics();
            case DISH_CATEGORY_QUERY -> dishCreationService.categories();
            case DISH_QUERY -> queryDishes(args);
            case DISH_DETAIL -> dish(dishService.getById(requiredLong(args, "dish_id")));
            case SETMEAL_QUERY -> querySetmeals(args);
            case SETMEAL_DETAIL -> setmeal(setmealService.getByIdWithDish(requiredLong(args, "setmeal_id")));
            case SHOP_STATUS_GET -> Map.of("status", Optional.ofNullable(
                    (Integer) redisTemplate.opsForValue().get(SHOP_STATUS_KEY)).orElse(1));
            case WORKSPACE_OVERVIEW -> workspaceOverview();
            case REPORT_QUERY -> report(args);
            default -> throw new IllegalArgumentException("写操作不能通过只读执行入口运行");
        };
    }

    private PageResult queryEmployees(JsonNode args) {
        EmployeePageQueryDTO dto = new EmployeePageQueryDTO();
        dto.setPage(page(args));
        dto.setPageSize(pageSize(args));
        dto.setName(optionalText(args, "name"));
        PageResult source = employeeService.page(dto);
        return new PageResult(source.getTotal(), source.getRecords().stream()
                .map(value -> employee((Employee) value)).toList());
    }

    private PageResult queryOrders(JsonNode args) {
        OrdersPageQueryDTO dto = new OrdersPageQueryDTO();
        dto.setPage(page(args));
        dto.setPageSize(pageSize(args));
        dto.setNumber(optionalText(args, "number"));
        dto.setPhone(optionalText(args, "phone"));
        dto.setStatus(optionalInt(args, "status"));
        dto.setBeginTime(optionalDateTime(args, "begin_time"));
        dto.setEndTime(optionalDateTime(args, "end_time"));
        PageResult source = orderService.conditionSearch(dto);
        return new PageResult(source.getTotal(), source.getRecords().stream()
                .map(value -> order((OrderVO) value)).toList());
    }

    private PageResult queryDishes(JsonNode args) {
        DishPageQueryDTO dto = new DishPageQueryDTO();
        dto.setPage(page(args));
        dto.setPageSize(pageSize(args));
        dto.setName(optionalText(args, "name"));
        dto.setCategoryId(optionalInt(args, "category_id"));
        dto.setStatus(optionalInt(args, "status"));
        PageResult source = dishService.pageQuery(dto);
        return new PageResult(source.getTotal(), source.getRecords().stream()
                .map(value -> dish((DishVO) value)).toList());
    }

    private PageResult querySetmeals(JsonNode args) {
        SetmealPageQueryDTO dto = new SetmealPageQueryDTO();
        dto.setPage(page(args));
        dto.setPageSize(pageSize(args));
        dto.setName(optionalText(args, "name"));
        dto.setCategoryId(optionalInt(args, "category_id"));
        dto.setStatus(optionalInt(args, "status"));
        PageResult source = setmealService.pageQuery(dto);
        return new PageResult(source.getTotal(), source.getRecords().stream()
                .map(value -> setmeal((SetmealVO) value)).toList());
    }

    private List<KnowledgeBaseToolVO> queryKnowledgeBases(Long employeeId, AdminRole role) {
        List<AgentKnowledgeBase> bases = role == AdminRole.SUPER_ADMIN
                ? knowledgeMapper.listAllBases() : knowledgeMapper.listBases(employeeId);
        return bases.stream().map(value -> new KnowledgeBaseToolVO(value.getKbId(), value.getName(),
                value.getDescription(), value.getStatus(), value.getCreateTime(), value.getUpdateTime())).toList();
    }

    private List<KnowledgeDocumentToolVO> queryKnowledgeDocuments(JsonNode args, Long employeeId, AdminRole role) {
        String kbId = requiredText(args, "kb_id");
        AgentKnowledgeBase base = role == AdminRole.SUPER_ADMIN
                ? knowledgeMapper.getBase(kbId) : knowledgeMapper.getOwnedBase(kbId, employeeId);
        if (base == null) {
            throw new PermissionDeniedException("无权访问该知识库或知识库不存在");
        }
        return knowledgeMapper.listDocuments(kbId).stream().map(value -> new KnowledgeDocumentToolVO(
                value.getDocumentId(), value.getKbId(), value.getFileName(), value.getFileType(),
                value.getVersion(), value.getActiveVersion(), value.getStatus(), value.getChunkCount(),
                value.getCreateTime(), value.getUpdateTime())).toList();
    }

    private Map<String, Object> workspaceOverview() {
        LocalDate today = LocalDate.now();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("business", workspaceService.getBusinessData(today.atStartOfDay(), today.plusDays(1).atStartOfDay()));
        result.put("orders", workspaceService.getOrderOverView());
        result.put("dishes", workspaceService.getDishOverView());
        result.put("setmeals", workspaceService.getSetmealOverView());
        return result;
    }

    private Map<String, Object> report(JsonNode args) {
        LocalDate begin = requiredDateTime(args, "begin_time").toLocalDate();
        LocalDate end = requiredDateTime(args, "end_time").toLocalDate();
        validateRange(begin.atStartOfDay(), end.atStartOfDay());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("turnover", reportService.getTurnoverStatistics(begin, end));
        result.put("users", reportService.getUserStatistics(begin, end));
        result.put("orders", reportService.getOrderStatistics(begin, end));
        result.put("sales_top_10", reportService.getSalesTop10(begin, end));
        return result;
    }

    private Object dispatchWrite(Operation operation, JsonNode args) throws Exception {
        return switch (operation) {
            case ORDER_STATUS_UPDATE -> updateOrderStatus(args);
            case DISH_UPDATE -> updateDish(args);
            case SETMEAL_UPDATE -> updateSetmeal(args);
            case SHOP_STATUS_UPDATE -> updateShopStatus(args);
            default -> throw new IllegalArgumentException("该操作不是可执行的写操作");
        };
    }

    private Object currentWriteResult(Operation operation, JsonNode args, String confirmationId) {
        return switch (operation) {
            case DISH_CREATE -> dishCreationService.result(confirmationId);
            case ORDER_STATUS_UPDATE -> order(orderService.details(requiredLong(args, "order_id")));
            case DISH_UPDATE -> dish(dishService.getById(requiredLong(args, "dish_id")));
            case SETMEAL_UPDATE -> setmeal(setmealService.getByIdWithDish(requiredLong(args, "setmeal_id")));
            case SHOP_STATUS_UPDATE -> Map.of("status", Optional.ofNullable(
                    (Integer) redisTemplate.opsForValue().get(SHOP_STATUS_KEY)).orElse(1));
            default -> throw new IllegalArgumentException("该操作不是写操作");
        };
    }

    private Object updateOrderStatus(JsonNode args) throws Exception {
        Long id = requiredLong(args, "order_id");
        String action = requiredText(args, "action");
        switch (action) {
            case "confirm" -> {
                OrdersConfirmDTO dto = new OrdersConfirmDTO(); dto.setId(id); orderService.confirm(dto);
            }
            case "reject" -> {
                OrdersRejectionDTO dto = new OrdersRejectionDTO(); dto.setId(id);
                dto.setRejectionReason(requiredText(args, "reason")); orderService.rejection(dto);
            }
            case "cancel" -> {
                OrdersCancelDTO dto = new OrdersCancelDTO(); dto.setId(id);
                dto.setCancelReason(requiredText(args, "reason")); orderService.cancel(dto);
            }
            case "deliver" -> orderService.delivery(id);
            case "complete" -> orderService.complete(id);
            default -> throw new IllegalArgumentException("不支持的订单状态操作");
        }
        return order(orderService.details(id));
    }

    private Object updateDish(JsonNode args) {
        Long id = requiredLong(args, "dish_id");
        DishVO current = dishService.getById(id);
        if (current == null) throw new IllegalArgumentException("菜品不存在");
        boolean metadataChanged = hasAny(args, "name", "category_id", "price", "image", "description");
        if (metadataChanged) {
            DishDTO dto = new DishDTO();
            dto.setId(id); dto.setName(textOr(args, "name", current.getName()));
            dto.setCategoryId(longOr(args, "category_id", current.getCategoryId()));
            dto.setPrice(decimalOr(args, "price", current.getPrice()));
            dto.setImage(textOr(args, "image", current.getImage()));
            dto.setDescription(textOr(args, "description", current.getDescription()));
            dto.setStatus(current.getStatus()); dto.setFlavors(current.getFlavors());
            dishService.updateWithFlavor(dto);
        }
        if (args.hasNonNull("status") && args.get("status").asInt() != current.getStatus())
            dishService.startOrStop(boundedInt(args, "status", 0, 0, 1), id);
        return dish(dishService.getById(id));
    }

    private Object updateSetmeal(JsonNode args) {
        Long id = requiredLong(args, "setmeal_id");
        SetmealVO current = setmealService.getByIdWithDish(id);
        if (current == null) throw new IllegalArgumentException("套餐不存在");
        boolean metadataChanged = hasAny(args, "name", "category_id", "price", "image", "description");
        if (metadataChanged) {
            SetmealDTO dto = new SetmealDTO();
            dto.setId(id); dto.setName(textOr(args, "name", current.getName()));
            dto.setCategoryId(longOr(args, "category_id", current.getCategoryId()));
            dto.setPrice(decimalOr(args, "price", current.getPrice()));
            dto.setImage(textOr(args, "image", current.getImage()));
            dto.setDescription(textOr(args, "description", current.getDescription()));
            dto.setStatus(current.getStatus()); dto.setSetmealDishes(current.getSetmealDishes());
            setmealService.update(dto);
        }
        if (args.hasNonNull("status") && args.get("status").asInt() != current.getStatus())
            setmealService.startOrStop(boundedInt(args, "status", 0, 0, 1), id);
        return setmeal(setmealService.getByIdWithDish(id));
    }

    private Object updateShopStatus(JsonNode args) {
        int status = boundedInt(args, "status", 0, 0, 1);
        redisTemplate.opsForValue().set(SHOP_STATUS_KEY, status);
        return Map.of("status", status);
    }

    private AgentToolConfirmation requiredConfirmation(String confirmationId) {
        AgentToolConfirmation value = confirmationMapper.getByConfirmationId(confirmationId);
        if (value == null) throw new IllegalArgumentException("确认凭证不存在");
        return value;
    }

    private void expireIfNeeded(AgentToolConfirmation value) {
        if ("PENDING".equals(value.getStatus()) && value.getExpiresAt().isBefore(LocalDateTime.now()))
            confirmationMapper.transition(value.getConfirmationId(), "PENDING", "EXPIRED");
    }

    private Map<String, Object> confirmationView(AgentToolConfirmation value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("confirmation_id", value.getConfirmationId());
        result.put("tool_call_id", value.getToolCallId());
        result.put("status", value.getStatus());
        result.put("summary", value.getSummary());
        result.put("actor_type", value.getActorType());
        result.put("expires_at", value.getExpiresAt());
        result.put("argument_hash", value.getArgumentHash());
        result.put("object_version", value.getResourceVersion());
        if ("dish.create".equals(value.getOperation()) && !"EXECUTED".equals(value.getStatus())) {
            try { result.put("preview", dishCreationService.preview(objectMapper.readTree(value.getArgumentsJson()))); }
            catch (Exception ex) { result.put("preview_error", "分类或参数已变化，请重新发起添加"); }
        }
        return result;
    }

    private String resourceVersion(Operation operation, JsonNode args) {
        return switch (operation) {
            case DISH_CREATE -> sha256(dishCreationService.categoryVersion(args));
            case ORDER_STATUS_UPDATE -> {
                OrderVO value = orderService.details(requiredLong(args, "order_id"));
                if (value == null) throw new IllegalArgumentException("订单不存在");
                yield sha256(value.getId() + ":" + value.getStatus() + ":" + value.getPayStatus()
                        + ":" + value.getOrderTime() + ":" + value.getDeliveryTime());
            }
            case DISH_UPDATE -> {
                DishVO value = dishService.getById(requiredLong(args, "dish_id"));
                if (value == null) throw new IllegalArgumentException("菜品不存在");
                yield sha256(value.getId() + ":" + value.getStatus() + ":" + value.getUpdateTime());
            }
            case SETMEAL_UPDATE -> {
                SetmealVO value = setmealService.getByIdWithDish(requiredLong(args, "setmeal_id"));
                if (value == null) throw new IllegalArgumentException("套餐不存在");
                yield sha256(value.getId() + ":" + value.getStatus() + ":" + value.getUpdateTime());
            }
            case SHOP_STATUS_UPDATE -> sha256(String.valueOf(Optional.ofNullable(
                    redisTemplate.opsForValue().get(SHOP_STATUS_KEY)).orElse(1)));
            default -> throw new IllegalArgumentException("只允许为写操作创建资源版本");
        };
    }

    private String summary(Operation operation, JsonNode args) {
        return switch (operation) {
            case DISH_CREATE -> "添加" + (args.path("test_data").asBoolean() ? "测试" : "")
                    + "菜品 " + args.path("dishes").size() + " 道（默认停售，图片可后续上传）";
            case ORDER_STATUS_UPDATE -> "将订单 " + requiredLong(args, "order_id")
                    + " 执行状态操作：" + requiredText(args, "action");
            case DISH_UPDATE -> "修改菜品 " + requiredLong(args, "dish_id");
            case SETMEAL_UPDATE -> "修改套餐 " + requiredLong(args, "setmeal_id");
            case SHOP_STATUS_UPDATE -> "将店铺状态修改为"
                    + (boundedInt(args, "status", 0, 0, 1) == 1 ? "营业" : "打烊");
            default -> "执行AI业务操作";
        };
    }

    private boolean hasAny(JsonNode args, String... names) {
        return Arrays.stream(names).anyMatch(args::hasNonNull);
    }

    private String textOr(JsonNode args, String name, String fallback) {
        return args.hasNonNull(name) ? optionalText(args, name) : fallback;
    }

    private Long longOr(JsonNode args, String name, Long fallback) {
        return args.hasNonNull(name) ? requiredLong(args, name) : fallback;
    }

    private BigDecimal decimalOr(JsonNode args, String name, BigDecimal fallback) {
        if (!args.hasNonNull(name)) return fallback;
        JsonNode node = args.get(name);
        BigDecimal value;
        if (node.isNumber()) {
            value = node.decimalValue();
        } else if (node.isTextual()) {
            // LLMs may include a currency suffix (for example, "99元").
            // Normalize only the supported currency notation; reject anything else.
            String normalized = node.textValue().trim()
                    .replace(",", "")
                    .replace("元", "")
                    .replace("¥", "")
                    .replace("￥", "")
                    .trim();
            try {
                value = new BigDecimal(normalized);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(name + "必须是数字");
            }
        } else {
            throw new IllegalArgumentException(name + "必须是数字");
        }
        if (value.signum() <= 0) throw new IllegalArgumentException(name + "必须大于0");
        return value;
    }

    private EmployeeToolVO employee(Employee value) {
        if (value == null) throw new IllegalArgumentException("员工不存在");
        return new EmployeeToolVO(value.getId(), value.getUsername(), value.getName(),
                maskPhone(value.getPhone()), value.getSex(), maskIdNumber(value.getIdNumber()),
                value.getStatus(), value.getRole());
    }

    private OrderToolVO order(OrderVO value) {
        if (value == null) throw new IllegalArgumentException("订单不存在");
        return new OrderToolVO(value.getId(), value.getNumber(), value.getStatus(), value.getOrderTime(),
                value.getCheckoutTime(), value.getPayMethod(), value.getPayStatus(), value.getAmount(),
                value.getRemark(), maskName(value.getUserName()), maskPhone(value.getPhone()),
                maskAddress(value.getAddress()), maskName(value.getConsignee()), value.getCancelReason(),
                value.getRejectionReason(), value.getEstimatedDeliveryTime(), value.getDeliveryTime(),
                value.getOrderDetailList());
    }

    private Map<String, Object> dish(DishVO value) {
        if (value == null) throw new IllegalArgumentException("菜品不存在");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", value.getId()); result.put("name", value.getName());
        result.put("category_id", value.getCategoryId()); result.put("category_name", value.getCategoryName());
        result.put("price", value.getPrice()); result.put("image", value.getImage());
        result.put("description", value.getDescription()); result.put("status", value.getStatus());
        result.put("update_time", value.getUpdateTime()); result.put("flavors", value.getFlavors());
        return result;
    }

    private Map<String, Object> setmeal(SetmealVO value) {
        if (value == null) throw new IllegalArgumentException("套餐不存在");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", value.getId()); result.put("name", value.getName());
        result.put("category_id", value.getCategoryId()); result.put("category_name", value.getCategoryName());
        result.put("price", value.getPrice()); result.put("image", value.getImage());
        result.put("description", value.getDescription()); result.put("status", value.getStatus());
        result.put("update_time", value.getUpdateTime()); result.put("dishes", value.getSetmealDishes());
        return result;
    }

    private void validateArguments(Operation operation, JsonNode args) {
        if (args == null || !args.isObject()) throw new IllegalArgumentException("arguments必须是JSON对象");
        Set<String> allowed = operation.allowedArguments;
        args.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) throw new IllegalArgumentException("不支持的参数: " + name);
        });
        if (operation.paged) { page(args); pageSize(args); }
        if (operation == Operation.ORDER_QUERY) {
            LocalDateTime begin = optionalDateTime(args, "begin_time");
            LocalDateTime end = optionalDateTime(args, "end_time");
            if ((begin == null) != (end == null)) throw new IllegalArgumentException("开始和结束时间必须同时提供");
            if (begin != null) validateRange(begin, end);
        }
        switch (operation) {
            case EMPLOYEE_DETAIL -> requiredLong(args, "employee_id");
            case KNOWLEDGE_DOCUMENT_QUERY -> requiredText(args, "kb_id");
            case ORDER_DETAIL -> requiredLong(args, "order_id");
            case ORDER_STATUS_UPDATE -> validateOrderUpdate(args);
            case DISH_CREATE -> dishCreationService.validate(args);
            case DISH_DETAIL -> requiredLong(args, "dish_id");
            case DISH_UPDATE -> validateEntityUpdate(args, "dish_id");
            case SETMEAL_DETAIL -> requiredLong(args, "setmeal_id");
            case SETMEAL_UPDATE -> validateEntityUpdate(args, "setmeal_id");
            case SHOP_STATUS_UPDATE -> {
                if (!args.hasNonNull("status")) throw new IllegalArgumentException("status不能为空");
                boundedInt(args, "status", 0, 0, 1);
            }
            case REPORT_QUERY -> validateRange(requiredDateTime(args, "begin_time"),
                    requiredDateTime(args, "end_time"));
            default -> { }
        }
    }

    private void validateOrderUpdate(JsonNode args) {
        requiredLong(args, "order_id");
        String action = requiredText(args, "action");
        if (!Set.of("confirm", "reject", "cancel", "deliver", "complete").contains(action))
            throw new IllegalArgumentException("action不是允许的订单状态操作");
        String reason = optionalText(args, "reason");
        if ((action.equals("reject") || action.equals("cancel")) && (reason == null || reason.isBlank()))
            throw new IllegalArgumentException("拒单或取消时必须提供原因");
        if (!action.equals("reject") && !action.equals("cancel") && reason != null)
            throw new IllegalArgumentException("仅拒单或取消操作允许提供原因");
    }

    private void validateEntityUpdate(JsonNode args, String idField) {
        requiredLong(args, idField);
        boolean changed = java.util.stream.StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(args.fieldNames(), 0), false)
                .anyMatch(name -> !idField.equals(name) && args.hasNonNull(name));
        if (!changed) throw new IllegalArgumentException("至少需要提供一个待修改字段");
    }

    private void validateRange(LocalDateTime begin, LocalDateTime end) {
        if (end.isBefore(begin)) throw new IllegalArgumentException("结束时间不能早于开始时间");
        if (Duration.between(begin, end).toDays() > 366) throw new IllegalArgumentException("查询时间跨度不能超过366天");
    }

    private int page(JsonNode args) { return boundedInt(args, "page", 1, 1, Integer.MAX_VALUE); }
    private int pageSize(JsonNode args) { return boundedInt(args, "page_size", 20, 1, 50); }

    private int boundedInt(JsonNode args, String name, int defaultValue, int min, int max) {
        JsonNode node = args.get(name);
        if (node == null || node.isNull()) return defaultValue;
        if (!node.canConvertToInt()) throw new IllegalArgumentException(name + "必须是整数");
        int value = node.intValue();
        if (value < min || value > max) throw new IllegalArgumentException(name + "超出允许范围");
        return value;
    }

    private Long requiredLong(JsonNode args, String name) {
        JsonNode node = args.get(name);
        if (node == null || !node.canConvertToLong() || node.longValue() <= 0)
            throw new IllegalArgumentException(name + "必须是正整数");
        return node.longValue();
    }

    private String requiredText(JsonNode args, String name) {
        String value = optionalText(args, name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + "不能为空");
        return value;
    }

    private String optionalText(JsonNode args, String name) {
        JsonNode node = args.get(name);
        if (node == null || node.isNull()) return null;
        if (!node.isTextual()) throw new IllegalArgumentException(name + "必须是字符串");
        return node.textValue();
    }

    private Integer optionalInt(JsonNode args, String name) {
        JsonNode node = args.get(name);
        if (node == null || node.isNull()) return null;
        if (!node.canConvertToInt()) throw new IllegalArgumentException(name + "必须是整数");
        return node.intValue();
    }

    private LocalDateTime requiredDateTime(JsonNode args, String name) {
        LocalDateTime value = optionalDateTime(args, name);
        if (value == null) throw new IllegalArgumentException(name + "不能为空");
        return value;
    }

    private LocalDateTime optionalDateTime(JsonNode args, String name) {
        String value = optionalText(args, name);
        if (value == null) return null;
        try { return LocalDateTime.parse(value); }
        catch (Exception exception) { throw new IllegalArgumentException(name + "必须是ISO 8601日期时间"); }
    }

    private String maskPhone(String value) {
        if (value == null || value.isBlank()) return value;
        if (value.length() < 7) return "*".repeat(value.length());
        return value.substring(0, 3) + "*".repeat(value.length() - 7) + value.substring(value.length() - 4);
    }

    private String maskIdNumber(String value) {
        if (value == null || value.isBlank()) return value;
        if (value.length() <= 7) return "*".repeat(value.length());
        return value.substring(0, 3) + "*".repeat(value.length() - 7) + value.substring(value.length() - 4);
    }

    private String maskName(String value) {
        if (value == null || value.isBlank()) return value;
        return value.substring(0, 1) + (value.length() == 1 ? "" : "*".repeat(value.length() - 1));
    }

    private String maskAddress(String value) {
        if (value == null || value.isBlank()) return value;
        int visible = Math.min(6, value.length());
        return value.substring(0, visible) + (visible == value.length() ? "" : "***");
    }

    private AgentToolOperationResponse error(AgentToolOperationRequest request, String status,
                                             String code, String message, String traceId) {
        return AgentToolOperationResponse.error(request.toolCallId(), status, code, message, traceId);
    }

    private void saveAudit(AgentToolOperationRequest request, AgentTask task, AdminRole role,
                           Operation operation, AgentToolOperationResponse response,
                           long durationMs, String traceId) {
        try {
            String errorCode = response.error() == null ? null : String.valueOf(response.error().get("code"));
            AgentToolAudit audit = AgentToolAudit.builder()
                    .requestId(request.requestId() == null || request.requestId().isBlank()
                            ? request.toolCallId() : request.requestId())
                    .taskId(request.taskId()).toolCallId(request.toolCallId())
                    .employeeId(task == null || !isAdminActor(task) ? null : actorId(task))
                    .actorType(task == null ? null : actorType(task))
                    .actorId(task == null ? null : actorId(task))
                    .actorRole(role == null ? null : role.name())
                    .operation(request.operation())
                    .requiredPermission(operation == null ? "UNKNOWN" : operation.permission.name())
                    .argumentHash(sha256(request.arguments() == null ? "null" : request.arguments().toString()))
                    .status(response.status()).errorCode(errorCode).durationMs(durationMs)
                    .traceId(traceId).build();
            if (task != null && AgentActorType.USER.name().equals(actorType(task))
                    && userAuditMapper != null) {
                userAuditMapper.insert(audit);
            } else {
                auditMapper.insert(audit);
            }
        } catch (Exception exception) {
            log.error("AI工具调用审计写入失败, traceId={}", traceId, exception);
        }
    }

    /** 返回任务的通用主体ID，并兼容迁移前只保存user_id的管理端任务。 */
    private Long actorId(AgentTask task) {
        return task.getActorId() == null ? task.getUserId() : task.getActorId();
    }

    /** 返回标准主体类型，并兼容历史管理端任务。 */
    private String actorType(AgentTask task) {
        return AgentActorType.fromDatabase(task.getActorType()).name();
    }

    private boolean isAdminActor(AgentTask task) {
        return AgentActorType.ADMIN.name().equals(actorType(task));
    }

    /** 管理端工具只能在管理员主体上下文中执行。 */
    private void requireAdminActor(AgentTask task) {
        if (!isAdminActor(task)) {
            throw new PermissionDeniedException("当前主体不能调用管理端工具");
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成审计摘要", exception);
        }
    }

    /** 对 JSON 对象键排序后计算摘要，避免 MySQL JSON 规范化键顺序造成误报。 */
    private String argumentHash(JsonNode value) {
        try {
            return sha256(canonicalJson(value));
        } catch (Exception exception) {
            throw new IllegalArgumentException("工具参数格式无效", exception);
        }
    }

    private String canonicalJson(JsonNode value) throws Exception {
        if (value == null || value.isNull()) return "null";
        if (value.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            java.util.Iterator<String> names = value.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                sorted.set(name, objectMapper.readTree(canonicalJson(value.get(name))));
            }
            java.util.TreeMap<String, JsonNode> ordered = new java.util.TreeMap<>();
            sorted.fields().forEachRemaining(entry -> ordered.put(entry.getKey(), entry.getValue()));
            ObjectNode normalized = objectMapper.createObjectNode();
            ordered.forEach(normalized::set);
            return objectMapper.writeValueAsString(normalized);
        }
        if (value.isArray()) {
            ArrayNode normalized = objectMapper.createArrayNode();
            value.forEach(item -> {
                try {
                    normalized.add(objectMapper.readTree(canonicalJson(item)));
                } catch (Exception exception) {
                    throw new IllegalArgumentException("工具参数格式无效", exception);
                }
            });
            return objectMapper.writeValueAsString(normalized);
        }
        return value.toString();
    }

    private enum Operation {
        EMPLOYEE_QUERY("employee.query", AdminPermission.EMPLOYEE_READ, false, true, "page", "page_size", "name"),
        EMPLOYEE_DETAIL("employee.detail", AdminPermission.EMPLOYEE_READ, false, false, "employee_id"),
        KNOWLEDGE_BASE_QUERY("knowledge.base.query", AdminPermission.KNOWLEDGE_READ, false, false),
        KNOWLEDGE_DOCUMENT_QUERY("knowledge.document.query", AdminPermission.KNOWLEDGE_READ, false, false, "kb_id"),
        ORDER_QUERY("order.query", AdminPermission.ORDER_READ, false, true, "page", "page_size", "number", "phone", "status", "begin_time", "end_time"),
        ORDER_DETAIL("order.detail", AdminPermission.ORDER_READ, false, false, "order_id"),
        ORDER_STATISTICS("order.statistics", AdminPermission.ORDER_READ, false, false),
        ORDER_STATUS_UPDATE("order.status.update", AdminPermission.ORDER_STATUS_WRITE, true, false, "order_id", "action", "reason"),
        DISH_CATEGORY_QUERY("dish.category.query", AdminPermission.DISH_READ, false, false),
        DISH_CREATE("dish.create", AdminPermission.DISH_WRITE, true, false, "test_data", "dishes"),
        DISH_QUERY("dish.query", AdminPermission.DISH_READ, false, true, "page", "page_size", "name", "category_id", "status"),
        DISH_DETAIL("dish.detail", AdminPermission.DISH_READ, false, false, "dish_id"),
        DISH_UPDATE("dish.update", AdminPermission.DISH_WRITE, true, false, "dish_id", "name", "category_id", "price", "image", "description", "status"),
        SETMEAL_QUERY("setmeal.query", AdminPermission.SETMEAL_READ, false, true, "page", "page_size", "name", "category_id", "status"),
        SETMEAL_DETAIL("setmeal.detail", AdminPermission.SETMEAL_READ, false, false, "setmeal_id"),
        SETMEAL_UPDATE("setmeal.update", AdminPermission.SETMEAL_WRITE, true, false, "setmeal_id", "name", "category_id", "price", "image", "description", "status"),
        SHOP_STATUS_GET("shop.status.get", AdminPermission.SHOP_READ, false, false),
        SHOP_STATUS_UPDATE("shop.status.update", AdminPermission.SHOP_STATUS_WRITE, true, false, "status"),
        USER_ORDER_ACTION_PREVIEW("user.order.action.preview", AdminPermission.ORDER_READ, false, false, "order_id", "action"),
        USER_ORDER_REMIND("user.order.remind", AdminPermission.ORDER_READ, false, false, "order_id"),
        USER_ORDER_CANCEL_REQUEST("user.order.cancel.request", AdminPermission.ORDER_READ, true, false, "order_id", "reason"),
        USER_AFTER_SALE_SUBMIT("user.after_sale.submit", AdminPermission.ORDER_READ, true, false, "order_id", "reason"),
        WORKSPACE_OVERVIEW("workspace.overview", AdminPermission.WORKSPACE_READ, false, false),
        REPORT_QUERY("report.query", AdminPermission.REPORT_READ, false, false, "begin_time", "end_time");

        private final String code;
        private final AdminPermission permission;
        private final boolean write;
        private final boolean paged;
        private final Set<String> allowedArguments;

        Operation(String code, AdminPermission permission, boolean write, boolean paged, String... allowedArguments) {
            this.code = code; this.permission = permission; this.write = write; this.paged = paged;
            this.allowedArguments = Set.of(allowedArguments);
        }

        static Operation fromCode(String code) {
            return Arrays.stream(values()).filter(value -> value.code.equals(code)).findFirst().orElse(null);
        }
    }
}
