package com.personal.ai_chatbot.service;

import com.personal.ai_chatbot.dto.BudgetResponse;
import com.personal.ai_chatbot.dto.CreateOrderRequest;
import com.personal.ai_chatbot.dto.CreateOrderResponse;
import com.personal.ai_chatbot.dto.OrderDetailResponse;
import com.personal.ai_chatbot.dto.OrderSummaryResponse;

import java.util.List;

public interface OrderQueryService {

    List<OrderSummaryResponse> findOrders(String userId);

    OrderDetailResponse findOrderDetail(String userId, String orderId);

    BudgetResponse findBudget(String userId);

    CreateOrderResponse createOrder(String userId, CreateOrderRequest request);
}
