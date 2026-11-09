package com.woodlogix.server.api.dto;

public record CustomerRequest(String fullName, String phone, String email, String address) {
}
