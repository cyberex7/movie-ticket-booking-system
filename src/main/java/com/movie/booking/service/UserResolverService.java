package com.movie.booking.service;

import com.movie.booking.entity.User;
import com.movie.booking.exception.ResourceNotFoundException;
import com.movie.booking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

/**
 * Utility service that bridges Spring Security's email-based principal
 * (from JWT) to the database User entity and its Long userId.
 * Controllers call resolveCurrentUser() to get the authenticated user
 * without duplicating the lookup logic.
 */
@Service
@RequiredArgsConstructor
public class UserResolverService {

    private final UserRepository userRepository;

    public User resolveCurrentUser(UserDetails principal) {
        return userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Authenticated user not found: " + principal.getUsername()));
    }

    public Long resolveCurrentUserId(UserDetails principal) {
        return resolveCurrentUser(principal).getUserId();
    }
}
