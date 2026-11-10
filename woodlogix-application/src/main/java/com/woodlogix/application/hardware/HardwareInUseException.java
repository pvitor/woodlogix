package com.woodlogix.application.hardware;

public class HardwareInUseException extends IllegalStateException {
    public HardwareInUseException() {
        super("error.hardware-has-related-records");
    }
}
