package org.microsoft.qintelipass.enums;

import lombok.Getter;

@Getter
public enum EnumTranslatableMessage {
    USER_LOGIN_SUCCESSFUL("user.success.login"),
    USER_LOGOUT_SUCCESSFUL("user.success.logout"),
    USER_REGISTER_SUCCESSFUL("user.success.register"),
    USER_LOGIN_WRONG("user.fail.login"),
    USER_LOGIN_EMAIL_NOTFOUND("user.notfound.login"),
    USER_LOGIN_BANNED("user.banned"),
    USER_INVALID_PHONE("user.fail.login"),
    ;
    private final String translationKey;
    EnumTranslatableMessage(String translationKey) {
        this.translationKey = translationKey;
    }
}
