package com.legalcs.service.rag;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import java.util.List;
import reactor.core.publisher.Flux;

public interface Generator {

    Flux<String> generate(List<Msg> messages, RuntimeContext runtimeContext);
}
