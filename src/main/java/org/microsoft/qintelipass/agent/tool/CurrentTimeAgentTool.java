package org.microsoft.qintelipass.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
public class CurrentTimeAgentTool implements AgentTool {
    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "current_time",
            "Return the current date and time for an IANA time-zone name.",
            """
                    {"type":"object","properties":{"timezone":{"type":"string","maxLength":64}},
                    "required":["timezone"],"additionalProperties":false}
                    """.replace("\n", ""),
            Duration.ofSeconds(2),
            false
    );

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolExecutionResult execute(JsonNode arguments) {
        if (arguments == null || !arguments.isObject()
                || !arguments.hasNonNull("timezone")
                || !arguments.get("timezone").isTextual()
                || arguments.size() != 1) {
            return ToolExecutionResult.error(DEFINITION.name(), "INVALID_ARGUMENTS");
        }
        String timezone = arguments.get("timezone").asText().trim();
        if (timezone.isEmpty() || timezone.length() > 64) {
            return ToolExecutionResult.error(DEFINITION.name(), "INVALID_ARGUMENTS");
        }
        try {
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of(timezone));
            return ToolExecutionResult.success(DEFINITION.name(), Map.of(
                    "timezone", timezone,
                    "dateTime", now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            ));
        } catch (DateTimeException exception) {
            return ToolExecutionResult.error(DEFINITION.name(), "INVALID_TIMEZONE");
        }
    }
}
