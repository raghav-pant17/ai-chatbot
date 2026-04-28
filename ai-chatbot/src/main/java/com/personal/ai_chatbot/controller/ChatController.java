package com.personal.ai_chatbot.controller;

import com.personal.ai_chatbot.dto.ChatMessageRequest;
import com.personal.ai_chatbot.dto.ChatMessageResponse;
import com.personal.ai_chatbot.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @MessageMapping("/chat.sendMessage")
    @SendTo("/topic/public")
    public ChatMessageResponse sendMessage(@Valid @Payload ChatMessageRequest request) {
        return chatService.handleMessage(request);
    }
}
