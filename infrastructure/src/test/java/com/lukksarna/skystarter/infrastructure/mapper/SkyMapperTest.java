package com.lukksarna.skystarter.infrastructure.mapper;

import com.lukksarna.skystarter.domain.model.Sky;
import com.lukksarna.skystarter.domain.model.SkyStatus;
import com.lukksarna.skystarter.infrastructure.api.rest.dto.response.SkyResponse;
import com.lukksarna.skystarter.infrastructure.persistence.entity.SkyEntity;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SkyMapperTest {

    private final SkyApiMapper apiMapper = Mappers.getMapper(SkyApiMapper.class);
    private final SkyPersistenceMapper persistenceMapper = Mappers.getMapper(SkyPersistenceMapper.class);

    @Test
    void domainToApiResponse_copiesAllFields() {
        UUID id = UUID.randomUUID();
        Sky sky = new Sky(id, "Orion", SkyStatus.CREATED);

        SkyResponse response = apiMapper.domainToApiResponse(sky);

        assertThat(response.skyId()).isEqualTo(id);
        assertThat(response.name()).isEqualTo("Orion");
        assertThat(response.status()).isEqualTo("CREATED");
    }

    @Test
    void entityToDomain_copiesAllFields() {
        UUID id = UUID.randomUUID();
        SkyEntity entity = new SkyEntity();
        entity.setSkyId(id);
        entity.setName("Vega");
        entity.setStatus(SkyStatus.CREATED);

        Sky sky = persistenceMapper.entityToDomain(entity);

        assertThat(sky.skyId()).isEqualTo(id);
        assertThat(sky.name()).isEqualTo("Vega");
        assertThat(sky.status()).isEqualTo(SkyStatus.CREATED);
    }

    @Test
    void domainToApiResponse_nullSafe() {
        assertThat(apiMapper.domainToApiResponse(null)).isNull();
    }
}
