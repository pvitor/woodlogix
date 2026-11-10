package com.woodlogix.application.customer;

public class CustomerInUseException extends IllegalStateException {
    public CustomerInUseException(Throwable cause) {
        super("error.customer-has-related-records", cause);
    }
}
