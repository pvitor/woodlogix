package com.woodlogix.application.transaction;

public interface TransactionPort {
    void execute(Runnable operation);
}
