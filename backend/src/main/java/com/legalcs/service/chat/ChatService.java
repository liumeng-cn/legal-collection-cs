package com.legalcs.service.chat;

import com.legalcs.dao.ConversationDAO;
import com.legalcs.dao.MessageDAO;
import com.legalcs.service.auth.AuthContext;
import com.legalcs.dto.ChatRequest;
import com.legalcs.entity.ChatMessage;
import com.legalcs.entity.Conversation;
import com.legalcs.common.MessageRole;
import com.legalcs.common.Role;
import com.legalcs.config.ModelProperties;
import com.legalcs.service.memory.MemoryService;
import com.legalcs.service.rag.Generator;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.SystemMessage;
import io.agentscope.core.message.UserMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int TITLE_MAX_LENGTH = 20;
    private static final String FALLBACK_DEBTOR = "抱歉，服务暂时不可用，请稍后再试或联系人工客服协助处理。";
    private static final String FALLBACK_STAFF = "模型服务暂不可用，请稍后重试。";

    private final Generator generator;
    private final ConversationDAO conversationDAO;
    private final MessageDAO messageDAO;
    private final ModelProperties modelProperties;
    private final MemoryService memoryService;

    public ChatStream chat(ChatRequest request, AuthContext authContext) {
        boolean isNewConversation = request.conversationId() == null || request.conversationId().isBlank();
        long conversationId = resolveConversationId(request.conversationId(), authContext, request.message());

        messageDAO.insert(conversationId, MessageRole.USER, request.message());

        List<Msg> messages = new ArrayList<>();
        if (isNewConversation) {
            String memory = memoryService.load(authContext.getUserId(), request.message());
            if (!memory.isBlank()) {
                messages.add(new SystemMessage(memory));
            }
        }
        messages.add(new UserMessage(request.message()));

        RuntimeContext ctx = RuntimeContext.builder()
                .sessionId("conv-" + conversationId)
                .userId(authContext.getUserId())
                .put(AuthContext.class, authContext)
                .build();

        long startNanos = System.nanoTime();
        AtomicLong modelStartNanos = new AtomicLong();
        AtomicBoolean fallback = new AtomicBoolean(false);
        StringBuilder reply = new StringBuilder();

        Flux<String> deltas = generator.generate(messages, ctx)
                .doOnSubscribe(subscription -> modelStartNanos.set(System.nanoTime()))
                .timeout(modelProperties.getTimeout())
                .onErrorResume(error -> {
                    fallback.set(true);
                    log.error("模型调用失败， msg:{}", error.getMessage(), error);
                    log.warn("模型调用失败，降级处理 userId={} role={} conversationId={} error={}",
                            authContext.getUserId(), authContext.getRole(), conversationId, error.toString());
                    return Flux.just(fallbackText(authContext.getRole()));
                })
                .doOnNext(reply::append)
                .doOnComplete(() -> {
                    long endNanos = System.nanoTime();
                    long modelElapsedMs = TimeUnit.NANOSECONDS.toMillis(endNanos - modelStartNanos.get());
                    long totalElapsedMs = TimeUnit.NANOSECONDS.toMillis(endNanos - startNanos);
                    log.info("问答完成 userId={} role={} conversationId={} modelElapsedMs={} fallback={} totalElapsedMs={} answerLength={}",
                            authContext.getUserId(), authContext.getRole(), conversationId,
                            modelElapsedMs, fallback.get(), totalElapsedMs, reply.length());
                    if (!reply.isEmpty()) {
                        messageDAO.insert(conversationId, MessageRole.ASSISTANT, reply.toString());
                        conversationDAO.updateTimestamp(conversationId);
                        memoryService.saveAsync(authContext.getUserId(), conversationId);
                    }
                });

        return new ChatStream(conversationId, deltas);
    }

    public List<Conversation> listConversations(AuthContext authContext) {
        return conversationDAO.findByUserId(authContext.getUserId());
    }

    public List<ChatMessage> listMessages(long conversationId) {
        return messageDAO.findByConversationId(conversationId);
    }

    private long resolveConversationId(String conversationId, AuthContext authContext, String message) {
        if (conversationId != null && !conversationId.isBlank()) {
            return Long.parseLong(conversationId);
        }
        String title = message.length() > TITLE_MAX_LENGTH
                ? message.substring(0, TITLE_MAX_LENGTH)
                : message;
        return conversationDAO.create(authContext.getUserId(), authContext.getRole().name(), title);
    }

    private String fallbackText(Role role) {
        return role == Role.DEBTOR ? FALLBACK_DEBTOR : FALLBACK_STAFF;
    }
}
