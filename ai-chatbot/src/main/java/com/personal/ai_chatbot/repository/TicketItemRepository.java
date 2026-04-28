package com.personal.ai_chatbot.repository;

import com.personal.ai_chatbot.entity.TicketItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketItemRepository extends JpaRepository<TicketItem, Long> {
}
