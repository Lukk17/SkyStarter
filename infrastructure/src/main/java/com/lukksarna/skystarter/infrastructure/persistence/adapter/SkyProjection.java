package com.lukksarna.skystarter.infrastructure.persistence.adapter;

import com.lukksarna.skystarter.domain.event.SkyCreatedEvent;
import com.lukksarna.skystarter.domain.event.SkyDeletedEvent;
import com.lukksarna.skystarter.domain.event.SkyUpdatedEvent;
import com.lukksarna.skystarter.domain.exception.SkyNotFoundException;
import com.lukksarna.skystarter.domain.model.Sky;
import com.lukksarna.skystarter.domain.query.FindSkyByIdQuery;
import com.lukksarna.skystarter.infrastructure.mapper.SkyPersistenceMapper;
import com.lukksarna.skystarter.infrastructure.persistence.entity.SkyEntity;
import com.lukksarna.skystarter.infrastructure.persistence.repository.mongo.SkyMongoRepository;
import lombok.RequiredArgsConstructor;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
@ProcessingGroup("sky-projection-processor")
public class SkyProjection {

    private final SkyMongoRepository skyRepository;
    private final SkyPersistenceMapper skyPersistenceMapper;

    @QueryHandler
    public Sky handle(FindSkyByIdQuery query) {
        Optional<SkyEntity> entityOptional = skyRepository.findById(query.getSkyId());

        SkyEntity entity = entityOptional.orElseThrow(
                () -> new SkyNotFoundException(query.getSkyId())
        );
        return skyPersistenceMapper.entityToDomain(entity);
    }

    @EventHandler
    public void on(SkyCreatedEvent event) {
        SkyEntity sky = new SkyEntity(
                event.getSkyId(),
                event.getName(),
                "CREATED"
        );
        skyRepository.save(sky);
    }

    @EventHandler
    public void on(SkyUpdatedEvent event) {
        skyRepository.findById(event.getSkyId()).ifPresent(sky -> {
            sky.setName(event.getName());
            skyRepository.save(sky);
        });
    }

    @EventHandler
    public void on(SkyDeletedEvent event) {
        skyRepository.deleteById(event.getSkyId());
    }
}
