package com.woodlogix.application.hardware;

import com.woodlogix.application.transaction.TransactionPort;

import java.util.List;
import java.util.Objects;

public class HardwareCatalog {
    private final HardwareCatalogPort hardware;
    private final TransactionPort transactions;

    public HardwareCatalog(HardwareCatalogPort hardware, TransactionPort transactions) {
        this.hardware = Objects.requireNonNull(hardware);
        this.transactions = Objects.requireNonNull(transactions);
    }

    public HardwareRow create(HardwareInput input) {
        Objects.requireNonNull(input);
        return transactions.executeWithResult(() -> hardware.create(input));
    }

    public HardwareRow update(String id, HardwareInput input) {
        Objects.requireNonNull(input);
        return transactions.executeWithResult(() -> hardware.update(id, input)
                .orElseThrow(HardwareNotFoundException::new));
    }

    public List<HardwareRow> findAll() {
        return transactions.executeReadOnly(hardware::findAllByName);
    }
}
