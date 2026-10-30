package com.parkgrid.auth;

import com.parkgrid.auth.dto.AuthResponse;
import com.parkgrid.auth.dto.LoginRequest;
import com.parkgrid.auth.dto.RegisterRequest;
import com.parkgrid.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "eureka.client.enabled=false")
public class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Test
    public void testRegisterAndLogin() {
        RegisterRequest registerRequest = new RegisterRequest(
                "Test Driver",
                "testdriver@parkgrid.com",
                "Password@123",
                "ROLE_USER"
        );

        AuthResponse regResponse = authService.register(registerRequest);
        assertNotNull(regResponse);
        assertNotNull(regResponse.getToken());
        assertEquals("testdriver@parkgrid.com", regResponse.getUser().getEmail());

        LoginRequest loginRequest = new LoginRequest("testdriver@parkgrid.com", "Password@123");
        AuthResponse loginResponse = authService.login(loginRequest);
        assertNotNull(loginResponse);
        assertNotNull(loginResponse.getToken());
    }

    @Test
    public void testDefaultAdminLogin() {
        LoginRequest loginRequest = new LoginRequest("admin@parkgrid.com", "Admin@123");
        AuthResponse loginResponse = authService.login(loginRequest);
        assertNotNull(loginResponse);
        assertEquals("ROLE_ADMIN", loginResponse.getUser().getRole());
    }

    @Test
    public void testUserCrudOperations() {
        RegisterRequest registerRequest = new RegisterRequest(
                "Crud Driver",
                "cruddriver@parkgrid.com",
                "Driver@123",
                "ROLE_USER"
        );
        AuthResponse reg = authService.register(registerRequest);
        Long userId = reg.getUser().getId();
        assertNotNull(userId);

        com.parkgrid.auth.dto.UserDto userDto = authService.getUserById(userId);
        assertEquals("Crud Driver", userDto.getName());

        userDto.setName("Updated Driver Name");
        com.parkgrid.auth.dto.UserDto updated = authService.updateUser(userId, userDto);
        assertEquals("Updated Driver Name", updated.getName());

        com.parkgrid.auth.dto.UserDto profile = authService.getUserProfile("Bearer " + reg.getToken());
        assertEquals("cruddriver@parkgrid.com", profile.getEmail());

        authService.deleteUser(userId);
        assertThrows(RuntimeException.class, () -> authService.getUserById(userId));
    }
}
