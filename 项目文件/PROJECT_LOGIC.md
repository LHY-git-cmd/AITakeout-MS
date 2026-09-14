# AI 代理服务项目实现逻辑

这是一个基于 **RAG (Retrieval-Augmented Generation, 检索增强生成)** 架构的 AI 代理服务。

整个项目的逻辑可以分为两条并行的生命线：
1.  **知识处理与索引**：将文档资料转化为可供 AI 检索的知识库。
2.  **用户查询与生成**：当用户提问时，利用知识库生成精准回答。

---

### 核心架构图

```
+---------------------------------------------------------------------------------+
|                                  用户 / Client                                  |
+---------------------------------------------------------------------------------+
     | (1. 上传文档)                                  | (5. 提出问题)
     |                                              |
+----v---------------------------------------------+----v--------------------------+
|                            FastAPI 应用 (app/main.py)                             |
|                                                                                 |
|  +---------------------------+                    +---------------------------+ |
|  |  知识处理与索引 (后台)    |                    |  用户查询与生成 (前台)    | |
|  |---------------------------|                    |---------------------------| |
|  | (2) 解析 & 分块          |                    | (6) 向量化问题            | |
|  |     [DocumentParser]      |                    |     [EmbeddingProvider]   | |
|  |     [StructureChunker]    |                    |                           | |
|  |           |               |                    |           |               | |
|  |           v               |                    |           v               | |
|  | (3) 向量化 (bge-m3)       |                    | (7) 检索相关知识          | |
|  |     [EmbeddingProvider]   |                    |     [VectorStore]         | |
|  |           |               |                    |           |               | |
|  |           v               |                    |           v               | |
|  | (4) 存入向量数据库        |                    | (8) 增强 & 生成 (LLM)     | |
|  |     [VectorStore]         |                    |     [PythonAgent]         | |
|  |     (e.g., Qdrant)        |                    |     (e.g., deepseek)      | |
|  +---------------------------+                    +---------------------------+ |
|                                                                                 |
+--------------------------------------------------^------------------------------+
                                                   | (9. 返回答案)
                                                   |
```

---

### 1. 知识处理与索引 (后台任务)

此流程的目标是**构建知识库**，通常由异步后台任务完成。

-   **第 1 步：接收文档**
    -   用户通过 API 上传文件（如 PDF, TXT 等）。
    -   `KnowledgeService` (`app/knowledge/service.py`) 接收请求并创建索引任务。

-   **第 2 步：解析与分块 (Chunking)**
    -   **解析 (`DocumentParser`)**: 根据文件类型抽取纯文本。
    -   **分块 (`StructureChunker`)**: 在 `app/knowledge/chunker.py` 中，将长文本切分成有重叠的、语义集中的小块（chunks），以提高检索精度和优化 LLM 上下文。

-   **第 3 步：向量化 (Embedding)**
    -   `HttpEmbeddingProvider` (`app/knowledge/embedding.py`) 将每个文本块通过 `bge-m3` 模型转换为向量（即文本的“数学指纹”）。

-   **第 4 步：存储 (Storing)**
    -   `KnowledgeService` 将文本块内容、元数据及其向量存储到向量数据库中（如 Qdrant 或用于测试的内存存储）。

---

### 2. 用户查询与生成 (前台实时交互)

此流程处理用户的实时问答请求。

-   **第 5 步：接收问题**
    -   用户通过 API 发送查询请求。

-   **第 6 步：任务分发与查询向量化**
    -   请求被分发给 `TaskQueue` (`app/core/task_queue.py`) 进行异步处理。
    -   `PythonAgent` (`app/core/agent.py`) 接管任务，并使用**同一个 `bge-m3` 模型**将用户的**问题**也转换为向量。

-   **第 7 步：检索相关知识 (Retrieval)**
    -   `PythonAgent` 调用 `KnowledgeService` 的搜索功能。
    -   在向量数据库中，通过计算问题向量与知识库文本块向量的相似度，找出最相关的“背景知识”。

-   **第 8 步：增强与生成 (Augmented Generation)**
    -   **RAG 架构的核心**。`PythonAgent` 的 `prepare_rag` 方法将**用户的原始问题**、**检索到的相关知识**和**系统预设指令**组合成一个内容丰富的**增强提示（Augmented Prompt）**。
    -   这个增强后的 Prompt 被发送给大语言模型（LLM），如 `deepseek`。

-   **第 9 步：返回答案**
    -   LLM 基于增强的上下文生成精准回答。
    -   `PythonAgent` 通过 Server-Sent Events (SSE) 以**流式（Streaming）**方式将答案实时推送给用户。

---

### 核心组件总结

-   **`app/main.py`**: 项目入口，负责初始化所有服务和定义 API 路由。
-   **`app/core/config.py`**: 集中管理所有配置，如模型名称、服务地址等。
-   **`app/core/agent.py` (`PythonAgent`)**: **项目的大脑**，编排整个 RAG 流程。
-   **`app/core/task_queue.py` (`TaskQueue`)**: 异步任务管理器，保证服务稳定性。
-   **`app/knowledge/service.py` (`KnowledgeService`)**: 知识库管家，封装所有知识库操作。
-   **`app/knowledge/chunker.py`**: 文档切割工具。
-   **`app/knowledge/embedding.py`**: 语义向量转换工具。
-   **`app/knowledge/vector_store.py`**: 向量数据库接口，负责存储和高效检索。