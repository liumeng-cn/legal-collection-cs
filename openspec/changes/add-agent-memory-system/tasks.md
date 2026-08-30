## 1. 数据层：user_memory 表与持久化

- [x] 1.1 在 chat 库新增 `user_memory` 表迁移脚本（列：`id`、`user_id`、`memory_type`（SEMANTIC/EPISODIC）、`content`、`embedding vector(1024)`、`importance`、`status`（ACTIVE/SUPERSEDED/EXPIRED）、`created_at`、`updated_at`），并建 `user_id` 索引与 `embedding` 的 ivfflat/hnsw 向量索引
- [x] 1.2 新增 `MemoryEntity` 实体（顶层类，Lombok `@Data`/`@Builder`，枚举 `MemoryType`、`MemoryStatus` 独立定义，不硬编码字符串）
- [x] 1.3 新增 `MemoryDAO`，提供按 `user_id + memory_type + status=ACTIVE` 查询、按 `user_id` 语义检索（`embedding <=> ?` 余弦距离 top-k）、upsert 写入、批量标记 `SUPERSEDED`/`EXPIRED` 的方法
- [x] 1.4 复用 `DashScopeClient` 生成情节记忆的 1024 维 embedding（方法：`embed(String) -> float[]/Vector`）

## 2. 记忆服务：加载 / 写入 / 衰减

- [x] 2.1 新增 `MemoryService`（`@RequiredArgsConstructor` 注入 `MemoryDAO` + embedding 客户端），提供 `load(userId, query)`：画像全量 + 情节按 query 语义检索 top-k，返回注入 system prompt 的文本块
- [x] 2.2 新增 `MemoryService.save(userId, conversationId)`：会话结束异步调用 LLM 摘要，抽取画像/情节，upsert 写库；写入前对新情节与既有 ACTIVE 情节比对，冲突的旧记录标记 `SUPERSEDED`
- [x] 2.3 新增 `MemoryService.expire()`：后台异步批处理，将已兑现/长期未再提及的记忆标记 `EXPIRED`（支持独立轻量模型与调度开关，见设计决策 6）
- [x] 2.4 记忆摘要/合并的 LLM prompt 独立封装（画像抽取 prompt、情节抽取 prompt），参考 AgentScope 两层思想（追加流水账 + 周期合并）

## 3. 门户迁移：AgentFactory → HarnessAgent

- [x] 3.1 `AgentFactory` 将智能客服门户从 `ReActAgent` 迁移到 `HarnessAgent`，配 `.compaction()` 与 `.disableMemoryHooks()`，保留既有 3 个 middleware（含 OTel tracing）
- [x] 3.2 验证 `HarnessAgent` 的流式事件模型与 `ChatService` 现有 `TextBlockDeltaEvent` 消费一致，必要时适配事件类型（设计风险 1）

## 4. 接入读写：ChatService 改造

- [x] 4.1 `ChatService.chat()` 移除手动 `loadHistory()` 全量加载，改为依赖 HarnessAgent 框架状态持久化加载会话历史
- [x] 4.2 新会话首条消息前调 `MemoryService.load(userId, query)`，将记忆文本注入 system prompt（复用 `RoleSystemPromptMiddleware` 或等价注入点）
- [x] 4.3 会话最后一条回复流式完成后，异步触发 `MemoryService.save(userId, conversationId)`（fire-and-forget，不阻塞响应）

## 5. 排障助手接入

- [x] 5.1 `AiopsAgentFactory` 加 `.disableMemoryHooks()`，复用同一套 `MemoryService` 接入记忆加载/写入（对齐客服门户行为）
- [ ] 5.2 全链路联调：客服与排障两门户在新会话加载记忆、会话结束写记忆、跨会话检索不串用户（A 用户检索不得返回 B 用户记忆）
