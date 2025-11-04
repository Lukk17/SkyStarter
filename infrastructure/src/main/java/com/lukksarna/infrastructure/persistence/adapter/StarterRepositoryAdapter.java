package com.lukksarna.infrastructure.persistence.adapter;

import com.lukksarna.domain.model.Starter;
import com.lukksarna.infrastructure.mapper.StarterApiMapper;
import com.lukksarna.infrastructure.mapper.StarterPersistenceMapper;
import com.lukksarna.infrastructure.persistence.StarterJpaRepository;
import com.lukksarna.infrastructure.persistence.entity.StarterEntity;
import com.lukksarna.service.port.StarterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class StarterRepositoryAdapter implements StarterRepository {

    private final StarterJpaRepository jpaRepository;
    private final StarterPersistenceMapper starterPersistenceMapper;

    @Override
    public Starter findById(Long id) {
        Optional<StarterEntity> entityOptional = jpaRepository.findById(id);

        StarterEntity entity = entityOptional.orElseThrow(
            () -> new IllegalArgumentException("Starter not found with id: " + id)
        );

        return starterPersistenceMapper.entityToDomain(entity);
    }
}
