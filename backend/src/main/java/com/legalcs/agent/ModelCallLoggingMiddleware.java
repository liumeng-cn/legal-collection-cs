package com.legalcs.agent;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.model.ToolSchema;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Slf4j
@Component
public class ModelCallLoggingMiddleware implements MiddlewareBase {

    private static final int MAX_MESSAGE_TEXT_LENGTH = 300;

    @Override
    public Flux<AgentEvent> onModelCall(
            Agent agent, RuntimeContext ctx, ModelCallInput input,
            Function<ModelCallInput, Flux<AgentEvent>> next) {
        long start = System.nanoTime();
        log.info("LLM 请求 agent={} session={} model={} messages=[{}] tools=[{}]",
                agent.getName(), ctx.getSessionId(), input.model().getModelName(),
                formatMessages(input.messages()), formatTools(input.tools()));
        return next.apply(input)
                .doFinally(sig -> log.info("LLM 响应结束 agent={} 耗时={}ms",
                        agent.getName(), (System.nanoTime() - start) / 1_000_000));
    }

    private static String formatMessages(List<Msg> messages) {
        if (messages == null) {
            return "";
        }
        return messages.stream()
                .map(ModelCallLoggingMiddleware::formatMessage)
                .collect(Collectors.joining(" | "));
    }

    private static String formatMessage(Msg message) {
        String text = message.getTextContent();
        String truncated = text.length() > MAX_MESSAGE_TEXT_LENGTH
                ? text.substring(0, MAX_MESSAGE_TEXT_LENGTH) + "..."
                : text;
        return message.getRole().name() + ":" + truncated;
    }

    private static String formatTools(List<ToolSchema> tools) {
        if (tools == null) {
            return "";
        }
        return tools.stream().map(ToolSchema::getName).collect(Collectors.joining(", "));
    }
}
