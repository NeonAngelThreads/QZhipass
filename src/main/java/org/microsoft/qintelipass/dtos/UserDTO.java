package org.microsoft.qintelipass.dtos;

import lombok.Data;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.models.User;

@Data
public class UserDTO {
    private Long id;
    private String phone;
    private String wechatOpenId;
    private UserStatus status;
    private String name;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final User User;
        public Builder() {
            User = new User();
        }

        public Builder userId(Long itemId) { this.User.setId(itemId); return this; }
        public Builder phone(String phone) { this.User.setPhone(phone); return this; }
        public Builder wechatOpenId(String wechatOpenId) { this.User.setWechatOpenId(wechatOpenId); return this; }
        public Builder status(UserStatus status) { this.User.setStatus(status); return this; }
        public Builder name(String name) { this.User.setName(name); return this; }

        public User build() {
            return User;
        }
    }
}
