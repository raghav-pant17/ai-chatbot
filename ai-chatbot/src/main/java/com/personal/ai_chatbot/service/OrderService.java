package com.personal.ai_chatbot.service;

import com.personal.ai_chatbot.entity.CustomerOrder;
import com.personal.ai_chatbot.entity.OrderItem;

import java.util.List;
import java.util.Optional;

public interface OrderService {

    Optional<CustomerOrder> findUserOrder(String orderId, String userId);

    List<OrderItem> selectItems(CustomerOrder order, String selection);

    String buildItemSelectionText(CustomerOrder order);
}
