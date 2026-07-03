package org.microsoft.qintelipass.repository;

import org.microsoft.qintelipass.models.Models;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ModelsRepository extends JpaRepository<Models, Long> {
    Optional<Models> findByModelName(String modelName);
}