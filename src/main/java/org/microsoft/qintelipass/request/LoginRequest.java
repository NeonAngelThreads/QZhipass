package org.microsoft.qintelipass.request;

import lombok.Data;

import java.util.Collections;
import java.util.Map;

@Data
public class LoginRequest {
    private String loginType;
    private Map<String, Object> params;
    private Map<String, Object> credential;

    public Map<String, Object> effectiveParams() {
        if (params != null) {
            return params;
        }
        if (credential != null) {
            return credential;
        }
        return Collections.emptyMap();
    }
}
