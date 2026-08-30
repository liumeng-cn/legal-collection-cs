## 上下文

- **当前状态**：智能客服门户 `legalCollectionAgent` 使用裸 `ReActAgent`，`ChatService.chat()` 手动 `loadHistory()` 全量加载会话历史塞进 LLM，无长期记忆、无上下文压缩；排障助手 `aiopsAgent` 已是 `HarnessAgent`，配了 `compaction` + 3 个 inline subagent，但未启用记忆。
- **约束**：四库物理隔离（`auth`/`business`/`knowledge`/`chat`），WebFlux 响应式链路（禁止 ThreadLocal），Lombok `@RequiredArgsConstructor` 注入，Java 21。环境未上线，均为测试数据，表结构可重构。
- **技术栈**：Spring Boot 3.3.7 + AgentScope 2.0.0（`ReActAgent` / `HarnessAgent`），PostgreSQL + pgvector，DashScope embedding（qwen3-vl-embedding，1024 维）。

## 目标 / 非目标

**目标：**
- 客服与排障助手跨会话记住用户（画像、偏好、承诺）。
- 长对话上下文有界，不无限增长。
- 记忆按 `user_id` 隔离，贴合四库 DB 架构。
- 复用 AgentScope 的 compaction / 多用户隔离 / 状态持久化，不重复造轮子。

**非目标：**
- 不改造 `knowledge` 库（静态 FAQ 知识检索，与用户级记忆正交）。
- 不做海量债务人的全局语义检索（记忆仅按当前用户隔离后检索）。
- 不做 AgentScope 文件式记忆（`MEMORY.md`）的 DB 化适配——直接弃用其文件记忆。

## 决策

### 决策 1：门户统一迁移到 HarnessAgent
智能客服门户从 `ReActAgent` 迁移到 `HarnessAgent`；排障助手保持 `HarnessAgent`。

- **理由**：`HarnessAgent` 原生提供 compaction、`(userId, sessionId)` 多用户隔离、状态持久化、子 agent 编排——正是门户 agent 所需的工程能力。
- **备选**：保持 `ReActAgent` 并自建压缩/隔离/持久化 → 拒绝，重复造轮子且易漏。

### 决策 2：记忆走自建 DB 层（disableMemoryHooks）
关闭 AgentScope 内置文件记忆（`disableMemoryHooks()`），自建 DB 记忆层。

- **理由**：AgentScope 内置记忆是文件语义（`MEMORY.md` 整体重写、`memory_search` 关键词扫文件），且检索是关键词非语义，扛不住"海量债务人 × 累积情节"；自建一张 `user_memory` 表 + pgvector 语义检索更贴合 DB 架构。
- **备选 A**：自定义 `.filesystem()`/`.stateStore()` 后端接 PostgreSQL → 拒绝，接口文件语义强，接 DB 别扭，且检索能力仍是关键词。

### 决策 3：记忆表放 chat 库
`user_memory` 表建在 chat 库，与 `message` 同源。

- **理由**：记忆是用户级、跨会话、与对话强相关，按 `user_id` 组织，与 `conversation`/`message` 天然一致；避免新增第五个库。
- **备选**：新建独立 `memory` 库 → 拒绝，增加运维复杂度，收益低。

### 决策 4：三层读写时机
写入 = 会话结束异步 LLM 摘要；加载 = 新会话开始注入；衰减 = 后台异步批处理。

- **理由**：与"会话边界"对齐——会话内记忆稳定，无需每轮重查；写入异步不阻塞响应（fire-and-forget）。
- **备选**：每次 call 结束 flush（AgentScope 默认）→ 拒绝，成本高且对多轮客服无必要。

### 决策 5：画像全量注入 + 情节语义检索
加载时画像（`SEMANTIC`）全量注入，情节（`EPISODIC`）按当前 query 语义检索 top-k 注入。

- **理由**：画像量小、长期稳定，全量注入成本低；情节会累积，必须语义检索取相关子集。
- **备选**：全部全量注入 → 拒绝，情节累积会撑爆上下文；全部语义检索 → 画像量小无需检索。

### 决策 6：记忆更新用 SUPERSEDED 标记
新记忆冲突时，旧记忆标记 `SUPERSEDED`，不物理删除。

- **理由**：保留历史可追溯（审计），同时避免检索返回矛盾的两条。
- **备选**：物理删除旧记忆 → 拒绝，丢失审计信息。

## 风险 / 权衡

- **[HarnessAgent 流式事件模型与现有 `ChatService` 的 `TextBlockDeltaEvent` 不一致]** → 迁移时先验证 `streamEvents` 输出形态，必要时适配事件类型。
- **[摘要 LLM 调用增加成本]** → 会话结束异步触发，可用独立轻量模型（`MemoryConfig`/自建层支持 `.model(...)` 覆盖）。
- **[SUPERSEDED/EXPIRED 记录导致表膨胀]** → 后台衰减批处理定期归档或物理清理过期记录。
- **[自建记忆层无框架默认 prompt]** → 参考 AgentScope 两层思想（追加流水账 + 周期合并），自定义摘要/合并 prompt。
- **[情节检索 recency vs relevance 权衡]** → 承诺类偏 recency，偏好类偏 relevance，排序加权参数需调优。

## 迁移计划

1. **建表 + 数据层**：chat 库建 `user_memory` 表（含 `embedding vector(1024)`），新增 `MemoryDAO` / `MemoryEntity`。
2. **记忆服务**：`MemoryService` 提供 `load`（画像全量 + 情节 top-k）、`save`（异步摘要 upsert）、`expire`（后台衰减）三个能力。
3. **门户迁移**：`AgentFactory` 改为 `HarnessAgent`，配 `.compaction()` + `.disableMemoryHooks()`。
4. **接入读写**：`ChatService` 移除手动 `loadHistory`，会话开始调 `MemoryService.load` 注入，会话结束异步 `save`。
5. **排障助手**：`AiopsAgentFactory` 加 `.disableMemoryHooks()`，复用同一套 `MemoryService`。

回滚：环境未上线、测试数据，可清库重建表结构；代码改动按提交粒度可 revert。

## 待定问题

- 摘要/合并 LLM 是否用独立轻量模型（成本 vs 质量）。
- 情节检索 top-k 默认值（建议 3~5）。
- `user_memory` 表 EXPIRED/SUPERSEDED 记录的保留与归档周期。
