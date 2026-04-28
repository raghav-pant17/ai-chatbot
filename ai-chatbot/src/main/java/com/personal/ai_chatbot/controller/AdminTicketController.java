package com.personal.ai_chatbot.controller;

import com.personal.ai_chatbot.dto.AdminTicketActionResponse;
import com.personal.ai_chatbot.dto.AdminTicketDetailResponse;
import com.personal.ai_chatbot.dto.AdminTicketReplyRequest;
import com.personal.ai_chatbot.dto.AdminTicketRefundRequest;
import com.personal.ai_chatbot.dto.AdminTicketSummaryResponse;
import com.personal.ai_chatbot.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/tickets")
public class AdminTicketController {

    private final AdminService adminService;

    public AdminTicketController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/escalated")
    public List<AdminTicketSummaryResponse> findEscalatedTickets() {
        return adminService.findEscalatedTickets();
    }

    @GetMapping("/{ticketId}")
    public AdminTicketDetailResponse findTicketDetail(@PathVariable Long ticketId) {
        return adminService.findTicketDetail(ticketId);
    }

    @PostMapping("/{ticketId}/reply")
    public AdminTicketActionResponse replyToTicket(
            @PathVariable Long ticketId,
            @Valid @RequestBody AdminTicketReplyRequest request) {
        return adminService.replyToTicket(ticketId, request);
    }

    @PostMapping("/{ticketId}/refund")
    public AdminTicketActionResponse refundTicket(
            @PathVariable Long ticketId,
            @Valid @RequestBody AdminTicketRefundRequest request) {
        return adminService.refundTicket(ticketId, request);
    }

    @PostMapping("/{ticketId}/resolve")
    public AdminTicketActionResponse resolveTicket(@PathVariable Long ticketId) {
        return adminService.resolveTicket(ticketId);
    }
}
