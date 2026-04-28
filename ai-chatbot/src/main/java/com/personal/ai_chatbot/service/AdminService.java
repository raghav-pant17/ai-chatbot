package com.personal.ai_chatbot.service;

import com.personal.ai_chatbot.dto.AdminLoginRequest;
import com.personal.ai_chatbot.dto.AdminLoginResponse;
import com.personal.ai_chatbot.dto.AdminTicketActionResponse;
import com.personal.ai_chatbot.dto.AdminTicketDetailResponse;
import com.personal.ai_chatbot.dto.AdminTicketReplyRequest;
import com.personal.ai_chatbot.dto.AdminTicketRefundRequest;
import com.personal.ai_chatbot.dto.AdminTicketSummaryResponse;

import java.util.List;

public interface AdminService {

    AdminLoginResponse login(AdminLoginRequest request);

    List<AdminTicketSummaryResponse> findEscalatedTickets();

    AdminTicketDetailResponse findTicketDetail(Long ticketId);

    AdminTicketActionResponse replyToTicket(Long ticketId, AdminTicketReplyRequest request);

    AdminTicketActionResponse refundTicket(Long ticketId, AdminTicketRefundRequest request);

    AdminTicketActionResponse resolveTicket(Long ticketId);
}
