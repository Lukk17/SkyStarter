package com.lukksarna.infrastructure.persistence;

import com.lukksarna.infrastructure.persistence.entity.StarterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StarterJpaRepository extends JpaRepository<StarterEntity, Long> {

}
