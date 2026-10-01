package com.sky.agent.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 定义了授权给Python Agent进行检索的知识库范围和参数。
 * <p>
 * 当Java后端授权一个检索请求时，会构建此对象，并将其作为调用Python Agent的参数之一。
 * 它精确地控制了Agent可以在哪个知识库、哪些文档的特定版本中进行搜索，以及搜索的行为（如top_k和score_threshold）。
 * </p>
 *
 * @param kbId             知识库的唯一标识符。
 * @param documentVersions 一个映射，key是文档ID，value是该文档允许被检索的版本号。
 *                         这确保了Agent只能查询已授权且版本正确的文档内容。
 * @param topK             在向量检索中，返回最相似的前K个结果。
 * @param scoreThreshold   向量检索的相似度得分阈值。只有得分高于此阈值的结果才会被返回。
 */
public record AgentKnowledgeScope(
        @JsonProperty("kb_id") String kbId,
        @JsonProperty("document_versions") Map<String, Integer> documentVersions,
        @JsonProperty("top_k") Integer topK,
        @JsonProperty("score_threshold") Double scoreThreshold,
        @JsonProperty("release_ids") List<String> releaseIds,
        @JsonProperty("categories") List<String> categories,
        @JsonProperty("expires_at") OffsetDateTime expiresAt) {

    /** 兼容管理端历史协议；新用户协议应使用发布版本范围。 */
    public AgentKnowledgeScope(String kbId, Map<String, Integer> documentVersions,
                               Integer topK, Double scoreThreshold) {
        this(kbId, documentVersions, topK, scoreThreshold, List.of(), List.of(), null);
    }
}
