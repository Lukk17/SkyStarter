package com.lukksarna.skystarter.infrastructure.mapper;

import com.lukksarna.skystarter.domain.model.Sky;
import com.lukksarna.skystarter.infrastructure.api.rest.dto.response.SkyResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SkyApiMapper {

    SkyResponse domainToApiResponse(Sky sky);
}
