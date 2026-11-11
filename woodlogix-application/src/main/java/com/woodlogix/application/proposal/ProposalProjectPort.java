package com.woodlogix.application.proposal;

import com.woodlogix.core.model.SalesProjectId;

import java.util.Optional;

public interface ProposalProjectPort {
    Optional<ProposalProjectReference> findById(SalesProjectId salesProjectId);
}
