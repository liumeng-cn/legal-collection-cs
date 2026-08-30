## 为什么

法催平台的两个门户 Agent 目前都存在"失忆"问题：

- **智能客服门户**使用裸 `ReActAgent`，无长期记忆、无上下文压缩，每轮 `loadHistory()` 全量加载历史塞进 LLM，长会话 token 膨胀；跨会话无法记住债务人/催收员的画像、偏好、承诺，每次对话都像"第一次认识"。
- **排障助手门户**虽已是 `HarnessAgent`，但未启用记忆，同样无法沉淀长期事实。

业务上，债务人会多轮、多天咨询同一案件（承诺还款、追问进度），催收员会长期使用本服务处理大量案件——系统需要跨会话记住用户，并保证长对话不溢出上下文。

## 变更内容

- **门户 Agent 统一迁移到 HarnessAgent**：智能客服从 `ReActAgent` 迁移到 `HarnessAgent`（**BREAKING**，会话历史加载机制由手动全量 `loadHistory` 改为框架状态持久化 + 上下文压缩）；排障助手保持 `HarnessAgent` 并补齐记忆。
- **新增 DB 长期记忆层**：chat 库新增 `user_memory` 表（含 `pgvector` 向量列），记忆按 `user_id` 隔离，不用 workspace 文件。
- **记忆写入**：会话结束后异步 LLM 摘要，抽取画像/情节，upsert 到 `user_memory`（新承诺覆盖旧承诺）。
- **记忆加载**：新会话开始，画像全量 + 情节按 query 语义检索 top-k，注入 system prompt。
- **记忆衰减**：后台异步批处理，将过时/已兑现的记忆标记为 `EXPIRED`。
- **关闭 AgentScope 内置文件记忆**：`disableMemoryHooks()`，自建 DB 记忆层（**BREAKING**，不再使用 `MEMORY.md` 文件）。

## 功能 (Capabilities)

### 新增功能

- `agent-memory`: 跨会话长期记忆系统——按用户隔离地存储、加载、更新、衰减用户画像（偏好/身份）与情节记忆（承诺/事件）。
- `agent-context`: 会话上下文的有界管理——跨轮加载会话历史，长对话触发压缩，上下文溢出时兜底重试。

### 修改功能

<!-- 本次为全新能力，无既有规范变更 -->

## 影响

- **代码**：`AgentFactory`（ReActAgent→HarnessAgent）、`ChatService`（移除手动历史加载，接入记忆加载/写入）、`AiopsAgentFactory`（补齐记忆）、新增 `MemoryService` / `MemoryDAO` / `MemoryEntity`。
- **数据库**：chat 库新增 `user_memory` 表（`user_id`、`memory_type`、`content`、`embedding vector(1024)`、`importance`、`status`、时间戳）。
- **依赖**：无新增外部依赖；`pgvector`、AgentScope `HarnessAgent`（compaction/stateStore）已在依赖中。
- **API**：无对外接口变化，均为内部实现调整。
