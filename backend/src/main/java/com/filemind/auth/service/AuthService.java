package com.filemind.auth.service;

import com.filemind.auth.dto.AuthResponse;
import com.filemind.auth.dto.LoginRequest;
import com.filemind.auth.dto.RegisterRequest;
import com.filemind.auth.security.JwtService;
import com.filemind.activity.entity.ActivityAction;
import com.filemind.activity.service.ActivityService;
import com.filemind.common.exception.BadRequestException;
import com.filemind.user.dto.CurrentUserResponse;
import com.filemind.user.entity.User;
import com.filemind.user.entity.UserRole;
import com.filemind.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final ActivityService activityService;

    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new BadRequestException("An account with this email already exists");
        }

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.USER);

        User savedUser = userRepository.save(user);
        activityService.log(ActivityAction.REGISTER, savedUser, null, "Created an account");
        return buildAuthResponse(savedUser);
    }

    public AuthResponse bootstrapAdmin(RegisterRequest request) {
        boolean hasAdmin = userRepository.findAll().stream()
            .anyMatch(user -> user.getRole() == UserRole.ADMIN);

        if (hasAdmin) {
            throw new BadRequestException("An admin account already exists");
        }

        String normalizedEmail = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new BadRequestException("An account with this email already exists");
        }

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.ADMIN);

        User savedUser = userRepository.save(user);
        activityService.log(ActivityAction.REGISTER, savedUser, null, "Created the first admin account");
        return buildAuthResponse(savedUser);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim().toLowerCase())
            .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        activityService.log(ActivityAction.LOGIN, user, null, "Signed in");
        return buildAuthResponse(user);
    }

    public CurrentUserResponse currentUser(User user) {
        return CurrentUserResponse.from(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtService.generateToken(user);
        long expiresAt = System.currentTimeMillis() + jwtService.getExpirationMs();
        return new AuthResponse(token, "Bearer", expiresAt, CurrentUserResponse.from(user));
    }
}
