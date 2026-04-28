package com.personal.ai_chatbot.repository;

import com.personal.ai_chatbot.entity.EcommerceUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EcommerceUserRepository extends JpaRepository<EcommerceUser, String> {
}
