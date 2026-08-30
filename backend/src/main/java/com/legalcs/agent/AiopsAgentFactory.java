package com.legalcs.agent;

import io.agentscope.core.model.Model;
import io.agentscope.core.tracing.OtelTracingMiddleware;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import io.agentscope.harness.agent.subagent.WorkspaceMode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class AiopsAgentFactory {

    private static final String AGENT_NAME = "aiops-diagnosis";
    private static final String WORKSPACE_DIR = "data/aiops-workspace";
    private static final int COMPACTION_TRIGGER_MESSAGES = 30;
    private static final int COMPACTION_KEEP_MESSAGES = 10;
    private static final int MAIN_TRIGGER_TOKENS = 60_000;
    private static final int SUBAGENT_MAX_ITERS = 8;
    private static final int MAIN_AGENT_MAX_ITERS = 15;
    private static final int MAX_RETRIES = 2;

    private static final String SYSTEM_PROMPT = """
            # 角色
            你是「法催平台」的智能排障主控 Agent，服务产研运维（SRE）同学，负责对线上问题做根因分析。
            
            # 任务
            针对用户上报的问题，按「了解现象 → 形成假设 → 取证 → 交叉验证 → 结论」的闭环定位根因，并给出可执行的修复建议。
            
            # 工具
            - search_logs / trace_timeline：检索应用日志、按 trace_id 还原一条请求的完整时间线。
            - query_database：对业务库（auth/business/knowledge/chat）做只读查询，表名受白名单约束。
            - search_knowledge：检索业务知识库，比对标准流程与预期行为。
            - verify_trace_coverage：确定性校验 trace 是否贯穿整条请求日志（入口/错误/连续性）。
            - verify_time_alignment：确定性校验两个时间戳是否在合理窗口内对齐。
            
            # 并行取证（多 agent）
            当问题跨越多个维度（日志 + 数据 + 流程）时，用 agent_spawn 并行派出子 agent 各领一个维度取证，汇总后交叉验证：
            - log-forensics：日志取证（search_logs / trace_timeline）。
            - data-forensics：数据取证（query_database）。
            - process-verifier：流程比对（search_knowledge）。
            单维度问题直接自理，不必 spawn。
            
            # 子 agent 容错
            - 子 agent 取证带超时；超时或失败时降级：跳过该维度，基于剩余证据继续，不阻塞整体排查。
            - 允许对失败维度重试一次（收窄范围或换说法）；仍失败则放弃，并明确告知用户该维度未完成及原因。
            - 禁止把未完成/失败的维度当作正常结果，禁止编造子 agent 未返回的证据。
            
            # 上下文
            - 所有结论必须基于工具返回的真实数据，禁止编造日志、数据或报错。
            - 工具返回「无权访问」时立即停止取证，告知用户无权限，不得泄露任何数据。
            - 工具返回「无命中/查询为空」时如实说明——这本身也是一条证据，证明该方向无异常。
            - search_knowledge 的结果已按当前用户权限过滤，只包含有权查看的片段；不足以完整比对标准流程时说明信息不完整，禁止编造流程或规则。
            - 片段不足以比对完整流程时，可用 expand_knowledge 补全上下文（window=-1 整篇 / >=0 相邻）。
            
            # 排查思路（软先验 → 取证 → 交叉验证 → 回溯）
            1. 先分析现象，定性问题类型：答疑 / 操作错误（不符合业务流程）/ 系统 BUG（报错、异常、降级）/ 环境问题（网络、依赖、中间件）。
            2. 形成「软先验」：列出最可能的根因方向，按可能性排序（1-3 条）。
            3. 取证：用最少的工具调用验证假设——先查日志定位报错与 trace_id，再查数据核对状态，用知识库比对标准流程。
            4. 交叉验证：将错误日志与问题现象对齐、将异常数据与用户操作对齐，多维度互相印证。
            5. 回溯：若验证推翻假设，回到第 2 步更新假设重新排查，不要一条路走到黑。
            
            # 交叉验证（下结论前必做）
            1. 先跑确定性规则（硬校验）：verify_trace_coverage 验证 trace 是否贯穿整条请求；verify_time_alignment 验证报错时间与数据变更时间是否在窗口内对齐。矛盾直接标记，不回旋。
            2. 再做 LLM 模糊判断（软校验）：判断「报错与业务失败是否同一根因」这类语义对齐，必须引用具体证据。
            3. 交叉验证失败 → 回到假设阶段换方向（回溯），不一条路走到黑。
            
            # 反问
            - 缺少关键信息（案件号、trace_id、报错时间、操作路径、环境）时，先向用户定向反问，一次问清，再继续取证。
            - 能自己从工具查到的信息不反问用户。
            
            # 输出格式
            用简体中文，按以下三段流式输出：
            1. 【排查思路】问题定性 + 软先验假设（按可能性排序）。
            2. 【取证过程】逐步说明调用了哪个工具、查到什么证据、如何与假设印证。
            3. 【根因结论】定位到的根因 + 修复建议；若尚未定位，给出下一步最值得验证的方向。
            每条关键证据标注来源（工具名/子 agent 名 + 原始片段：trace_id、时间戳、字段值）；未完成的取证维度显式说明原因。
            
            # 样例
            用户：「案件 A-20260801-001 状态显示逾期，但系统没触发催收任务。」
            排查思路：定性为「系统 BUG 或数据问题」，先验假设 1）案件状态更新成功但催收任务生成失败；2）状态字段与任务触发的判断条件不一致。
            取证过程：search_logs 查该案件相关 ERROR 日志与 trace_id → query_database 核对 case_info 状态与 repayment_record → search_knowledge 比对标准催收触发流程。
            根因结论：定位到 XX 服务在状态流转时抛异常，导致任务生成中断；建议修复 XX 并重跑任务。
            """;

    private static final String LOG_FORENSICS_PROMPT = """
            你是「法催平台」排障子 agent，专职日志取证。
            用 search_logs / trace_timeline 检索应用日志、按 trace_id 还原调用时间线。
            输出：命中的错误日志、关键堆栈、相关 trace_id 与完整时间线。只报告日志证据，不下结论。
            """;

    private static final String DATA_FORENSICS_PROMPT = """
            你是「法催平台」排障子 agent，专职数据取证。
            用 query_database 对业务库做只读查询，核对案件状态、欠款、还款记录与用户操作是否一致。
            输出：异常数据、状态不一致点、关键字段值。只报告数据证据，不下结论。
            """;

    private static final String PROCESS_VERIFIER_PROMPT = """
            你是「法催平台」排障子 agent，专职业务流程比对。
            用 search_knowledge 检索业务知识库，片段不足时用 expand_knowledge 补全上下文，比对标准业务流程与实际行为是否一致。
            输出：流程差异点、违反的业务规则、标准流程片段。只报告流程证据，不下结论。
            """;

    private final Model model;
    private final AiopsToolkitBuilder toolkitBuilder;
    private final ModelCallLoggingMiddleware modelCallLoggingMiddleware;
    private final OtelTracingMiddleware otelTracingMiddleware;

    @Bean
    public HarnessAgent aiopsAgent() {
        return HarnessAgent.builder()
                .name(AGENT_NAME)
                .sysPrompt(SYSTEM_PROMPT)
                .model(model)
                .workspace(Path.of(WORKSPACE_DIR))
                .toolkit(toolkitBuilder.build())
                .middleware(modelCallLoggingMiddleware)
                .middleware(otelTracingMiddleware)
                .maxIters(MAIN_AGENT_MAX_ITERS)
                .maxRetries(MAX_RETRIES)
                .compaction(CompactionConfig.builder()
                        .triggerMessages(COMPACTION_TRIGGER_MESSAGES)
                        .keepMessages(COMPACTION_KEEP_MESSAGES)
                        .triggerTokens(MAIN_TRIGGER_TOKENS)
                        .build())
                .disableSessionPersistence()
                .disableMemoryHooks()
                .disableMemoryTools()
                .subagent(logForensics())
                .subagent(dataForensics())
                .subagent(processVerifier())
                .build();
    }

    private SubagentDeclaration logForensics() {
        return SubagentDeclaration.builder()
                .name("log-forensics")
                .description("日志取证专家：检索应用日志、还原调用时间线")
                .inlineAgentsBody(LOG_FORENSICS_PROMPT)
                .workspaceMode(WorkspaceMode.ISOLATED)
                .tools(List.of("search_logs", "trace_timeline"))
                .steps(SUBAGENT_MAX_ITERS)
                .build();
    }

    private SubagentDeclaration dataForensics() {
        return SubagentDeclaration.builder()
                .name("data-forensics")
                .description("数据取证专家：只读查询业务库核对数据一致性")
                .inlineAgentsBody(DATA_FORENSICS_PROMPT)
                .workspaceMode(WorkspaceMode.ISOLATED)
                .tools(List.of("query_database"))
                .steps(SUBAGENT_MAX_ITERS)
                .build();
    }

    private SubagentDeclaration processVerifier() {
        return SubagentDeclaration.builder()
                .name("process-verifier")
                .description("流程比对专家：检索业务知识库比对标准流程")
                .inlineAgentsBody(PROCESS_VERIFIER_PROMPT)
                .workspaceMode(WorkspaceMode.ISOLATED)
                .tools(List.of("search_knowledge", "expand_knowledge"))
                .steps(SUBAGENT_MAX_ITERS)
                .build();
    }
}
