package com.sky.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.dto.AgentToolOperationRequest;
import com.sky.entity.AgentTask;
import com.sky.entity.AgentToolAudit;
import com.sky.entity.AgentToolConfirmation;
import com.sky.entity.Employee;
import com.sky.enumeration.AdminPermission;
import com.sky.enumeration.AdminRole;
import com.sky.exception.AgentConfirmationConflictException;
import com.sky.mapper.AgentKnowledgeMapper;
import com.sky.mapper.AgentTaskMapper;
import com.sky.mapper.AgentToolAuditMapper;
import com.sky.mapper.AgentToolConfirmationMapper;
import com.sky.properties.AgentProperties;
import com.sky.service.*;
import com.sky.service.security.AdminAuthorizationService;
import com.sky.vo.AgentToolOperationResponse;
import com.sky.vo.EmployeeToolVO;
import com.sky.vo.OrderVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

class AgentToolOperationServiceImplTest {
    private final AgentTaskMapper taskMapper = mock(AgentTaskMapper.class);
    private final AgentToolAuditMapper auditMapper = mock(AgentToolAuditMapper.class);
    private final AgentToolConfirmationMapper confirmationMapper = mock(AgentToolConfirmationMapper.class);
    private final AgentKnowledgeMapper knowledgeMapper = mock(AgentKnowledgeMapper.class);
    private final AdminAuthorizationService authorizationService = mock(AdminAuthorizationService.class);
    private final EmployeeService employeeService = mock(EmployeeService.class);
    private final OrderService orderService = mock(OrderService.class);
    private final DishService dishService = mock(DishService.class);
    private final SetmealService setmealService = mock(SetmealService.class);
    private final WorkspaceService workspaceService = mock(WorkspaceService.class);
    private final ReportService reportService = mock(ReportService.class);
    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentProperties agentProperties = new AgentProperties();
    private AgentToolOperationServiceImpl service;

    @BeforeEach
    void setUp() {
        reset(taskMapper, auditMapper, confirmationMapper, knowledgeMapper, authorizationService, employeeService,
                orderService, dishService, setmealService, workspaceService, reportService, redisTemplate);
        service = new AgentToolOperationServiceImpl(taskMapper, auditMapper, confirmationMapper, knowledgeMapper,
                authorizationService, employeeService, orderService, dishService, setmealService,
                workspaceService, reportService, redisTemplate, agentProperties, objectMapper);
        when(taskMapper.getByTaskId("task-1")).thenReturn(AgentTask.builder().taskId("task-1").userId(9L).build());
        when(authorizationService.resolveRole(9L)).thenReturn(AdminRole.ADMIN);
    }

    @Test
    void employeeDetailUsesTaskIdentityAndReturnsOnlyMaskedToolVo() throws Exception {
        Employee employee = Employee.builder().id(3L).username("worker").name("员工")
                .phone("13812341234").idNumber("110101199001011234")
                .password("must-not-leak").role("ADMIN").status(1).build();
        when(employeeService.getById(3L)).thenReturn(employee);

        AgentToolOperationResponse response = service.execute(request("employee.detail", "{\"employee_id\":3}"));

        assertEquals("success", response.status());
        EmployeeToolVO data = assertInstanceOf(EmployeeToolVO.class, response.data());
        assertEquals("138****1234", data.maskedPhone());
        assertEquals("110***********1234", data.maskedIdNumber());
        verify(authorizationService).resolveRole(9L);
        verify(authorizationService).require(AdminRole.ADMIN, AdminPermission.EMPLOYEE_READ);
        assertFalse(objectMapper.writeValueAsString(data).contains("must-not-leak"));
    }

    @Test
    void writeOperationStopsAtConfirmationBoundary() throws Exception {
        AgentToolOperationResponse response = service.execute(request("order.status.update",
                "{\"order_id\":8,\"action\":\"confirm\"}"));

        assertEquals("confirmation_required", response.status());
        assertEquals("CONFIRMATION_REQUIRED", response.error().get("code"));
        verify(authorizationService).require(AdminRole.ADMIN, AdminPermission.ORDER_STATUS_WRITE);
        verifyNoInteractions(orderService);
    }

    @Test
    void unknownOperationIsRejectedAndAudited() throws Exception {
        AgentToolOperationResponse response = service.execute(request("employee.delete", "{}"));

        assertEquals("rejected", response.status());
        assertEquals("UNKNOWN_OPERATION", response.error().get("code"));
        ArgumentCaptor<AgentToolAudit> captor = ArgumentCaptor.forClass(AgentToolAudit.class);
        verify(auditMapper).insert(captor.capture());
        assertEquals("UNKNOWN", captor.getValue().getRequiredPermission());
        verifyNoInteractions(authorizationService);
    }

    @Test
    void javaRejectsOversizedPageEvenIfPythonWasBypassed() throws Exception {
        AgentToolOperationResponse response = service.execute(request("employee.query",
                "{\"page\":1,\"page_size\":51}"));

        assertEquals("rejected", response.status());
        assertEquals("INVALID_ARGUMENT", response.error().get("code"));
        verifyNoInteractions(employeeService);
    }

    @Test
    void onlyOriginalTaskActorCanConfirmWrite() {
        AgentToolConfirmation confirmation = AgentToolConfirmation.builder()
                .confirmationId("confirm-1").taskId("task-1").employeeId(9L)
                .operation("order.status.update").status("PENDING")
                .expiresAt(LocalDateTime.now().plusMinutes(5)).build();
        when(confirmationMapper.getByConfirmationId("confirm-1")).thenReturn(confirmation);

        assertThrows(com.sky.exception.PermissionDeniedException.class,
                () -> service.decideConfirmation("confirm-1", 10L, true));
        verify(confirmationMapper, never()).transitionByActor(anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void expiredConfirmationReturnsBusinessConflict() {
        AgentToolConfirmation confirmation = AgentToolConfirmation.builder()
                .confirmationId("confirm-expired").taskId("task-1").employeeId(9L)
                .operation("order.status.update").status("EXPIRED")
                .expiresAt(LocalDateTime.now().minusSeconds(1)).build();
        when(confirmationMapper.getByConfirmationId("confirm-expired")).thenReturn(confirmation);

        AgentConfirmationConflictException exception = assertThrows(
                AgentConfirmationConflictException.class,
                () -> service.decideConfirmation("confirm-expired", 9L, true));

        assertTrue(exception.getMessage().contains("已过期"));
        verify(confirmationMapper).transitionByActor(
                "confirm-expired", 9L, "PENDING", "CONFIRMED");
    }

    @Test
    void confirmedWriteIsSingleUseAndCallsDomainService() throws Exception {
        String arguments = "{\"order_id\":8,\"action\":\"confirm\"}";
        OrderVO order = new OrderVO();
        order.setId(8L); order.setStatus(2); order.setPayStatus(1);
        order.setOrderTime(LocalDateTime.of(2026, 9, 13, 12, 0));
        String version = sha256("8:2:1:2026-09-13T12:00:null");
        AgentToolConfirmation confirmation = AgentToolConfirmation.builder()
                .confirmationId("confirm-2").taskId("task-1").toolCallId("call-2")
                .employeeId(9L).operation("order.status.update")
                .argumentsJson(arguments).argumentHash(sha256("{\"action\":\"confirm\",\"order_id\":8}"))
                .resourceVersion(version).status("CONFIRMED")
                .expiresAt(LocalDateTime.now().plusMinutes(5)).build();
        when(confirmationMapper.getByConfirmationId("confirm-2")).thenReturn(confirmation);
        when(orderService.details(8L)).thenReturn(order);
        when(confirmationMapper.transition("confirm-2", "CONFIRMED", "EXECUTING")).thenReturn(1);
        when(confirmationMapper.markExecuted("confirm-2")).thenReturn(1);

        AgentToolOperationResponse response = service.executeConfirmed("confirm-2");

        assertEquals("success", response.status());
        verify(orderService).confirm(argThat(value -> value.getId().equals(8L)));
        verify(confirmationMapper).markExecuted("confirm-2");
    }

    private AgentToolOperationRequest request(String operation, String arguments) throws Exception {
        return new AgentToolOperationRequest("request-1", "task-1", "call-1", operation,
                objectMapper.readTree(arguments));
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
