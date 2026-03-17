package com.xyz.booking.service;

import com.xyz.booking.dto.request.AuthRequest;
import com.xyz.booking.dto.response.AuthResponse;
import com.xyz.booking.entity.User;
import com.xyz.booking.enums.UserRole;
import com.xyz.booking.exception.BookingException;
import com.xyz.booking.repository.UserRepository;
import com.xyz.booking.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public AuthResponse registerNewUser(AuthRequest.Register request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BookingException("An account already exists for: " + request.getEmail());
        }

        User newUser = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(request.getPhoneNumber())
                .role(request.getRole() != null ? request.getRole() : UserRole.CUSTOMER)
                .isActive(true)
                .build();
        userRepository.save(newUser);

        String token = authenticateAndGenerateToken(request.getEmail(), request.getPassword());
        return buildAuthResponse(newUser, token);
    }

    public AuthResponse loginUser(AuthRequest.Login request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BookingException("No account found for: " + request.getEmail()));

        String token = authenticateAndGenerateToken(request.getEmail(), request.getPassword());
        return buildAuthResponse(user, token);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String authenticateAndGenerateToken(String email, String password) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password));
        SecurityContextHolder.getContext().setAuthentication(auth);
        return jwtTokenProvider.generateToken((UserDetails) auth.getPrincipal());
    }

    private AuthResponse buildAuthResponse(User user, String token) {
        return AuthResponse.builder()
                .accessToken(token)
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }
}
