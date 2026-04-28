package com.personal.ai_chatbot.service;

import com.personal.ai_chatbot.entity.OrderItem;
import com.personal.ai_chatbot.entity.Ticket;
import com.personal.ai_chatbot.enums.ConversationState;
import com.personal.ai_chatbot.enums.IssueType;
import com.personal.ai_chatbot.enums.TicketStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface TicketService {

    Ticket createTicket(String userId, String orderId);

    Ticket attachOrder(Ticket ticket, String orderId);

    Optional<Ticket> findById(Long ticketId);

    Optional<Ticket> findRecoverableTicket(String userId);

    Ticket saveSelectedItems(Ticket ticket, List<OrderItem> selectedItems);

    Ticket updateIssue(Ticket ticket, IssueType issueType);

    Ticket updateFinalStatus(Ticket ticket, ConversationState state, TicketStatus status, BigDecimal refundAmount);
}
