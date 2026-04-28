package com.personal.ai_chatbot.service.impl;

import com.personal.ai_chatbot.entity.CustomerOrder;
import com.personal.ai_chatbot.entity.OrderItem;
import com.personal.ai_chatbot.repository.CustomerOrderRepository;
import com.personal.ai_chatbot.service.OrderService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class OrderServiceImpl implements OrderService {

    private final CustomerOrderRepository customerOrderRepository;

    public OrderServiceImpl(CustomerOrderRepository customerOrderRepository) {
        this.customerOrderRepository = customerOrderRepository;
    }

    @Override
    public Optional<CustomerOrder> findUserOrder(String orderId, String userId) {
        return customerOrderRepository.findByOrderIdAndUserId(orderId, userId);
    }

    @Override
    public List<OrderItem> selectItems(CustomerOrder order, String selection) {
        String normalizedSelection = selection.trim().toLowerCase();
        if ("all".equals(normalizedSelection)) {
            return order.getItems();
        }

        Set<Integer> selectedPositions = new LinkedHashSet<>();
        for (String token : normalizedSelection.split(",")) {
            try {
                selectedPositions.add(Integer.parseInt(token.trim()));
            } catch (NumberFormatException ex) {
                return List.of();
            }
        }

        List<OrderItem> selectedItems = new ArrayList<>();
        List<OrderItem> orderItems = order.getItems();
        for (Integer position : selectedPositions) {
            int index = position - 1;
            if (index < 0 || index >= orderItems.size()) {
                return List.of();
            }
            selectedItems.add(orderItems.get(index));
        }
        return selectedItems;
    }

    @Override
    public String buildItemSelectionText(CustomerOrder order) {
        StringBuilder builder = new StringBuilder("Your order contains:\n");
        List<OrderItem> items = order.getItems();
        for (int i = 0; i < items.size(); i++) {
            OrderItem item = items.get(i);
            builder.append(i + 1)
                    .append(". ")
                    .append(item.getName())
                    .append(" - Rs.")
                    .append(item.getPrice())
                    .append("\n");
        }
        builder.append("\nReply with item number(s), for example 1,2, or type \"all\".");
        return builder.toString();
    }
}
