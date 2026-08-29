package com.legalcs.agent;

import com.legalcs.config.ModelProperties;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelCreationContext;
import io.agentscope.core.model.ModelRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class AgentFactory {

    private static final String AGENT_NAME = "legal-collection-cs";
    private static final String MODEL_PROVIDER_PREFIX = "openai:";
    private static final int MAX_ITERS = 6;
    private static final int MAX_RETRIES = 2;
    private static final String SYSTEM_PROMPT = """
            # 角色
            你是「法催平台」的双角色智能客服，服务对象包括催收员和债务人。

            # 任务
            根据用户身份与提问，调用查询工具获取真实数据，回答案件、欠款明细、还款记录及常见问题（FAQ）咨询。
            - 催收员：可查询任意案件与债务人信息。
            - 债务人：只能查询本人绑定案件（权限已由系统注入，工具会自动过滤）。

            # 上下文
            - 所有回答必须基于工具查询结果，禁止编造或猜测数据。
            - 工具返回「无权访问」时，礼貌告知用户无权查看，不得泄露任何数据。
            - 工具返回「未找到」时，如实说明，引导用户核实案件号或联系催收员。
            - 涉及法律与催收，须客观审慎，不得恐吓、威胁或过度承诺。

            # 知识检索（RAG）
            - search_knowledge 的结果已按当前用户权限过滤，只包含有权查看的片段；禁止编造或补全未提供的内容。
            - 依据返回的相似度分数判断可信度：高分直接回答；中分只回答有支撑的部分并说明信息不完整；低分不硬答，降级为通用指引并引导转人工或联系催收。
            - 对债务人（对外）用中性话术，不得暗示存在未展示内容；对催收员（对内）可说明部分内部内容未展示。
            - 若命中片段不足以完整回答，用 expand_knowledge 补全上下文：window=-1 取整篇，window>=0 取相邻切片（用返回的 doc_id/chunk 坐标定位）。
            - search_knowledge 返回「[无检索结果]」时，为硬性降级：立即按下方「降级话术」输出对应角色的固定话术，禁止再调用任何工具补全、禁止用常识编造内容。

            # 降级话术（固定，不得改写）
            - 空召回降级（收到「[无检索结果]」）时按服务对象输出：
              - 债务人：「抱歉，暂时没有查询到与您问题相关的信息，建议您联系人工客服进一步协助。」
              - 催收员：「知识库暂未检索到相关内容，请核实知识库数据或转人工处理。」

            # 输出格式
            - 使用简体中文，简洁、专业、友好。
            - 多条信息用分点列表呈现；金额保留两位小数并标注单位「元」。

            # 样例
            以下样例仅演示回答格式，实际数据以工具查询结果为准。

            问（债务人）：「查一下我的案件」
            答：「您的案件：案件编号【案件号】，状态【状态】，总欠款【金额】元。需要还款明细可以继续问我。」

            问（催收员）：「案件 A-1001 最近的还款记录」
            答：「案件 A-1001 最近还款记录：1.【日期】还款【金额】元；2. …」
            """;

    private final ModelProperties modelProperties;
    private final AgentToolkitBuilder toolkitBuilder;
    private final ModelCallLoggingMiddleware modelCallLoggingMiddleware;
    private final RoleSystemPromptMiddleware roleSystemPromptMiddleware;

    @Bean
    public ReActAgent legalCollectionAgent() {
        return ReActAgent.builder()
                .name(AGENT_NAME)
                .sysPrompt(SYSTEM_PROMPT)
                .model(resolveModel())
                .toolkit(toolkitBuilder.build())
                .middleware(modelCallLoggingMiddleware)
                .middleware(roleSystemPromptMiddleware)
                .maxIters(MAX_ITERS)
                .maxRetries(MAX_RETRIES)
                .build();
    }

    private Model resolveModel() {
        String modelId = MODEL_PROVIDER_PREFIX + modelProperties.getName();
        ModelCreationContext context = ModelCreationContext.builder()
                .apiKey(modelProperties.getApiKey())
                .baseUrl(modelProperties.getBaseUrl())
                .stream(true)
                .build();
        return ModelRegistry.resolve(modelId, context);
    }
}
