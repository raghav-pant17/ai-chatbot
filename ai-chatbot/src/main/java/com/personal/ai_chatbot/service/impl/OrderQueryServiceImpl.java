package com.personal.ai_chatbot.service.impl;

import com.personal.ai_chatbot.dto.OrderDetailResponse;
import com.personal.ai_chatbot.dto.OrderItemResponse;
import com.personal.ai_chatbot.dto.OrderRefundItemResponse;
import com.personal.ai_chatbot.dto.OrderSummaryResponse;
import com.personal.ai_chatbot.entity.CustomerOrder;
import com.personal.ai_chatbot.entity.OrderItem;
import com.personal.ai_chatbot.entity.Ticket;
import com.personal.ai_chatbot.entity.TicketItem;
import com.personal.ai_chatbot.enums.TicketStatus;
import com.personal.ai_chatbot.repository.CustomerOrderRepository;
import com.personal.ai_chatbot.repository.EcommerceUserRepository;
import com.personal.ai_chatbot.repository.TicketRepository;
import com.personal.ai_chatbot.service.OrderQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderQueryServiceImpl implements OrderQueryService {

    private final CustomerOrderRepository customerOrderRepository;
    private final EcommerceUserRepository ecommerceUserRepository;
    private final TicketRepository ticketRepository;

    public OrderQueryServiceImpl(
            CustomerOrderRepository customerOrderRepository,
            EcommerceUserRepository ecommerceUserRepository,
            TicketRepository ticketRepository) {
        this.customerOrderRepository = customerOrderRepository;
        this.ecommerceUserRepository = ecommerceUserRepository;
        this.ticketRepository = ticketRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> findOrders(String userId) {
        validateUser(userId);
        return customerOrderRepository.findByUserIdOrderByOrderTimeDesc(userId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDetailResponse findOrderDetail(String userId, String orderId) {
        validateUser(userId);
        CustomerOrder order = customerOrderRepository.findByOrderIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found for this user."));

        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> new OrderItemResponse(item.getItemId(), item.getName(), item.getPrice()))
                .toList();
        BigDecimal refundAmount = ticketRepository.sumRefundAmountByUserIdAndOrderIdAndStatus(
                order.getUserId(),
                order.getOrderId(),
                TicketStatus.RESOLVED);
        List<OrderRefundItemResponse> refundItems = findRefundItems(order);

        return new OrderDetailResponse(
                order.getOrderId(),
                order.getUserId(),
                order.getTotalAmount(),
                refundAmount == null ? BigDecimal.ZERO : refundAmount,
                refundItems,
                order.getOrderTime(),
                items);
    }

    private List<OrderRefundItemResponse> findRefundItems(CustomerOrder order) {
        Map<String, Integer> itemNumbersById = new LinkedHashMap<>();
        List<OrderItem> orderItems = order.getItems();
        for (int i = 0; i < orderItems.size(); i++) {
            itemNumbersById.put(orderItems.get(i).getItemId(), i + 1);
        }

        Map<String, OrderRefundItemResponse> refundItemsById = new LinkedHashMap<>();
        List<Ticket> refundedTickets = ticketRepository.findByUserIdAndOrderIdAndStatusOrderByUpdatedAtDesc(
                order.getUserId(),
                order.getOrderId(),
                TicketStatus.RESOLVED);

        for (Ticket ticket : refundedTickets) {
            if (ticket.getRefundAmount() == null || ticket.getRefundAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            for (TicketItem item : ticket.getItems()) {
                refundItemsById.putIfAbsent(
                        item.getItemId(),
                        new OrderRefundItemResponse(
                                itemNumbersById.get(item.getItemId()),
                                item.getItemId(),
                                item.getItemName()));
            }
        }

        return refundItemsById.values().stream().toList();
    }

    private OrderSummaryResponse toSummary(CustomerOrder order) {
        return new OrderSummaryResponse(order.getOrderId(), order.getTotalAmount(), order.getOrderTime(), order.getItems().size());
    }

    private void validateUser(String userId) {
        if (!ecommerceUserRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User does not exist on the e-commerce platform.");
        }
    }
}
