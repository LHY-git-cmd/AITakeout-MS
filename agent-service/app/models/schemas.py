"""
数据模型定义模块
使用 Pydantic 定义请求体和响应体的结构，
FastAPI 会自动根据这些模型生成 Swagger 文档和进行参数校验
"""
from pydantic import BaseModel, Field
from typing import Optional, Dict, Any, Literal
from datetime import datetime


class AgentRequest(BaseModel):
    """
    Agent 通用请求模型
    同步查询、流式查询、异步提交三个接口共用此请求体
    """
    # Java/数据库中的用户主键为数值型；Python 3 的 int 可承载 Java Long。
    user_id: Optional[int] = Field(None, description="当前用户ID，异步任务接口必填，其他接口可选")
    task_id: Optional[str] = Field(None, min_length=1, max_length=64, description="由Java生成的任务ID")
    trace_id: Optional[str] = Field(None, min_length=1, max_length=64, description="跨服务调用追踪ID")
    actor_role: Literal["SUPER_ADMIN", "ADMIN"] = Field(
        default="ADMIN", description="由Java数据库确定的管理员角色快照，仅用于筛选工具"
    )
    session_id: Optional[str] = Field(None, max_length=64, description="Java会话ID")
    query: str = Field(..., description="用户输入的问题或指令")
    model: Optional[str] = Field(None, max_length=64, description="模型名称，为空时使用服务端默认值")
    context: Optional[Dict[str, Any]] = Field(
        default=None, description="上下文信息，可携带历史对话、业务参数等"
    )
    knowledge: Optional[Dict[str, Any]] = Field(default=None, description="Java鉴权后的知识库检索范围")
    temperature: float = Field(
        default=0.2, ge=0.0, le=2.0, description="LLM 采样温度，值越高回答越发散"
    )
    stream: bool = Field(default=False, description="是否以流式方式输出")


class AgentSubmitRequest(AgentRequest):
    """异步提交专用模型：任务身份必须由调用方生成并在重试时复用。"""
    task_id: str = Field(..., min_length=1, max_length=64, description="由Java生成的任务ID")
    user_id: int = Field(..., gt=0, description="当前用户ID")


class AgentResponse(BaseModel):
    """
    Agent 同步响应模型
    用于 /api/v1/agent/query 接口的返回体
    """
    status: str = Field(..., description="执行状态：success 成功 / processing 处理中 / error 失败")
    result: Optional[str] = Field(default=None, description="Agent 返回的文本结果")
    task_id: Optional[str] = Field(default=None, description="异步任务ID（异步模式下返回）")
    error_msg: Optional[str] = Field(default=None, description="错误信息（失败时返回）")
    usage: Optional[Dict[str, int]] = Field(default=None, description="Token 消耗统计")
    created_at: datetime = Field(default_factory=datetime.now, description="响应创建时间")


class SubmitResponse(BaseModel):
    """
    异步任务提交响应模型（旧版，仅返回 task_id）
    """
    task_id: str = Field(description="异步任务唯一标识")
    status: str = Field(default="processing", description="初始状态")


class SubmitStreamResponse(BaseModel):
    """
    Submit + SSE 模式的提交响应模型
    客户端拿到 task_id 和 stream_url 后，立即连接 stream_url 订阅事件
    """
    task_id: str = Field(description="异步任务唯一标识")
    status: str = Field(description="当前任务状态")
    stream_url: str = Field(description="SSE 订阅地址，客户端 GET 此 URL 即可接收事件流")
    result: Optional[str] = Field(default=None, description="已完成任务的结果")
    error_msg: Optional[str] = Field(default=None, description="失败任务的错误信息")


class TaskStatusResponse(BaseModel):
    """
    任务状态查询响应模型
    用于 /api/v1/agent/status/{task_id} 接口
    """
    task_id: str = Field(description="任务唯一标识")
    status: str = Field(description="任务状态：pending / running / completed / failed / cancelled")
    result: Optional[str] = Field(default=None, description="任务完成后的结果（仅 completed 状态有值）")
    error_msg: Optional[str] = Field(default=None, description="失败原因（仅 failed 状态有值）")
    created_at: datetime = Field(description="任务创建时间")
    updated_at: Optional[datetime] = Field(default=None, description="任务最近一次状态变更时间")


class HealthResponse(BaseModel):
    """
    健康检查响应模型
    用于 /health 接口，供注册中心或负载均衡器探活
    """
    status: str = Field(default="healthy", description="健康状态")
    version: str = Field(description="服务版本号")
    uptime_seconds: float = Field(description="服务运行时长（秒）")


class KnowledgeIndexRequest(BaseModel):
    task_id: str
    kb_id: str
    document_id: str
    document_version: int = Field(gt=0)
    file_name: str
    file_type: str
    embedding_model: str
    request_hash: str


class KnowledgeSearchRequest(BaseModel):
    kb_id: str
    query: str
    document_versions: Dict[str, int] = Field(default_factory=dict)
    top_k: int = Field(default=8, ge=1, le=12)
    score_threshold: float = Field(default=0.2, ge=-1, le=1)
