package com.woodlogix.application.transaction;

import java.util.function.Supplier;

public interface TransactionPort {
    void execute(Runnable operation);

    <T> T executeWithResult(Supplier<T> operation);

    <T> T executeReadOnly(Supplier<T> operation);
}
