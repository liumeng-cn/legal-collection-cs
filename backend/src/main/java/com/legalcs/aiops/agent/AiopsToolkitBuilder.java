package com.legalcs.aiops.agent;

import com.legalcs.agent.RagRetrievalTool;
import com.legalcs.aiops.tool.CrossValidationTool;
import com.legalcs.aiops.tool.DbQueryTool;
import com.legalcs.aiops.tool.LogRetrievalTool;
import io.agentscope.core.tool.Toolkit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiopsToolkitBuilder {

    private final LogRetrievalTool logRetrievalTool;
    private final DbQueryTool dbQueryTool;
    private final RagRetrievalTool ragRetrievalTool;
    private final CrossValidationTool crossValidationTool;

    public Toolkit build() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(logRetrievalTool);
        toolkit.registerTool(dbQueryTool);
        toolkit.registerTool(ragRetrievalTool);
        toolkit.registerTool(crossValidationTool);
        return toolkit;
    }
}
