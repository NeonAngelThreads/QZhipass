package org.microsoft.qintelipass.repository;

import org.microsoft.qintelipass.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByPhone(String phone);
    Optional<User> findByName(String name);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
}