package com.legalcs.tools;

import com.legalcs.service.auth.AuthContext;
import com.legalcs.dao.CaseDAO;
import com.legalcs.entity.CaseInfo;
import com.legalcs.common.Role;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CaseQueryTool {

    private final CaseDAO caseDao;

    @Tool(name = "query_case", description = "根据案件编号查询案件详情，返回案件状态与总金额")
    public String queryCase(
            @ToolParam(name = "case_no", description = "案件编号") String caseNo,
            AuthContext authContext) {
        CaseInfo caseInfo = caseDao.findByCaseNo(caseNo).orElse(null);
        if (caseInfo == null) {
            return "未找到案件: " + caseNo;
        }
        if (!authContext.canAccessCase(caseInfo.getDebtorId())) {
            return "无权访问该案件";
        }
        return formatCase(caseInfo);
    }

    @Tool(name = "list_my_cases", description = "列出当前用户可访问的案件列表")
    public String listCases(AuthContext authContext) {
        List<CaseInfo> cases = authContext.getRole() == Role.STAFF
                ? caseDao.findAll()
                : caseDao.findByDebtorId(Long.parseLong(authContext.getUserId()));
        if (cases.isEmpty()) {
            return "没有可访问的案件";
        }
        return cases.stream().map(this::formatCase).collect(Collectors.joining("\n"));
    }

    private String formatCase(CaseInfo caseInfo) {
        return "案件编号: " + caseInfo.getCaseNo()
                + "，状态: " + caseInfo.getStatus()
                + "，总金额: " + caseInfo.getAmountTotal();
    }
}
