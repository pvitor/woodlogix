package com.woodlogix.desktop.service;

import com.woodlogix.application.customer.CustomerCatalog;
import com.woodlogix.application.transaction.TransactionPort;
import com.woodlogix.desktop.persistence.CustomerCatalogAdapter;
import com.woodlogix.desktop.persistence.CustomerChangeNotifierAdapter;
import com.woodlogix.desktop.persistence.SqliteIntegrityErrors;
import com.woodlogix.desktop.persistence.entity.CustomerEntity;
import com.woodlogix.desktop.persistence.entity.ProjectEnvironmentEntity;
import com.woodlogix.desktop.persistence.repository.CustomerRepository;
import com.woodlogix.desktop.persistence.repository.FurnitureItemRepository;
import com.woodlogix.desktop.persistence.repository.FurniturePartRepository;
import com.woodlogix.desktop.persistence.repository.ProjectEnvironmentRepository;
import com.woodlogix.desktop.persistence.repository.SalesProjectRepository;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeletionErrorTranslationTest {
    private static class DirectTransactions implements TransactionPort {
        public void execute(Runnable operation) { operation.run(); }
        public <T> T executeWithResult(Supplier<T> operation) { return operation.get(); }
        public <T> T executeReadOnly(Supplier<T> operation) { return operation.get(); }
    }

    @Test
    void recognizesOnlyTheExtendedForeignKeyCodeInTheCauseChain() {
        assertTrue(SqliteIntegrityErrors.isForeignKeyViolation(new RuntimeException(
                new SQLiteException("foreign key", SQLiteErrorCode.SQLITE_CONSTRAINT_FOREIGNKEY))));
        for (var code : List.of(SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE,
                SQLiteErrorCode.SQLITE_CONSTRAINT_NOTNULL, SQLiteErrorCode.SQLITE_CONSTRAINT,
                SQLiteErrorCode.SQLITE_BUSY)) {
            assertFalse(SqliteIntegrityErrors.isForeignKeyViolation(new RuntimeException(
                    new SQLiteException("foreign key", code))), code.name());
        }
        assertFalse(SqliteIntegrityErrors.isForeignKeyViolation(new RuntimeException("foreign key")));
    }

    @Test
    void unrelatedIntegrityErrorsRemainUnchangedInCustomerAndEnvironmentDeletion() {
        var customerRepository = mock(CustomerRepository.class);
        var environmentRepository = mock(ProjectEnvironmentRepository.class);
        var events = mock(ApplicationEventPublisher.class);
        var customers = new CustomerService(new CustomerCatalog(
                new CustomerCatalogAdapter(customerRepository),
                new CustomerChangeNotifierAdapter(events), new DirectTransactions()));
        var environments = new EnvironmentService(customerRepository, environmentRepository,
                mock(FurnitureItemRepository.class), mock(FurniturePartRepository.class),
                mock(SalesProjectRepository.class), events);
        var failure = new DataIntegrityViolationException("unrelated", new SQLiteException(
                "unique", SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE));
        when(customerRepository.existsById("customer")).thenReturn(true);
        when(environmentRepository.existsById("environment")).thenReturn(true);
        doThrow(failure).when(customerRepository).flush();
        doThrow(failure).when(environmentRepository).flush();

        assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                () -> customers.delete("customer")));
        assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                () -> environments.delete("environment")));
    }

    @Test
    void unrelatedIntegrityErrorsRemainUnchangedInBulkDeletion() {
        var customers = mock(CustomerRepository.class);
        var environments = mock(ProjectEnvironmentRepository.class);
        var service = new EnvironmentService(customers, environments,
                mock(FurnitureItemRepository.class), mock(FurniturePartRepository.class),
                mock(SalesProjectRepository.class), mock(ApplicationEventPublisher.class));
        var customer = new CustomerEntity("customer", "Customer", null, null, null);
        var environment = new ProjectEnvironmentEntity("environment", customer, "Kitchen");
        when(customers.findById("customer")).thenReturn(Optional.of(customer));
        when(environments.findAllByCustomerId("customer")).thenReturn(List.of(environment));
        var failure = new DataIntegrityViolationException("unrelated", new SQLiteException(
                "check", SQLiteErrorCode.SQLITE_CONSTRAINT_CHECK));
        doThrow(failure).when(environments).flush();

        assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                () -> service.saveOrUpdateCustomerEnvironments("customer", List.of())));
    }
}
