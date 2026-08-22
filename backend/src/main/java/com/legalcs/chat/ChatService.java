package com.legalcs.chat;

import com.legalcs.auth.AuthContext;
import com.legalcs.chat.dto.ChatRequest;
import com.legalcs.common.MessageRole;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.SystemMessage;
import io.agentscope.core.message.UserMessage;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int TITLE_MAX_LENGTH = 20;
    private static final String ASSISTANT_NAME = "assistant";

    private final ReActAgent agent;
    private final ConversationDao conversationDao;
    private final MessageDao messageDao;

    public ChatStream chat(ChatRequest request, AuthContext authContext) {
        long conversationId = resolveConversationId(request.conversationId(), authContext, request.message());

        List<Msg> messages = new ArrayList<>(loadHistory(conversationId));
        messageDao.insert(conversationId, MessageRole.USER, request.message());
        messages.add(new UserMessage(request.message()));

        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId("conv-" + conversationId)
                .userId(authContext.getUserId())
                .put(AuthContext.class, authContext)
                .build();

        StringBuilder reply = new StringBuilder();
        Flux<String> deltas = agent.streamEvents(messages, ctx)
                .ofType(TextBlockDeltaEvent.class)
                .map(TextBlockDeltaEvent::getDelta)
                .doOnNext(reply::append)
                .doOnComplete(() -> {
                    if (!reply.isEmpty()) {
                        messageDao.insert(conversationId, MessageRole.ASSISTANT, reply.toString());
                        conversationDao.updateTimestamp(conversationId);
                    }
                });

        return new ChatStream(conversationId, deltas);
    }

    public List<Conversation> listConversations(AuthContext authContext) {
        return conversationDao.findByUserId(authContext.getUserId());
    }

    public List<ChatMessage> listMessages(long conversationId) {
        return messageDao.findByConversationId(conversationId);
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
