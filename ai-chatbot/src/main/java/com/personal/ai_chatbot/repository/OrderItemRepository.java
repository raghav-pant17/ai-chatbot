package com.personal.ai_chatbot.repository;

import com.personal.ai_chatbot.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, String> {
}
