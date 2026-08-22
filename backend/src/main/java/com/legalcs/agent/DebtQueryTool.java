package com.legalcs.agent;

import com.legalcs.auth.AuthContext;
import com.legalcs.business.CaseDao;
import com.legalcs.business.CaseInfo;
import com.legalcs.business.DebtDetail;
import com.legalcs.business.DebtDetailDao;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DebtQueryTool {

    private final CaseDao caseDao;
    private final DebtDetailDao debtDetailDao;

    @Tool(name = "query_debt", description = "根据案件编号查询欠款明细（本金、利息、费用）")
    public String queryDebt(
            @ToolParam(name = "case_no", description = "案件编号") String caseNo,
            AuthContext authContext) {
        CaseInfo caseInfo = caseDao.findByCaseNo(caseNo).orElse(null);
        if (caseInfo == null) {
            return "未找到案件: " + caseNo;
        }
        if (!authContext.canAccessCase(caseInfo.getDebtorId())) {
            return "无权访问该案件";
        }
        DebtDetail detail = debtDetailDao.findByCaseId(caseInfo.getId()).orElse(null);
        if (detail == null) {
            return "该案件暂无欠款明细";
        }
        return "案件 " + caseNo + " 欠款明细：本金 " + detail.getPrincipal()
                + "，利息 " + detail.getInterest()
                + "，费用 " + detail.getFee();
    }
}
