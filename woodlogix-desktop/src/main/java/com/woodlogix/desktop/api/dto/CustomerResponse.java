package com.woodlogix.desktop.api.dto;

public record CustomerResponse(
        String id,
        String fullName,
        String phone,
        String email,
        String address
) {
}
