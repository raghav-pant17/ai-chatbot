package com.personal.ai_chatbot.service;

import com.personal.ai_chatbot.entity.Ticket;

import java.math.BigDecimal;

public interface RefundService {

    BigDecimal calculateRefund(Ticket ticket);
}
