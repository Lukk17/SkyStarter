package com.lukksarna.skystarter.infrastructure.persistence.adapter;

import com.lukksarna.skystarter.domain.event.SkyCreatedEvent;
import com.lukksarna.skystarter.domain.event.SkyDeletedEvent;
import com.lukksarna.skystarter.domain.event.SkyUpdatedEvent;
import com.lukksarna.skystarter.domain.exception.SkyNotFoundException;
import com.lukksarna.skystarter.domain.model.Sky;
import com.lukksarna.skystarter.domain.model.SkyStatus;
import com.lukksarna.skystarter.domain.query.FindSkyByIdQuery;
import com.lukksarna.skystarter.infrastructure.mapper.SkyPersistenceMapper;
import com.lukksarna.skystarter.infrastructure.persistence.entity.SkyEntity;
import com.lukksarna.skystarter.infrastructure.persistence.repository.mongo.SkyMongoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkyProjectionTest {

    @Mock
    private SkyMongoRepository repository;

    @Mock
    private SkyPersistenceMapper mapper;

    @InjectMocks
    private SkyProjection projection;

    private static final UUID ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void findById_returnsMappedDomain() {
        SkyEntity entity = new SkyEntity();
        entity.setSkyId(ID);
        entity.setName("Vega");
        entity.setStatus(SkyStatus.CREATED);
        Sky sky = new Sky(ID, "Vega", SkyStatus.CREATED);
        when(repository.findById(ID)).thenReturn(Optional.of(entity));
        when(mapper.entityToDomain(entity)).thenReturn(sky);

        Sky actual = projection.handle(new FindSkyByIdQuery(ID));

        assertThat(actual).isEqualTo(sky);
    }

    @Test
    void findById_missing_throwsNotFound() {
        when(repository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projection.handle(new FindSkyByIdQuery(ID)))
                .isInstanceOf(SkyNotFoundException.class)
                .hasMessageContaining(ID.toString());
    }

    @Test
    void onCreated_savesEntityWithCreatedStatus() {
        projection.on(new SkyCreatedEvent(ID, "Vega"));

        ArgumentCaptor<SkyEntity> captor = ArgumentCaptor.forClass(SkyEntity.class);
        verify(repository).save(captor.capture());
        SkyEntity saved = captor.getValue();
        assertThat(saved.getSkyId()).isEqualTo(ID);
        assertThat(saved.getName()).isEqualTo("Vega");
        assertThat(saved.getStatus()).isEqualTo(SkyStatus.CREATED);
    }

    @Test
    void onUpdated_updatesNameWhenPresent() {
        SkyEntity existing = new SkyEntity();
        existing.setSkyId(ID);
        existing.setName("Vega");
        existing.setStatus(SkyStatus.CREATED);
        when(repository.findById(ID)).thenReturn(Optional.of(existing));

        projection.on(new SkyUpdatedEvent(ID, "Vega-2"));

        assertThat(existing.getName()).isEqualTo("Vega-2");
        verify(repository).save(existing);
    }

    @Test
    void onUpdated_missingEntity_doesNothing() {
        when(repository.findById(ID)).thenReturn(Optional.empty());

        projection.on(new SkyUpdatedEvent(ID, "Vega-2"));

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void onDeleted_deletesById() {
        projection.on(new SkyDeletedEvent(ID));
        verify(repository).deleteById(ID);
    }
}
