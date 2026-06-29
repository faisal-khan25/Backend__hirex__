package com.hirex.service;

import com.hirex.dto.AuthDto;
import com.hirex.dto.AuthDto.AuthResponse;
import com.hirex.dto.AuthDto.LoginRequest;
import com.hirex.dto.AuthDto.RegisterRequest;
import com.hirex.entity.Role;
import com.hirex.entity.User;
import com.hirex.repository.UserRepository;
import com.hirex.security.JwtUtil;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authManager;

    public AuthService(UserRepository userRepo,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       AuthenticationManager authManager) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authManager = authManager;
    }

    public AuthResponse register(RegisterRequest req) {
        if (userRepo.findByEmail(req.getEmail()).isPresent()) {
            throw new RuntimeException("Email already registered");
        }
        User user = new User();
        user.setName(req.getName());
        user.setEmail(req.getEmail());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setPhone(req.getPhone());
        user.setRole(req.getRole() != null ? req.getRole() : Role.JOBSEEKER);
        userRepo.save(user);

        String token = jwtUtil.generateToken(user.getEmail());
        // FIX: pass user.getId() so the frontend receives the id
        return new AuthResponse(user.getId(), token, user.getEmail(), user.getName(), user.getRole().name());
    }

    public AuthResponse login(LoginRequest req) {
        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword())
        );
        User user = userRepo.findByEmail(req.getEmail()).orElseThrow();
        String token = jwtUtil.generateToken(user.getEmail());
        // FIX: pass user.getId() so the frontend receives the id
        return new AuthResponse(user.getId(), token, user.getEmail(), user.getName(), user.getRole().name());
    }
}