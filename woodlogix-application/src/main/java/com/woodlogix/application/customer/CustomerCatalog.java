package com.woodlogix.application.customer;

import com.woodlogix.application.transaction.TransactionPort;

import java.util.List;
import java.util.Objects;

public class CustomerCatalog {
    private final CustomerCatalogPort customers;
    private final CustomerChangeNotifier notifier;
    private final TransactionPort transactions;

    public CustomerCatalog(CustomerCatalogPort customers, CustomerChangeNotifier notifier,
                           TransactionPort transactions) {
        this.customers = Objects.requireNonNull(customers);
        this.notifier = Objects.requireNonNull(notifier);
        this.transactions = Objects.requireNonNull(transactions);
    }

    public CustomerRow create(CustomerInput input) {
        Objects.requireNonNull(input);
        return transactions.executeWithResult(() -> {
            CustomerRow created = customers.create(input);
            notifier.customerChanged();
            return created;
        });
    }

    public CustomerRow update(String id, CustomerInput input) {
        Objects.requireNonNull(input);
        return transactions.executeWithResult(() -> {
            CustomerRow updated = customers.update(id, input)
                    .orElseThrow(CustomerNotFoundException::new);
            notifier.customerChanged();
            return updated;
        });
    }

    public void delete(String id) {
        transactions.execute(() -> {
            if (!customers.exists(id)) {
                throw new CustomerNotFoundException();
            }
            customers.deleteAndFlush(id);
            notifier.customerChanged();
        });
    }

    public List<CustomerRow> findAll() {
        return transactions.executeReadOnly(customers::findAll);
    }

    public List<CustomerRow> search(String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return findAll();
        }
        String term = searchTerm.trim();
        return transactions.executeReadOnly(() -> customers.searchByAnyField(term));
    }

    public List<CustomerRow> findByName(String name) {
        if (name == null || name.isBlank()) {
            return findAll();
        }
        String trimmed = name.trim();
        return transactions.executeReadOnly(() -> customers.searchByName(trimmed));
    }
}
