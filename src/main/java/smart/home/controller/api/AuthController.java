package smart.home.controller.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import smart.home.dto.ErrorResponse;
import smart.home.dto.LoginRequest;
import smart.home.dto.LoginResponse;
import smart.home.entity.User;
import smart.home.repository.UserRepository;
import smart.home.security.JwtUtil;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @PostMapping("/login")
    @Transactional
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        log.debug("Login attempt for username: {}", request.getUsername());

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            User user = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);

            String accessToken = jwtUtil.generateAccessToken(user.getUsername(), user.getId());

            log.info("User logged in successfully: {}", user.getUsername());

            return ResponseEntity.ok(LoginResponse.builder()
                    .accessToken(accessToken)
                    .username(user.getUsername())
                    .build());

        } catch (BadCredentialsException e) {
            log.warn("Failed login attempt for username: {} - Invalid credentials", request.getUsername());
            return ResponseEntity.status(401).body(ErrorResponse.builder()
                    .message("Invalid username or password")
                    .error("Unauthorized")
                    .status(401)
                    .build());
        } catch (Exception e) {
            log.error("Login error for username: {} - {}", request.getUsername(), e.getMessage());
            return ResponseEntity.status(401).body(ErrorResponse.builder()
                    .message(e.getMessage())
                    .error("Unauthorized")
                    .status(401)
                    .build());
        }
    }
}