package org.microsoft.qintelipass.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.util.Map;

@Component
public class CalculatorAgentTool implements AgentTool {
    private static final int MAX_EXPRESSION_LENGTH = 200;
    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "calculator",
            "Evaluate an arithmetic expression containing numbers, parentheses, +, -, *, and /.",
            """
                    {"type":"object","properties":{"expression":{"type":"string","maxLength":200}},
                    "required":["expression"],"additionalProperties":false}
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
                || !arguments.hasNonNull("expression")
                || !arguments.get("expression").isTextual()
                || arguments.size() != 1) {
            return ToolExecutionResult.error(DEFINITION.name(), "INVALID_ARGUMENTS");
        }
        String expression = arguments.get("expression").asText().trim();
        if (expression.isEmpty() || expression.length() > MAX_EXPRESSION_LENGTH) {
            return ToolExecutionResult.error(DEFINITION.name(), "INVALID_ARGUMENTS");
        }
        try {
            BigDecimal value = new ExpressionParser(expression).parse();
            return ToolExecutionResult.success(DEFINITION.name(), Map.of(
                    "expression", expression,
                    "result", value.stripTrailingZeros().toPlainString()
            ));
        } catch (IllegalArgumentException exception) {
            return ToolExecutionResult.error(DEFINITION.name(), "INVALID_EXPRESSION");
        }
    }

    private static final class ExpressionParser {
        private final String input;
        private int position;

        private ExpressionParser(String input) {
            this.input = input;
        }

        private BigDecimal parse() {
            BigDecimal value = expression();
            skipWhitespace();
            if (position != input.length()) {
                throw new IllegalArgumentException("Unexpected token.");
            }
            return value;
        }

        private BigDecimal expression() {
            BigDecimal value = term();
            while (true) {
                skipWhitespace();
                if (consume('+')) {
                    value = value.add(term());
                } else if (consume('-')) {
                    value = value.subtract(term());
                } else {
                    return value;
                }
            }
        }

        private BigDecimal term() {
            BigDecimal value = factor();
            while (true) {
                skipWhitespace();
                if (consume('*')) {
                    value = value.multiply(factor(), MathContext.DECIMAL128);
                } else if (consume('/')) {
                    BigDecimal divisor = factor();
                    if (BigDecimal.ZERO.compareTo(divisor) == 0) {
                        throw new IllegalArgumentException("Division by zero.");
                    }
                    value = value.divide(divisor, MathContext.DECIMAL128);
                } else {
                    return value;
                }
            }
        }

        private BigDecimal factor() {
            skipWhitespace();
            if (consume('+')) {
                return factor();
            }
            if (consume('-')) {
                return factor().negate();
            }
            if (consume('(')) {
                BigDecimal value = expression();
                skipWhitespace();
                if (!consume(')')) {
                    throw new IllegalArgumentException("Missing closing parenthesis.");
                }
                return value;
            }
            return number();
        }

        private BigDecimal number() {
            skipWhitespace();
            int start = position;
            boolean dotSeen = false;
            while (position < input.length()) {
                char current = input.charAt(position);
                if (Character.isDigit(current)) {
                    position++;
                } else if (current == '.' && !dotSeen) {
                    dotSeen = true;
                    position++;
                } else {
                    break;
                }
            }
            if (start == position || ".".equals(input.substring(start, position))) {
                throw new IllegalArgumentException("Number expected.");
            }
            try {
                return new BigDecimal(input.substring(start, position), MathContext.DECIMAL128);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid number.", exception);
            }
        }

        private boolean consume(char expected) {
            if (position < input.length() && input.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (position < input.length() && Character.isWhitespace(input.charAt(position))) {
                position++;
            }
        }
    }
}
