package com.personal.ai_chatbot.controller;

import com.personal.ai_chatbot.dto.BudgetResponse;
import com.personal.ai_chatbot.dto.CreateOrderRequest;
import com.personal.ai_chatbot.dto.CreateOrderResponse;
import com.personal.ai_chatbot.dto.OrderDetailResponse;
import com.personal.ai_chatbot.dto.OrderSummaryResponse;
import com.personal.ai_chatbot.service.OrderQueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @GetMapping("/budget")
    public BudgetResponse findBudget(@PathVariable String userId) {
        return orderQueryService.findBudget(userId);
    }

    @PostMapping
    public CreateOrderResponse createOrder(@PathVariable String userId, @Valid @RequestBody CreateOrderRequest request) {
        return orderQueryService.createOrder(userId, request);
    }

    @GetMapping("/{orderId}")
    public OrderDetailResponse findOrderDetail(@PathVariable String userId, @PathVariable String orderId) {
        return orderQueryService.findOrderDetail(userId, orderId);
    }
}
