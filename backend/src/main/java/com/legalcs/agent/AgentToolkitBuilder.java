package com.legalcs.agent;

import com.legalcs.tools.CaseQueryTool;
import com.legalcs.tools.DebtQueryTool;
import com.legalcs.tools.RagRetrievalTool;
import com.legalcs.tools.RepaymentQueryTool;
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
