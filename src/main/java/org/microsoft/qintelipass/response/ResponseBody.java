package org.microsoft.qintelipass.response;

import lombok.Data;
import lombok.ToString;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@Data
@ToString
public class ResponseBody {
    public ResponseBody(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
    private boolean success;
    private String message;

    /** 简单场景：Key-Value 用户数据 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, String> data;

    /** 复杂场景：任意类型的聚合数据（仪表盘、列表等） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Object rawData;

    public void setRawData(Object rawData) {
        this.rawData = rawData;
    }
}
