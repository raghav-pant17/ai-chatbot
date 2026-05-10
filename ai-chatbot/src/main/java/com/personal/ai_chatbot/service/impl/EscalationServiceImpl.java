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
        return refundAmount.compareTo(refundEscalationLimit) > 0
                || session.getRetryCount() > 2
                || (aiResult.escalationRecommended() && aiResult.confidence() >= 0.75);
    }
}
