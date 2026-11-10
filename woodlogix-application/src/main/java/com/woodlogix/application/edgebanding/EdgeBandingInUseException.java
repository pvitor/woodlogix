package com.woodlogix.application.edgebanding;

public class EdgeBandingInUseException extends IllegalStateException {
    public EdgeBandingInUseException() {
        super("error.edge-banding-has-related-records");
    }
}
