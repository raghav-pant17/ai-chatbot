package com.personal.ai_chatbot.service.impl;

import com.personal.ai_chatbot.entity.Ticket;
import com.personal.ai_chatbot.service.RefundService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class RefundServiceImpl implements RefundService {

    @Override
    public BigDecimal calculateRefund(Ticket ticket) {
        // Refunds are deterministic: AI is never allowed to calculate money.
        return ticket.getItems().stream()
                .map(item -> item.getPrice() == null ? BigDecimal.ZERO : item.getPrice())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
