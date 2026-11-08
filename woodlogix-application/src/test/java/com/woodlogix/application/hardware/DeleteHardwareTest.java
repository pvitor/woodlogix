package com.woodlogix.application.hardware;

import com.woodlogix.application.transaction.TransactionPort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeleteHardwareTest {
    @Test
    void deletesInsideTransactionInOrder() {
        var fixture = new Fixture();
        fixture.useCase.execute("hardware");
        assertEquals(List.of("begin", "exists:hardware", "referenced:hardware",
                "delete:hardware", "commit"), fixture.events);
    }

    @Test
    void rejectsMissingHardwareBeforeCheckingReferences() {
        var fixture = new Fixture();
        fixture.exists = false;
        var failure = assertThrows(HardwareNotFoundException.class, () -> fixture.useCase.execute("missing"));
        assertEquals("error.hardware-not-found", failure.getMessage());
        assertEquals(List.of("begin", "exists:missing", "rollback"), fixture.events);
    }

    @Test
    void rejectsReferencedHardwareBeforeDeleting() {
        var fixture = new Fixture();
        fixture.referenced = true;
        var failure = assertThrows(IllegalStateException.class, () -> fixture.useCase.execute("hardware"));
        assertEquals("error.hardware-has-related-records", failure.getMessage());
        assertEquals(List.of("begin", "exists:hardware", "referenced:hardware", "rollback"), fixture.events);
    }

    @Test
    void propagatesFailuresFromEachPersistenceOperation() {
        for (String stage : List.of("exists", "referenced", "delete")) {
            var fixture = new Fixture();
            fixture.failedStage = stage;
            var failure = assertThrows(RuntimeException.class, () -> fixture.useCase.execute("hardware"));
            assertSame(fixture.failure, failure);
            assertEquals("rollback", fixture.events.get(fixture.events.size() - 1));
            if (!stage.equals("delete")) {
                assertEquals(false, fixture.events.contains("delete:hardware"));
            }
        }
    }

    private static class Fixture implements HardwareDeletionPort, TransactionPort {
        private final List<String> events = new ArrayList<>();
        private final RuntimeException failure = new RuntimeException("persistence failure");
        private final DeleteHardware useCase = new DeleteHardware(this, this);
        private boolean exists = true;
        private boolean referenced;
        private String failedStage;

        @Override
        public void execute(Runnable operation) {
            events.add("begin");
            try {
                operation.run();
                events.add("commit");
            } catch (RuntimeException exception) {
                events.add("rollback");
                throw exception;
            }
        }

        @Override
        public boolean exists(String id) {
            record("exists", id);
            return exists;
        }

        @Override
        public boolean isReferencedByDraft(String id) {
            record("referenced", id);
            return referenced;
        }

        @Override
        public void deleteAndFlush(String id) {
            record("delete", id);
        }

        private void record(String stage, String id) {
            events.add(stage + ":" + id);
            if (stage.equals(failedStage)) {
                throw failure;
            }
        }
    }
}
