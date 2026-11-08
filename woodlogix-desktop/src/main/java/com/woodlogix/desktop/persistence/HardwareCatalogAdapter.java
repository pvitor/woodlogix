package com.woodlogix.desktop.persistence;

import com.woodlogix.application.hardware.HardwareCatalogPort;
import com.woodlogix.application.hardware.HardwareInput;
import com.woodlogix.application.hardware.HardwareRow;
import com.woodlogix.desktop.persistence.entity.HardwareEntity;
import com.woodlogix.desktop.persistence.repository.HardwareRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class HardwareCatalogAdapter implements HardwareCatalogPort {
    private final HardwareRepository repository;

    public HardwareCatalogAdapter(HardwareRepository repository) {
        this.repository = repository;
    }

    @Override
    public HardwareRow create(HardwareInput input) {
        return toRow(repository.saveAndFlush(
                new HardwareEntity(input.name(), input.unit(), input.unitPrice())));
    }

    @Override
    public Optional<HardwareRow> update(String id, HardwareInput input) {
        return repository.findById(id).map(entity -> {
            entity.update(input.name(), input.unit(), input.unitPrice());
            return toRow(repository.saveAndFlush(entity));
        });
    }

    @Override
    public List<HardwareRow> findAllByName() {
        return repository.findAllByOrderByNameAsc().stream().map(HardwareCatalogAdapter::toRow).toList();
    }

    private static HardwareRow toRow(HardwareEntity entity) {
        return new HardwareRow(entity.getId(), entity.getName(), entity.getUnit(), entity.getUnitPrice());
    }
}
