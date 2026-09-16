package com.sky.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.agent.AgentClient;
import com.sky.context.BaseContext;
import com.sky.entity.AgentSession;
import com.sky.exception.PermissionDeniedException;
import com.sky.handler.GlobalExceptionHandler;
import com.sky.mapper.AgentCitationMapper;
import com.sky.mapper.AgentEventMapper;
import com.sky.mapper.AgentKnowledgeMapper;
import com.sky.mapper.AgentMessageMapper;
import com.sky.mapper.AgentSessionMapper;
import com.sky.mapper.AgentSessionSummaryMapper;
import com.sky.mapper.AgentTaskMapper;
import com.sky.mapper.EmployeeMapper;
import com.sky.properties.AgentProperties;
import com.sky.interceptor.AdminPermissionInterceptor;
import com.sky.service.agent.AgentEventHub;
import com.sky.service.agent.AgentEventStreamCoordinator;
import com.sky.service.agent.AgentKnowledgeService;
import com.sky.service.agent.AgentMessageCacheService;
import com.sky.service.agent.AgentSummaryService;
import com.sky.service.EmployeeService;
import com.sky.service.impl.AgentServiceImpl;
import com.sky.service.security.AdminAuthorizationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

/**
 * 验证其他已登录管理员访问 Agent 私有资源时的 HTTP 权限语义。
 */
@ExtendWith(MockitoExtension.class)
class AgentAuthorizationHttpTest {

    @Mock private AgentClient agentClient;
    @Mock private AgentSessionMapper sessionMapper;
    @Mock private AgentTaskMapper taskMapper;
    @Mock private AgentMessageMapper messageMapper;
    @Mock private AgentEventMapper eventMapper;
    @Mock private AgentEventStreamCoordinator eventStreamCoordinator;
    @Mock private AgentMessageCacheService messageCacheService;
    @Mock private AgentSummaryService summaryService;
    @Mock private AgentKnowledgeMapper knowledgeMapper;
    @Mock private AgentCitationMapper citationMapper;
    @Mock private AgentSessionSummaryMapper summaryMapper;
    @Mock private AgentEventHub eventHub;
    @Mock private EmployeeService employeeService;
    @Mock private EmployeeMapper employeeMapper;

    private MockMvc mvc;
    private MockMvc nonAgentMvc;
    private MockMvc permissionMvc;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(7L);
        AgentProperties properties = new AgentProperties();
        AgentServiceImpl agentService = new AgentServiceImpl(agentClient, sessionMapper, taskMapper,
                messageMapper, eventMapper, properties, eventStreamCoordinator, messageCacheService,
                summaryService, knowledgeMapper, citationMapper, summaryMapper);
        AgentKnowledgeService knowledgeService = new AgentKnowledgeService(knowledgeMapper, agentClient, properties);
        mvc = MockMvcBuilders.standaloneSetup(
                        new AgentController(agentService, agentClient, eventHub, new ObjectMapper()),
                        new AgentKnowledgeController(knowledgeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        EmployeeController employeeController = new EmployeeController();
        ReflectionTestUtils.setField(employeeController, "employeeService", employeeService);
        nonAgentMvc = MockMvcBuilders.standaloneSetup(employeeController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        permissionMvc = MockMvcBuilders.standaloneSetup(
                        new AgentKnowledgeController(knowledgeService), employeeController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new AdminPermissionInterceptor(new AdminAuthorizationService(employeeMapper)))
                .build();
        BaseContext.setCurrentRole("ADMIN");
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    @Test
    void anotherAdministratorCannotReadTaskWithoutResourceDisclosure() throws Exception {
        when(taskMapper.getByTaskIdAndUserId("task-owned-by-another-admin", 7L)).thenReturn(null);

        assertForbidden(get("/admin/agent/tasks/task-owned-by-another-admin"));
    }

    @Test
    void anotherAdministratorCannotReadSessionWithoutResourceDisclosure() throws Exception {
        when(sessionMapper.getBySessionId("session-owned-by-another-admin")).thenReturn(
                AgentSession.builder().sessionId("session-owned-by-another-admin").userId(8L).build());

        assertForbidden(get("/admin/agent/sessions/session-owned-by-another-admin"));
    }

    @Test
    void anotherAdministratorCannotDeleteKnowledgeBaseWithoutResourceDisclosure() throws Exception {
        when(knowledgeMapper.getOwnedBase("knowledge-base-owned-by-another-admin", 7L)).thenReturn(null);

        assertForbidden(delete("/admin/agent/knowledge-bases/knowledge-base-owned-by-another-admin"));
    }

    @Test
    void anotherAdministratorCannotReadDocumentWithoutResourceDisclosure() throws Exception {
        when(knowledgeMapper.getOwnedDocument("document-owned-by-another-admin", 7L)).thenReturn(null);

        assertForbidden(get("/admin/agent/documents/document-owned-by-another-admin/content"));
    }

    @Test
    void anotherAdministratorCannotSubmitToTheirDeletedSessionWithoutResourceDisclosure() throws Exception {
        when(sessionMapper.getBySessionIdForUpdate("deleted-session-owned-by-another-admin")).thenReturn(
                AgentSession.builder().sessionId("deleted-session-owned-by-another-admin")
                        .userId(8L).status(3).build());

        assertForbidden(post("/admin/agent/tasks/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"taskId":"task-for-deleted-session","query":"question",
                         "sessionId":"deleted-session-owned-by-another-admin"}
                        """));
    }

    @Test
    void nonAgentPermissionDeniedKeepsItsOriginalMessage() throws Exception {
        when(employeeService.getById(9L))
                .thenThrow(new PermissionDeniedException("当前管理员无权执行该操作"));

        nonAgentMvc.perform(get("/admin/employee/9"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("当前管理员无权执行该操作"));
    }

    @Test
    void agentWritePermissionDeniedByInterceptorUsesSafeMessage() throws Exception {
        permissionMvc.perform(post("/admin/agent/knowledge-bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("资源不存在或无权访问"));
    }

    @Test
    void nonAgentWritePermissionDeniedByInterceptorKeepsOriginalMessage() throws Exception {
        permissionMvc.perform(post("/admin/employee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("当前管理员无权执行该操作"));
    }

    private void assertForbidden(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        mvc.perform(request)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("资源不存在或无权访问"));
    }
}
