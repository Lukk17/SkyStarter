package com.lukksarna.infrastructure.mapper;

import com.lukksarna.domain.model.Starter;
import com.lukksarna.infrastructure.persistence.entity.StarterEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface StarterPersistenceMapper {

    Starter entityToDomain(StarterEntity entity);
}
