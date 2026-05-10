package com.personal.ai_chatbot.repository;

import com.personal.ai_chatbot.entity.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, String> {

    Optional<CustomerOrder> findByOrderIdAndUserId(String orderId, String userId);

    List<CustomerOrder> findByUserIdOrderByOrderTimeDesc(String userId);

    @Query("select coalesce(sum(o.totalAmount), 0) from CustomerOrder o where o.userId = :userId")
    BigDecimal sumTotalAmountByUserId(String userId);
}
