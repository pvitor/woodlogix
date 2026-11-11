package com.woodlogix.application.proposal;

public class SalesProjectNotFoundException extends RuntimeException {
    public SalesProjectNotFoundException() {
        super("error.project-not-found");
    }
}
