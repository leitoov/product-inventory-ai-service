package com.inventory.infrastructure.persistence;

import com.inventory.infrastructure.persistence.entity.SalesSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SalesSessionJpaRepository extends JpaRepository<SalesSessionEntity, String> {
}
