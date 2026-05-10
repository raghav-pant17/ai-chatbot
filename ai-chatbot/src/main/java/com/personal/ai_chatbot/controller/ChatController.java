package com.personal.ai_chatbot.controller;

import com.personal.ai_chatbot.dto.AdminSocketReplyRequest;
import com.personal.ai_chatbot.dto.AdminTicketReplyRequest;
import com.personal.ai_chatbot.dto.ChatMessageRequest;
import com.personal.ai_chatbot.dto.ChatMessageResponse;
import com.personal.ai_chatbot.service.AdminService;
import com.personal.ai_chatbot.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

@Controller
public class ChatController {

    private final ChatService chatService;
    private final AdminService adminService;

    public ChatController(ChatService chatService, AdminService adminService) {
        this.chatService = chatService;
        this.adminService = adminService;
    }

    @MessageMapping("/chat.sendMessage")
    @SendToUser("/queue/chat")
    public ChatMessageResponse sendMessage(@Valid @Payload ChatMessageRequest request, Principal principal) {
        if (principal == null || !principal.getName().equals(request.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "WebSocket user does not match message user.");
        }
        return chatService.handleMessage(request);
    }

    @MessageMapping("/admin.reply")
    public void sendAdminReply(@Valid @Payload AdminSocketReplyRequest request) {
        adminService.replyToTicket(request.ticketId(), new AdminTicketReplyRequest(request.message()));
    }
}
