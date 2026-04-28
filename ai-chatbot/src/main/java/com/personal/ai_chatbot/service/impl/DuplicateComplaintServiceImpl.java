package com.personal.ai_chatbot.service.impl;

import com.personal.ai_chatbot.entity.Ticket;
import com.personal.ai_chatbot.repository.TicketRepository;
import com.personal.ai_chatbot.service.DuplicateComplaintService;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DuplicateComplaintServiceImpl implements DuplicateComplaintService {

    private final TicketRepository ticketRepository;

    public DuplicateComplaintServiceImpl(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @Override
    public Optional<Ticket> findDuplicate(Ticket ticket) {
        Set<String> currentItemIds = itemIds(ticket);
        return ticketRepository.findByUserIdAndOrderIdAndIssueType(
                        ticket.getUserId(),
                        ticket.getOrderId(),
                        ticket.getIssueType())
                .stream()
                .filter(existing -> !existing.getTicketId().equals(ticket.getTicketId()))
                .filter(existing -> itemIds(existing).equals(currentItemIds))
                .findFirst();
    }

    private Set<String> itemIds(Ticket ticket) {
        return ticket.getItems().stream()
                .map(item -> item.getItemId())
                .collect(Collectors.toSet());
    }
}
