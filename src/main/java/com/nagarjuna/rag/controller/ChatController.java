package com.nagarjuna.rag.controller;

import com.nagarjuna.rag.dto.AskReply;
import com.nagarjuna.rag.dto.AskRequest;
import com.nagarjuna.rag.service.RagChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final RagChatService ragChatService;

    @PostMapping
    public AskReply ask(
            @Valid @RequestBody
            AskRequest request
    ) {

        return ragChatService
                .ask(
                        request.question(),
                        request.sessionId()
                );
    }
}
