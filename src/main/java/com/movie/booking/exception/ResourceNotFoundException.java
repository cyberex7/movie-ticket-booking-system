package com.movie.booking.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, Long resourceId) {
        super(resourceName + " not found with id: " + resourceId);
    }
}
