package org.microsoft.qintelipass.dtos;

import lombok.*;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.util.Snowflake;

import java.time.OffsetDateTime;

@Data
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Long id = Snowflake.nextId();
    private String phone;
    private String email;
    private UserStatus status = UserStatus.NORMAL;
    private String name;
    private String department;
    private OffsetDateTime joinedAt = OffsetDateTime.now();

    public static UserDTO fromUser(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .phone(user.getPhone())
                .email(user.getEmail())
                .status(user.getStatus())
                .name(user.getName())
                .department(user.getDepartment())
                .joinedAt(user.getJoinedAt())
                .build();
    }

    public User toUser() {
        User user = new User();
        user.setId(this.id);
        user.setPhone(this.phone);
        user.setEmail(this.email);
        user.setStatus(this.status);
        user.setName(this.name);
        user.setDepartment(this.department);
        return user;
    }
}
