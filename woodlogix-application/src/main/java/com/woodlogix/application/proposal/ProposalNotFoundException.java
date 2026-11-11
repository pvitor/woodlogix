package com.woodlogix.application.proposal;

public class ProposalNotFoundException extends RuntimeException {
    public ProposalNotFoundException() {
        super("error.proposal-not-found");
    }
}
