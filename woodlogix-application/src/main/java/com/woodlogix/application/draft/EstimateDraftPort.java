package com.woodlogix.application.draft;

import java.util.Optional;

public interface EstimateDraftPort {
    void saveActiveDraftAndFlush(EstimateDraftInput input);

    Optional<EstimateDraftRow> findActiveDraft();
}
