package com.woodlogix.application.draft;

import com.woodlogix.application.transaction.TransactionPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EstimateDraftTest {
    @Test
    void savesInsideRequiredTransactionAfterValidation() {
        var fixture = new Fixture();
        var input = input();

        fixture.estimateDraft.saveActiveDraft(input);

        assertEquals(input, fixture.saved);
        assertEquals(List.of("write", "save"), fixture.events);
    }

    @Test
    void readsInsideReadOnlyTransaction() {
        var fixture = new Fixture();
        var row = row();
        fixture.result = Optional.of(row);

        assertEquals(Optional.of(row), fixture.estimateDraft.findActiveDraft());
        assertEquals(List.of("read", "find"), fixture.events);
    }

    @Test
    void rejectsInvalidWorkpiecesAndHardwareBeforePersistence() {
        var fixture = new Fixture();
        var invalidWorkpiece = new EstimateDraftInput.WorkpieceInput(
                null, " ", bd("100"), bd("200"), 1, "material", null,
                false, false, false, false);
        var invalid = new EstimateDraftInput(null, bd("0"), bd("0"), bd("0"),
                bd("0"), bd("0"), List.of(invalidWorkpiece), List.of());

        assertEquals("Draft workpiece data is invalid",
                assertThrows(IllegalArgumentException.class,
                        () -> fixture.estimateDraft.saveActiveDraft(invalid)).getMessage());
        assertEquals(List.of("write"), fixture.events);

        fixture.events.clear();
        var invalidHardware = new EstimateDraftInput(null, bd("0"), bd("0"), bd("0"),
                bd("0"), bd("0"), List.of(),
                List.of(new EstimateDraftInput.HardwareInput("hardware", 0)));
        assertEquals("Draft hardware quantity must be positive",
                assertThrows(IllegalArgumentException.class,
                        () -> fixture.estimateDraft.saveActiveDraft(invalidHardware)).getMessage());
        assertEquals(List.of("write"), fixture.events);
    }

    @Test
    void rejectsSelectedEdgesWithoutBandingBeforePersistence() {
        var fixture = new Fixture();
        for (int selectedEdge = 0; selectedEdge < 4; selectedEdge++) {
            boolean[] edges = new boolean[4];
            edges[selectedEdge] = true;
            var workpiece = new EstimateDraftInput.WorkpieceInput(null, "Panel", bd("100"),
                    bd("200"), 1, "material", null, edges[0], edges[1], edges[2], edges[3]);
            var invalid = withWorkpieces(List.of(workpiece));

            assertEquals("Edge banding is required for selected edges",
                    assertThrows(IllegalArgumentException.class,
                            () -> fixture.estimateDraft.saveActiveDraft(invalid)).getMessage());
        }

        var blankBanding = new EstimateDraftInput.WorkpieceInput(null, "Panel", bd("100"),
                bd("200"), 1, "material", " ", true, false, false, false);
        assertEquals("Edge banding is required for selected edges",
                assertThrows(IllegalArgumentException.class,
                        () -> fixture.estimateDraft.saveActiveDraft(
                                withWorkpieces(List.of(blankBanding)))).getMessage());
        assertPortWasNotCalled(fixture);
    }

    @Test
    void rejectsNonPositiveQuantitiesAndDimensionsBeforePersistence() {
        var invalidInputs = List.of(
                new InvalidDraft(withWorkpieces(List.of(workpiece(0, "100", "200"))),
                        "Draft workpiece data is invalid"),
                new InvalidDraft(withWorkpieces(List.of(workpiece(1, "0", "200"))),
                        "Draft workpiece dimensions must be positive"),
                new InvalidDraft(withWorkpieces(List.of(workpiece(1, "100", "-1"))),
                        "Draft workpiece dimensions must be positive"),
                new InvalidDraft(withHardwares(List.of(
                        new EstimateDraftInput.HardwareInput("hardware", -1))),
                        "Draft hardware quantity must be positive")
        );

        for (InvalidDraft invalid : invalidInputs) {
            var fixture = new Fixture();
            assertEquals(invalid.message(), assertThrows(IllegalArgumentException.class,
                    () -> fixture.estimateDraft.saveActiveDraft(invalid.input())).getMessage());
            assertPortWasNotCalled(fixture);
        }
    }

    @Test
    void rejectsInvalidRatesAndMarginsBeforePersistence() {
        var invalidInputs = List.of(
                new InvalidDraft(withParameters(bd("-0.01"), bd("3.5"), bd("42"),
                        bd("18"), bd("35")), "wasteFactor must not be negative"),
                new InvalidDraft(withParameters(bd("12.5"), bd("-0.01"), bd("42"),
                        bd("18"), bd("35")), "estimatedHours must not be negative"),
                new InvalidDraft(withParameters(bd("12.5"), bd("3.5"), bd("-0.01"),
                        bd("18"), bd("35")), "hourlyRate must not be negative"),
                new InvalidDraft(withParameters(bd("12.5"), bd("3.5"), bd("42"),
                        bd("-0.01"), bd("35")), "machineRate must not be negative"),
                new InvalidDraft(withParameters(bd("12.5"), bd("3.5"), bd("42"),
                        bd("18"), bd("-0.01")), "profitMargin must not be negative"),
                new InvalidDraft(withParameters(bd("12.5"), bd("3.5"), bd("42"),
                        bd("18"), bd("100")), "profitMargin must be below 100")
        );

        for (InvalidDraft invalid : invalidInputs) {
            var fixture = new Fixture();
            assertEquals(invalid.message(), assertThrows(IllegalArgumentException.class,
                    () -> fixture.estimateDraft.saveActiveDraft(invalid.input())).getMessage());
            assertPortWasNotCalled(fixture);
        }
    }

    @Test
    void propagatesPersistenceFailuresWithoutWrapping() {
        var fixture = new Fixture();
        var failure = new IllegalStateException("persistence failure");
        fixture.failure = failure;

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> fixture.estimateDraft.saveActiveDraft(input())));
        assertEquals(List.of("write", "save"), fixture.events);
    }

    @Test
    void preservesAnEmptyDraftResult() {
        var fixture = new Fixture();

        assertTrue(fixture.estimateDraft.findActiveDraft().isEmpty());
    }

    private static EstimateDraftInput input() {
        return new EstimateDraftInput(null, bd("12.5"), bd("3.5"), bd("42"),
                bd("18"), bd("35"),
                List.of(new EstimateDraftInput.WorkpieceInput(null, "Panel", bd("1200"),
                        bd("600"), 2, "material", null, false, false, false, false)),
                List.of(new EstimateDraftInput.HardwareInput("hardware", 4)));
    }

    private static EstimateDraftInput withParameters(BigDecimal wasteFactor,
                                                    BigDecimal estimatedHours,
                                                    BigDecimal hourlyRate,
                                                    BigDecimal machineRate,
                                                    BigDecimal profitMargin) {
        EstimateDraftInput valid = input();
        return new EstimateDraftInput(valid.projectId(), wasteFactor, estimatedHours,
                hourlyRate, machineRate, profitMargin, valid.workpieces(), valid.hardwares());
    }

    private static EstimateDraftInput withWorkpieces(
            List<EstimateDraftInput.WorkpieceInput> workpieces
    ) {
        EstimateDraftInput valid = input();
        return new EstimateDraftInput(valid.projectId(), valid.wasteFactor(),
                valid.estimatedHours(), valid.hourlyRate(), valid.machineRate(),
                valid.profitMargin(), workpieces, valid.hardwares());
    }

    private static EstimateDraftInput withHardwares(
            List<EstimateDraftInput.HardwareInput> hardwares
    ) {
        EstimateDraftInput valid = input();
        return new EstimateDraftInput(valid.projectId(), valid.wasteFactor(),
                valid.estimatedHours(), valid.hourlyRate(), valid.machineRate(),
                valid.profitMargin(), valid.workpieces(), hardwares);
    }

    private static EstimateDraftInput.WorkpieceInput workpiece(
            int quantity, String lengthMm, String widthMm
    ) {
        return new EstimateDraftInput.WorkpieceInput(null, "Panel", bd(lengthMm), bd(widthMm),
                quantity, "material", null, false, false, false, false);
    }

    private static void assertPortWasNotCalled(Fixture fixture) {
        assertFalse(fixture.events.contains("save"));
        assertNull(fixture.saved);
    }

    private static EstimateDraftRow row() {
        return new EstimateDraftRow(null, bd("12.5"), bd("3.5"), bd("42"),
                bd("18"), bd("35"),
                List.of(new EstimateDraftRow.WorkpieceRow(null, "Panel", bd("1200"),
                        bd("600"), 2, "material", null, false, false, false, false)),
                List.of(new EstimateDraftRow.HardwareRow("hardware", 4)));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private record InvalidDraft(EstimateDraftInput input, String message) {
    }

    private static final class Fixture implements EstimateDraftPort, TransactionPort {
        private final List<String> events = new ArrayList<>();
        private final EstimateDraft estimateDraft = new EstimateDraft(this, this);
        private Optional<EstimateDraftRow> result = Optional.empty();
        private EstimateDraftInput saved;
        private RuntimeException failure;

        @Override
        public void saveActiveDraftAndFlush(EstimateDraftInput input) {
            events.add("save");
            if (failure != null) {
                throw failure;
            }
            saved = input;
        }

        @Override
        public Optional<EstimateDraftRow> findActiveDraft() {
            events.add("find");
            return result;
        }

        @Override
        public void execute(Runnable operation) {
            events.add("write");
            operation.run();
        }

        @Override
        public <T> T executeWithResult(Supplier<T> operation) {
            return operation.get();
        }

        @Override
        public <T> T executeReadOnly(Supplier<T> operation) {
            events.add("read");
            return operation.get();
        }
    }
}
