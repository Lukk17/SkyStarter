package com.lukksarna.skystarter.infrastructure.mapper;

import com.lukksarna.skystarter.domain.model.Sky;
import com.lukksarna.skystarter.infrastructure.persistence.entity.SkyEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SkyPersistenceMapper {

    Sky entityToDomain(SkyEntity entity);
}
