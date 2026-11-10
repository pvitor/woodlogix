package com.woodlogix.server.persistence;

import com.woodlogix.application.transaction.TransactionPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Component
public class SpringTransactionAdapter implements TransactionPort {
    private final TransactionTemplate transaction;
    private final TransactionTemplate readOnlyTransaction;

    public SpringTransactionAdapter(PlatformTransactionManager transactionManager) {
        transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        readOnlyTransaction = new TransactionTemplate(transactionManager);
        readOnlyTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        readOnlyTransaction.setReadOnly(true);
    }

    @Override
    public void execute(Runnable operation) {
        transaction.executeWithoutResult(status -> operation.run());
    }

    @Override
    public <T> T executeWithResult(Supplier<T> operation) {
        return transaction.execute(status -> operation.get());
    }

    @Override
    public <T> T executeReadOnly(Supplier<T> operation) {
        return readOnlyTransaction.execute(status -> operation.get());
    }
}
