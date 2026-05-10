package com.personal.ai_chatbot.service.impl;

import com.personal.ai_chatbot.dto.AdminChatMessageResponse;
import com.personal.ai_chatbot.dto.AdminLoginRequest;
import com.personal.ai_chatbot.dto.AdminLoginResponse;
import com.personal.ai_chatbot.dto.AdminTicketActionResponse;
import com.personal.ai_chatbot.dto.AdminTicketDetailResponse;
import com.personal.ai_chatbot.dto.AdminTicketReplyRequest;
import com.personal.ai_chatbot.dto.AdminTicketRefundRequest;
import com.personal.ai_chatbot.dto.AdminTicketSummaryResponse;
import com.personal.ai_chatbot.dto.ChatMessageResponse;
import com.personal.ai_chatbot.dto.OrderItemResponse;
import com.personal.ai_chatbot.entity.AdminUser;
import com.personal.ai_chatbot.entity.ChatMessage;
import com.personal.ai_chatbot.entity.Ticket;
import com.personal.ai_chatbot.enums.ConversationState;
import com.personal.ai_chatbot.enums.MessageSender;
import com.personal.ai_chatbot.enums.TicketStatus;
import com.personal.ai_chatbot.enums.UserRole;
import com.personal.ai_chatbot.repository.AdminUserRepository;
import com.personal.ai_chatbot.repository.ChatMessageRepository;
import com.personal.ai_chatbot.repository.TicketRepository;
import com.personal.ai_chatbot.service.AdminService;
import com.personal.ai_chatbot.service.AuthTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

@Service
public class AdminServiceImpl implements AdminService {

    private final AdminUserRepository adminUserRepository;
    private final TicketRepository ticketRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AuthTokenService authTokenService;
    private final SimpMessagingTemplate messagingTemplate;

    public AdminServiceImpl(
            AdminUserRepository adminUserRepository,
            TicketRepository ticketRepository,
            ChatMessageRepository chatMessageRepository,
            AuthTokenService authTokenService,
            SimpMessagingTemplate messagingTemplate) {
        this.adminUserRepository = adminUserRepository;
        this.ticketRepository = ticketRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.authTokenService = authTokenService;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public AdminLoginResponse login(AdminLoginRequest request) {
        AdminUser adminUser = adminUserRepository.findById(request.adminId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin credentials."));

        if (!passwordMatches(request.password(), adminUser)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin credentials.");
        }

        String accessToken = authTokenService.createAccessToken(adminUser.getAdminId(), UserRole.ADMIN);
        return new AdminLoginResponse(
                adminUser.getAdminId(),
                adminUser.getFullName(),
                accessToken,
                "Bearer",
                authTokenService.accessTokenExpirySeconds());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminTicketSummaryResponse> findEscalatedTickets() {
        return ticketRepository.findByStatusOrderByUpdatedAtDesc(TicketStatus.ESCALATED)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminTicketDetailResponse findTicketDetail(Long ticketId) {
        Ticket ticket = findTicket(ticketId);
        return toDetail(ticket);
    }

    @Override
    @Transactional
    public AdminTicketActionResponse replyToTicket(Long ticketId, AdminTicketReplyRequest request) {
        Ticket ticket = findTicket(ticketId);
        if (ticket.getStatus() != TicketStatus.ESCALATED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only escalated tickets can receive admin replies.");
        }

        ChatMessage message = saveAdminMessage(ticket, request.message());
        publishAdminMessage(ticket, message);

        return new AdminTicketActionResponse(ticket.getTicketId(), ticket.getState(), ticket.getStatus(), "Admin reply saved.");
    }

    @Override
    @Transactional
    public AdminTicketActionResponse refundTicket(Long ticketId, AdminTicketRefundRequest request) {
        Ticket ticket = findTicket(ticketId);
        if (ticket.getStatus() == TicketStatus.RESOLVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ticket is already resolved.");
        }

        ticket.setRefundAmount(request.amount());
        ticket.setState(ConversationState.RESOLVED);
        ticket.setStatus(TicketStatus.RESOLVED);
        ticketRepository.save(ticket);
        ChatMessage message = saveAdminMessage(ticket, "Support initiated a refund of Rs." + request.amount() + ".");
        publishAdminMessage(ticket, message);

        return new AdminTicketActionResponse(ticket.getTicketId(), ticket.getState(), ticket.getStatus(), "Refund initiated.");
    }

    @Override
    @Transactional
    public AdminTicketActionResponse resolveTicket(Long ticketId) {
        Ticket ticket = findTicket(ticketId);
        if (ticket.getStatus() == TicketStatus.RESOLVED) {
            return new AdminTicketActionResponse(ticket.getTicketId(), ticket.getState(), ticket.getStatus(), "Ticket is already resolved.");
        }

        ticket.setState(ConversationState.RESOLVED);
        ticket.setStatus(TicketStatus.RESOLVED);
        ticket.setRefundAmount(BigDecimal.ZERO);
        ticketRepository.save(ticket);
        ChatMessage message = saveAdminMessage(ticket, "Support resolved this ticket without a refund.");
        publishAdminMessage(ticket, message);
        return new AdminTicketActionResponse(ticket.getTicketId(), ticket.getState(), ticket.getStatus(), "Ticket resolved without refund.");
    }

    private Ticket findTicket(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found."));
    }

    private AdminTicketSummaryResponse toSummary(Ticket ticket) {
        return new AdminTicketSummaryResponse(
                ticket.getTicketId(),
                ticket.getUserId(),
                ticket.getOrderId(),
                ticket.getIssueType(),
                ticket.getState(),
                ticket.getStatus(),
                ticket.getRefundAmount(),
                ticket.getItems().size(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt());
    }

    private AdminTicketDetailResponse toDetail(Ticket ticket) {
        List<OrderItemResponse> items = ticket.getItems().stream()
                .map(item -> new OrderItemResponse(item.getItemId(), item.getItemName(), item.getPrice()))
                .toList();
        List<AdminChatMessageResponse> messages = chatMessageRepository.findByTicketIdOrderByTimestampAsc(ticket.getTicketId())
                .stream()
                .map(message -> new AdminChatMessageResponse(message.getId(), message.getSender(), message.getMessage(), message.getTimestamp()))
                .toList();

        return new AdminTicketDetailResponse(
                ticket.getTicketId(),
                ticket.getUserId(),
                ticket.getOrderId(),
                ticket.getIssueType(),
                ticket.getState(),
                ticket.getStatus(),
                ticket.getRefundAmount() == null ? BigDecimal.ZERO : ticket.getRefundAmount(),
                items,
                messages,
                ticket.getCreatedAt(),
                ticket.getUpdatedAt());
    }

    private ChatMessage saveAdminMessage(Ticket ticket, String message) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setTicketId(ticket.getTicketId());
        chatMessage.setUserId(ticket.getUserId());
        chatMessage.setSender(MessageSender.ADMIN);
        chatMessage.setMessage(message);
        return chatMessageRepository.save(chatMessage);
    }

    private void publishAdminMessage(Ticket ticket, ChatMessage message) {
        ChatMessageResponse response = new ChatMessageResponse(
                ticket.getUserId(),
                ticket.getTicketId(),
                ticket.getOrderId(),
                ticket.getState(),
                ticket.getStatus(),
                message.getMessage(),
                message.getTimestamp(),
                MessageSender.ADMIN,
                true);
        messagingTemplate.convertAndSendToUser(ticket.getUserId(), "/queue/chat", response);
        messagingTemplate.convertAndSend("/topic/admin/tickets", response);
    }

    private boolean passwordMatches(String rawPassword, AdminUser adminUser) {
        String candidateHash = sha256(adminUser.getPasswordSalt() + ":" + rawPassword);
        return MessageDigest.isEqual(
                candidateHash.getBytes(StandardCharsets.UTF_8),
                adminUser.getPasswordHash().getBytes(StandardCharsets.UTF_8));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to verify admin password.", ex);
        }
    }
}
