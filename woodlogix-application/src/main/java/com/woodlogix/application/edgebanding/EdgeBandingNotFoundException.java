package com.woodlogix.application.edgebanding;

public class EdgeBandingNotFoundException extends RuntimeException {
    public EdgeBandingNotFoundException() {
        super("error.edge-banding-not-found");
    }
}
