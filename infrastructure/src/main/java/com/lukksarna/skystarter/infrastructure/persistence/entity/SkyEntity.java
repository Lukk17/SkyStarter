package com.lukksarna.skystarter.infrastructure.persistence.entity;

import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "skyProjections")
public class SkyEntity {

    @Id
    private UUID skyId;

    @Indexed(name = "idx_name")
    private String name;

    private String status;
}
