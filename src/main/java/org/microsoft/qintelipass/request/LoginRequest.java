package org.microsoft.qintelipass.request;

import lombok.Data;

import java.util.Map;

@Data
public class LoginRequest {
    private String loginType;
    private Map<String, Object> credential;
}
