package com.personal.ai_chatbot.repository;

import com.personal.ai_chatbot.entity.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminUserRepository extends JpaRepository<AdminUser, String> {
}
