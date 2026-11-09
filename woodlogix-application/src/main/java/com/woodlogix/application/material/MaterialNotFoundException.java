package com.woodlogix.application.material;

public class MaterialNotFoundException extends RuntimeException {
    public MaterialNotFoundException() {
        super("error.material-not-found");
    }
}
