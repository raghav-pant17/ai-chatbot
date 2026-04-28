package com.personal.ai_chatbot.service;

import com.personal.ai_chatbot.entity.Ticket;

import java.util.Optional;

public interface DuplicateComplaintService {

    Optional<Ticket> findDuplicate(Ticket ticket);
}
