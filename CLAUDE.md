# 法催平台智能客服（legal-collection-cs）

双角色（催员 / 债务人）AI 智能客服。后端 Spring Boot 3.3.x + Java 21 + AgentScope Java，前端 Vue3 + Element Plus，存储 PostgreSQL（pgvector）。

## 编码规范（强制）

1. **不要内部类 / 匿名内部类**：DTO、请求响应、事件对象一律独立顶层类；匿名类用 lambda / 方法引用替代；避免非静态成员内部类。
2. **不要魔法值**：业务常量用枚举（如 `Role`、`CaseStatus`、`MessageRole`）；其余常量抽成 `public static final`，禁止硬编码字符串/数字散落代码。
3. **不手写构造器注入**：依赖注入用 Lombok `@RequiredArgsConstructor` + `final` 字段；不用字段注入（`@Resource`/`@Autowired`），不手写 setter 注入。

## 关键约定

- 权限上下文（`userId + role`）通过 `RuntimeContext` 类型化属性注入 `AuthContext`（`RuntimeContext.builder().put(AuthContext.class, authContext)`）穿透到 Agent 工具层，工具保持单例，避免在 WebFlux 响应式链路里使用 ThreadLocal。
- 四库物理隔离：`auth`（身份）/ `business`（案件欠款）/ `knowledge`（FAQ 向量）/ `chat`（会话消息），各自独立 DataSource。
- LLM：DeepSeek `deepseek-v4-pro`，OpenAI 兼容，baseUrl `https://api.deepseek.com`；Embedding：阿里云 DashScope 通义 text-embedding。
