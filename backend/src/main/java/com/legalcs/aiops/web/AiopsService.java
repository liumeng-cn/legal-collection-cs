package com.legalcs.aiops.web;

import com.legalcs.aiops.web.dto.DiagnoseRequest;
import com.legalcs.auth.AuthContext;
import com.legalcs.chat.ChatMessage;
import com.legalcs.chat.ConversationDao;
import com.legalcs.chat.MessageDao;
import com.legalcs.common.MessageRole;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.SystemMessage;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SynchronousSink;

@Service
@RequiredArgsConstructor
public class AiopsService {

    private static final int TITLE_MAX_LENGTH = 20;
    private static final String ASSISTANT_NAME = "assistant";
    private static final String EVENT_TEXT = "text";
    private static final String EVENT_TOOL = "tool";

    private final HarnessAgent agent;
    private final ConversationDao conversationDao;
    private final MessageDao messageDao;

    public AiopsStream diagnose(DiagnoseRequest request, AuthContext authContext) {
        long conversationId = resolveConversationId(request.conversationId(), authContext, request.message());

        List<Msg> messages = new ArrayList<>(loadHistory(conversationId));
        messageDao.insert(conversationId, MessageRole.USER, request.message());
        messages.add(new UserMessage(request.message()));

        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId("aiops-" + conversationId)
                .userId(authContext.getUserId())
                .put(AuthContext.class, authContext)
                .build();

        StringBuilder reply = new StringBuilder();
        Flux<ServerSentEvent<String>> events = agent.streamEvents(messages, ctx)
                .handle((AgentEvent event, SynchronousSink<ServerSentEvent<String>> sink) -> {
                    if (event instanceof TextBlockDeltaEvent text) {
                        String delta = text.getDelta();
                        if (delta != null && !delta.isEmpty()) {
                            reply.append(delta);
                            sink.next(ServerSentEvent.<String>builder(delta).event(EVENT_TEXT).build());
                        }
                    } else if (event instanceof ToolCallStartEvent tool) {
                        sink.next(ServerSentEvent.<String>builder(tool.getToolCallName()).event(EVENT_TOOL).build());
                    }
                })
                .doOnComplete(() -> {
                    if (!reply.isEmpty()) {
                        messageDao.insert(conversationId, MessageRole.ASSISTANT, reply.toString());
                        conversationDao.updateTimestamp(conversationId);
                    }
                });

        return new AiopsStream(conversationId, events);
    }

    private List<Msg> loadHistory(long conversationId) {
        List<Msg> history = new ArrayList<>();
        for (ChatMessage message : messageDao.findByConversationId(conversationId)) {
            history.add(toMsg(message));
        }
        return history;
    }

    private Msg toMsg(ChatMessage message) {
        return switch (message.getRole()) {
            case USER -> new UserMessage(message.getContent());
            case ASSISTANT -> new AssistantMessage(ASSISTANT_NAME, message.getContent());
            case SYSTEM -> new SystemMessage(message.getContent());
        };
    }

    private long resolveConversationId(String conversationId, AuthContext authContext, String message) {
        if (conversationId != null && !conversationId.isBlank()) {
            return Long.parseLong(conversationId);
        }
        String title = message.length() > TITLE_MAX_LENGTH
                ? message.substring(0, TITLE_MAX_LENGTH)
                : message;
        return conversationDao.create(authContext.getUserId(), authContext.getRole().name(), title);
    }
}
