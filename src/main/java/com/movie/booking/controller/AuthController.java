package com.movie.booking.controller;

import com.movie.booking.dto.request.AuthRequest;
import com.movie.booking.dto.response.AuthResponse;
import com.movie.booking.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register and login for customers and theatre partners")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new account (CUSTOMER or THEATRE_PARTNER)")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody AuthRequest.Register request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.registerNewUser(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Login and receive a JWT bearer token")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody AuthRequest.Login request) {
        return ResponseEntity.ok(authService.loginUser(request));
    }
}
