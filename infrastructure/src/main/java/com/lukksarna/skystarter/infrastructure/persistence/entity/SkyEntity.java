package com.lukksarna.skystarter.infrastructure.persistence.entity;

import com.lukksarna.skystarter.domain.model.SkyStatus;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.UUID;

@Data
@NoArgsConstructor
@Document(collection = "skyProjections")
public class SkyEntity {

    @Id
    private UUID skyId;

    @Version
    private Long version;

    private String name;

    private SkyStatus status;
}
