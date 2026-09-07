package com.sky.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sky.agent.model.AgentStreamEvent;
import com.sky.entity.AgentEvent;
import com.sky.entity.AgentMessage;
import com.sky.entity.AgentMessageCitation;
import com.sky.entity.AgentSession;
import com.sky.entity.AgentTask;
import com.sky.mapper.AgentEventMapper;
import com.sky.mapper.AgentCitationMapper;
import com.sky.mapper.AgentMessageMapper;
import com.sky.mapper.AgentSessionMapper;
import com.sky.mapper.AgentTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentEventProcessorTest {

    @Mock private AgentEventMapper eventMapper;
    @Mock private AgentTaskMapper taskMapper;
    @Mock private AgentMessageMapper messageMapper;
    @Mock private AgentSessionMapper sessionMapper;
    @Mock private AgentCitationMapper citationMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AgentEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new AgentEventProcessor(eventMapper, taskMapper, messageMapper,
                sessionMapper, citationMapper, objectMapper, null);
    }

    @Test
    void duplicateEventDoesNotAdvanceTaskState() {
        AgentTask task = AgentTask.builder().taskId("task-1").status(0).build();
        when(taskMapper.getByTaskId("task-1")).thenReturn(task);
        when(eventMapper.insertIfAbsent(any(AgentEvent.class))).thenReturn(0);

        boolean accepted = processor.process(event("task-1", 1, "task_start", null));

        assertFalse(accepted);
        verify(taskMapper, never()).transitionStatus(any(), any(), any(), any(), any(), any());
    }

    @Test
    void taskEndPersistsOneAssistantMessageAndCompletesTask() {
        AgentTask task = AgentTask.builder().taskId("task-2").sessionId("session-1")
                .status(1).progress(1).build();
        AgentSession session = AgentSession.builder().id(10L).sessionId("session-1").build();
        ObjectNode data = objectMapper.createObjectNode().put("result", "final answer");
        when(taskMapper.getByTaskId("task-2")).thenReturn(task);
        when(eventMapper.insertIfAbsent(any(AgentEvent.class))).thenReturn(1);
        when(taskMapper.transitionStatus(eq("task-2"), eq(2), eq(100), any(),
                eq(null), eq(java.util.List.of(0, 1)))).thenReturn(1);
        when(messageMapper.getNextSeqNo("session-1")).thenReturn(2);
        when(messageMapper.insertIfAbsent(any(AgentMessage.class))).thenReturn(1);
        when(sessionMapper.getBySessionIdForUpdate("session-1")).thenReturn(session);

        assertTrue(processor.process(event("task-2", 4, "task_end", data)));

        ArgumentCaptor<AgentMessage> message = ArgumentCaptor.forClass(AgentMessage.class);
        verify(messageMapper).insertIfAbsent(message.capture());
        assertEquals("final answer", message.getValue().getContent());
        assertEquals(2, message.getValue().getRole());
        verify(sessionMapper).incrementMessageCount(10L, "task-2");
    }

    @Test
    void completedTaskRejectsLateTokenBeforePersistence() {
        AgentTask task = AgentTask.builder().taskId("task-3").status(2).build();
        when(taskMapper.getByTaskId("task-3")).thenReturn(task);

        assertFalse(processor.process(event("task-3", 9, "token",
                objectMapper.createObjectNode().put("content", "late"))));

        verify(eventMapper, never()).insertIfAbsent(any());
    }

    @Test
    void taskEndPersistsOnlyCitationFromBoundKnowledgeBase() {
        AgentTask task = AgentTask.builder().taskId("task-citation").sessionId("session-1")
                .status(1).progress(1).build();
        AgentSession session = AgentSession.builder().id(10L).sessionId("session-1").kbId("kb-1").build();
        ObjectNode data = objectMapper.createObjectNode().put("result", "answer");
        data.putArray("citations")
                .add(citation("kb-1", "chunk-1"))
                .add(citation("kb-2", "chunk-2"));
        when(taskMapper.getByTaskId("task-citation")).thenReturn(task);
        when(eventMapper.insertIfAbsent(any(AgentEvent.class))).thenReturn(1);
        when(taskMapper.transitionStatus(eq("task-citation"), eq(2), eq(100), any(),
                eq(null), eq(java.util.List.of(0, 1)))).thenReturn(1);
        when(messageMapper.getNextSeqNo("session-1")).thenReturn(2);
        when(messageMapper.insertIfAbsent(any(AgentMessage.class))).thenReturn(1);
        when(sessionMapper.getBySessionIdForUpdate("session-1")).thenReturn(session);

        assertTrue(processor.process(event("task-citation", 4, "task_end", data)));

        ArgumentCaptor<AgentMessageCitation> citation = ArgumentCaptor.forClass(AgentMessageCitation.class);
        verify(citationMapper).insertIfAbsent(citation.capture());
        assertEquals("kb-1", citation.getValue().getKbId());
        assertEquals("chunk-1", citation.getValue().getChunkId());
    }

    @Test
    void cancelledTaskStillPersistsEarlierOrderedEvents() {
        AgentTask task = AgentTask.builder().taskId("task-4").status(4).build();
        when(taskMapper.getByTaskId("task-4")).thenReturn(task);
        when(eventMapper.insertIfAbsent(any())).thenReturn(1);

        assertTrue(processor.process(event("task-4", 1, "task_start", null)));

        verify(taskMapper, never()).transitionStatus(any(), any(), any(), any(), any(), any());
    }

    private AgentStreamEvent event(String taskId, int seqNo, String type, ObjectNode data) {
        return new AgentStreamEvent(taskId, seqNo, type, data);
    }

    private ObjectNode citation(String kbId, String chunkId) {
        return objectMapper.createObjectNode()
                .put("kb_id", kbId)
                .put("document_id", "document-1")
                .put("document_version", 1)
                .put("chunk_id", chunkId)
                .put("file_name", "manual.pdf")
                .put("page_no", 3)
                .put("score", 0.91)
                .put("quote", "quoted text");
    }
}
