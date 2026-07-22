package org.microsoft.qintelipass.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateAgentRequest {
    @NotBlank(message = "Agent名称不能为空")
    @Size(max = 20, message = "Agent名称长度不能超过20个字符")
    @Pattern(regexp = "^[\\u4e00-\\u9fa5a-zA-Z0-9_\\-]+$", message = "不得使用特殊字符")
    private String name;

    @NotBlank(message = "提示词不能为空")
    @Size(max = 20_000, message = "提示词长度不能超过20000个字符")
    private String prompt;
}
