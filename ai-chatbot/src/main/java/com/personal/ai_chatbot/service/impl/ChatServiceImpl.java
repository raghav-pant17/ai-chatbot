package com.personal.ai_chatbot.service.impl;

import com.personal.ai_chatbot.dto.AIEscalationResult;
import com.personal.ai_chatbot.dto.ChatMessageRequest;
import com.personal.ai_chatbot.dto.ChatMessageResponse;
import com.personal.ai_chatbot.dto.UserSession;
import com.personal.ai_chatbot.entity.ChatMessage;
import com.personal.ai_chatbot.entity.CustomerOrder;
import com.personal.ai_chatbot.entity.OrderItem;
import com.personal.ai_chatbot.entity.Ticket;
import com.personal.ai_chatbot.enums.ConversationState;
import com.personal.ai_chatbot.enums.IssueType;
import com.personal.ai_chatbot.enums.MessageSender;
import com.personal.ai_chatbot.enums.TicketStatus;
import com.personal.ai_chatbot.repository.ChatMessageRepository;
import com.personal.ai_chatbot.repository.TicketRepository;
import com.personal.ai_chatbot.service.AIService;
import com.personal.ai_chatbot.service.ChatService;
import com.personal.ai_chatbot.service.DuplicateComplaintService;
import com.personal.ai_chatbot.service.EscalationService;
import com.personal.ai_chatbot.service.OrderService;
import com.personal.ai_chatbot.service.RefundService;
import com.personal.ai_chatbot.service.SessionService;
import com.personal.ai_chatbot.service.TicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatServiceImpl.class);

    private final SessionService sessionService;
    private final TicketService ticketService;
    private final OrderService orderService;
    private final RefundService refundService;
    private final DuplicateComplaintService duplicateComplaintService;
    private final AIService aiService;
    private final EscalationService escalationService;
    private final ChatMessageRepository chatMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final TicketRepository ticketRepository;

    public ChatServiceImpl(
            SessionService sessionService,
            TicketService ticketService,
            OrderService orderService,
            RefundService refundService,
            DuplicateComplaintService duplicateComplaintService,
            AIService aiService,
            EscalationService escalationService,
            ChatMessageRepository chatMessageRepository,
            SimpMessagingTemplate messagingTemplate,
            TicketRepository ticketRepository) {
        this.sessionService = sessionService;
        this.ticketService = ticketService;
        this.orderService = orderService;
        this.refundService = refundService;
        this.duplicateComplaintService = duplicateComplaintService;
        this.aiService = aiService;
        this.escalationService = escalationService;
        this.chatMessageRepository = chatMessageRepository;
        this.messagingTemplate = messagingTemplate;
        this.ticketRepository = ticketRepository;
    }

    @Override
    @Transactional
    public ChatMessageResponse handleMessage(ChatMessageRequest request) {
        UserSession session = loadOrRecoverSession(request.userId());
        LOGGER.info(
                "Chat request received userId={} state={} ticketId={} retryCount={} messageLength={}",
                request.userId(),
                session.getState(),
                session.getCurrentTicketId(),
                session.getRetryCount(),
                request.message() == null ? 0 : request.message().length());
        Optional<Ticket> activeTicket = currentTicket(session);
        if (activeTicket.filter(this::isResolved).isPresent()) {
            Ticket ticket = activeTicket.get();
            LOGGER.info("Chat request ignored because ticket is resolved userId={} ticketId={}", request.userId(), ticket.getTicketId());
            session.setState(ConversationState.RESOLVED);
            sessionService.save(session);
            return new ChatMessageResponse(
                    request.userId(),
                    ticket.getTicketId(),
                    ticket.getOrderId(),
                    ConversationState.RESOLVED,
                    TicketStatus.RESOLVED,
                    "This ticket has been resolved. Please create a new ticket if you need more help.",
                    Instant.now(),
                    false);
        }

        ChatMessage userMessage = saveMessage(request.userId(), session.getCurrentTicketId(), MessageSender.USER, request.message());
        publishUserMessage(session, userMessage);

        ChatMessageResponse response = handleImmediateEscalation(request, session)
                .orElseGet(() -> switch (session.getState()) {
            case START -> handleStart(request, session);
            case ASK_ORDER_ID -> handleOrderId(request, session);
            case SELECT_ITEMS -> handleItemSelection(request, session);
            case ASK_ISSUE -> handleIssue(request, session);
            case PROCESS_REFUND -> processRefund(request, session);
            case RESOLVED -> buildResponse(session, TicketStatus.RESOLVED, "This complaint is already resolved. How else can I help with your order?");
            case ESCALATED -> buildTransientResponse(session, TicketStatus.ESCALATED, "");
        });

        if (response.persistMessage() && response.message() != null && !response.message().isBlank()) {
            saveMessage(request.userId(), response.ticketId(), MessageSender.BOT, response.message());
        }
        sessionService.save(session);
        LOGGER.info(
                "Chat response userId={} state={} status={} ticketId={} orderId={} persistMessage={} messageLength={}",
                response.userId(),
                response.state(),
                response.status(),
                response.ticketId(),
                response.orderId(),
                response.persistMessage(),
                response.message() == null ? 0 : response.message().length());
        return response;
    }

    private UserSession loadOrRecoverSession(String userId) {
        return sessionService.findByUserId(userId)
                .orElseGet(() -> ticketService.findRecoverableTicket(userId)
                        .map(this::buildSessionFromTicket)
                        .orElseGet(() -> sessionService.create(userId)));
    }

    private UserSession buildSessionFromTicket(Ticket ticket) {
        UserSession session = new UserSession();
        session.setUserId(ticket.getUserId());
        session.setCurrentTicketId(ticket.getTicketId());
        session.setState(ticket.getState());
        session.setSelectedItemIds(ticket.getItems().stream().map(item -> item.getItemId()).toList());
        return session;
    }

    private ChatMessageResponse handleStart(ChatMessageRequest request, UserSession session) {
        if (aiService.isComplaintIntent(request.message())) {
            session.setState(ConversationState.ASK_ORDER_ID);
            return buildResponse(session, TicketStatus.OPEN, "Please provide your order ID.");
        }
        if (isInScope(request.message())) {
            return buildResponse(session, TicketStatus.OPEN, "How can I help you with your order today?");
        }
        return buildResponse(session, TicketStatus.OPEN, "I can help with order, refund, delivery, and support-related queries only.");
    }

    private ChatMessageResponse handleOrderId(ChatMessageRequest request, UserSession session) {
        LOGGER.info("Handling order id step userId={} ticketId={}", request.userId(), session.getCurrentTicketId());
        Optional<String> orderId = aiService.extractOrderId(request.message());
        if (orderId.isEmpty()) {
            session.increaseRetryCount();
            LOGGER.info("Order id extraction failed userId={} retryCount={}", request.userId(), session.getRetryCount());
            return buildResponse(session, TicketStatus.OPEN, "Please provide a valid order ID.");
        }

        Optional<CustomerOrder> order = orderService.findUserOrder(orderId.get(), request.userId());
        if (order.isEmpty()) {
            session.increaseRetryCount();
            LOGGER.info("Order id not found for user userId={} orderId={} retryCount={}", request.userId(), orderId.get(), session.getRetryCount());
            return buildResponse(session, TicketStatus.OPEN, "I could not find this order for your account. Please check the order ID.");
        }

        Ticket ticket = currentTicket(session)
                .filter(current -> current.getOrderId() == null)
                .map(current -> ticketService.attachOrder(current, orderId.get()))
                .orElseGet(() -> ticketService.createTicket(request.userId(), orderId.get()));
        session.setCurrentTicketId(ticket.getTicketId());
        session.setState(ConversationState.SELECT_ITEMS);
        return buildResponse(session, ticket.getStatus(), orderService.buildItemSelectionText(order.get()));
    }

    private ChatMessageResponse handleItemSelection(ChatMessageRequest request, UserSession session) {
        Optional<Ticket> ticket = currentTicket(session);
        if (ticket.isEmpty()) {
            session.setState(ConversationState.ASK_ORDER_ID);
            return buildResponse(session, TicketStatus.OPEN, "Please provide your order ID again.");
        }

        CustomerOrder order = orderService.findUserOrder(ticket.get().getOrderId(), ticket.get().getUserId()).orElse(null);
        if (order == null) {
            session.setState(ConversationState.ASK_ORDER_ID);
            return buildResponse(session, TicketStatus.OPEN, "I could not reload your order. Please provide your order ID again.");
        }

        List<OrderItem> selectedItems = orderService.selectItems(order, request.message());
        if (selectedItems.isEmpty()) {
            session.increaseRetryCount();
            return buildResponse(session, TicketStatus.OPEN, "Please select item numbers like 1,2 or type \"all\".");
        }

        Set<String> resolvedItemIds = resolvedItemIds(order);
        List<OrderItem> alreadyResolvedItems = selectedItems.stream()
                .filter(item -> resolvedItemIds.contains(item.getItemId()))
                .toList();
        List<OrderItem> availableItems = selectedItems.stream()
                .filter(item -> !resolvedItemIds.contains(item.getItemId()))
                .toList();

        if (!alreadyResolvedItems.isEmpty() && availableItems.isEmpty()) {
            session.increaseRetryCount();
            return buildResponse(session, TicketStatus.OPEN, resolvedItemsMessage(order, alreadyResolvedItems) + " Please select another item.");
        }

        Ticket updatedTicket = ticketService.saveSelectedItems(ticket.get(), availableItems);
        session.setSelectedItemIds(availableItems.stream().map(OrderItem::getItemId).toList());
        session.setState(ConversationState.ASK_ISSUE);
        String prefix = alreadyResolvedItems.isEmpty()
                ? ""
                : resolvedItemsMessage(order, alreadyResolvedItems) + " I will continue with " + itemReference(order, availableItems) + ". ";
        return buildResponse(session, updatedTicket.getStatus(), prefix + "Could you specify the issue? Choose damaged, not delivered, or wrong item.");
    }

    private ChatMessageResponse handleIssue(ChatMessageRequest request, UserSession session) {
        Optional<IssueType> issueType = aiService.normalizeIssueType(request.message());
        if (issueType.isEmpty()) {
            session.increaseRetryCount();
            return buildResponse(session, TicketStatus.OPEN, "Could you specify the issue? Choose damaged, not delivered, or wrong item.");
        }

        Ticket ticket = currentTicket(session).orElseThrow();
        ticket = ticketService.updateIssue(ticket, issueType.get());

        Optional<Ticket> duplicate = duplicateComplaintService.findDuplicate(ticket);
        if (duplicate.isPresent()) {
            Ticket existingTicket = duplicate.get();
            return buildResponse(session, existingTicket.getStatus(), duplicateMessage(existingTicket.getStatus()));
        }

        session.setState(ConversationState.PROCESS_REFUND);
        return processRefund(request, session);
    }

    private ChatMessageResponse processRefund(ChatMessageRequest request, UserSession session) {
        Ticket ticket = currentTicket(session).orElseThrow();
        BigDecimal refundAmount = refundService.calculateRefund(ticket);
        AIEscalationResult aiResult = AIEscalationResult.neutral();

        if (!escalationService.shouldEscalate(refundAmount, session, request.message(), aiResult)) {
            LOGGER.info("Calling AI escalation analysis during refund userId={} ticketId={} state={}", request.userId(), ticket.getTicketId(), ticket.getState());
            List<ChatMessage> recentMessages = chatMessageRepository.findTop5ByUserIdOrderByTimestampDesc(request.userId());
            aiResult = aiService.analyzeEscalation(recentMessages, ticket.getState(), request.message());
            LOGGER.info(
                    "AI escalation analysis completed userId={} ticketId={} recommended={} confidence={} sentiment={}",
                    request.userId(),
                    ticket.getTicketId(),
                    aiResult.escalationRecommended(),
                    aiResult.confidence(),
                    aiResult.sentiment());
        }

        if (escalationService.shouldEscalate(refundAmount, session, request.message(), aiResult)) {
            Ticket updatedTicket = ticketService.updateFinalStatus(ticket, ConversationState.ESCALATED, TicketStatus.ESCALATED, refundAmount);
            session.setState(ConversationState.ESCALATED);
            LOGGER.info("Refund flow escalated userId={} ticketId={} refundAmount={}", request.userId(), updatedTicket.getTicketId(), refundAmount);
            return buildResponse(session, updatedTicket.getStatus(), "I'm sorry for the inconvenience. I'm escalating this to a support agent for priority assistance.");
        }

        Ticket updatedTicket = ticketService.updateFinalStatus(ticket, ConversationState.RESOLVED, TicketStatus.RESOLVED, refundAmount);
        session.setState(ConversationState.RESOLVED);
        LOGGER.info("Refund flow resolved userId={} ticketId={} refundAmount={}", request.userId(), updatedTicket.getTicketId(), refundAmount);
        return buildResponse(session, updatedTicket.getStatus(), "Your refund of Rs." + refundAmount + " has been initiated.");
    }

    private Optional<ChatMessageResponse> handleImmediateEscalation(ChatMessageRequest request, UserSession session) {
        if (session.getState() == ConversationState.RESOLVED || session.getState() == ConversationState.ESCALATED) {
            return Optional.empty();
        }

        Optional<Ticket> ticket = currentTicket(session);
        ConversationState ticketState = ticket.map(Ticket::getState).orElse(session.getState());
        AIEscalationResult aiResult = AIEscalationResult.neutral();

        // Ask the configured AI provider first; provider classes fall back to compact rules only if AI is unavailable.
        if (!escalationService.shouldEscalate(BigDecimal.ZERO, session, request.message(), aiResult)) {
            LOGGER.info("Calling AI immediate escalation analysis userId={} ticketId={} state={}", request.userId(), session.getCurrentTicketId(), ticketState);
            List<ChatMessage> recentMessages = chatMessageRepository.findTop5ByUserIdOrderByTimestampDesc(request.userId());
            aiResult = aiService.analyzeEscalation(recentMessages, ticketState, request.message());
            LOGGER.info(
                    "AI immediate escalation analysis completed userId={} ticketId={} recommended={} confidence={} sentiment={}",
                    request.userId(),
                    session.getCurrentTicketId(),
                    aiResult.escalationRecommended(),
                    aiResult.confidence(),
                    aiResult.sentiment());
            if (!escalationService.shouldEscalate(BigDecimal.ZERO, session, request.message(), aiResult)) {
                LOGGER.info("Immediate escalation not triggered userId={} ticketId={} state={}", request.userId(), session.getCurrentTicketId(), ticketState);
                return Optional.empty();
            }
        }

        Ticket escalatedTicket = ticket
                .orElseGet(() -> ticketService.createTicket(request.userId(), null));
        Ticket updatedTicket = ticketService.updateFinalStatus(escalatedTicket, ConversationState.ESCALATED, TicketStatus.ESCALATED, BigDecimal.ZERO);
        session.setCurrentTicketId(updatedTicket.getTicketId());
        session.setState(ConversationState.ESCALATED);
        LOGGER.info("Immediate escalation triggered userId={} ticketId={} orderId={} previousState={}", request.userId(), updatedTicket.getTicketId(), updatedTicket.getOrderId(), ticketState);

        return Optional.of(buildResponse(session, updatedTicket.getStatus(), "I'm sorry for the inconvenience. I'm escalating this to a support agent for priority assistance."));
    }

    private Optional<Ticket> currentTicket(UserSession session) {
        if (session.getCurrentTicketId() == null) {
            return Optional.empty();
        }
        return ticketService.findById(session.getCurrentTicketId());
    }

    private Set<String> resolvedItemIds(CustomerOrder order) {
        Set<String> itemIds = new LinkedHashSet<>();
        List<Ticket> resolvedTickets = ticketRepository.findByUserIdAndOrderIdAndStatusOrderByUpdatedAtDesc(
                order.getUserId(),
                order.getOrderId(),
                TicketStatus.RESOLVED);
        for (Ticket ticket : resolvedTickets) {
            ticket.getItems().forEach(item -> itemIds.add(item.getItemId()));
        }
        return itemIds;
    }

    private String resolvedItemsMessage(CustomerOrder order, List<OrderItem> items) {
        String label = itemReference(order, items);
        return label + (items.size() == 1 ? " is" : " are") + " already resolved.";
    }

    private String itemReference(CustomerOrder order, List<OrderItem> items) {
        List<Integer> itemNumbers = items.stream()
                .map(item -> itemNumber(order, item))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted()
                .toList();
        if (itemNumbers.isEmpty()) {
            return items.size() == 1 ? "This item" : "These items";
        }
        return (itemNumbers.size() == 1 ? "Item " : "Items ") + formatNumberList(itemNumbers);
    }

    private Optional<Integer> itemNumber(CustomerOrder order, OrderItem selectedItem) {
        List<OrderItem> orderItems = order.getItems();
        for (int i = 0; i < orderItems.size(); i++) {
            if (orderItems.get(i).getItemId().equals(selectedItem.getItemId())) {
                return Optional.of(i + 1);
            }
        }
        return Optional.empty();
    }

    private String formatNumberList(List<Integer> values) {
        if (values.size() <= 1) {
            return values.stream().findFirst().map(String::valueOf).orElse("");
        }
        return String.join(", ", values.subList(0, values.size() - 1).stream().map(String::valueOf).toList())
                + " & "
                + values.get(values.size() - 1);
    }

    private boolean isResolved(Ticket ticket) {
        return ticket.getStatus() == TicketStatus.RESOLVED || ticket.getState() == ConversationState.RESOLVED;
    }

    private ChatMessage saveMessage(String userId, Long ticketId, MessageSender sender, String message) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setUserId(userId);
        chatMessage.setTicketId(ticketId);
        chatMessage.setSender(sender);
        chatMessage.setMessage(message);
        return chatMessageRepository.save(chatMessage);
    }

    private void publishUserMessage(UserSession session, ChatMessage message) {
        if (message.getTicketId() == null) {
            return;
        }
        messagingTemplate.convertAndSend(
                "/topic/admin/tickets",
                new ChatMessageResponse(
                        message.getUserId(),
                        message.getTicketId(),
                        currentTicket(session).map(Ticket::getOrderId).orElse(null),
                        session.getState(),
                        TicketStatus.OPEN,
                        message.getMessage(),
                        message.getTimestamp(),
                        MessageSender.USER,
                        true));
    }

    private ChatMessageResponse buildResponse(UserSession session, TicketStatus status, String message) {
        return new ChatMessageResponse(
                session.getUserId(),
                session.getCurrentTicketId(),
                currentTicket(session).map(Ticket::getOrderId).orElse(null),
                session.getState(),
                status,
                message,
                Instant.now());
    }

    private ChatMessageResponse buildTransientResponse(UserSession session, TicketStatus status, String message) {
        return new ChatMessageResponse(
                session.getUserId(),
                session.getCurrentTicketId(),
                session.getState(),
                status,
                message,
                Instant.now(),
                false);
    }

    private boolean isInScope(String message) {
        String normalized = message.toLowerCase();
        return normalized.contains("order")
                || normalized.contains("refund")
                || normalized.contains("payment")
                || normalized.contains("delivery")
                || normalized.contains("support");
    }

    private String duplicateMessage(TicketStatus status) {
        return switch (status) {
            case OPEN -> "You already have an active request for this issue.";
            case RESOLVED -> "This issue has already been resolved for your order. If you still need help, I can escalate this to a support agent.";
            case ESCALATED -> "This issue has already been escalated to our support team.";
        };
    }
}
