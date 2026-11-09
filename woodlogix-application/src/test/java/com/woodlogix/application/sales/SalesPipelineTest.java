package com.woodlogix.application.sales;

import com.woodlogix.application.transaction.TransactionPort;
import com.woodlogix.core.enums.SalesStage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class SalesPipelineTest {
    @Test
    void listsEveryStageInPortOrderInsideReadOnlyTransaction() {
        var fixture = new Fixture();
        var later = row("later", "Later", SalesStage.CONTACT);
        var earlier = row("earlier", "Earlier", SalesStage.CONTACT);
        fixture.byStage.put(SalesStage.CONTACT, List.of(later, earlier));

        var result = fixture.pipeline.findPipeline();

        assertEquals(List.of(later, earlier), result.get(SalesStage.CONTACT));
        assertEquals(List.of(), result.get(SalesStage.CLOSED));
        assertEquals(List.of("read", "list:CONTACT", "list:MEASUREMENT_SCHEDULED",
                "list:QUOTE_SENT", "list:NEGOTIATION", "list:CLOSED"), fixture.events);
    }

    @Test
    void createsForExistingCustomerWithOptionalEnvironmentInsideWriteTransaction() {
        var fixture = new Fixture();
        fixture.environmentOwners.put("env", "customer");
        var input = input("customer", "env", "Kitchen", "1234.56");

        assertEquals("Kitchen", fixture.pipeline.create(input).projectName());
        assertEquals(List.of("writeResult", "customer:customer", "environment:env",
                "create:customer:env:Kitchen"), fixture.events);
    }

    @Test
    void createsWithoutEnvironmentWhenItsIdIsBlank() {
        var fixture = new Fixture();
        fixture.pipeline.create(input("customer", "  ", "Kitchen", "0"));

        assertEquals(List.of("writeResult", "customer:customer", "create:customer:null:Kitchen"),
                fixture.events);
    }

    @Test
    void rejectsMissingOrMismatchedReferencesBeforeCreating() {
        var missingCustomer = new Fixture();
        missingCustomer.customers.clear();
        assertEquals("error.customer-not-found", assertThrows(IllegalArgumentException.class,
                () -> missingCustomer.pipeline.create(input("missing", null, "Project", "1"))).getMessage());
        assertEquals(List.of("writeResult", "customer:missing"), missingCustomer.events);

        var missingEnvironment = new Fixture();
        assertEquals("error.environment-not-found", assertThrows(IllegalArgumentException.class,
                () -> missingEnvironment.pipeline.create(input("customer", "missing", "Project", "1")))
                .getMessage());
        assertEquals(List.of("writeResult", "customer:customer", "environment:missing"),
                missingEnvironment.events);

        var mismatchedEnvironment = new Fixture();
        mismatchedEnvironment.environmentOwners.put("env", "another-customer");
        assertEquals("error.environment-customer-mismatch", assertThrows(IllegalArgumentException.class,
                () -> mismatchedEnvironment.pipeline.create(input("customer", "env", "Project", "1")))
                .getMessage());
        assertEquals(List.of("writeResult", "customer:customer", "environment:env"),
                mismatchedEnvironment.events);
    }

    @Test
    void movesAndFlushesInsideWriteTransactionAndPreservesMissingProjectBehavior() {
        var fixture = new Fixture();
        fixture.projects.put("project", row("project", "Project", SalesStage.CONTACT));

        fixture.pipeline.move("project", SalesStage.CLOSED);

        assertEquals(SalesStage.CLOSED, fixture.projects.get("project").stage());
        assertEquals(List.of("write", "move:project:CLOSED"), fixture.events);
        assertEquals("error.project-not-found", assertThrows(IllegalArgumentException.class,
                () -> fixture.pipeline.move("missing", SalesStage.CONTACT)).getMessage());
        assertEquals("error.project-not-found", assertThrows(IllegalArgumentException.class,
                () -> fixture.pipeline.findById("missing")).getMessage());
        assertThrows(NullPointerException.class, () -> fixture.pipeline.move("project", null));
    }

    @Test
    void propagatesFailuresFromReadsAndWritesWithoutWrapping() {
        for (String stage : List.of("list:CONTACT", "customer:customer", "environment:env",
                "create:customer:env:Project", "move:project:CLOSED", "find:project")) {
            var fixture = new Fixture();
            fixture.environmentOwners.put("env", "customer");
            fixture.projects.put("project", row("project", "Project", SalesStage.CONTACT));
            fixture.fail = stage;
            var input = input("customer", "env", "Project", "1");

            var failure = assertThrows(IllegalStateException.class, () -> {
                if (stage.startsWith("list:")) {
                    fixture.pipeline.findPipeline();
                } else if (stage.startsWith("customer:") || stage.startsWith("environment:")
                        || stage.startsWith("create:")) {
                    fixture.pipeline.create(input);
                } else if (stage.startsWith("move:")) {
                    fixture.pipeline.move("project", SalesStage.CLOSED);
                } else {
                    fixture.pipeline.findById("project");
                }
            });

            assertSame(fixture.failure, failure, stage);
        }
    }

    @Test
    void inputPreservesValuesAndRejectsInvalidRequiredFields() {
        var input = input(" customer ", " ", " Project ", "0.00");
        assertEquals(" customer ", input.customerId());
        assertEquals(" ", input.environmentId());
        assertEquals(" Project ", input.projectName());

        assertThrows(NullPointerException.class,
                () -> new SalesProjectInput(null, null, "Project", BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
                () -> new SalesProjectInput(" ", null, "Project", BigDecimal.ONE));
        assertThrows(NullPointerException.class,
                () -> new SalesProjectInput("customer", null, null, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class,
                () -> new SalesProjectInput("customer", null, " ", BigDecimal.ONE));
        assertThrows(NullPointerException.class,
                () -> new SalesProjectInput("customer", null, "Project", null));
        assertThrows(IllegalArgumentException.class,
                () -> new SalesProjectInput("customer", null, "Project", new BigDecimal("-0.01")));
    }

    private static SalesProjectInput input(String customerId, String environmentId,
                                           String projectName, String totalValue) {
        return new SalesProjectInput(customerId, environmentId, projectName, new BigDecimal(totalValue));
    }

    private static SalesProjectRow row(String id, String projectName, SalesStage stage) {
        return new SalesProjectRow(id, projectName, "Customer", null, null, "", BigDecimal.ZERO, stage);
    }

    private static class Fixture implements SalesPipelinePort, TransactionPort {
        final List<String> events = new ArrayList<>();
        final Map<SalesStage, List<SalesProjectRow>> byStage = new EnumMap<>(SalesStage.class);
        final Map<String, String> environmentOwners = new java.util.HashMap<>();
        final Map<String, SalesProjectRow> projects = new java.util.HashMap<>();
        final java.util.Set<String> customers = new java.util.HashSet<>(List.of("customer"));
        final SalesPipeline pipeline = new SalesPipeline(this, this);
        final IllegalStateException failure = new IllegalStateException("failure");
        String fail;

        private void record(String event) {
            events.add(event);
            if (event.equals(fail)) {
                throw failure;
            }
        }

        @Override
        public boolean customerExists(String customerId) {
            record("customer:" + customerId);
            return customers.contains(customerId);
        }

        @Override
        public Optional<String> findEnvironmentCustomerId(String environmentId) {
            record("environment:" + environmentId);
            return Optional.ofNullable(environmentOwners.get(environmentId));
        }

        @Override
        public List<SalesProjectRow> findAllByStageOrderByCreatedAtDesc(SalesStage stage) {
            record("list:" + stage);
            return byStage.getOrDefault(stage, List.of());
        }

        @Override
        public SalesProjectRow createAndFlush(SalesProjectInput input) {
            record("create:" + input.customerId() + ":"
                    + (input.environmentId() == null || input.environmentId().isBlank()
                    ? "null" : input.environmentId()) + ":" + input.projectName());
            return row("created", input.projectName(), SalesStage.CONTACT);
        }

        @Override
        public boolean moveAndFlush(String projectId, SalesStage stage) {
            record("move:" + projectId + ":" + stage);
            SalesProjectRow project = projects.get(projectId);
            if (project == null) {
                return false;
            }
            java.util.Objects.requireNonNull(stage, "stage must not be null");
            projects.put(projectId, new SalesProjectRow(project.id(), project.projectName(),
                    project.customerName(), project.customerEmail(), project.customerPhone(),
                    project.environmentName(), project.totalValue(), stage));
            return true;
        }

        @Override
        public Optional<SalesProjectRow> findById(String projectId) {
            record("find:" + projectId);
            return Optional.ofNullable(projects.get(projectId));
        }

        @Override
        public void execute(Runnable operation) {
            record("write");
            operation.run();
        }

        @Override
        public <T> T executeWithResult(Supplier<T> operation) {
            record("writeResult");
            return operation.get();
        }

        @Override
        public <T> T executeReadOnly(Supplier<T> operation) {
            record("read");
            return operation.get();
        }
    }
}
