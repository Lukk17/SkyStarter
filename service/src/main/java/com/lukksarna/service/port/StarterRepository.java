package com.lukksarna.service.port;

import com.lukksarna.domain.model.Starter;

public interface StarterRepository {
    Starter findById(Long id);
}
