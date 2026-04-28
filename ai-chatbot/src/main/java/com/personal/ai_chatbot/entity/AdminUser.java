package com.personal.ai_chatbot.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "admin_users")
public class AdminUser {

    @Id
    private String adminId;

    private String fullName;
    private String passwordSalt;
    private String passwordHash;

    public String getAdminId() {
        return adminId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPasswordSalt() {
        return passwordSalt;
    }

    public String getPasswordHash() {
        return passwordHash;
    }
}
