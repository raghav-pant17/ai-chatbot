package com.personal.ai_chatbot.controller;

import com.personal.ai_chatbot.dto.OrderDetailResponse;
import com.personal.ai_chatbot.dto.OrderSummaryResponse;
import com.personal.ai_chatbot.service.OrderQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/orders")
public class OrderController {

    private final OrderQueryService orderQueryService;

    public OrderController(OrderQueryService orderQueryService) {
        this.orderQueryService = orderQueryService;
    }

    @GetMapping
    public List<OrderSummaryResponse> findOrders(@PathVariable String userId) {
        return orderQueryService.findOrders(userId);
    }

    @GetMapping("/{orderId}")
    public OrderDetailResponse findOrderDetail(@PathVariable String userId, @PathVariable String orderId) {
        return orderQueryService.findOrderDetail(userId, orderId);
    }
}
