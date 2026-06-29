package com.hirex.dto;

import com.hirex.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class AuthDto {

    public static class RegisterRequest {
        @NotBlank
        private String name;

        @Email @NotBlank
        private String email;

        @NotBlank
        private String password;

        private String phone;
        private Role role;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }

        public Role getRole() { return role; }
        public void setRole(Role role) { this.role = role; }
    }

    public static class LoginRequest {
        @Email @NotBlank
        private String email;

        @NotBlank
        private String password;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class AuthResponse {
        private Long id;       // FIX: include user ID so frontend isOwn() works
        private String token;
        private String email;
        private String name;
        private String role;

        public AuthResponse(Long id, String token, String email, String name, String role) {
            this.id = id;
            this.token = token;
            this.email = email;
            this.name = name;
            this.role = role;
        }

        public Long getId() { return id; }
        public String getToken() { return token; }
        public String getEmail() { return email; }
        public String getName() { return name; }
        public String getRole() { return role; }
    }
}