package com.woodlogix.desktop.persistence;

import com.woodlogix.application.transaction.TransactionPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SpringTransactionAdapter implements TransactionPort {
    private final TransactionTemplate transaction;

    public SpringTransactionAdapter(PlatformTransactionManager transactionManager) {
        transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    @Override
    public void execute(Runnable operation) {
        transaction.executeWithoutResult(status -> operation.run());
    }
}
