package org.microsoft.qintelipass.repository;

import org.microsoft.qintelipass.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * JPA 自动生成的用户仓库接口
 * 继承 JpaRepository，自动实现 CRUD 和分页查询
 */
public interface UserJpaRepository extends JpaRepository<User, Long> {

    Optional<User> findByPhone(String phone);

    Optional<User> findByWechat(String wechat);

    boolean existsByPhone(String phone);
}
