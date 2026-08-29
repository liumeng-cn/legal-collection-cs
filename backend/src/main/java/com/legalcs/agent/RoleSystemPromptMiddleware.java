package com.legalcs.agent;

import com.legalcs.service.auth.AuthContext;
import com.legalcs.common.Role;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.middleware.MiddlewareBase;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class RoleSystemPromptMiddleware implements MiddlewareBase {

    @Override
    public Mono<String> onSystemPrompt(Agent agent, RuntimeContext ctx, String systemPrompt) {
        AuthContext authContext = ctx.get(AuthContext.class);
        if (authContext == null) {
            return Mono.just(systemPrompt);
        }
        return Mono.just(systemPrompt + "\n当前服务对象角色：" + roleLabel(authContext.getRole()));
    }

    private static String roleLabel(Role role) {
        return switch (role) {
            case STAFF -> "催收员";
            case DEBTOR -> "债务人";
            case SRE -> "运维";
        };
    }
}
