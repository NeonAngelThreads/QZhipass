package org.microsoft.qintelipass.response;

import java.util.List;

/** Full audit view. This response is never returned by a user-facing endpoint. */
public record AdminConversationDetailResponse(
        AdminConversationSummaryResponse conversation,
        List<ConversationMessageResponse> messages
) {
}
