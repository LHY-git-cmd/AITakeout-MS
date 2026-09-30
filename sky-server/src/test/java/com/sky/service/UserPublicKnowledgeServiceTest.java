package com.sky.service;

import com.sky.agent.AgentClient;
import com.sky.context.BaseContext;
import com.sky.entity.AgentKnowledgeBase;
import com.sky.entity.AgentKnowledgeDocument;
import com.sky.entity.UserAgentKnowledgeRelease;
import com.sky.exception.AgentBusinessException;
import com.sky.mapper.UserPublicKnowledgeMapper;
import com.sky.properties.AgentProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 公共知识发布前置条件与用户向量元数据同步测试。 */
@ExtendWith(MockitoExtension.class)
class UserPublicKnowledgeServiceTest {
    @Mock private UserPublicKnowledgeMapper mapper;
    @Mock private AgentClient agentClient;
    private UserPublicKnowledgeService service;

    @BeforeEach
    void setUp() {
        BaseContext.setCurrentId(22L);
        AgentProperties properties = new AgentProperties();
        properties.setEmbeddingModel("hash-384");
        service = new UserPublicKnowledgeService(mapper, agentClient, properties);
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
        service.shutdown();
    }

    @Test
    void creatorCanReviewOwnRelease() {
        UserAgentKnowledgeRelease release = release("DRAFT", 22L);
        when(mapper.getRelease("release-1")).thenReturn(release);
        when(mapper.getBase("kb-1")).thenReturn(base());
        when(mapper.countReleaseDocuments("release-1")).thenReturn(1);
        when(mapper.countInvalidReleaseDocuments("release-1")).thenReturn(0);
        when(mapper.approve("release-1", 22L, "APPROVED")).thenReturn(1);

        service.review("release-1", true, "ok");

        verify(mapper).approve("release-1", 22L, "APPROVED");
        verify(mapper).insertReview(argThat(review ->
                review.getReviewerId() == 22L && "APPROVED".equals(review.getDecision())));
    }

    @Test
    void documentWithUnfinishedIndexCannotBeBound() {
        UserAgentKnowledgeRelease release = release("DRAFT", 21L);
        when(mapper.getRelease("release-1")).thenReturn(release);
        when(mapper.getBase("kb-1")).thenReturn(base());
        AgentKnowledgeDocument document = new AgentKnowledgeDocument();
        document.setDocumentId("doc-1");
        document.setKbId("kb-1");
        document.setVersion(1);
        document.setStatus(2);
        document.setReviewStatus("PENDING");
        when(mapper.getDocumentVersion("doc-1", 1)).thenReturn(document);

        assertThrows(AgentBusinessException.class,
                () -> service.bindDocument("release-1", "doc-1", 1, "GENERAL"));
        verify(mapper, never()).bindDocument(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void indexedDocumentCanBeBoundWithoutManualReview() {
        when(mapper.getRelease("release-1")).thenReturn(release("DRAFT", 21L));
        when(mapper.getBase("kb-1")).thenReturn(base());
        AgentKnowledgeDocument document = new AgentKnowledgeDocument();
        document.setDocumentId("doc-1");
        document.setKbId("kb-1");
        document.setVersion(1);
        document.setStatus(3);
        document.setReviewStatus("PENDING");
        document.setCategory("GENERAL");
        when(mapper.getDocumentVersion("doc-1", 1)).thenReturn(document);

        service.bindDocument("release-1", "doc-1", 1, "GENERAL");

        verify(mapper).bindDocument("release-1", "doc-1", 1, "GENERAL");
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishWritesSnakeCaseReleaseScopeBeforeChangingDatabaseStatus() {
        when(mapper.getRelease("release-1")).thenReturn(release("APPROVED", 21L));
        when(mapper.getBase("kb-1")).thenReturn(base());
        when(mapper.listReleaseDocuments("release-1")).thenReturn(List.of(
                Map.of("documentId", "doc-1", "documentVersion", 3, "category", "GENERAL")));
        when(mapper.countInvalidReleaseDocuments("release-1")).thenReturn(0);
        when(mapper.changeStatus("release-1", 22L, "PUBLISHED")).thenReturn(1);

        service.publish("release-1");

        ArgumentCaptor<List<Map<String, Object>>> documents = ArgumentCaptor.forClass(List.class);
        verify(agentClient).updateKnowledgeRelease(eq("release-1"), documents.capture(), eq(true));
        assertEquals("doc-1", documents.getValue().get(0).get("document_id"));
        assertEquals(3, documents.getValue().get(0).get("document_version"));
        verify(mapper).changeStatus("release-1", 22L, "PUBLISHED");
    }

    @Test
    void sceneBindingIsExclusiveAndCanReactivateAnExistingBinding() {
        when(mapper.getRelease("release-1")).thenReturn(release("PUBLISHED", 21L));
        when(mapper.getBase("kb-1")).thenReturn(base());
        when(mapper.countBinding("USER_CHAT", "release-1")).thenReturn(1);

        service.bind("release-1", "USER_CHAT");

        var order = inOrder(mapper);
        order.verify(mapper).disableSceneBindings("USER_CHAT");
        order.verify(mapper).countBinding("USER_CHAT", "release-1");
        order.verify(mapper).activateBinding("USER_CHAT", "release-1");
        verify(mapper, never()).bindScene(any());
    }

    @Test
    void documentReferencedByImmutableReleaseCannotBeDeleted() {
        AgentKnowledgeDocument document = new AgentKnowledgeDocument();
        document.setDocumentId("doc-1");
        when(mapper.getDocument("doc-1")).thenReturn(document);
        when(mapper.countImmutableDocumentReleaseReferences("doc-1")).thenReturn(1);

        assertThrows(AgentBusinessException.class, () -> service.deleteDocument("doc-1"));

        verify(mapper, never()).deleteDocument(anyString());
        verify(agentClient, never()).deleteKnowledgeDocument(anyString(), any(), anyString());
    }

    @Test
    void documentReferencedOnlyByDraftIsDetachedAndDeleted() {
        AgentKnowledgeDocument document = new AgentKnowledgeDocument();
        document.setDocumentId("doc-1");
        when(mapper.getDocument("doc-1")).thenReturn(document);
        when(mapper.countImmutableDocumentReleaseReferences("doc-1")).thenReturn(0);
        when(mapper.listDocumentVersions("doc-1")).thenReturn(List.of(document));
        when(mapper.deleteDocument("doc-1")).thenReturn(1);

        service.deleteDocument("doc-1");

        verify(mapper).deleteDraftDocumentReferences("doc-1");
        verify(mapper).deleteDocument("doc-1");
    }

    @Test
    void failedDocumentReleasesStaleIndexTaskBeforeDelete() {
        AgentKnowledgeDocument document = new AgentKnowledgeDocument();
        document.setDocumentId("doc-1");
        document.setStatus(4);
        when(mapper.getDocument("doc-1")).thenReturn(document);
        when(mapper.countUnfinishedIndexTasks("doc-1")).thenReturn(0);
        when(mapper.countImmutableDocumentReleaseReferences("doc-1")).thenReturn(0);
        when(mapper.listDocumentVersions("doc-1")).thenReturn(List.of(document));
        when(mapper.deleteDocument("doc-1")).thenReturn(1);

        service.deleteDocument("doc-1");

        var order = inOrder(mapper);
        order.verify(mapper).failUnfinishedIndexTasks(eq("doc-1"), anyString());
        order.verify(mapper).countUnfinishedIndexTasks("doc-1");
        order.verify(mapper).deleteDocument("doc-1");
    }

    @Test
    void draftDocumentCanBeUnbound() {
        when(mapper.getRelease("release-1")).thenReturn(release("DRAFT", 22L));
        when(mapper.unbindDocument("release-1", "doc-1", 1)).thenReturn(1);

        service.unbindDocument("release-1", "doc-1", 1);

        verify(mapper).unbindDocument("release-1", "doc-1", 1);
    }

    @Test
    void publishedDocumentCannotBeUnbound() {
        when(mapper.getRelease("release-1")).thenReturn(release("PUBLISHED", 22L));

        assertThrows(AgentBusinessException.class,
                () -> service.unbindDocument("release-1", "doc-1", 1));

        verify(mapper, never()).unbindDocument(anyString(), anyString(), anyInt());
    }

    @Test
    void draftCanBeAbandonedAndItsReferencesRemoved() {
        when(mapper.getRelease("release-1")).thenReturn(release("DRAFT", 22L));
        when(mapper.abandonRelease("release-1")).thenReturn(1);

        service.abandonRelease("release-1");

        var order = inOrder(mapper);
        order.verify(mapper).deleteReleaseDocuments("release-1");
        order.verify(mapper).abandonRelease("release-1");
        verify(mapper).insertAudit(argThat(audit -> "ABANDON".equals(audit.getAction())));
    }

    @Test
    void archiveImmediatelyDisablesBindingsAndOfflinesPublishedRelease() {
        AgentKnowledgeBase base = new AgentKnowledgeBase();
        base.setKbId("kb-1");
        base.setLifecycleStatus("PUBLISHED");
        when(mapper.getBase("kb-1")).thenReturn(base);
        when(mapper.listReleasesByBase("kb-1")).thenReturn(List.of(release("PUBLISHED", 22L)));
        when(mapper.listReleaseDocuments("release-1")).thenReturn(List.of());
        when(mapper.changeStatus("release-1", 22L, "OFFLINE")).thenReturn(1);
        when(mapper.archiveBase("kb-1")).thenReturn(1);

        service.archiveBase("kb-1");

        var order = inOrder(mapper);
        order.verify(mapper).disableBindings("release-1");
        order.verify(mapper).changeStatus("release-1", 22L, "OFFLINE");
        order.verify(mapper).archiveBase("kb-1");
    }

    @Test
    void offlineRevokesDatabaseAccessBeforeRemoteCleanup() {
        when(mapper.getRelease("release-1")).thenReturn(release("PUBLISHED", 22L));
        when(mapper.listReleaseDocuments("release-1")).thenReturn(List.of());
        when(mapper.changeStatus("release-1", 22L, "OFFLINE")).thenReturn(1);

        service.offline("release-1");

        var order = inOrder(mapper);
        order.verify(mapper).disableBindings("release-1");
        order.verify(mapper).changeStatus("release-1", 22L, "OFFLINE");
    }

    @Test
    void purgeRequiresArchivedBase() {
        AgentKnowledgeBase base = base();
        base.setName("test");
        when(mapper.getBase("kb-1")).thenReturn(base);

        assertThrows(AgentBusinessException.class, () -> service.purgeBase("kb-1", "test"));

        verify(mapper, never()).startPurgeBase(anyString());
    }

    @Test
    void purgeRequiresExactKnowledgeBaseName() {
        AgentKnowledgeBase base = base();
        base.setName("test");
        base.setLifecycleStatus("ARCHIVED");
        when(mapper.getBase("kb-1")).thenReturn(base);

        assertThrows(AgentBusinessException.class, () -> service.purgeBase("kb-1", "wrong"));

        verify(mapper, never()).startPurgeBase(anyString());
    }

    private UserAgentKnowledgeRelease release(String status, long creator) {
        UserAgentKnowledgeRelease value = new UserAgentKnowledgeRelease();
        value.setReleaseId("release-1");
        value.setKbId("kb-1");
        value.setStatus(status);
        value.setCreatedBy(creator);
        return value;
    }

    private AgentKnowledgeBase base() {
        AgentKnowledgeBase value = new AgentKnowledgeBase();
        value.setKbId("kb-1");
        value.setStatus(1);
        value.setLifecycleStatus("DRAFT");
        return value;
    }
}
