package com.personal.ai_chatbot.service;

import com.personal.ai_chatbot.dto.OrderDetailResponse;
import com.personal.ai_chatbot.dto.OrderSummaryResponse;

import java.util.List;

public interface OrderQueryService {

    List<OrderSummaryResponse> findOrders(String userId);

    OrderDetailResponse findOrderDetail(String userId, String orderId);
}
