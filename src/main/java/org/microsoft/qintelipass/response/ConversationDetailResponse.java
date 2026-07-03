package org.microsoft.qintelipass.response;

import java.util.List;

public record ConversationDetailResponse(
        ConversationResponse conversation,
        List<ConversationMessageResponse> messages,
        ModelResponse model
) {
}
