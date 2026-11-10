package com.woodlogix.application.material;

public class MaterialInUseException extends IllegalStateException {
    public MaterialInUseException() {
        super("error.material-has-related-records");
    }
}
