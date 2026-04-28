package com.personal.ai_chatbot.service;

import com.personal.ai_chatbot.dto.AIEscalationResult;
import com.personal.ai_chatbot.entity.ChatMessage;
import com.personal.ai_chatbot.enums.ConversationState;
import com.personal.ai_chatbot.enums.IssueType;

import java.util.List;
import java.util.Optional;

public interface AIService {

    AIEscalationResult analyzeEscalation(List<ChatMessage> recentMessages, ConversationState state, String latestUserMessage);

    boolean isComplaintIntent(String message);

    Optional<String> extractOrderId(String message);

    Optional<IssueType> normalizeIssueType(String message);
}
