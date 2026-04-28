package com.personal.ai_chatbot.dto;

import com.personal.ai_chatbot.enums.ConversationState;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class UserSession implements Serializable {

    private String userId;
    private Long currentTicketId;
    private ConversationState state = ConversationState.START;
    private List<String> selectedItemIds = new ArrayList<>();
    private int retryCount;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Long getCurrentTicketId() {
        return currentTicketId;
    }

    public void setCurrentTicketId(Long currentTicketId) {
        this.currentTicketId = currentTicketId;
    }

    public ConversationState getState() {
        return state;
    }

    public void setState(ConversationState state) {
        this.state = state;
    }

    public List<String> getSelectedItemIds() {
        return selectedItemIds;
    }

    public void setSelectedItemIds(List<String> selectedItemIds) {
        this.selectedItemIds = selectedItemIds;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public void increaseRetryCount() {
        retryCount++;
    }
}
