from pydantic import Field

from app.tools.models import EmptyArguments, ToolArguments, ToolDefinition


class QueryKnowledgeDocumentsArguments(ToolArguments):
    kb_id: str = Field(min_length=1, max_length=64, description="知识库ID")


DEFINITIONS = (
    ToolDefinition(
        name="query_knowledge_bases",
        description="查询当前管理员有权查看的知识库元数据列表。",
        arguments_model=EmptyArguments,
        operation="knowledge.base.query",
    ),
    ToolDefinition(
        name="query_knowledge_documents",
        description="查询指定知识库中的文档元数据，不返回完整文档正文。",
        arguments_model=QueryKnowledgeDocumentsArguments,
        operation="knowledge.document.query",
    ),
)
