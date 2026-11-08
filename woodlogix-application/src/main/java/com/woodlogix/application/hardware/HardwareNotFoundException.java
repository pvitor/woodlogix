package com.woodlogix.application.hardware;

public class HardwareNotFoundException extends RuntimeException {
    public HardwareNotFoundException() {
        super("error.hardware-not-found");
    }
}
