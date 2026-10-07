package com.example.service.dto;
 import java.time.LocalDate;
public record ErrorDTO (
    String message,
    LocalDate date
) {
    }

