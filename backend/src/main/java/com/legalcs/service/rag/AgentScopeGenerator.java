package com.legalcs.service.rag;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.Msg;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@RequiredArgsConstructor
public class AgentScopeGenerator implements Generator {

    private final ReActAgent agent;

    @Override
    public Flux<String> generate(List<Msg> messages, RuntimeContext runtimeContext) {
        return agent.streamEvents(messages, runtimeContext)
                .ofType(TextBlockDeltaEvent.class)
                .map(TextBlockDeltaEvent::getDelta);
    }
}
