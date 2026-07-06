package org.microsoft.qintelipass.repository;

import org.microsoft.qintelipass.entity.CensorKeyword;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CensorKeywordRepository extends JpaRepository<CensorKeyword, Long> {

    List<CensorKeyword> findByEnabledTrue();
    List<CensorKeyword> findAllByOrderByCreatedAtDesc();
}