package com.woodlogix.application.customer;

public class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException() {
        super("error.customer-not-found");
    }
}
