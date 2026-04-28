package com.personal.ai_chatbot.repository;

import com.personal.ai_chatbot.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findTop5ByUserIdOrderByTimestampDesc(String userId);

    List<ChatMessage> findByTicketIdOrderByTimestampAsc(Long ticketId);
}
