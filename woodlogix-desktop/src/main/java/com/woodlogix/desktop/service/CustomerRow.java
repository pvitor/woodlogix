package com.woodlogix.desktop.service;

public record CustomerRow(String id, String fullName, String phone, String email, String address) {
    @Override
    public String toString() {
        return fullName;
    }
}
