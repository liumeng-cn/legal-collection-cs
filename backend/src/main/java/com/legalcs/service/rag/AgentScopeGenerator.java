package com.legalcs.service.rag;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.harness.agent.HarnessAgent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@RequiredArgsConstructor
public class AgentScopeGenerator implements Generator {

    private final HarnessAgent legalCollectionAgent;

    @Override
    public Flux<String> generate(List<Msg> messages, RuntimeContext runtimeContext) {
        return legalCollectionAgent.streamEvents(messages, runtimeContext)
                .ofType(TextBlockDeltaEvent.class)
                .map(TextBlockDeltaEvent::getDelta);
    }
}
