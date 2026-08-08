package org.microsoft.qintelipass.enums;

import lombok.Getter;

@Getter
public enum UserRole {
    USER("USER"),
    ADMIN("ADMIN");

    UserRole(String name) {
        this.name = name;
    }

    private final String name;
}