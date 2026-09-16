package com.sky.service.agent;

import com.sky.agent.AgentClient;
import com.sky.agent.model.AgentSubmitRequest;
import com.sky.context.BaseContext;
import com.sky.dto.AgentSubmitDTO;
import com.sky.entity.AgentKnowledgeBase;
import com.sky.entity.AgentKnowledgeDocument;
import com.sky.entity.AgentSession;
import com.sky.entity.AgentTask;
import com.sky.exception.AgentBusinessException;
import com.sky.exception.AgentTaskConflictException;
import com.sky.exception.PermissionDeniedException;
import com.sky.mapper.AgentCitationMapper;
import com.sky.mapper.AgentEventMapper;
import com.sky.mapper.AgentKnowledgeMapper;
import com.sky.mapper.AgentMessageMapper;
import com.sky.mapper.AgentSessionMapper;
import com.sky.mapper.AgentSessionSummaryMapper;
import com.sky.mapper.AgentTaskMapper;
import com.sky.properties.AgentProperties;
import com.sky.service.impl.AgentServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServiceKnowledgeTest {

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

    private AgentServiceImpl service;

    @BeforeEach
    void setUp() {
        AgentProperties properties = new AgentProperties();
        properties.setRagTopK(5);
        properties.setRagScoreThreshold(0.41);
        service = new AgentServiceImpl(agentClient, sessionMapper, taskMapper, messageMapper,
                eventMapper, properties, eventStreamCoordinator, messageCacheService, summaryService,
                knowledgeMapper, citationMapper, summaryMapper);
        BaseContext.setCurrentId(7L);
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    @Test
    void historicalSessionCanBindKnowledgeBaseOnceAndSendReadyVersions() {
        AgentSession session = AgentSession.builder().id(10L).sessionId("session-1")
                .userId(7L).status(1).build();
        AgentKnowledgeBase kb = new AgentKnowledgeBase();
        kb.setKbId("kb-1");
        kb.setStatus(1);
        AgentKnowledgeDocument document = new AgentKnowledgeDocument();
        document.setDocumentId("document-1");
        document.setActiveVersion(3);
        when(sessionMapper.getBySessionIdForUpdate("session-1")).thenReturn(session);
        when(knowledgeMapper.getOwnedBase("kb-1", 7L)).thenReturn(kb);
        when(knowledgeMapper.listReadyDocuments("kb-1")).thenReturn(List.of(document));
        when(messageMapper.listRecentBySessionId(any(), any())).thenReturn(List.of());
        when(taskMapper.insertIgnore(any())).thenReturn(1);

        service.submitTask(request("task-1", "kb-1"));

        ArgumentCaptor<AgentSession> update = ArgumentCaptor.forClass(AgentSession.class);
        verify(sessionMapper).update(update.capture());
        assertEquals("kb-1", update.getValue().getKbId());
        ArgumentCaptor<AgentSubmitRequest> submitted = ArgumentCaptor.forClass(AgentSubmitRequest.class);
        verify(agentClient).submit(submitted.capture());
        assertEquals("kb-1", submitted.getValue().knowledge().kbId());
        assertEquals(3, submitted.getValue().knowledge().documentVersions().get("document-1"));
        assertEquals(5, submitted.getValue().knowledge().topK());
        assertEquals(0.41, submitted.getValue().knowledge().scoreThreshold());
    }

    @Test
    void disabledRagDoesNotSendKnowledgeScope() {
        AgentProperties properties = new AgentProperties();
        properties.setRagEnabled(false);
        service = new AgentServiceImpl(agentClient, sessionMapper, taskMapper, messageMapper,
                eventMapper, properties, eventStreamCoordinator, messageCacheService, summaryService,
                knowledgeMapper, citationMapper, summaryMapper);
        AgentSession session = AgentSession.builder().id(10L).sessionId("session-1")
                .userId(7L).status(1).kbId("kb-1").build();
        AgentKnowledgeBase kb = new AgentKnowledgeBase();
        kb.setKbId("kb-1");
        kb.setStatus(1);
        when(sessionMapper.getBySessionIdForUpdate("session-1")).thenReturn(session);
        when(knowledgeMapper.getOwnedBase("kb-1", 7L)).thenReturn(kb);
        when(messageMapper.listRecentBySessionId(any(), any())).thenReturn(List.of());
        when(taskMapper.insertIgnore(any())).thenReturn(1);

        service.submitTask(request("task-rag-off", "kb-1"));

        ArgumentCaptor<AgentSubmitRequest> submitted = ArgumentCaptor.forClass(AgentSubmitRequest.class);
        verify(agentClient).submit(submitted.capture());
        org.junit.jupiter.api.Assertions.assertNull(submitted.getValue().knowledge());
    }

    @Test
    void boundSessionRejectsKnowledgeBaseSwitch() {
        AgentSession session = AgentSession.builder().id(10L).sessionId("session-1")
                .userId(7L).status(1).kbId("kb-1").build();
        when(sessionMapper.getBySessionIdForUpdate("session-1")).thenReturn(session);

        assertThrows(AgentBusinessException.class, () -> service.submitTask(request("task-2", "kb-2")));

        verify(agentClient, never()).submit(any(AgentSubmitRequest.class));
    }

    @Test
    void disabledOrUnownedKnowledgeBaseIsRejected() {
        AgentSession session = AgentSession.builder().id(10L).sessionId("session-1")
                .userId(7L).status(1).build();
        when(sessionMapper.getBySessionIdForUpdate("session-1")).thenReturn(session);
        when(knowledgeMapper.getOwnedBase("kb-1", 7L)).thenReturn(null);

        assertThrows(PermissionDeniedException.class, () -> service.submitTask(request("task-3", "kb-1")));

        verify(agentClient, never()).submit(any(AgentSubmitRequest.class));
    }

    @Test
    void sameTaskIdWithDifferentKnowledgeBaseIsRejected() {
        AgentSession session = AgentSession.builder().id(10L).sessionId("session-1")
                .userId(7L).status(1).build();
        AgentKnowledgeBase kb = new AgentKnowledgeBase();
        kb.setKbId("kb-1");
        kb.setStatus(1);
        AtomicReference<AgentTask> storedTask = new AtomicReference<>();
        when(taskMapper.getByTaskIdAndUserId("task-4", 7L)).thenAnswer(ignored -> storedTask.get());
        when(sessionMapper.getBySessionIdForUpdate("session-1")).thenReturn(session);
        when(sessionMapper.getBySessionId("session-1")).thenReturn(session);
        when(knowledgeMapper.getOwnedBase("kb-1", 7L)).thenReturn(kb);
        when(knowledgeMapper.listReadyDocuments("kb-1")).thenReturn(List.of());
        when(messageMapper.listRecentBySessionId(any(), any())).thenReturn(List.of());
        when(taskMapper.insertIgnore(any())).thenAnswer(invocation -> {
            storedTask.set(invocation.getArgument(0));
            return 1;
        });

        service.submitTask(request("task-4", "kb-1"));

        service.submitTask(request("task-4", null));
        verify(agentClient, times(1)).submit(any(AgentSubmitRequest.class));

        assertThrows(AgentTaskConflictException.class,
                () -> service.submitTask(request("task-4", "kb-2")));
    }

    @Test
    void anotherAdministratorCannotSubmitToTheirSession() {
        AgentSession session = AgentSession.builder().id(11L).sessionId("session-owned-by-another-admin")
                .userId(8L).status(1).build();
        when(sessionMapper.getBySessionIdForUpdate(session.getSessionId())).thenReturn(session);

        assertThrows(PermissionDeniedException.class,
                () -> service.submitTask(request("task-foreign-session", session.getSessionId(), null)));

        verify(agentClient, never()).submit(any(AgentSubmitRequest.class));
    }

    @Test
    void anotherAdministratorCannotReadTheirTask() {
        when(taskMapper.getByTaskIdAndUserId("task-owned-by-another-admin", 7L)).thenReturn(null);

        assertThrows(PermissionDeniedException.class,
                () -> service.getTaskDetail("task-owned-by-another-admin"));
    }

    private AgentSubmitDTO request(String taskId, String kbId) {
        return request(taskId, "session-1", kbId);
    }

    private AgentSubmitDTO request(String taskId, String sessionId, String kbId) {
        AgentSubmitDTO dto = new AgentSubmitDTO();
        dto.setTaskId(taskId);
        dto.setSessionId(sessionId);
        dto.setQuery("question");
        dto.setKbId(kbId);
        return dto;
    }
}
