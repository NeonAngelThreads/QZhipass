package org.microsoft.qintelipass.repository;

import org.microsoft.qintelipass.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /** 按手机号查询用户（含已注销的） */
    Optional<User> findByPhone(String phone);

    /** 按手机号查询正常状态用户 */
    Optional<User> findByPhoneAndStatus(String phone, User.AccountStatus status);

    /** 判断手机号是否已存在 */
    boolean existsByPhone(String phone);
}
