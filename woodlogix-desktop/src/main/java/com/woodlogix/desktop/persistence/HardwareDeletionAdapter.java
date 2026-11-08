package com.woodlogix.desktop.persistence;

import com.woodlogix.application.hardware.HardwareDeletionPort;
import com.woodlogix.desktop.persistence.repository.EstimateDraftRepository;
import com.woodlogix.desktop.persistence.repository.HardwareRepository;
import org.springframework.stereotype.Component;

@Component
public class HardwareDeletionAdapter implements HardwareDeletionPort {
    private final HardwareRepository hardwareRepository;
    private final EstimateDraftRepository draftRepository;

    public HardwareDeletionAdapter(HardwareRepository hardwareRepository, EstimateDraftRepository draftRepository) {
        this.hardwareRepository = hardwareRepository;
        this.draftRepository = draftRepository;
    }

    @Override
    public boolean exists(String id) {
        return hardwareRepository.existsById(id);
    }

    @Override
    public boolean isReferencedByDraft(String id) {
        return draftRepository.existsByHardwaresHardwareId(id);
    }

    @Override
    public void deleteAndFlush(String id) {
        hardwareRepository.deleteById(id);
        hardwareRepository.flush();
    }
}
