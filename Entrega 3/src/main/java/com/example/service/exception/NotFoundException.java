package com.example.service.exception;

public class NotFoundException extends RuntimeException{
    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(String entidad, Object id) {
        super(String.format("La entidad %s con id %s no existe.", entidad, id));
    }
}
