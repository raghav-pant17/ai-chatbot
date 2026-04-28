package com.personal.ai_chatbot.service;

import com.personal.ai_chatbot.dto.ChatMessageRequest;
import com.personal.ai_chatbot.dto.ChatMessageResponse;

public interface ChatService {

    ChatMessageResponse handleMessage(ChatMessageRequest request);
}
