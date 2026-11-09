package com.woodlogix.server.api.dto;

public record ApiFieldErrorResponse(String field, String code, String messageKey) {
}
