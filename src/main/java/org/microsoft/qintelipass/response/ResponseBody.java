package org.microsoft.qintelipass.response;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class ResponseBody {
    public ResponseBody(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
    private boolean success;
    private String message;
}
