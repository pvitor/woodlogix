package com.woodlogix.application.workpiece;

public class ProjectWorkpieceNotFoundException extends RuntimeException {
    public ProjectWorkpieceNotFoundException() {
        super("error.workpiece-not-found");
    }
}
