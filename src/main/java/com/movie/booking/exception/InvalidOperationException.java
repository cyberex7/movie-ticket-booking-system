package com.movie.booking.exception;

/**
 * Thrown when an operation is logically forbidden in the current state —
 * for example: updating a show that already has confirmed bookings,
 * or scheduling a show on a screen that has a conflicting time slot.
 */
public class InvalidOperationException extends RuntimeException {

    public InvalidOperationException(String message) {
        super(message);
    }

    public InvalidOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
