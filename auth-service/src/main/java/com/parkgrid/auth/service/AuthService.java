package com.parkgrid.auth.service;

import com.parkgrid.auth.dto.*;
import com.parkgrid.auth.entity.User;
import com.parkgrid.auth.repository.UserRepository;
import com.parkgrid.auth.security.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email address is already registered: " + request.getEmail());
        }

        String role = (request.getRole() != null && !request.getRole().isBlank()) 
                ? request.getRole().toUpperCase() 
                : "ROLE_USER";

        if (!role.startsWith("ROLE_")) {
            role = "ROLE_" + role;
        }

        User user = new User(
                request.getName(),
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                role
        );

        User savedUser = userRepository.save(user);

        String token = jwtUtil.generateToken(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getName(),
                savedUser.getRole()
        );

        UserDto userDto = new UserDto(savedUser.getId(), savedUser.getName(), savedUser.getEmail(), savedUser.getRole());
        return new AuthResponse(token, userDto);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid credentials: user not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials: password mismatch");
        }

        String token = jwtUtil.generateToken(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole()
        );

        UserDto userDto = new UserDto(user.getId(), user.getName(), user.getEmail(), user.getRole());
        return new AuthResponse(token, userDto);
    }

    public Map<String, Object> validateToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        Claims claims = jwtUtil.extractAllClaims(token);
        Map<String, Object> response = new HashMap<>();
        response.put("valid", !jwtUtil.isTokenExpired(token));
        response.put("userId", claims.get("userId"));
        response.put("email", claims.getSubject());
        response.put("name", claims.get("name"));
        response.put("role", claims.get("role"));
        return response;
    }

    public UserDto getUserProfile(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authHeader = authHeader.substring(7);
        }
        String email = jwtUtil.extractUsername(authHeader);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found for email: " + email));
        return new UserDto(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }

    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(u -> new UserDto(u.getId(), u.getName(), u.getEmail(), u.getRole()))
                .toList();
    }

    public UserDto getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + id));
        return new UserDto(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }

    public UserDto updateUser(Long id, UserDto updateDto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + id));
        if (updateDto.getName() != null && !updateDto.getName().isBlank()) {
            user.setName(updateDto.getName());
        }
        if (updateDto.getEmail() != null && !updateDto.getEmail().isBlank()) {
            user.setEmail(updateDto.getEmail());
        }
        if (updateDto.getRole() != null && !updateDto.getRole().isBlank()) {
            String role = updateDto.getRole().toUpperCase();
            if (!role.startsWith("ROLE_")) {
                role = "ROLE_" + role;
            }
            user.setRole(role);
        }
        User saved = userRepository.save(user);
        return new UserDto(saved.getId(), saved.getName(), saved.getEmail(), saved.getRole());
    }

    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new RuntimeException("User not found with ID: " + id);
        }
        userRepository.deleteById(id);
    }
}
