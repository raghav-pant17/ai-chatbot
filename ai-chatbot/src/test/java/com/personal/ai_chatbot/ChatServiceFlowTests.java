package com.personal.ai_chatbot;

import com.personal.ai_chatbot.dto.ChatMessageRequest;
import com.personal.ai_chatbot.dto.ChatMessageResponse;
import com.personal.ai_chatbot.enums.ConversationState;
import com.personal.ai_chatbot.enums.TicketStatus;
import com.personal.ai_chatbot.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "chatbot.ai.openrouter.api-key=")
class ChatServiceFlowTests {

    private final ChatService chatService;

    @Autowired
    ChatServiceFlowTests(ChatService chatService) {
        this.chatService = chatService;
    }

    @Test
    void resolvesRefundForSelectedItems() {
        ChatMessageResponse start = chatService.handleMessage(new ChatMessageRequest("user-1", "I have a refund issue"));
        assertThat(start.state()).isEqualTo(ConversationState.ASK_ORDER_ID);

        ChatMessageResponse order = chatService.handleMessage(new ChatMessageRequest("user-1", "my order is ORD-1001"));
        assertThat(order.state()).isEqualTo(ConversationState.SELECT_ITEMS);

        ChatMessageResponse selection = chatService.handleMessage(new ChatMessageRequest("user-1", "1,2"));
        assertThat(selection.state()).isEqualTo(ConversationState.ASK_ISSUE);

        ChatMessageResponse issue = chatService.handleMessage(new ChatMessageRequest("user-1", "damaged"));
        assertThat(issue.status()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(issue.message()).contains("Rs.3000");
    }

    @Test
    void escalatesHumanRequestDuringItemSelection() {
        ChatMessageResponse start = chatService.handleMessage(new ChatMessageRequest("user-2", "I have a refund issue"));
        assertThat(start.state()).isEqualTo(ConversationState.ASK_ORDER_ID);

        ChatMessageResponse order = chatService.handleMessage(new ChatMessageRequest("user-2", "ORD-2001"));
        assertThat(order.state()).isEqualTo(ConversationState.SELECT_ITEMS);

        ChatMessageResponse escalation = chatService.handleMessage(new ChatMessageRequest("user-2", "I want to talk to human"));
        assertThat(escalation.state()).isEqualTo(ConversationState.ESCALATED);
        assertThat(escalation.status()).isEqualTo(TicketStatus.ESCALATED);
        assertThat(escalation.message()).contains("escalating this to a support agent");
    }

    @Test
    void escalatesHumanRequestBeforeOrderIdIsProvided() {
        ChatMessageResponse start = chatService.handleMessage(new ChatMessageRequest("user-3", "I have a refund issue"));
        assertThat(start.state()).isEqualTo(ConversationState.ASK_ORDER_ID);

        ChatMessageResponse escalation = chatService.handleMessage(new ChatMessageRequest("user-3", "I want to talk to human"));
        assertThat(escalation.ticketId()).isNotNull();
        assertThat(escalation.orderId()).isNull();
        assertThat(escalation.state()).isEqualTo(ConversationState.ESCALATED);
        assertThat(escalation.status()).isEqualTo(TicketStatus.ESCALATED);
        assertThat(escalation.message()).contains("escalating this to a support agent");
    }
}
