package com.legalcs.agent;

import com.legalcs.tools.RagRetrievalTool;
import com.legalcs.tools.CrossValidationTool;
import com.legalcs.tools.DbQueryTool;
import com.legalcs.tools.LogRetrievalTool;
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
