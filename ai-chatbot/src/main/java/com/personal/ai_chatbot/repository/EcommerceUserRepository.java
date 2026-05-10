package com.personal.ai_chatbot.repository;

import com.personal.ai_chatbot.entity.EcommerceUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EcommerceUserRepository extends JpaRepository<EcommerceUser, String> {

    Optional<EcommerceUser> findByUsername(String username);

    Optional<EcommerceUser> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
