package com.woodlogix.application.hardware;

import com.woodlogix.application.transaction.TransactionPort;

import java.util.Objects;

public class DeleteHardware {
    private final HardwareDeletionPort hardware;
    private final TransactionPort transactions;

    public DeleteHardware(HardwareDeletionPort hardware, TransactionPort transactions) {
        this.hardware = Objects.requireNonNull(hardware);
        this.transactions = Objects.requireNonNull(transactions);
    }

    public void execute(String id) {
        transactions.execute(() -> {
            if (!hardware.exists(id)) {
                throw new HardwareNotFoundException();
            }
            if (hardware.isReferencedByDraft(id)) {
                throw new IllegalStateException("error.hardware-has-related-records");
            }
            hardware.deleteAndFlush(id);
        });
    }
}
