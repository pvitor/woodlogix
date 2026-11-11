package com.woodlogix.server.persistence.repository;

import com.woodlogix.server.persistence.entity.ProposalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProposalRepository extends JpaRepository<ProposalEntity, String> {
    List<ProposalEntity> findAllBySalesProjectIdOrderById(String salesProjectId);
}
