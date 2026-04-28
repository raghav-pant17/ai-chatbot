package com.personal.ai_chatbot.service.impl;

import com.personal.ai_chatbot.dto.AIEscalationResult;
import com.personal.ai_chatbot.dto.UserSession;
import com.personal.ai_chatbot.service.EscalationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class EscalationServiceImpl implements EscalationService {

    private final BigDecimal refundEscalationLimit;

    public EscalationServiceImpl(@Value("${chatbot.refund-escalation-limit}") BigDecimal refundEscalationLimit) {
        this.refundEscalationLimit = refundEscalationLimit;
    }

    @Override
    public boolean shouldEscalate(BigDecimal refundAmount, UserSession session, String latestMessage, AIEscalationResult aiResult) {
        String normalizedMessage = latestMessage == null ? "" : latestMessage.toLowerCase();
        return refundAmount.compareTo(refundEscalationLimit) > 0
                || session.getRetryCount() > 2
                || normalizedMessage.contains("human")
                || normalizedMessage.contains("agent")
                || normalizedMessage.contains("manager")
                || normalizedMessage.contains("pissed")
                || normalizedMessage.contains("angry")
                || normalizedMessage.contains("frustrated")
                || (aiResult.escalationRecommended() && aiResult.confidence() >= 0.75);
    }
}
