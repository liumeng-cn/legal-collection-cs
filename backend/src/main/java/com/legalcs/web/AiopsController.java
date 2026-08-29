package com.legalcs.web;

import com.legalcs.service.aiops.AiopsService;
import com.legalcs.service.aiops.AiopsStream;
import com.legalcs.dto.DiagnoseRequest;
import com.legalcs.service.auth.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/diagnose")
@RequiredArgsConstructor
public class AiopsController {

    private static final String EVENT_CONVERSATION = "conversation";

    private final AiopsService aiopsService;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(
            @RequestBody DiagnoseRequest request,
            @AuthenticationPrincipal AuthContext authContext) {
        AiopsStream stream = aiopsService.diagnose(request, authContext);
        Flux<ServerSentEvent<String>> head = Flux.just(
                ServerSentEvent.<String>builder(String.valueOf(stream.conversationId()))
                        .event(EVENT_CONVERSATION)
                        .build());
        return head.concatWith(stream.events());
    }
}
