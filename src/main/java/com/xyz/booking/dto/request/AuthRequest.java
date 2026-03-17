package com.xyz.booking.dto.request;

import com.xyz.booking.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class AuthRequest {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Register {
        @NotBlank(message = "Full name is required")
        private String fullName;

        @Email(message = "A valid email address is required")
        @NotBlank(message = "Email is required")
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        private String password;

        private String phoneNumber;

        @NotNull(message = "Role is required (CUSTOMER or THEATRE_PARTNER)")
        private UserRole role;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Login {
        @Email(message = "A valid email address is required")
        @NotBlank(message = "Email is required")
        private String email;

        @NotBlank(message = "Password is required")
        private String password;
    }
}
