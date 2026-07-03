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
    public ResponseBody(boolean success, String message, Object data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }
    private boolean success;
    private String message;
    private Object data;
}
