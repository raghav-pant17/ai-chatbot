package com.personal.ai_chatbot.service;

import com.personal.ai_chatbot.dto.AIEscalationResult;
import com.personal.ai_chatbot.dto.UserSession;

import java.math.BigDecimal;

public interface EscalationService {

    boolean shouldEscalate(BigDecimal refundAmount, UserSession session, String latestMessage, AIEscalationResult aiResult);
}
