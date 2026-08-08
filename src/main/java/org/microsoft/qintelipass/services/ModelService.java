package org.microsoft.qintelipass.services;


import org.microsoft.qintelipass.entity.Models;

import java.util.Optional;

public interface ModelService {
    Optional<Models> findModelById(Long id);
}
