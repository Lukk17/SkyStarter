package com.lukksarna.skystarter.infrastructure.persistence.repository.mongo;

import com.lukksarna.skystarter.infrastructure.persistence.entity.SkyEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SkyMongoRepository extends MongoRepository<SkyEntity, UUID> {

}
