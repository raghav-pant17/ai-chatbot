package com.personal.ai_chatbot.service.impl;

import com.personal.ai_chatbot.entity.OrderItem;
import com.personal.ai_chatbot.entity.Ticket;
import com.personal.ai_chatbot.entity.TicketItem;
import com.personal.ai_chatbot.enums.ConversationState;
import com.personal.ai_chatbot.enums.IssueType;
import com.personal.ai_chatbot.enums.TicketStatus;
import com.personal.ai_chatbot.repository.TicketRepository;
import com.personal.ai_chatbot.service.TicketService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;

    public TicketServiceImpl(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @Override
    public Ticket createTicket(String userId, String orderId) {
        Ticket ticket = new Ticket();
        ticket.setUserId(userId);
        ticket.setOrderId(orderId);
        ticket.setState(orderId == null ? ConversationState.ASK_ORDER_ID : ConversationState.SELECT_ITEMS);
        ticket.setStatus(TicketStatus.OPEN);
        return ticketRepository.save(ticket);
    }

    @Override
    public Ticket attachOrder(Ticket ticket, String orderId) {
        ticket.setOrderId(orderId);
        ticket.setState(ConversationState.SELECT_ITEMS);
        return ticketRepository.save(ticket);
    }

    @Override
    public Optional<Ticket> findById(Long ticketId) {
        return ticketRepository.findById(ticketId);
    }

    @Override
    public Optional<Ticket> findRecoverableTicket(String userId) {
        return ticketRepository.findFirstByUserIdAndStatusInOrderByUpdatedAtDesc(
                userId,
                List.of(TicketStatus.OPEN, TicketStatus.ESCALATED));
    }

    @Override
    public Ticket saveSelectedItems(Ticket ticket, List<OrderItem> selectedItems) {
        ticket.getItems().clear();
        for (OrderItem selectedItem : selectedItems) {
            TicketItem ticketItem = new TicketItem();
            ticketItem.setItemId(selectedItem.getItemId());
            ticketItem.setItemName(selectedItem.getName());
            ticketItem.setPrice(selectedItem.getPrice());
            ticket.addItem(ticketItem);
        }
        ticket.setState(ConversationState.ASK_ISSUE);
        return ticketRepository.save(ticket);
    }

    @Override
    public Ticket updateIssue(Ticket ticket, IssueType issueType) {
        ticket.setIssueType(issueType);
        ticket.setState(ConversationState.PROCESS_REFUND);
        return ticketRepository.save(ticket);
    }

    @Override
    public Ticket updateFinalStatus(Ticket ticket, ConversationState state, TicketStatus status, BigDecimal refundAmount) {
        ticket.setState(state);
        ticket.setStatus(status);
        ticket.setRefundAmount(refundAmount);
        return ticketRepository.save(ticket);
    }
}
