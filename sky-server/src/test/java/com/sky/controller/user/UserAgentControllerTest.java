package com.sky.controller.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.dto.AgentSubmitDTO;
import com.sky.service.UserAgentService;
import com.sky.service.AgentToolOperationService;
import com.sky.service.agent.UserAgentSseService;
import com.sky.vo.AgentSubmitVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserAgentControllerTest {
    private final UserAgentService agentService = mock(UserAgentService.class);
    private final UserAgentController controller = new UserAgentController(
            agentService, mock(AgentToolOperationService.class), mock(UserAgentSseService.class));
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 用户提交协议不接收主体和模型，任务ID只能来自幂等请求头。 */
    @Test
    void submitBuildsSafeInternalRequest() throws Exception {
        when(agentService.submitTask(any())).thenReturn(AgentSubmitVO.builder()
                .taskId("request-key-123").sessionId("session-1").status(0).build());

        mvc.perform(post("/user/agent/tasks")
                        .header("Idempotency-Key", "request-key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "message", "  推荐清淡午餐  ",
                                "clientContext", java.util.Map.of("page", "home"),
                                "userId", 999,
                                "model", "untrusted-model"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value("request-key-123"));

        ArgumentCaptor<AgentSubmitDTO> request = ArgumentCaptor.forClass(AgentSubmitDTO.class);
        verify(agentService).submitTask(request.capture());
        assertEquals("request-key-123", request.getValue().getTaskId());
        assertEquals("推荐清淡午餐", request.getValue().getQuery());
        assertNull(request.getValue().getModel());
        assertNull(request.getValue().getKbId());
    }

    @Test
    void submitRejectsInvalidIdempotencyKey() throws Exception {
        assertThrows(Exception.class, () -> mvc.perform(post("/user/agent/tasks")
                .header("Idempotency-Key", "bad key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"hello\"}")));
        verifyNoInteractions(agentService);
    }
}
