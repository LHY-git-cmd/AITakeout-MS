package com.sky.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.agent.model.AgentHistoryMessage;
import com.sky.agent.model.AgentKnowledgeScope;
import com.sky.agent.model.AgentStreamEvent;
import com.sky.agent.model.AgentSubmitRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void submitRequestUsesPythonSnakeCaseFields() throws Exception {
        AgentSubmitRequest request = new AgentSubmitRequest(
                "task-1", "session-1", 7L, "hello", "deepseek-v4-pro", 0.7,
                Map.of("history", List.of(new AgentHistoryMessage("user", "previous"))),
                new AgentKnowledgeScope("kb-1", Map.of("document-1", 2), 8, 0.2));

        String json = objectMapper.writeValueAsString(request);

        assertTrue(json.contains("\"task_id\":\"task-1\""));
        assertTrue(json.contains("\"session_id\":\"session-1\""));
        assertTrue(json.contains("\"user_id\":7"));
        assertTrue(json.contains("\"kb_id\":\"kb-1\""));
        assertTrue(json.contains("\"document_versions\":{\"document-1\":2}"));
        assertTrue(json.contains("\"top_k\":8"));
        assertTrue(json.contains("\"score_threshold\":0.2"));
    }

    @Test
    void streamEventReadsPythonProtocol() throws Exception {
        String json = "{\"task_id\":\"task-1\",\"seq_no\":2,"
                + "\"event\":\"token\",\"data\":{\"content\":\"hi\"}}";

        AgentStreamEvent event = objectMapper.readValue(json, AgentStreamEvent.class);

        assertEquals("task-1", event.taskId());
        assertEquals(2, event.seqNo());
        assertEquals("hi", event.data().get("content").asText());
    }
}
