package com.woodlogix.application.hardware;

import java.util.List;
import java.util.Optional;

public interface HardwareCatalogPort {
    HardwareRow create(HardwareInput input);

    Optional<HardwareRow> update(String id, HardwareInput input);

    List<HardwareRow> findAllByName();
}
