package com.personal.ai_chatbot.controller;

import com.personal.ai_chatbot.dto.CreateCustomerTicketResponse;
import com.personal.ai_chatbot.dto.AdminChatMessageResponse;
import com.personal.ai_chatbot.dto.CustomerTicketDetailResponse;
import com.personal.ai_chatbot.dto.CustomerTicketSummaryResponse;
import com.personal.ai_chatbot.dto.OrderItemResponse;
import com.personal.ai_chatbot.dto.UserSession;
import com.personal.ai_chatbot.entity.ChatMessage;
import com.personal.ai_chatbot.entity.Ticket;
import com.personal.ai_chatbot.enums.MessageSender;
import com.personal.ai_chatbot.enums.ConversationState;
import com.personal.ai_chatbot.repository.ChatMessageRepository;
import com.personal.ai_chatbot.repository.TicketRepository;
import com.personal.ai_chatbot.service.SessionService;
import com.personal.ai_chatbot.service.TicketService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/tickets")
public class CustomerTicketController {

    private final TicketRepository ticketRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final TicketService ticketService;
    private final SessionService sessionService;

    public CustomerTicketController(
            TicketRepository ticketRepository,
            ChatMessageRepository chatMessageRepository,
            TicketService ticketService,
            SessionService sessionService) {
        this.ticketRepository = ticketRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.ticketService = ticketService;
        this.sessionService = sessionService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<CustomerTicketSummaryResponse> findTickets(@PathVariable String userId) {
        return ticketRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @GetMapping("/{ticketId}")
    @Transactional(readOnly = true)
    public CustomerTicketDetailResponse findTicketDetail(@PathVariable String userId, @PathVariable Long ticketId) {
        Ticket ticket = findUserTicket(userId, ticketId);
        activateTicketSession(userId, ticket);
        return toDetail(ticket);
    }

    @PostMapping
    @Transactional
    public CreateCustomerTicketResponse createTicket(@PathVariable String userId) {
        Ticket ticket = ticketService.createTicket(userId, null);
        UserSession session = sessionService.create(userId);
        session.setCurrentTicketId(ticket.getTicketId());
        session.setState(ConversationState.ASK_ORDER_ID);
        sessionService.save(session);
        String message = "Ticket #" + ticket.getTicketId() + " created. Please provide your order ID when you are ready.";
        saveMessage(userId, ticket.getTicketId(), MessageSender.BOT, message);

        return new CreateCustomerTicketResponse(
                toSummary(ticket),
                message);
    }

    @PostMapping("/session")
    public ResponseEntity<Void> startFreshSupportSession(@PathVariable String userId) {
        sessionService.create(userId);
        return ResponseEntity.noContent().build();
    }

    private CustomerTicketSummaryResponse toSummary(Ticket ticket) {
        return new CustomerTicketSummaryResponse(
                ticket.getTicketId(),
                ticket.getOrderId(),
                ticket.getIssueType(),
                ticket.getState(),
                ticket.getStatus(),
                ticket.getRefundAmount() == null ? BigDecimal.ZERO : ticket.getRefundAmount(),
                ticket.getItems().size(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt());
    }

    private CustomerTicketDetailResponse toDetail(Ticket ticket) {
        List<OrderItemResponse> items = ticket.getItems().stream()
                .map(item -> new OrderItemResponse(item.getItemId(), item.getItemName(), item.getPrice()))
                .toList();
        List<AdminChatMessageResponse> messages = chatMessageRepository.findByTicketIdOrderByTimestampAsc(ticket.getTicketId())
                .stream()
                .map(message -> new AdminChatMessageResponse(message.getId(), message.getSender(), message.getMessage(), message.getTimestamp()))
                .toList();

        return new CustomerTicketDetailResponse(
                ticket.getTicketId(),
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

    private Ticket findUserTicket(String userId, Long ticketId) {
        return ticketRepository.findByTicketIdAndUserId(ticketId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found for this customer."));
    }

    private void activateTicketSession(String userId, Ticket ticket) {
        UserSession session = sessionService.create(userId);
        session.setCurrentTicketId(ticket.getTicketId());
        session.setState(ticket.getState());
        session.setSelectedItemIds(ticket.getItems().stream().map(item -> item.getItemId()).toList());
        sessionService.save(session);
    }

    private void saveMessage(String userId, Long ticketId, MessageSender sender, String message) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setUserId(userId);
        chatMessage.setTicketId(ticketId);
        chatMessage.setSender(sender);
        chatMessage.setMessage(message);
        chatMessageRepository.save(chatMessage);
    }
}
