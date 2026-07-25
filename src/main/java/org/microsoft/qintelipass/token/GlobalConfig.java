package org.microsoft.qintelipass.token;

import jakarta.persistence.*;

/**
 * 全局键值配置表。
 * 当前用于存放"统一 token 限额"：key = global_token_quota。
 */
@Entity
@Table(name = "global_config")
public class GlobalConfig {

    @Id
    @Column(name = "config_key", nullable = false, length = 100)
    private String key;

    @Column(name = "config_value", nullable = false, columnDefinition = "TEXT")
    private String value;

    public GlobalConfig() {}

    public GlobalConfig(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
