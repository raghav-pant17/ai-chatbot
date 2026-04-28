package com.personal.ai_chatbot.repository;

import com.personal.ai_chatbot.entity.Ticket;
import com.personal.ai_chatbot.enums.IssueType;
import com.personal.ai_chatbot.enums.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findFirstByUserIdAndStatusInOrderByUpdatedAtDesc(String userId, Collection<TicketStatus> statuses);

    Optional<Ticket> findByTicketIdAndUserId(Long ticketId, String userId);

    List<Ticket> findByUserIdAndOrderIdAndIssueType(String userId, String orderId, IssueType issueType);

    List<Ticket> findByUserIdOrderByUpdatedAtDesc(String userId);

    List<Ticket> findByStatusOrderByUpdatedAtDesc(TicketStatus status);

    List<Ticket> findByUserIdAndOrderIdAndStatusOrderByUpdatedAtDesc(String userId, String orderId, TicketStatus status);

    @Query("""
            select coalesce(sum(t.refundAmount), 0)
            from Ticket t
            where t.userId = :userId
              and t.orderId = :orderId
              and t.status = :status
            """)
    BigDecimal sumRefundAmountByUserIdAndOrderIdAndStatus(
            @Param("userId") String userId,
            @Param("orderId") String orderId,
            @Param("status") TicketStatus status);
}
