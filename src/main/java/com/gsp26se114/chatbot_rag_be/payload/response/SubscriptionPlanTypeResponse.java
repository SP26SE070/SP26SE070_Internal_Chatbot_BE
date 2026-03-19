package com.gsp26se114.chatbot_rag_be.payload.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SubscriptionPlanTypeResponse {
    private String code;
    private String defaultName;
}
