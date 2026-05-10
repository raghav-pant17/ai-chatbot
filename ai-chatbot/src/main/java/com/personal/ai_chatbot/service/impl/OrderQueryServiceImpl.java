package com.personal.ai_chatbot.service.impl;

import com.personal.ai_chatbot.dto.BudgetResponse;
import com.personal.ai_chatbot.dto.CreateOrderRequest;
import com.personal.ai_chatbot.dto.CreateOrderResponse;
import com.personal.ai_chatbot.dto.OrderLineRequest;
import com.personal.ai_chatbot.dto.OrderDetailResponse;
import com.personal.ai_chatbot.dto.OrderItemResponse;
import com.personal.ai_chatbot.dto.OrderRefundItemResponse;
import com.personal.ai_chatbot.dto.OrderSummaryResponse;
import com.personal.ai_chatbot.dto.ProductResponse;
import com.personal.ai_chatbot.entity.CustomerOrder;
import com.personal.ai_chatbot.entity.EcommerceUser;
import com.personal.ai_chatbot.entity.OrderItem;
import com.personal.ai_chatbot.entity.Ticket;
import com.personal.ai_chatbot.entity.TicketItem;
import com.personal.ai_chatbot.enums.TicketStatus;
import com.personal.ai_chatbot.repository.CustomerOrderRepository;
import com.personal.ai_chatbot.repository.EcommerceUserRepository;
import com.personal.ai_chatbot.repository.TicketRepository;
import com.personal.ai_chatbot.service.OrderQueryService;
import com.personal.ai_chatbot.service.ProductCatalogService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderQueryServiceImpl implements OrderQueryService {

    private final CustomerOrderRepository customerOrderRepository;
    private final EcommerceUserRepository ecommerceUserRepository;
    private final TicketRepository ticketRepository;
    private final ProductCatalogService productCatalogService;

    public OrderQueryServiceImpl(
            CustomerOrderRepository customerOrderRepository,
            EcommerceUserRepository ecommerceUserRepository,
            TicketRepository ticketRepository,
            ProductCatalogService productCatalogService) {
        this.customerOrderRepository = customerOrderRepository;
        this.ecommerceUserRepository = ecommerceUserRepository;
        this.ticketRepository = ticketRepository;
        this.productCatalogService = productCatalogService;
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

        return toDetail(order);
    }

    @Override
    @Transactional(readOnly = true)
    public BudgetResponse findBudget(String userId) {
        EcommerceUser user = findUser(userId);
        return buildBudget(user);
    }

    @Override
    @Transactional
    public CreateOrderResponse createOrder(String userId, CreateOrderRequest request) {
        EcommerceUser user = findUser(userId);
        BudgetResponse currentBudget = buildBudget(user);

        CustomerOrder order = new CustomerOrder();
        order.setOrderId("ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setUserId(userId);
        order.setOrderTime(Instant.now());

        BigDecimal total = BigDecimal.ZERO;
        int itemNumber = 1;
        for (OrderLineRequest line : request.items()) {
            ProductResponse product = productCatalogService.findProduct(line.productId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown product: " + line.productId()));

            for (int quantity = 0; quantity < line.quantity(); quantity++) {
                OrderItem item = new OrderItem();
                item.setItemId(order.getOrderId() + "-ITEM-" + itemNumber++);
                item.setName(product.name());
                item.setPrice(product.price());
                order.addItem(item);
                total = total.add(product.price());
            }
        }

        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order total must be greater than zero.");
        }
        if (total.compareTo(currentBudget.remainingBudget()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order exceeds remaining shopping budget.");
        }

        order.setTotalAmount(total);
        CustomerOrder savedOrder = customerOrderRepository.save(order);
        return new CreateOrderResponse(toDetail(savedOrder), buildBudget(user));
    }

    private OrderDetailResponse toDetail(CustomerOrder order) {
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
        findUser(userId);
    }

    private EcommerceUser findUser(String userId) {
        return ecommerceUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User does not exist on the e-commerce platform."));
    }

    private BudgetResponse buildBudget(EcommerceUser user) {
        BigDecimal shoppingBudget = user.getShoppingBudget() == null ? new BigDecimal("15000.00") : user.getShoppingBudget();
        BigDecimal spentAmount = customerOrderRepository.sumTotalAmountByUserId(user.getUserId());
        if (spentAmount == null) {
            spentAmount = BigDecimal.ZERO;
        }
        return new BudgetResponse(shoppingBudget, spentAmount, shoppingBudget.subtract(spentAmount).max(BigDecimal.ZERO));
    }
}
