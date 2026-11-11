package com.woodlogix.application.proposal;

import com.woodlogix.application.transaction.TransactionPort;
import com.woodlogix.core.enums.CurrencyCode;
import com.woodlogix.core.model.CommercialTermsDraft;
import com.woodlogix.core.model.CustomerSnapshot;
import com.woodlogix.core.model.LeadTime;
import com.woodlogix.core.model.PaymentTerms;
import com.woodlogix.core.model.Proposal;
import com.woodlogix.core.model.ProposalDraftStatus;
import com.woodlogix.core.model.ProposalId;
import com.woodlogix.core.model.SalesProjectId;
import com.woodlogix.core.model.ValidityPeriod;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProposalManagementTest {
    @Test
    void createsProposalWithEmptyEditableDraftInsideWriteTransaction() {
        Fixture fixture = new Fixture();

        ProposalRecord record = fixture.management.create(input());
        Proposal proposal = record.proposal();

        assertEquals(new ProposalId("PROP-2026-0001"),
                proposal.getProposalId());
        assertEquals(2026, fixture.reservedYear);
        assertEquals(new SalesProjectId("project-1"), proposal.getSalesProjectId());
        assertEquals(ProposalDraftStatus.EDITABLE,
                proposal.getCurrentDraft().getStatus());
        assertEquals(1, proposal.getCurrentDraftRevision().getValue());
        assertEquals(List.of(), proposal.getCurrentDraft().getItems());
        assertEquals(null, proposal.getCurrentDraft().getSourceVersionNumber());
        assertEquals(new CustomerSnapshot("customer-1", "Customer"),
                proposal.getCurrentDraft().getCustomer());
        assertEquals(0, record.persistenceVersion());
        assertNotNull(proposal.getCurrentDraftId());
        assertFalse(proposal.getCurrentDraftId().getValue().isBlank());
        assertEquals(List.of("write-result", "reserve", "project", "save"),
                fixture.events);
    }

    @Test
    void reservesProposalIdWithYearFromAFixed2027Clock() {
        Fixture fixture = new Fixture(fixedClock("2027-06-15T10:00:00Z"));

        ProposalRecord record = fixture.management.create(input());

        assertEquals(new ProposalId("PROP-2027-0001"), record.proposal().getProposalId());
        assertEquals(2027, fixture.reservedYear);
    }

    @Test
    void reservesExactlyTheYearDeterminedByTheInjectedClock() {
        Fixture fixture = new Fixture(fixedClock("2026-12-31T23:59:59Z"));

        fixture.management.create(input());

        assertEquals(2026, fixture.reservedYear);
    }

    @Test
    void findsExistingProposalInsideReadOnlyTransaction() {
        Fixture fixture = new Fixture();
        ProposalRecord expected = fixture.management.create(input());
        fixture.events.clear();

        ProposalRecord actual = fixture.management.findById(expected.proposal().getProposalId());

        assertSame(expected, actual);
        assertEquals(List.of("read", "find"), fixture.events);
    }

    @Test
    void rejectsMissingProposal() {
        Fixture fixture = new Fixture();

        assertThrows(ProposalNotFoundException.class, () ->
                fixture.management.findById(new ProposalId("PROP-2026-0002")));
        assertEquals(List.of("read", "find"), fixture.events);
    }

    @Test
    void listsSummariesByProjectInsideReadOnlyTransaction() {
        Fixture fixture = new Fixture();
        ProposalSummaryRow first = new ProposalSummaryRow(
                new ProposalId("PROP-2026-0001"), new SalesProjectId("project-1"));
        ProposalSummaryRow second = new ProposalSummaryRow(
                new ProposalId("PROP-2026-0002"), new SalesProjectId("project-1"));
        fixture.summaries.put(new SalesProjectId("project-1"), List.of(first, second));

        List<ProposalSummaryRow> result = fixture.management.findAllBySalesProjectId(
                new SalesProjectId("project-1"));

        assertEquals(List.of(first, second), result);
        assertEquals(List.of("read", "list"), fixture.events);
    }

    @Test
    void rejectsProposalForMissingProjectBeforeReservingId() {
        Fixture fixture = new Fixture();
        fixture.projects.clear();

        assertThrows(SalesProjectNotFoundException.class, () ->
                fixture.management.create(input()));
        assertEquals(List.of("write-result", "reserve", "project"), fixture.events);
    }

    @Test
    void propagatesPortFailuresWithoutWrapping() {
        for (String failurePoint : List.of("project", "reserve", "save", "find", "list")) {
            Fixture fixture = new Fixture();
            fixture.failurePoint = failurePoint;
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> {
                if (failurePoint.equals("find")) {
                    fixture.management.findById(new ProposalId("PROP-2026-0001"));
                } else if (failurePoint.equals("list")) {
                    fixture.management.findAllBySalesProjectId(
                            new SalesProjectId("project-1"));
                } else {
                    fixture.management.create(input());
                }
            });
            assertSame(fixture.failure, failure, failurePoint);
        }
    }

    private static CreateProposalInput input() {
        return new CreateProposalInput(
                new SalesProjectId("project-1"),
                null,
                CurrencyCode.UYU,
                null,
                List.of(),
                null,
                ValidityPeriod.of(30),
                new CommercialTermsDraft(
                        new PaymentTerms("Transfer"),
                        new LeadTime("30 days"),
                        null,
                        null,
                        null));
    }

    private static final class Fixture
            implements ProposalPersistencePort, ProposalProjectPort, TransactionPort {
        private final Map<SalesProjectId, ProposalProjectReference> projects = new HashMap<>();
        private final Map<ProposalId, ProposalRecord> proposals = new HashMap<>();
        private final Map<SalesProjectId, List<ProposalSummaryRow>> summaries = new HashMap<>();
        private final List<String> events = new ArrayList<>();
        private final IllegalStateException failure = new IllegalStateException("failure");
        private final ProposalManagement management;
        private String failurePoint;
        private int nextSequence = 1;
        private int reservedYear;

        private Fixture() {
            this(fixedClock("2026-01-15T10:00:00Z"));
        }

        private Fixture(Clock clock) {
            this.management = new ProposalManagement(this, this, this, clock);
            projects.put(
                    new SalesProjectId("project-1"),
                    new ProposalProjectReference(
                            new SalesProjectId("project-1"),
                            new CustomerSnapshot("customer-1", "Customer")));
        }

        @Override
        public ProposalId reserveNextId(int year) {
            record("reserve");
            reservedYear = year;
            return new ProposalId("PROP-" + year + "-" + String.format("%04d", nextSequence++));
        }

        @Override
        public ProposalRecord saveNew(Proposal proposal) {
            record("save");
            ProposalRecord record = new ProposalRecord(proposal, 0);
            proposals.put(proposal.getProposalId(), record);
            return record;
        }

        @Override
        public Optional<ProposalRecord> findById(ProposalId proposalId) {
            record("find");
            return Optional.ofNullable(proposals.get(proposalId));
        }

        @Override
        public List<ProposalSummaryRow> findAllBySalesProjectId(SalesProjectId salesProjectId) {
            record("list");
            return summaries.getOrDefault(salesProjectId, List.of());
        }

        @Override
        public Optional<ProposalProjectReference> findById(SalesProjectId salesProjectId) {
            record("project");
            return Optional.ofNullable(projects.get(salesProjectId));
        }

        @Override
        public void execute(Runnable operation) {
            record("write");
            operation.run();
        }

        @Override
        public <T> T executeWithResult(Supplier<T> operation) {
            record("write-result");
            return operation.get();
        }

        @Override
        public <T> T executeReadOnly(Supplier<T> operation) {
            record("read");
            return operation.get();
        }

        private void record(String event) {
            events.add(event);
            if (event.equals(failurePoint)) {
                throw failure;
            }
        }

    }

    private static Clock fixedClock(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneId.of("UTC"));
    }
}
