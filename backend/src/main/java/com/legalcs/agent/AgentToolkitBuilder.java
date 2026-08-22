package com.legalcs.agent;

import io.agentscope.core.tool.Toolkit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentToolkitBuilder {

    private final CaseQueryTool caseQueryTool;
    private final DebtQueryTool debtQueryTool;
    private final RepaymentQueryTool repaymentQueryTool;
    private final RagRetrievalTool ragRetrievalTool;

    public Toolkit build() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(caseQueryTool);
        toolkit.registerTool(debtQueryTool);
        toolkit.registerTool(repaymentQueryTool);
        toolkit.registerTool(ragRetrievalTool);
        return toolkit;
    }
}
