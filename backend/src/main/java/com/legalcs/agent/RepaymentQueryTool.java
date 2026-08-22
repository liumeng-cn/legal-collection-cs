package com.legalcs.agent;

import com.legalcs.auth.AuthContext;
import com.legalcs.business.CaseDao;
import com.legalcs.business.CaseInfo;
import com.legalcs.business.RepaymentDao;
import com.legalcs.business.RepaymentRecord;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RepaymentQueryTool {

    private final CaseDao caseDao;
    private final RepaymentDao repaymentDao;

    @Tool(name = "query_repayments", description = "根据案件编号查询还款记录")
    public String queryRepayments(
            @ToolParam(name = "case_no", description = "案件编号") String caseNo,
            AuthContext authContext) {
        CaseInfo caseInfo = caseDao.findByCaseNo(caseNo).orElse(null);
        if (caseInfo == null) {
            return "未找到案件: " + caseNo;
        }
        if (!authContext.canAccessCase(caseInfo.getDebtorId())) {
            return "无权访问该案件";
        }
        List<RepaymentRecord> records = repaymentDao.findByCaseId(caseInfo.getId());
        if (records.isEmpty()) {
            return "该案件暂无还款记录";
        }
        return records.stream()
                .map(record -> "金额 " + record.getAmount()
                        + "，渠道 " + record.getChannel()
                        + "，时间 " + record.getRepaidAt())
                .collect(Collectors.joining("\n"));
    }
}
