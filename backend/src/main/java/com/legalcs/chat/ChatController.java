package com.legalcs.chat;

import com.legalcs.auth.AuthContext;
import com.legalcs.chat.dto.ChatRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private static final String EVENT_CONVERSATION = "conversation";
    private static final String EVENT_TEXT = "text";

    private final ChatService chatService;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(
            @RequestBody ChatRequest request,
            @AuthenticationPrincipal AuthContext authContext) {
        ChatStream chatStream = chatService.chat(request, authContext);
        Flux<ServerSentEvent<String>> head = Flux.just(
                ServerSentEvent.<String>builder(String.valueOf(chatStream.conversationId()))
                        .event(EVENT_CONVERSATION)
                        .build());
        Flux<ServerSentEvent<String>> body = chatStream.deltas()
                .map(delta -> ServerSentEvent.<String>builder(delta).event(EVENT_TEXT).build());
        return head.concatWith(body);
    }

    @GetMapping("/conversations")
    public List<Conversation> listConversations(@AuthenticationPrincipal AuthContext authContext) {
        return chatService.listConversations(authContext);
    }

    @GetMapping("/conversations/{id}/messages")
    public List<ChatMessage> listMessages(@PathVariable long id) {
        return chatService.listMessages(id);
    }
}
